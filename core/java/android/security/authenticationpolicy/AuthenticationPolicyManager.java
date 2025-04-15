/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.security.authenticationpolicy;

import static android.Manifest.permission.MANAGE_SECURE_LOCK_DEVICE;
import static android.security.Flags.FLAG_SECURE_LOCKDOWN;
import static android.security.Flags.FLAG_SECURE_LOCK_DEVICE;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * AuthenticationPolicyManager is a centralized interface for managing authentication related
 * policies on the device. This includes device locking capabilities to protect users in "at risk"
 * environments.
 *
 * AuthenticationPolicyManager is designed to protect Android users by integrating with apps and
 * key system components, such as the lock screen. It is not related to enterprise control surfaces
 * and does not offer additional administrative controls.
 *
 * <p>
 * To use this class, call {@link #enableSecureLockDevice} to enable secure lock on the device.
 * This will require the caller to have the
 * {@link android.Manifest.permission#MANAGE_SECURE_LOCK_DEVICE} permission.
 *
 * <p>
 * To disable secure lock on the device, call {@link #disableSecureLockDevice}. This will require
 * the caller to have the {@link android.Manifest.permission#MANAGE_SECURE_LOCK_DEVICE} permission.
 *
 * <p>
 * To check if the device meets the requirements to enable secure lock, call
 * {@link #isSecureLockDeviceAvailable}. This will require the caller to have the
 * {@link android.Manifest.permission#MANAGE_SECURE_LOCK_DEVICE} permission.
 *
 * <p>
 * To check if secure lock is already enabled on the device, call
 * {@link #isSecureLockDeviceEnabled}. This will require the caller to have the
 * {@link android.Manifest.permission#MANAGE_SECURE_LOCK_DEVICE} permission.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(FLAG_SECURE_LOCKDOWN)
@SystemService(Context.AUTHENTICATION_POLICY_SERVICE)
public final class AuthenticationPolicyManager {
    private static final String TAG = "AuthenticationPolicyManager";

    @NonNull private final IAuthenticationPolicyService mAuthenticationPolicyService;
    @NonNull private final Context mContext;

    /**
     * Success result code for {@link #enableSecureLockDevice} and {@link #disableSecureLockDevice}.
     *
     * Secure lock device request successful.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public static final int SUCCESS = 0;

    /**
     * Error result code for {@link #enableSecureLockDevice} and {@link #disableSecureLockDevice}.
     *
     * Secure lock device request status unknown.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public static final int ERROR_UNKNOWN = 1;

    /**
     * Error result code for {@link #enableSecureLockDevice} and {@link #disableSecureLockDevice}.
     *
     * Secure lock device is unsupported.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public static final int ERROR_UNSUPPORTED = 2;


    /**
     * Error result code for {@link #enableSecureLockDevice} and {@link #disableSecureLockDevice}.
     *
     * Invalid secure lock device request params provided.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public static final int ERROR_INVALID_PARAMS = 3;


    /**
     * Error result code for {@link #enableSecureLockDevice} and {@link #disableSecureLockDevice}.
     *
     * Secure lock device is unavailable because there are no biometrics enrolled on the device.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public static final int ERROR_NO_BIOMETRICS_ENROLLED = 4;

    /**
     * Error result code for {@link #enableSecureLockDevice} and {@link #disableSecureLockDevice}.
     *
     * Secure lock device is unavailable because the device has no biometric hardware or the
     * biometric sensors do not meet
     * {@link android.hardware.biometrics.BiometricManager.Authenticators#BIOMETRIC_STRONG}
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public static final int ERROR_INSUFFICIENT_BIOMETRICS = 5;

    /**
     * Error result code for {@link #enableSecureLockDevice}.
     *
     * Secure lock is already enabled.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public static final int ERROR_ALREADY_ENABLED = 6;

    /**
     * Error result code for {@link #disableSecureLockDevice}
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public static final int ERROR_NOT_AUTHORIZED = 7;

    /**
     * Communicates the current status of a request to enable secure lock on the device.
     *
     * @hide
     */
    @IntDef(prefix = {"ENABLE_SECURE_LOCK_DEVICE_STATUS_"}, value = {
            SUCCESS,
            ERROR_UNKNOWN,
            ERROR_UNSUPPORTED,
            ERROR_INVALID_PARAMS,
            ERROR_NO_BIOMETRICS_ENROLLED,
            ERROR_INSUFFICIENT_BIOMETRICS,
            ERROR_ALREADY_ENABLED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnableSecureLockDeviceRequestStatus {}

    /**
     * Communicates the current status of a request to disable secure lock on the device.
     *
     * @hide
     */
    @IntDef(prefix = {"DISABLE_SECURE_LOCK_DEVICE_STATUS_"}, value = {
            SUCCESS,
            ERROR_UNKNOWN,
            ERROR_UNSUPPORTED,
            ERROR_INVALID_PARAMS,
            ERROR_NOT_AUTHORIZED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DisableSecureLockDeviceRequestStatus {}

    /**
     * Communicates the current status of a request to check if the device meets the requirements
     * for secure lock device.
     *
     * @hide
     */
    @IntDef(prefix = {"IS_SECURE_LOCK_DEVICE_AVAILABLE_STATUS_"}, value = {
            SUCCESS,
            ERROR_UNSUPPORTED,
            ERROR_NO_BIOMETRICS_ENROLLED,
            ERROR_INSUFFICIENT_BIOMETRICS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface IsSecureLockDeviceAvailableRequestStatus {}

    /** @hide */
    public AuthenticationPolicyManager(@NonNull Context context,
            @NonNull IAuthenticationPolicyService authenticationPolicyService) {
        mContext = context;
        mAuthenticationPolicyService = authenticationPolicyService;
    }

    /**
     * Called by a privileged component to indicate if secure lock device is available for the
     * calling user.
     *
     * @return {@link IsSecureLockDeviceAvailableRequestStatus} int indicating whether secure lock
     * device is available for the calling user. This will return {@link #SUCCESS} if the device
     * meets all requirements to enable secure lock device, {@link #ERROR_INSUFFICIENT_BIOMETRICS}
     * if the device is missing a strong biometric enrollment, {@link #ERROR_NO_BIOMETRICS_ENROLLED}
     * if the device has no biometric enrollments, or {@link #ERROR_UNSUPPORTED} if secure lock
     * device is otherwise unsupported.
     *
     * @hide
     */
    @IsSecureLockDeviceAvailableRequestStatus
    @RequiresPermission(MANAGE_SECURE_LOCK_DEVICE)
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCK_DEVICE)
    public int isSecureLockDeviceAvailable() {
        try {
            return mAuthenticationPolicyService.isSecureLockDeviceAvailable(mContext.getUser());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }


    /**
     * Called by a privileged component to remotely enable secure lock on the device across all
     * users. This operation will first check {@link #isSecureLockDeviceAvailable()} to see if the
     * calling user meets the requirements to enable secure lock device, including a strong
     * biometric enrollment, and will return an error if not.
     *
     * Secure lock is an enhanced security state that restricts access to sensitive data (app
     * notifications, widgets, quick settings, assistant, etc), and locks the device under the
     * calling user's credentials with multi-factor authentication for device entry, such as
     * {@link android.hardware.biometrics.BiometricManager.Authenticators#DEVICE_CREDENTIAL} and
     * {@link android.hardware.biometrics.BiometricManager.Authenticators#BIOMETRIC_STRONG}.
     *
     * If secure lock is already enabled when this method is called, it will return
     * {@link #ERROR_ALREADY_ENABLED}.
     *
     * @param params {@link EnableSecureLockDeviceParams} for caller to supply params related to
     *                                                   the secure lock device request
     * @return {@link EnableSecureLockDeviceRequestStatus} int indicating the result of the secure
     * lock device request. This returns {@link #SUCCESS} if secure lock device is successfully
     * enabled, or an error code indicating more information about the failure otherwise.
     *
     * @hide
     */
    @EnableSecureLockDeviceRequestStatus
    @RequiresPermission(MANAGE_SECURE_LOCK_DEVICE)
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public int enableSecureLockDevice(@NonNull EnableSecureLockDeviceParams params) {
        try {
            return mAuthenticationPolicyService.enableSecureLockDevice(mContext.getUser(), params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Called by a privileged component to disable secure lock on the device across all users. This
     * operation is restricted to the user that originally enabled the current secure lock device
     * state.
     *
     * If the calling user identity does not match the user that enabled secure lock device, it
     * will return {@link #ERROR_NOT_AUTHORIZED}
     *
     * If secure lock is already disabled when this method is called, it will return
     * {@link #SUCCESS}.
     *
     * @param params {@link DisableSecureLockDeviceParams} for caller to supply params related to
     *                                                    the secure lock device request
     * @return {@link DisableSecureLockDeviceRequestStatus} int indicating the result of the secure
     * lock device request
     *
     * @hide
     */
    @DisableSecureLockDeviceRequestStatus
    @RequiresPermission(MANAGE_SECURE_LOCK_DEVICE)
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCKDOWN)
    public int disableSecureLockDevice(@NonNull DisableSecureLockDeviceParams params) {
        try {
            return mAuthenticationPolicyService.disableSecureLockDevice(mContext.getUser(), params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Called by a privileged component to query if secure lock device is currently enabled.
     * @return true if secure lock device is enabled, false otherwise.
     *
     * @hide
     */
    @RequiresPermission(MANAGE_SECURE_LOCK_DEVICE)
    @SystemApi
    @FlaggedApi(FLAG_SECURE_LOCK_DEVICE)
    public boolean isSecureLockDeviceEnabled() {
        try {
            return mAuthenticationPolicyService.isSecureLockDeviceEnabled();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
