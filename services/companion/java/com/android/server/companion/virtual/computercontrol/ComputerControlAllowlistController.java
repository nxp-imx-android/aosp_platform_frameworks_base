/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningDetails;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Manages allowlists/denylists for computer control.
 */
final class ComputerControlAllowlistController implements DeviceConfig.OnPropertiesChangedListener {
    private static final String TAG = "ComputerControlAllowlistController";
    private static final String SIGNED_PACKAGE_SEPARATOR = ",";
    private static final String CERTIFICATE_SEPARATOR = ":";
    private static final String INCLUDE_EVERYTHING_WILDCARD = "*";
    private static final String COMPUTER_CONTROL_DIR = "computercontrol";
    private static final String SESSION_OWNER_ALLOWLIST_FILE_NAME = "session_owner_allowlist.txt";
    private static final String AUTOMATABLE_APP_ALLOWLIST_FILE_NAME =
            "automatable_app_allowlist.txt";
    private static final String AUTOMATABLE_APP_DENYLIST_FILE_NAME = "automatable_app_denylist.txt";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_NAMESPACE = "computer_control";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY = "allowed_session_owner_apps";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY = "allowed_automatable_apps";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY = "blocked_automatable_apps";

    private final Executor mBackgroundExecutor;
    private final RemoteList mSessionOwnerAllowlist;
    private final RemoteList mAutomatableAppAllowlist;
    private final RemoteList mAutomatableAppDenylist;
    private final boolean mBuildIsDebuggable;
    // In debuggable builds, we can have a list of "super agents" (defined by a config resource)
    // which are allowed to automate any app.
    private final Set<String> mSuperAgentPackages;

    ComputerControlAllowlistController(@NonNull Context context) {
        this(context, BackgroundThread.getExecutor(),
                new File(new File(Environment.getDataSystemDirectory(), COMPUTER_CONTROL_DIR),
                        SESSION_OWNER_ALLOWLIST_FILE_NAME),
                new File(new File(Environment.getDataSystemDirectory(), COMPUTER_CONTROL_DIR),
                        AUTOMATABLE_APP_ALLOWLIST_FILE_NAME),
                new File(new File(Environment.getDataSystemDirectory(), COMPUTER_CONTROL_DIR),
                        AUTOMATABLE_APP_DENYLIST_FILE_NAME),
                Build.isDebuggable());
    }

    @VisibleForTesting
    ComputerControlAllowlistController(@NonNull Context context, @NonNull Executor executor,
            @NonNull File agentAllowlistFile, @NonNull File automatableAppsAllowlistFile,
            @NonNull File automatableAppsDenylistFile, boolean buildIsDebuggable) {
        mBackgroundExecutor = executor;
        mSessionOwnerAllowlist = new RemoteList(agentAllowlistFile,
                COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY);
        mAutomatableAppAllowlist = new RemoteList(automatableAppsAllowlistFile,
                COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY);
        mAutomatableAppDenylist = new RemoteList(automatableAppsDenylistFile,
                COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY);
        mBuildIsDebuggable = buildIsDebuggable;
        final Resources resources = context.getResources();
        String[] superAgentPackages = null;
        try {
            superAgentPackages =
                    resources.getStringArray(R.array.config_computerControlKnownSuperAgents);
        } catch (Resources.NotFoundException e) {
            Slog.e(TAG, "superAgentPackages not found in resources", e);
        }
        mSuperAgentPackages = new ArraySet<>(superAgentPackages);
    }

    @Override
    public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
        final Set<String> keySet = properties.getKeyset();
        if (keySet.contains(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST_KEY)) {
            Slog.v(TAG, "DeviceConfig onPropertiesChanged: Updating session owner allowlist");
            mSessionOwnerAllowlist.update();
        }
        if (keySet.contains(COMPUTER_CONTROL_AUTOMATABLE_APP_ALLOWLIST_KEY)) {
            Slog.v(TAG, "DeviceConfig onPropertiesChanged: Updating automatable app allowlist");
            mAutomatableAppAllowlist.update();
        }
        if (keySet.contains(COMPUTER_CONTROL_AUTOMATABLE_APP_DENYLIST_KEY)) {
            Slog.v(TAG, "DeviceConfig onPropertiesChanged: Updating automatable app denylist");
            mAutomatableAppDenylist.update();
        }
    }

    void initialize() {
        if (!Flags.computerControlAllowlists()) {
            return;
        }
        Slog.v(TAG, "Initialize called, updating allowlists and adding DeviceConfig listener");

        mBackgroundExecutor.execute(() -> {
            mSessionOwnerAllowlist.update();
            mAutomatableAppAllowlist.update();
            mAutomatableAppDenylist.update();
        });
        DeviceConfig.addOnPropertiesChangedListener(
                COMPUTER_CONTROL_NAMESPACE, mBackgroundExecutor, this);
    }

    boolean isPackageAllowedToCreateSession(@Nullable String packageName,
            @NonNull PackageManager packageManager) {
        if (packageName == null) {
            return false;
        }

        // Check if the caller actually has this packageName.
        if (!packageAssociatedToCallingUid(packageName, packageManager)) {
            return false;
        }

        if (!mBuildIsDebuggable && !isPreInstalledApp(packageName, packageManager)) {
            Slog.i(TAG, packageName + " is not pre-installed, hence cannot be a session owner");
            return false;
        }

        if (isSuperAgent(packageName)) {
            Slog.i(TAG, "isPackageAllowedToCreateSession: Found super agent " + packageName);
            return true;
        }

        if (!Flags.computerControlAllowlists()) {
            return true;
        }

        final SigningDetails signingDetails = getSigningDetails(packageName, packageManager);
        if (signingDetails == null) {
            Slog.e(TAG, "isPackageAllowedToCreateSession: Failed to fetch signing details for "
                    + packageName);
            return false;
        }

        final boolean isInAllowlist = mSessionOwnerAllowlist.anyMatch(packageName, signingDetails);
        Slog.i(TAG, "isPackageAllowedToCreateSession: Is there any allowlist entry for "
                + packageName + " : " + isInAllowlist);
        return isInAllowlist;
    }

    boolean isPackageAutomatable(@Nullable String targetPackage,
            @Nullable String sessionOwnerPackage, @NonNull PackageManager packageManager) {
        if (targetPackage == null || sessionOwnerPackage == null) {
            return false;
        }

        if (isSuperAgent(sessionOwnerPackage)) {
            Slog.i(TAG, "isPackageAutomatable: Found super agent " + sessionOwnerPackage);
            return true;
        }

        if (packageManager.getLaunchIntentForPackage(targetPackage) == null) {
            Slog.i(TAG, "isPackageAutomatable: No launch intent for package " + targetPackage);
            return false;
        }

        if (Objects.equals(packageManager.getPermissionControllerPackageName(), targetPackage)) {
            Slog.i(TAG, "isPackageAutomatable: Cannot automate permission controller");
            return false;
        }

        if (!Flags.computerControlAllowlists()) {
            return true;
        }

        final SigningDetails targetPackageSigningDetails =
                getSigningDetails(targetPackage, packageManager);
        if (targetPackageSigningDetails == null) {
            Slog.e(TAG, "isPackageAutomatable: Failed to fetch signing details for target package "
                    + targetPackage);
            return false;
        }

        // Check if the app is denylisted first.
        if (mAutomatableAppDenylist.anyMatch(targetPackage, targetPackageSigningDetails)) {
            Slog.i(TAG, "isPackageAutomatable: Found denylist entry for " + targetPackage);
            return false;
        }

        // Check if the app is allowlisted.
        final boolean isInAllowlist = mAutomatableAppAllowlist.anyMatch(targetPackage,
                targetPackageSigningDetails);
        Slog.i(TAG, "isPackageAutomatable: Is there any allowlist entry for " + targetPackage
                + " : " + isInAllowlist);
        return isInAllowlist;
    }

    /**
     * Returns whether the given {@code packageName} is a "super agent". In debuggable builds, we
     * can have a list of "super agents" (defined by a config resource) which are allowed to
     * automate any app.
     */
    private boolean isSuperAgent(@NonNull String packageName) {
        if (!mBuildIsDebuggable) {
            return false;
        }
        return mSuperAgentPackages.contains(packageName);
    }

    private static boolean isPreInstalledApp(@NonNull String packageName,
            @NonNull PackageManager packageManager) {
        try {
            final ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName,
                    0);
            return applicationInfo.isSystemApp();
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "isPreInstalledApp: Failed to fetch application info for "
                    + packageName);
            return false;
        }
    }

    private static boolean packageAssociatedToCallingUid(@NonNull String packageName,
            @NonNull PackageManager packageManager) {
        try {
            final int uid = packageManager.getPackageUidAsUser(packageName,
                    Binder.getCallingUserHandle().getIdentifier());
            if (uid != Binder.getCallingUid()) {
                Slog.w(TAG, "Package " + packageName + " is not owned by calling uid " + uid);
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Failed to get uid for " + packageName, e);
            return false;
        }
        return true;
    }

    @Nullable
    private static SigningDetails getSigningDetails(@NonNull String packageName,
            @NonNull PackageManager packageManager) {
        try {
            final PackageInfo packageInfo = packageManager.getPackageInfo(packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (packageInfo == null || packageInfo.signingInfo == null) {
                return null;
            }
            return packageInfo.signingInfo.getSigningDetails();
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Failed to get package info for " + packageName, e);
        }
        return null;
    }

    private static boolean packageSignatureMatches(@NonNull String packageName,
            @NonNull SigningDetails signingDetails, @NonNull SignedPackage signedPackage) {
        if (!Objects.equals(packageName, signedPackage.getPackageName())) {
            return false;
        }

        return signingDetails.hasAncestorOrSelfWithDigest(
                Set.of(signedPackage.getCertificateDigest()));
    }

    /**
     * Parses a {@link SignedPackage} from a string input.
     *
     * @param input the package name, followed by a colon and a signing certificate digest
     * @return the parsed {@link SignedPackage}
     */
    @NonNull
    private static SignedPackage parse(@NonNull String input) throws IllegalArgumentException {
        final int certificateSeparatorIndex = input.indexOf(CERTIFICATE_SEPARATOR);
        if (certificateSeparatorIndex <= 0) {
            throw new IllegalArgumentException("Invalid signed package input: " + input);
        }
        final String packageName = input.substring(0, certificateSeparatorIndex);
        final String certificate = input.substring(certificateSeparatorIndex + 1);
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(certificate)) {
            throw new IllegalArgumentException("Invalid signed package input: " + input
                    + " , package name or certificate is empty");
        }
        return new SignedPackage(packageName, certificate);
    }

    /**
     * Parses {@link SignedPackages} from a string input.
     *
     * @param input the package names, each followed by a colon and a signing certificate digest
     * @return the parsed valid {@link SignedPackages}
     */
    @NonNull
    private static SignedPackages parseList(@NonNull String input) throws IllegalArgumentException {
        if (input.isEmpty()) {
            return new SignedPackages();
        }

        final List<SignedPackage> signedPackages = new ArrayList<>();
        final String[] parts = input.split(SIGNED_PACKAGE_SEPARATOR);
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid input string " + input);
        }

        for (int i = 0; i < parts.length; i++) {
            final String part = parts[i];
            if (INCLUDE_EVERYTHING_WILDCARD.equals(part)) {
                return new SignedPackages(true /* includeEverything */);
            }
            final SignedPackage signedPackage = parse(part);
            Slog.v(TAG, "Parsed signed package : " + signedPackage);
            signedPackages.add(signedPackage);
        }
        return new SignedPackages(signedPackages);
    }

    /**
     * Represents a remote allowlist/denylist, automatically handles persistence of valid snapshots
     * from DeviceConfig.
     */
    private static final class RemoteList {
        private final ValueStorage mStorage;
        private final String mDeviceConfigKey;
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        @Nullable
        private SignedPackages mSignedPackages = null;

        RemoteList(@NonNull File file, @NonNull String deviceConfigKey) {
            mStorage = new ValueStorage(file);
            mDeviceConfigKey = deviceConfigKey;
        }

        private boolean anyMatch(@NonNull String packageName,
                @NonNull SigningDetails signingDetails) {
            synchronized (mLock) {
                return mSignedPackages != null
                        && mSignedPackages.anyMatch(packageName, signingDetails);
            }
        }

        private void update() {
            final Pair<String, SignedPackages> parsedValueAndSignedPackages = readDeviceConfig();
            if (parsedValueAndSignedPackages != null) {
                synchronized (mLock) {
                    if (Objects.equals(parsedValueAndSignedPackages.second, mSignedPackages)) {
                        // If the parsed value is same as what we already have right now,
                        // then nothing to do.
                        return;
                    }
                }
                // Persist the parsed value;
                mStorage.writeValue(parsedValueAndSignedPackages.first);
                // Update the cached value in memory.
                setSignedPackages(parsedValueAndSignedPackages.second);
                return;
            }

            // Read last persisted value, if we didn't find a valid value in DeviceConfig.
            final SignedPackages storedSignedPackages = mStorage.readPersistedValue();
            if (storedSignedPackages == null) {
                Slog.w(TAG, "No persisted value found for key " + mDeviceConfigKey);
                return;
            }

            Slog.v(TAG, "Using previously stored value " + storedSignedPackages + " for key "
                    + mDeviceConfigKey);
            // Update the cached value in memory.
            setSignedPackages(storedSignedPackages);
        }

        @SuppressLint("MissingPermission")
        @Nullable
        private Pair<String, SignedPackages> readDeviceConfig() {
            try {
                final String value = DeviceConfig.getString(COMPUTER_CONTROL_NAMESPACE,
                        mDeviceConfigKey, null /* defaultValue */);
                Slog.v(TAG, "Read value from DeviceConfig: " + value + " for key "
                        + mDeviceConfigKey);
                if (value == null) {
                    return null;
                }
                final SignedPackages signedPackages = parseList(value);
                return new Pair<>(value, signedPackages);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to parse value from DeviceConfig for key "
                        + mDeviceConfigKey);
            }
            return null;
        }

        private void setSignedPackages(SignedPackages signedPackages) {
            synchronized (mLock) {
                mSignedPackages = signedPackages;
            }
        }
    }

    private static final class ValueStorage {
        @NonNull
        private final AtomicFile mAtomicFile;
        @NonNull
        private final Object mLock = new Object();

        /**
         * Creates an instance that manages the value at the specified file path.
         *
         * @param file The file to read from and write to.
         */
        ValueStorage(@NonNull File file) {
            mAtomicFile = new AtomicFile(file);
        }

        /**
         * Reads and parses the value from persistent storage.
         *
         * <p>If the file is found to be corrupt during reading or parsing, it will be deleted.
         *
         * @return A list of {@link SignedPackage}s if the file exists and is valid, or
         * {@code null}
         */
        @Nullable
        SignedPackages readPersistedValue() {
            synchronized (mLock) {
                if (!mAtomicFile.exists()) {
                    Slog.w(TAG, "Allowlist file does not exist.");
                    return null;
                }

                try {
                    byte[] data = mAtomicFile.readFully();
                    final String value = new String(data, StandardCharsets.UTF_8);
                    return parseList(value);
                } catch (Exception e) {
                    Slog.e(TAG, "Error reading or parsing allowlist file", e);
                    mAtomicFile.delete();
                    return null;
                }
            }
        }

        /**
         * Writes the given value to persistent storage atomically.
         *
         * @param value The raw string representation of the allowlist/denylist to be written.
         */
        void writeValue(@NonNull String value) {
            synchronized (mLock) {
                FileOutputStream fos = null;
                try {
                    fos = mAtomicFile.startWrite();
                    fos.write(value.getBytes(StandardCharsets.UTF_8));
                    mAtomicFile.finishWrite(fos);
                } catch (IOException e) {
                    Slog.e(TAG, "Error writing allowlist file", e);
                    if (fos != null) {
                        mAtomicFile.failWrite(fos);
                    }
                }
            }
        }
    }

    private static final class SignedPackages {
        private final List<SignedPackage> mSignedPackages;
        private final boolean mIncludeEverything;

        SignedPackages(@NonNull List<SignedPackage> signedPackages) {
            this(signedPackages, false /* includeEverything */);
        }

        SignedPackages(boolean includeEverything) {
            this(Collections.emptyList(), includeEverything);
        }

        SignedPackages() {
            this(Collections.emptyList(), false /* includeEverything */);
        }

        private SignedPackages(@NonNull List<SignedPackage> signedPackages,
                boolean includeEverything) {
            mSignedPackages = signedPackages;
            mIncludeEverything = includeEverything;
        }

        boolean anyMatch(@NonNull String packageName, @NonNull SigningDetails signingDetails) {
            if (mIncludeEverything) {
                Slog.i(TAG, "Allowlist match for package " + packageName
                        + " as it includes everything");
                return true;
            }

            for (int i = 0; i < mSignedPackages.size(); i++) {
                final SignedPackage allowlistedPackage = mSignedPackages.get(i);
                if (packageSignatureMatches(packageName, signingDetails, allowlistedPackage)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SignedPackages that)) {
                return false;
            }
            return mIncludeEverything == that.mIncludeEverything && Objects.equals(
                    mSignedPackages, that.mSignedPackages);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSignedPackages, mIncludeEverything);
        }

        @Override
        public String toString() {
            return "SignedPackages{" + "mSignedPackages=" + mSignedPackages
                    + ", mIncludeEverything=" + mIncludeEverything + '}';
        }
    }

    @VisibleForTesting
    static final class SignedPackage {
        private final String mPackageName;
        private final String mCertificateDigest;

        SignedPackage(@NonNull String packageName, @NonNull String certificateDigest) {
            mPackageName = packageName;
            mCertificateDigest = certificateDigest;
        }

        @NonNull
        String getPackageName() {
            return mPackageName;
        }

        @NonNull
        String getCertificateDigest() {
            return mCertificateDigest;
        }

        @Override
        public String toString() {
            return mPackageName + ":" + mCertificateDigest;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SignedPackage that)) {
                return false;
            }
            return Objects.equals(mPackageName, that.mPackageName)
                    && Objects.equals(mCertificateDigest, that.mCertificateDigest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPackageName, mCertificateDigest);
        }
    }
}
