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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningDetails;
import android.os.Binder;
import android.os.Environment;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.AtomicFile;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private static final String COMPUTER_CONTROL_DIR = "computercontrol";
    private static final String SESSION_OWNER_ALLOWLIST_FILE_NAME = "session_owner_allowlist.txt";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_NAMESPACE = "computer_control";
    @VisibleForTesting
    static final String COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST = "allowed_session_owner_apps";

    private final AllowlistStorage mSessionOwnerAllowlistStorage;
    private final PackageManager mPackageManager;
    private final Executor mBackgroundExecutor;

    private final Object mSessionOwnerAllowlistLock = new Object();
    @GuardedBy("mSessionOwnerAllowlistLock")
    private final List<SignedPackage> mSessionOwnerAllowlist = new ArrayList<>();

    ComputerControlAllowlistController(@NonNull Context context) {
        this(context, BackgroundThread.getExecutor(),
                new File(new File(Environment.getDataSystemDirectory(), COMPUTER_CONTROL_DIR),
                        SESSION_OWNER_ALLOWLIST_FILE_NAME));
    }

    @VisibleForTesting
    ComputerControlAllowlistController(@NonNull Context context, @NonNull Executor executor,
            @NonNull File agentAllowlistFile) {
        mPackageManager = context.getPackageManager();
        mBackgroundExecutor = executor;
        mSessionOwnerAllowlistStorage = new AllowlistStorage(agentAllowlistFile);
    }

    @Override
    public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
        if (properties.getKeyset().contains(COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST)) {
            updateSessionOwnerAllowlist();
        }
    }

    void initialize() {
        if (!Flags.computerControlAllowlists()) {
            return;
        }

        mBackgroundExecutor.execute(this::updateSessionOwnerAllowlist);
        DeviceConfig.addOnPropertiesChangedListener(
                COMPUTER_CONTROL_NAMESPACE, mBackgroundExecutor, this);
    }

    boolean isPackageAllowedToCreateSession(@Nullable String packageName) {
        if (!Flags.computerControlAllowlists()) {
            return true;
        }
        if (packageName == null) {
            return false;
        }

        if (!packageAssociatedToCallingUid(packageName)) {
            return false;
        }

        final SigningDetails signingDetails = getSigningDetails(packageName);
        if (signingDetails == null) {
            Slog.e(TAG, "Failed to fetch signing details for " + packageName);
            return false;
        }

        synchronized (mSessionOwnerAllowlistLock) {
            for (int i = 0; i < mSessionOwnerAllowlist.size(); i++) {
                final SignedPackage allowlistedPackage = mSessionOwnerAllowlist.get(i);
                if (packageSignatureMatches(packageName, signingDetails, allowlistedPackage)) {
                    return true;
                }
            }
        }
        Slog.w(TAG, "Could not find any allowlist entry for " + packageName);
        return false;
    }

    private boolean packageAssociatedToCallingUid(@NonNull String packageName) {
        try {
            final int uid = mPackageManager.getPackageUidAsUser(packageName,
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

    private void updateSessionOwnerAllowlist() {
        final Pair<String, List<SignedPackage>> parsedValueAndSignedPackages = readDeviceConfig();
        if (parsedValueAndSignedPackages != null) {
            synchronized (mSessionOwnerAllowlistLock) {
                if (Objects.equals(parsedValueAndSignedPackages.second, mSessionOwnerAllowlist)) {
                    // If the parsed value is same as what we already have right now,
                    // then nothing to do.
                    return;
                }
            }
            // Persist the parsed value;
            mSessionOwnerAllowlistStorage.writeCurrentAllowlist(parsedValueAndSignedPackages.first);
            // Update the cached value in memory.
            updateSessionOwnerAllowlist(parsedValueAndSignedPackages.second);
            return;
        }

        // Read last persisted value, if we didn't find a valid value in DeviceConfig.
        final List<SignedPackage> storedSignedPackages =
                mSessionOwnerAllowlistStorage.readPreviousValidAllowlist();
        if (storedSignedPackages == null) {
            Slog.w(TAG, "No persisted value found for key "
                    + COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST);
            return;
        }

        Slog.v(TAG, "Using previously stored value " + storedSignedPackages + " for key "
                + COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST);
        // Update the cached value in memory.
        updateSessionOwnerAllowlist(storedSignedPackages);
    }

    @SuppressLint("MissingPermission")
    @Nullable
    private Pair<String, List<SignedPackage>> readDeviceConfig() {
        try {
            final String value = DeviceConfig.getString(COMPUTER_CONTROL_NAMESPACE,
                    COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST, null /* defaultValue */);
            Slog.v(TAG, "Read value from DeviceConfig: " + value + " for key "
                    + COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST);
            if (value == null) {
                return null;
            }
            final List<SignedPackage> signedPackages = parseList(value);
            return new Pair<>(value, signedPackages);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to parse value from DeviceConfig for key "
                    + COMPUTER_CONTROL_SESSION_OWNER_ALLOWLIST);
        }
        return null;
    }

    private void updateSessionOwnerAllowlist(@NonNull List<SignedPackage> allowlist) {
        synchronized (mSessionOwnerAllowlistLock) {
            mSessionOwnerAllowlist.clear();
            mSessionOwnerAllowlist.addAll(allowlist);
        }
    }

    @Nullable
    private SigningDetails getSigningDetails(@NonNull String packageName) {
        try {
            final PackageInfo packageInfo = mPackageManager.getPackageInfo(packageName,
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
            @NonNull SigningDetails signingDetails,
            @NonNull SignedPackage signedPackage) {
        if (!Objects.equals(packageName, signedPackage.getPackageName())) {
            return false;
        }

        return signingDetails.hasAncestorOrSelfWithDigest(
                Set.of(signedPackage.getCertificateDigest()));
    }

    /**
     * Parse a {@link SignedPackage} from a string input.
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
     * Parse a list of {@link SignedPackage}s from a string input.
     *
     * @param input the package names, each followed by a colon and a signing certificate digest
     * @return the parsed list of valid {@link SignedPackage}s
     */
    @NonNull
    private static List<SignedPackage> parseList(@NonNull String input)
            throws IllegalArgumentException {
        final List<SignedPackage> signedPackages = new ArrayList<>();
        if (input.isEmpty()) {
            return signedPackages;
        }

        for (String signedPackageInput : input.split(SIGNED_PACKAGE_SEPARATOR)) {
            final SignedPackage signedPackage = parse(signedPackageInput);
            Slog.v(TAG, "Parsed signed package : " + signedPackage);
            signedPackages.add(signedPackage);
        }
        return signedPackages;
    }

    private static final class AllowlistStorage {
        @NonNull
        private final AtomicFile mAtomicFile;
        @NonNull
        private final Object mAllowlistStorageLock = new Object();

        /**
         * Creates an instance that manages the allowlist at the specified file path.
         *
         * @param file The file to read from and write to.
         */
        AllowlistStorage(@NonNull File file) {
            mAtomicFile = new AtomicFile(file);
        }

        /**
         * Reads and parses the allowlist from persistent storage.
         *
         * <p>If the file is found to be corrupt during reading or parsing, it will be deleted.
         *
         * @return A list of {@link SignedPackage}s if the file exists and is valid, or
         * {@code null}
         */
        @Nullable
        List<SignedPackage> readPreviousValidAllowlist() {
            synchronized (mAllowlistStorageLock) {
                if (!mAtomicFile.exists()) {
                    Slog.w(TAG, "Allowlist file does not exist.");
                    return null;
                }

                try {
                    byte[] data = mAtomicFile.readFully();
                    final String allowlistString = new String(data, StandardCharsets.UTF_8);
                    return parseList(allowlistString);
                } catch (Exception e) {
                    Slog.e(TAG, "Error reading or parsing allowlist file", e);
                    mAtomicFile.delete();
                    return null;
                }
            }
        }

        /**
         * Writes the given allowlist string to persistent storage atomically.
         *
         * @param allowlistString The raw string representation of the allowlist to be written.
         */
        void writeCurrentAllowlist(@NonNull String allowlistString) {
            synchronized (mAllowlistStorageLock) {
                FileOutputStream fos = null;
                try {
                    fos = mAtomicFile.startWrite();
                    fos.write(allowlistString.getBytes(StandardCharsets.UTF_8));
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
