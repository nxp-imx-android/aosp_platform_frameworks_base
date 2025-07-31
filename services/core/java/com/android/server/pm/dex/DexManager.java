/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.pm.dex;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.util.Slog;
import android.util.jar.StrictJarFile;

import com.android.server.pm.PackageManagerService;

import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipEntry;

/**
 * TODO(jiakaiz): Move this to DexOptHelper.
 */
public class DexManager {
    private static final String TAG = "DexManager";

    private final Context mContext;

    private BatteryManager mBatteryManager = null;
    private PowerManager mPowerManager = null;

    // An integer percentage value used to determine when the device is considered to be on low
    // power for compilation purposes.
    private final int mCriticalBatteryLevel;

    public DexManager(Context context) {
        mContext = context;

        // This is currently checked to handle tests that pass in a null context.
        // TODO(b/174783329): Modify the tests to pass in a mocked Context, PowerManager,
        //      and BatteryManager.
        if (mContext != null) {
            mPowerManager = mContext.getSystemService(PowerManager.class);

            if (mPowerManager == null) {
                Slog.wtf(TAG, "Power Manager is unavailable at time of Dex Manager start");
            }

            mCriticalBatteryLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        } else {
            // This value will never be used as the Battery Manager null check will fail first.
            mCriticalBatteryLevel = 0;
        }
    }

    /**
     * Generates log if the archive located at {@code fileName} has uncompressed dex file that can
     * be direclty mapped.
     */
    public static boolean auditUncompressedDexInApk(String fileName) {
        StrictJarFile jarFile = null;
        try {
            jarFile = new StrictJarFile(fileName,
                    false /*verify*/, false /*signatureSchemeRollbackProtectionsEnforced*/);
            Iterator<ZipEntry> it = jarFile.iterator();
            boolean allCorrect = true;
            while (it.hasNext()) {
                ZipEntry entry = it.next();
                if (entry.getName().endsWith(".dex")) {
                    if (entry.getMethod() != ZipEntry.STORED) {
                        allCorrect = false;
                        Slog.w(TAG, "APK " + fileName + " has compressed dex code " +
                                entry.getName());
                    } else if ((entry.getDataOffset() & 0x3) != 0) {
                        allCorrect = false;
                        Slog.w(TAG, "APK " + fileName + " has unaligned dex code " +
                                entry.getName());
                    }
                }
            }
            return allCorrect;
        } catch (IOException ignore) {
            Slog.wtf(TAG, "Error when parsing APK " + fileName);
            return false;
        } finally {
            try {
                if (jarFile != null) {
                    jarFile.close();
                }
            } catch (IOException ignore) {}
        }
    }

    /**
     * Translates install scenarios into compilation reasons.  This process can be influenced
     * by the state of the device.
     */
    public int getCompilationReasonForInstallScenario(int installScenario) {
        // Compute the compilation reason from the installation scenario.

        boolean resourcesAreCritical = areBatteryThermalOrMemoryCritical();
        switch (installScenario) {
            case PackageManager.INSTALL_SCENARIO_DEFAULT: {
                return PackageManagerService.REASON_INSTALL;
            }
            case PackageManager.INSTALL_SCENARIO_FAST: {
                return PackageManagerService.REASON_INSTALL_FAST;
            }
            case PackageManager.INSTALL_SCENARIO_BULK: {
                if (resourcesAreCritical) {
                    return PackageManagerService.REASON_INSTALL_BULK_DOWNGRADED;
                } else {
                    return PackageManagerService.REASON_INSTALL_BULK;
                }
            }
            case PackageManager.INSTALL_SCENARIO_BULK_SECONDARY: {
                if (resourcesAreCritical) {
                    return PackageManagerService.REASON_INSTALL_BULK_SECONDARY_DOWNGRADED;
                } else {
                    return PackageManagerService.REASON_INSTALL_BULK_SECONDARY;
                }
            }
            default: {
                throw new IllegalArgumentException("Invalid installation scenario");
            }
        }
    }

    /**
     * Fetches the battery manager object and caches it if it hasn't been fetched already.
     */
    private BatteryManager getBatteryManager() {
        if (mBatteryManager == null && mContext != null) {
            mBatteryManager = mContext.getSystemService(BatteryManager.class);
        }

        return mBatteryManager;
    }

    /**
     * Returns true if the battery level, device temperature, or memory usage are considered to be
     * in a critical state.
     */
    private boolean areBatteryThermalOrMemoryCritical() {
        BatteryManager batteryManager = getBatteryManager();
        boolean isBtmCritical = (batteryManager != null
                && batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
                    == BatteryManager.BATTERY_STATUS_DISCHARGING
                && batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    <= mCriticalBatteryLevel)
                || (mPowerManager != null
                    && mPowerManager.getCurrentThermalStatus()
                        >= PowerManager.THERMAL_STATUS_SEVERE);

        return isBtmCritical;
    }

    public static class RegisterDexModuleResult {
        public RegisterDexModuleResult() {
            this(false, null);
        }

        public RegisterDexModuleResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public final boolean success;
        public final String message;
    }
}
