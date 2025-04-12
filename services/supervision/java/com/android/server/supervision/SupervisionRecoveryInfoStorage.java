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

package com.android.server.supervision;

import android.app.supervision.SupervisionRecoveryInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Provides storage and retrieval of device supervision recovery information.
 *
 * <p>This class uses {@link SharedPreferences} as a temporary solution for persistent storage of
 * the recovery email and ID associated with device supervision.
 *
 * <p>The storage is managed as a singleton, ensuring a single point of access for recovery
 * information. Access to the shared preferences is synchronized to ensure thread safety.
 *
 * <p>TODO(b/406054267): need to figure out better solutions(binary xml) for persistent storage.
 */
public class SupervisionRecoveryInfoStorage {
    private static final String LOG_TAG = "RecoveryInfoStorage";
    private static final String PREF_NAME = "supervision_recovery_info";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ID = "id";

    private final SharedPreferences mSharedPreferences;

    private static SupervisionRecoveryInfoStorage sInstance;

    private static final Object sLock = new Object();

    private SupervisionRecoveryInfoStorage(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        File sharedPrefs = new File(Environment.getDataSystemDirectory(), PREF_NAME);
        mSharedPreferences = deviceContext.getSharedPreferences(sharedPrefs, Context.MODE_PRIVATE);
    }

    /**
     * Gets the singleton instance of {@link SupervisionRecoveryInfoStorage}.
     *
     * @param context The application context.
     * @return The singleton instance.
     */
    public static SupervisionRecoveryInfoStorage getInstance(Context context) {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new SupervisionRecoveryInfoStorage(context.getApplicationContext());
            }
            return sInstance;
        }
    }

    /**
     * Loads the device supervision recovery information from persistent storage.
     *
     * @return The {@link SupervisionRecoveryInfo} if found, otherwise {@code null}.
     */
    public SupervisionRecoveryInfo loadRecoveryInfo() {
        synchronized (sLock) {
            String email = mSharedPreferences.getString(KEY_EMAIL, null);
            String id = mSharedPreferences.getString(KEY_ID, null);

            if (email != null || id != null) {
                SupervisionRecoveryInfo info = new SupervisionRecoveryInfo();
                info.email = email;
                info.id = id;
                return info;
            }
        }
        return null;
    }

    /**
     * Saves the device supervision recovery information to persistent storage.
     *
     * @param recoveryInfo The {@link SupervisionRecoveryInfo} to save or {@code null} to clear the
     *     stored information.
     */
    public void saveRecoveryInfo(SupervisionRecoveryInfo recoveryInfo) {
        synchronized (sLock) {
            SharedPreferences.Editor editor = mSharedPreferences.edit();

            if (recoveryInfo == null) {
                editor.remove(KEY_EMAIL);
                editor.remove(KEY_ID);
            } else {
                editor.putString(KEY_EMAIL, recoveryInfo.email);
                editor.putString(KEY_ID, recoveryInfo.id);
            }
            editor.apply();
            if (!editor.commit()) {
                Log.e(LOG_TAG, "Failed to save recovery info to SharedPreferences");
            }
        }
    }
}
