/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.internal.telephony;

import android.annotation.SuppressLint;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.SigningDetails;
import android.content.pm.SignedPackage;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Slog;
import android.os.Environment;
import android.os.Process;
import android.provider.DeviceConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import com.android.internal.os.BackgroundThread;
import com.android.internal.annotations.VisibleForTesting;

/**
 * A class that monitors the DeviceConfig key for the read restriction allowlist and grants the
 * {@link AppOpsManager#OP_READ_RESTRICTED_MESSAGES} to the selected applications.
 */
public class MessagingReadRestrictionAllowlistMonitor
    implements DeviceConfig.OnPropertiesChangedListener {
    private static final String TAG = "MessagingReadRestrictionAllowlistMonitor";
    private static final String SIGNED_PACKAGE_SEPARATOR = ",";
    private static final String CERTIFICATE_SEPARATOR = ":";
    private static final String KEY_READ_RESTRICTION_ALLOWLIST
        = "messaging_read_restriction_allowlist";
    private static final String READ_RESTRICTION_ALLOWLIST_FILE_NAME
        = "messaging_read_restriction_allowlist.txt";
    private static final String TELEPHONY_DIR = "telephony";

    private final Executor mBackgroundExecutor;
    private DeviceConfigAllowlist mDeviceConfigAllowlist;

    public MessagingReadRestrictionAllowlistMonitor(Context context) {
        this(context, new File(new File(Environment.getDataSystemDirectory(), TELEPHONY_DIR),
             READ_RESTRICTION_ALLOWLIST_FILE_NAME), BackgroundThread.getExecutor());
    }

    @VisibleForTesting
    public MessagingReadRestrictionAllowlistMonitor(Context context, File allowlistFile,
            Executor executor) {
        mBackgroundExecutor = executor;
        mDeviceConfigAllowlist
            = new DeviceConfigAllowlist(context, allowlistFile, KEY_READ_RESTRICTION_ALLOWLIST);
    }

    public void initialize() {
        mBackgroundExecutor.execute(() -> {
            mDeviceConfigAllowlist.update();
        });

        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_TELEPHONY,
            mBackgroundExecutor, this);
    }

    @Override
    public void onPropertiesChanged(DeviceConfig.Properties properties) {
        if (!properties.getKeyset().contains(KEY_READ_RESTRICTION_ALLOWLIST)) {
            return;
        }
        mDeviceConfigAllowlist.update();
    }

    /**
     * Class that represents a remote allowlist. It handles reading and writing the allowlist to
     * persistent storage and updating the AppOps modes for the packages in the allowlist.
     */
    private static final class DeviceConfigAllowlist {
        private final Context mContext;
        private final ValueStorage mStorage;
        private final String mDeviceConfigKey;
        private Allowlist mAllowlist = Allowlist.EMPTY_ALLOWLIST;

        DeviceConfigAllowlist(@NonNull Context context, @NonNull File file,
                @NonNull String deviceConfigKey) {
            mContext = context;
            mStorage = new ValueStorage(file);
            mDeviceConfigKey = deviceConfigKey;
        }

        private synchronized void update() {
            if(mAllowlist == null) {
                final Allowlist storedAllowlist = mStorage.readPersistedValue();
                if (storedAllowlist == null) {
                    Slog.w(TAG, "Failed to initialize allowlist from storage.");
                    setAllowlist(Allowlist.EMPTY_ALLOWLIST);
                } else {
                    Slog.v(TAG, "Initialized allowlist from storage. Updating AppOps.");
                    updateAppOps(mContext, Allowlist.EMPTY_ALLOWLIST, storedAllowlist);
                    setAllowlist(storedAllowlist);
                }
            }

            final Allowlist deviceConfigAllowlist = readDeviceConfig();
            if (deviceConfigAllowlist == null) {
                Slog.w(TAG, "Failed to read allowlist from DeviceConfig.");
                return;
            }
            Slog.v(TAG, "Reading allowlist from DeviceConfig.");
            if (isSameAllowlistObject(deviceConfigAllowlist)) {
                Slog.v(TAG, "DeviceConfig allowlist is the same as the current allowlist.");
                return;
            }
            updateAppOps(mContext, mAllowlist, deviceConfigAllowlist);
            Slog.v(TAG, "Updated AppOps for DeviceConfig allowlist.");
            setAllowlist(deviceConfigAllowlist);
            Slog.v(TAG, "Wrote updated DeviceConfig allowlist to memory.");
            mStorage.writeValue(deviceConfigAllowlist.getStringValue());
            Slog.v(TAG, "Wrote updated DeviceConfig allowlist to storage.");
        }

        @SuppressLint("MissingPermission")
        @Nullable
        private Allowlist readDeviceConfig() {
            try {
                final String value = DeviceConfig.getString(DeviceConfig.NAMESPACE_TELEPHONY,
                        mDeviceConfigKey, null /* defaultValue */);
                Slog.v(TAG, "Value retrieved from DeviceConfig: for key " + mDeviceConfigKey);
                if (value == null) {
                    return null;
                }
                return Allowlist.parse(value);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to parse value from DeviceConfig for key "
                        + mDeviceConfigKey);
            }
            return null;
        }

        /**
         * Updates the cached allowlist in memory.
         *
         * @param allowlist The new allowlist to be cached.
         */
        void setAllowlist(Allowlist allowlist) {
            mAllowlist = allowlist;
        }

        /**
         * Checks if the given allowlist is the same object as the current allowlist.
         *
         * @param allowlist The allowlist to be compared with the current allowlist
         */
        boolean isSameAllowlistObject(@NonNull Allowlist allowlist) {
            return Objects.equals(allowlist, mAllowlist);
        }
    }

    private static void updateAppOps(Context context, Allowlist storedAllowlist,
            Allowlist updatedAllowlist) {
        final PackageManager packageManager = context.getPackageManager();
        final AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);

        storedAllowlist.getAppOpChanges(updatedAllowlist)
            .forEach(change -> change.apply(packageManager, appOpsManager));
    }

    private static void setAppOpMode(AppOpsManager appOpsManager, @NonNull String packageName,
            int uid, int mode) {
        try {
            appOpsManager.setMode(AppOpsManager.OP_READ_RESTRICTED_MESSAGES,
                    uid, packageName, mode);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to set AppOp mode for " + packageName, e);
        }
    }

    @Nullable
    private static ApplicationInfo getApplicationInfo(@Nullable String packageName,
            @NonNull PackageManager packageManager) {
        if (packageName == null) {
            return null;
        }
        try {
            return packageManager.getApplicationInfo(packageName,
                    PackageManager.ApplicationInfoFlags.of(0));
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "getApplicationInfo: Failed to fetch application info for "
                    + packageName);
            return null;
        }
    }

    private static int getPackageUid(@NonNull String packageName,
            @NonNull PackageManager packageManager) {
        final ApplicationInfo applicationInfo = getApplicationInfo(packageName, packageManager);
        if (applicationInfo == null) {
            return Process.INVALID_UID;
        }
        return applicationInfo.uid;
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

    /**
     * Class that handles reading and writing the allowlist to persistent storage. It persists the
     * allowlist to a file in the system directory. The allowlist is written in a format that is
     * parsable by the {@link Allowlist} class - a comma separated list of package names and
     * certificate hashes, e.g. "package1:sha256_cert1,package2:sha256_cert2".
     *
     * The class is not thread-safe and the access to it should be synchronized by the caller.
     */
    private static final class ValueStorage {
        @NonNull
        private final AtomicFile mAtomicFile;

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
         * This method is not thread-safe and read/write atomicity has to be handled by the
         * caller.
         *
         * @return A list of {@link AllowlistEntry} wrapped in an {@link Allowlist} object if the
         * file exists and is valid, or {@code null}
         */
        @Nullable
        Allowlist readPersistedValue() {
            if (!mAtomicFile.exists()) {
                Slog.w(TAG, "Allowlist file does not exist.");
                return null;
            }

            try {
                byte[] data = mAtomicFile.readFully();
                final String value = new String(data, StandardCharsets.UTF_8);
                return Allowlist.parse(value);
            } catch (Exception e) {
                Slog.e(TAG, "Error reading or parsing allowlist file", e);
                mAtomicFile.delete();
                return null;
            }
        }

        /**
         * Writes the given value to persistent storage atomically.
         *
         * This method is not thread-safe and read/write atomicity has to be handled by the
         * caller.
         *
         * @param value The raw string representation of the allowlist/denylist to be written.
         */
        void writeValue(@NonNull String value) {
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

    record Allowlist(Set<AllowlistedPackage> packages) {
        static final Allowlist EMPTY_ALLOWLIST = new Allowlist(Collections.emptySet());

        static Allowlist parse(String input) {
            if (input == null || input.isEmpty()) {
                return EMPTY_ALLOWLIST;
            }
            return new Allowlist(
                Arrays.stream(input.split(SIGNED_PACKAGE_SEPARATOR))
                    .map(AllowlistedPackage::parse)
                    .collect(Collectors.toSet()));
        }

        String getStringValue() {
            return packages.stream().map(AllowlistedPackage::toString)
                    .collect(Collectors.joining(SIGNED_PACKAGE_SEPARATOR));
        }

        List<AppOpChange> getAppOpChanges(@NonNull Allowlist updatedAllowlist) {
            List<AppOpChange> changes = new ArrayList<>();
            Set<AllowlistedPackage> updatedPackages = updatedAllowlist.packages();

            // Identify packages that are no longer allowlisted, set to MODE_DEFAULT
            for (AllowlistedPackage pkg : this.packages) {
                if (!updatedPackages.contains(pkg)) {
                    changes.add(new AppOpChange(AppOpsManager.MODE_DEFAULT, pkg));
                }
            }

            // Identify packages that are newly allowlisted, set to MODE_ALLOWED
            for (AllowlistedPackage pkg : updatedPackages) {
                if (!this.packages.contains(pkg)) {
                    changes.add(new AppOpChange(AppOpsManager.MODE_ALLOWED, pkg));
                }
            }
            return changes;
        }
    }

    record AllowlistedPackage(String packageName, String sha256Certificate) {
        static AllowlistedPackage parse(String input) {
            String[] parts = input.split(CERTIFICATE_SEPARATOR);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid allowlist entry: " + input);
            }
            return new AllowlistedPackage(parts[0], parts[1]);
        }

        @Override
        public String toString() {
            return packageName + CERTIFICATE_SEPARATOR + sha256Certificate;
        }
    }

    record AppOpChange(@AppOpsManager.Mode int mode, AllowlistedPackage packageEntry) {
        void apply(@NonNull PackageManager packageManager, @NonNull AppOpsManager appOpsManager) {
            if (mode == AppOpsManager.MODE_DEFAULT) {
                final int uid = getPackageUid(packageEntry.packageName(), packageManager);
                if (uid < 0) {
                    return;
                }
                Slog.v(TAG, "Setting AppOp mode to default for " + packageEntry.packageName());
                setAppOpMode(appOpsManager, packageEntry.packageName(), uid, mode);
            } else if (mode == AppOpsManager.MODE_ALLOWED) {
                final ApplicationInfo applicationInfo = getApplicationInfo(
                        packageEntry.packageName(), packageManager);
                if (applicationInfo == null || applicationInfo.uid < 0) {
                    return;
                }
                final int uid = applicationInfo.uid;
                SigningDetails signingDetails = getSigningDetails(packageEntry.packageName(),
                        packageManager);
                if (signingDetails == null || !isPreInstalledApp(applicationInfo)) {
                    Slog.v(TAG, "signingDetails is null or package is not preinstalled");
                    return;
                }

                if (packageSignatureMatches(packageEntry.packageName(), signingDetails,
                        packageEntry)) {
                    Slog.v(TAG, "Setting AppOp mode to allowed for " + packageEntry.packageName());
                    setAppOpMode(appOpsManager, packageEntry.packageName(), uid, mode);
                }
            }
        }
    }

    private static boolean packageSignatureMatches(@NonNull String packageName,
            @NonNull SigningDetails signingDetails, @NonNull AllowlistedPackage entry) {
        if (!Objects.equals(packageName, entry.packageName())) {
            return false;
        }

        return signingDetails.hasAncestorOrSelfWithDigest(
                Set.of(entry.sha256Certificate()));
    }

    private static boolean isPreInstalledApp(@Nullable ApplicationInfo applicationInfo) {
        return applicationInfo != null && applicationInfo.isSystemApp();
    }
}
