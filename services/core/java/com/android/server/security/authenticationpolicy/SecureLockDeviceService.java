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

package com.android.server.security.authenticationpolicy;

import static android.content.Context.STATUS_BAR_SERVICE;
import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static android.security.Flags.secureLockDevice;
import static android.security.Flags.secureLockdown;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_ALREADY_ENABLED;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_INSUFFICIENT_BIOMETRICS;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_NOT_AUTHORIZED;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_NO_BIOMETRICS_ENROLLED;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_UNKNOWN;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.ERROR_UNSUPPORTED;
import static android.security.authenticationpolicy.AuthenticationPolicyManager.SUCCESS;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.hardware.biometrics.BiometricEnrollmentStatus;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricStateListener;
import android.hardware.biometrics.SensorProperties;
import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.UsbManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.AuthenticationPolicyManager.DisableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.EnableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.GetSecureLockDeviceAvailabilityRequestStatus;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.security.authenticationpolicy.ISecureLockDeviceStatusListener;
import android.util.Slog;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.IVoiceInteractionManagerService;
import com.android.internal.statusbar.IStatusBarService;
import com.android.server.IoThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.security.authenticationpolicy.settings.SecureLockDeviceSettingsManager;
import com.android.server.security.authenticationpolicy.settings.SecureLockDeviceSettingsManagerImpl;
import com.android.server.security.authenticationpolicy.settings.SecureLockDeviceStore;
import com.android.server.wm.WindowManagerInternal;

import java.util.Map;
import java.util.Objects;

/**
 * System service for remotely calling secure lock on the device.
 *
 * Callers will access this class via {@link AuthenticationPolicyService}.
 *
 * @see AuthenticationPolicyService
 * @see AuthenticationPolicyManager#enableSecureLockDevice
 * @see AuthenticationPolicyManager#disableSecureLockDevice
 * @hide
 */
public class SecureLockDeviceService extends SecureLockDeviceServiceInternal {
    private static final String TAG = "SecureLockDeviceService";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    @Nullable private final BiometricManager mBiometricManager;
    private final Context mContext;
    @Nullable private final FaceManager mFaceManager;
    @Nullable private final FingerprintManager mFingerprintManager;
    @NonNull private final PowerManager mPowerManager;
    @NonNull private final Object mSecureLockDeviceStatusListenerLock = new Object();
    @NonNull private final SecureLockDeviceStore mStore;
    @NonNull private final SecureLockDeviceSettingsManager mSecureLockDeviceSettingsManager;

    private final RemoteCallbackList<ISecureLockDeviceStatusListener>
            mSecureLockDeviceStatusListeners = new RemoteCallbackList<>();

    // Not final because initialized after SecureLockDeviceService in SystemServer
    private ActivityManager mActivityManager;
    private WindowManagerInternal mWindowManagerInternal;

    SecureLockDeviceService(@NonNull Context context,
            @NonNull SecureLockDeviceSettingsManager settingsManager,
            @Nullable BiometricManager biometricManager, @Nullable FaceManager faceManager,
            @Nullable FingerprintManager fingerprintManager, @NonNull PowerManager powerManager) {
        mContext = context;
        mBiometricManager = biometricManager;
        mFaceManager = faceManager;
        mFingerprintManager = fingerprintManager;
        mPowerManager = powerManager;
        mSecureLockDeviceSettingsManager = settingsManager;
        mSecureLockDeviceSettingsManager.resetManagedSettings();
        mStore = new SecureLockDeviceStore(IoThread.getHandler(), mSecureLockDeviceSettingsManager);
    }

    /**
     * Creates a new instance of SecureLockDeviceService.
     * @param context {@link Context} for this service
     * @return {@link SecureLockDeviceService} instance
     */
    public static SecureLockDeviceService create(@NonNull Context context) {
        SecureLockDeviceSettingsManager settingsManager = new SecureLockDeviceSettingsManagerImpl(
                context,
                ActivityTaskManager.getInstance(),
                IStatusBarService.Stub.asInterface(ServiceManager.getService(STATUS_BAR_SERVICE)),
                IVoiceInteractionManagerService.Stub.asInterface(ServiceManager.getService(
                        Context.VOICE_INTERACTION_MANAGER_SERVICE))
        );

        return new SecureLockDeviceService(
                context,
                settingsManager,
                context.getSystemService(BiometricManager.class),
                context.getSystemService(FaceManager.class),
                context.getSystemService(FingerprintManager.class),
                Objects.requireNonNull(context.getSystemService(PowerManager.class))
        );
    }

    @NonNull
    @VisibleForTesting
    SecureLockDeviceStore getStore() {
        return mStore;
    }

    private void start() {
        // Expose private service for system components to use.
        LocalServices.addService(SecureLockDeviceServiceInternal.class, this);
    }

    /**
     * Attempts to re-enable secure lock device after boot. Returns true if successful, false
     * otherwise.
     */
    private boolean enableSecureLockDeviceAfterBoot() {
        // Switch users to the user who enabled secure lock device, in order to lock the device
        // under their credentials.
        int secureLockDeviceClientId = mStore.retrieveSecureLockDeviceClientId();
        UserHandle userWhoEnabledSecureLockDevice = UserHandle.of(secureLockDeviceClientId);
        boolean result = switchUserToForeground(userWhoEnabledSecureLockDevice);
        if (!result) {
            if (DEBUG) {
                Slog.d(TAG, "Failed to switch calling user to foreground.");
            }
            return false;
        }
        mSecureLockDeviceSettingsManager.enableSecurityFeaturesFromBoot(secureLockDeviceClientId);

        // TODO (b/398058587): Set strong auth flags for user to configure allowed auth types

        mStore.storeSecureLockDeviceEnabled(secureLockDeviceClientId);
        notifyAllSecureLockDeviceListenersEnabledStatusUpdated();

        if (DEBUG) {
            Slog.d(TAG, "Secure lock device is re-enabled after boot");
        }
        return true;
    }

    private void listenForBiometricEnrollmentChanges() {
        if (mFaceManager != null) {
            mFaceManager.registerBiometricStateListener(
                    new BiometricStateListener() {
                        @Override
                        public void onEnrollmentsChanged(int userId, int sensorId,
                                boolean hasEnrollments) {
                            notifySecureLockDeviceAvailabilityForUser(userId);
                        }
                    });
        } else {
            Slog.i(TAG, "FaceManager is null: not registering listener");
        }

        if (mFingerprintManager != null) {
            mFingerprintManager.registerBiometricStateListener(
                    new BiometricStateListener() {
                        @Override
                        public void onEnrollmentsChanged(int userId, int sensorId,
                                boolean hasEnrollments) {
                            notifySecureLockDeviceAvailabilityForUser(userId);
                        }
                    });
        } else {
            Slog.i(TAG, "FingerprintManager is null: not registering listener");
        }
    }

    /**
     * @see AuthenticationPolicyManager#registerSecureLockDeviceStatusListener
     */
    @Override
    public void registerSecureLockDeviceStatusListener(@NonNull UserHandle user,
            @NonNull ISecureLockDeviceStatusListener listener) {
        @GetSecureLockDeviceAvailabilityRequestStatus int secureLockDeviceAvailability =
                getSecureLockDeviceAvailability(user);
        synchronized (mSecureLockDeviceStatusListenerLock) {
            boolean isSecureLockDeviceEnabled = isSecureLockDeviceEnabled();

            // Register the listener with the UserHandle as its identifying cookie
            if (mSecureLockDeviceStatusListeners.register(listener, user)) {
                if (DEBUG) {
                    Slog.d(TAG, "Registered listener: " + listener + " for user "
                            + user.getIdentifier());
                }
                try {
                    listener.onSecureLockDeviceAvailableStatusChanged(secureLockDeviceAvailability);
                    listener.onSecureLockDeviceEnabledStatusChanged(
                            isSecureLockDeviceEnabled);
                    if (DEBUG) {
                        Slog.d(TAG, "Sent initial enabled state " + isSecureLockDeviceEnabled
                                + " and available state " + secureLockDeviceAvailability
                                + " to listener " + listener.asBinder() + "for user "
                                + user.getIdentifier());
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed initial callback to listener for user "
                            + user.getIdentifier() + ", unregistering listener.", e);
                    mSecureLockDeviceStatusListeners.unregister(listener);
                }
            } else {
                Slog.w(TAG, "Failed to register listener " + listener.asBinder() + " for user "
                        + user.getIdentifier());
            }
        }
    }

    /**
     * @see AuthenticationPolicyManager#unregisterSecureLockDeviceStatusListener
     */
    @Override
    public void unregisterSecureLockDeviceStatusListener(
            @NonNull ISecureLockDeviceStatusListener listener) {
        synchronized (mSecureLockDeviceStatusListenerLock) {
            if (mSecureLockDeviceStatusListeners.unregister(listener)) {
                if (DEBUG) {
                    Slog.d(TAG, "Unregistered listener: " + listener.asBinder());
                }
            } else {
                Slog.w(TAG, "Failed to unregister listener: " + listener.asBinder());
            }
        }
    }

    /**
     * Sets up references to system services initialized after SecureLockDeviceService in
     * SystemServer, and restores secure lock device after boot if needed.
     */
    @VisibleForTesting
    void onLockSettingsReady() {
        if (DEBUG) {
            Slog.d(TAG, "onLockSettingsReady()");
        }
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
    }

    @VisibleForTesting
    void onBootCompleted() {
        if (DEBUG) {
            Slog.d(TAG, "onBootCompleted()");
        }

        listenForBiometricEnrollmentChanges();

        mSecureLockDeviceSettingsManager.initSettingsControllerDependencies(
                mContext.getSystemService(DevicePolicyManager.class),
                NfcAdapter.getDefaultAdapter(mContext),
                mContext.getSystemService(UsbManager.class),
                LocalServices.getService(IUsbManagerInternal.class)
        );

        if (isSecureLockDeviceEnabled()) {
            if (DEBUG) {
                Slog.d(TAG, "Restoring secure lock device enabled state after boot");
            }
            boolean enableStatus = enableSecureLockDeviceAfterBoot();
            if (!enableStatus) {
                Slog.e(TAG, "Failed to re-enable secure lock device after boot");
            }
        }
    }

    /**
     * @param user {@link UserHandle} to check that secure lock device is available fo
     * @return {@link GetSecureLockDeviceAvailabilityRequestStatus} int indicating whether secure
     * lock device is available for the calling user
     * @hide
     * @see AuthenticationPolicyManager#getSecureLockDeviceAvailability()
     */
    @Override
    @GetSecureLockDeviceAvailabilityRequestStatus
    public int getSecureLockDeviceAvailability(UserHandle user) {
        if (!secureLockDevice()) {
            return ERROR_UNSUPPORTED;
        }

        if (mBiometricManager == null) {
            Slog.w(TAG, "BiometricManager not available: secure lock device is unsupported.");
            return ERROR_UNSUPPORTED;
        } else if (!hasStrongBiometricSensor()) {
            if (DEBUG) {
                Slog.d(TAG, "Secure lock device unavailable: device does not have biometric"
                        + "sensors of sufficient strength.");
            }
            return ERROR_INSUFFICIENT_BIOMETRICS;
        } else if (!hasStrongBiometricsEnrolled(user)) {
            if (DEBUG) {
                Slog.d(TAG, "Secure lock device unavailable: device is missing enrollments "
                        + "for strong biometric sensor.");
            }
            return ERROR_NO_BIOMETRICS_ENROLLED;
        } else {
            return SUCCESS;
        }
    }

    private boolean hasStrongBiometricSensor() {
        for (SensorProperties sensorProps : mBiometricManager.getSensorProperties()) {
            if (sensorProps.getSensorStrength() == SensorProperties.STRENGTH_STRONG) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStrongBiometricsEnrolled(UserHandle user) {
        Context userContext = mContext.createContextAsUser(user, 0);
        BiometricManager biometricManager = userContext.getSystemService(BiometricManager.class);

        if (biometricManager == null) {
            Slog.w(TAG, "BiometricManager not available, strong biometric enrollment cannot be "
                    + "checked.");
            return false;
        }
        Map<Integer, BiometricEnrollmentStatus> enrollmentStatusMap =
                biometricManager.getEnrollmentStatus();

        for (BiometricEnrollmentStatus status : enrollmentStatusMap.values()) {
            if (status.getStrength() == BIOMETRIC_STRONG && status.getEnrollmentCount() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param user   {@link UserHandle} of caller requesting to enable secure lock device
     * @param params {@link EnableSecureLockDeviceParams} for caller to supply params related
     *               to the secure lock device request
     * @return {@link EnableSecureLockDeviceRequestStatus} int indicating the result of the
     * secure lock device request
     * @hide
     * @see AuthenticationPolicyManager#enableSecureLockDevice
     */
    @Override
    @EnableSecureLockDeviceRequestStatus
    public int enableSecureLockDevice(UserHandle user, EnableSecureLockDeviceParams params) {
        if (!secureLockdown()) {
            return ERROR_UNSUPPORTED;
        }
        int secureLockDeviceAvailability = getSecureLockDeviceAvailability(user);
        if (secureLockDeviceAvailability != SUCCESS) {
            return secureLockDeviceAvailability;
        }

        if (isSecureLockDeviceEnabled()) {
            if (DEBUG) {
                Slog.d(TAG, "Device is already in secure lock device.");
            }
            return ERROR_ALREADY_ENABLED;
        }

        // Switch to the context user enabling secure lock device, in order to lock the device
        // under their credentials.
        boolean result = switchUserToForeground(user);
        if (!result) {
            if (DEBUG) {
                Slog.d(TAG, "Failed to switch calling user " + user + " to "
                        + "foreground, returning error.");
            }
            return ERROR_UNKNOWN;
        }

        // TODO (b/398058587): Set strong auth flags for user to configure allowed auth types

        mPowerManager.goToSleep(SystemClock.uptimeMillis(),
                PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN, 0);
        mWindowManagerInternal.lockNow();

        int userId = user.getIdentifier();
        mSecureLockDeviceSettingsManager.enableSecurityFeatures(userId);
        mStore.storeSecureLockDeviceEnabled(userId);
        notifyAllSecureLockDeviceListenersEnabledStatusUpdated();
        Slog.d(TAG, "Secure lock device is enabled");

        return SUCCESS;
    }

    /**
     * Clears two factor authentication device entry requirement, clears strong auth flags
     * associated with secure lock device, and disables security features.
     *
     * @param user   {@link UserHandle} of caller requesting to disable secure lock device
     * @param params {@link DisableSecureLockDeviceParams} for caller to supply params related
     *               to the secure lock device request
     * @return {@link DisableSecureLockDeviceRequestStatus} int indicating the result of the
     * secure lock device request
     * @hide
     * @see AuthenticationPolicyManager#disableSecureLockDevice
     */
    @Override
    @DisableSecureLockDeviceRequestStatus
    public int disableSecureLockDevice(UserHandle user, DisableSecureLockDeviceParams params) {
        if (!secureLockdown()) {
            return ERROR_UNSUPPORTED;
        } else if (!isSecureLockDeviceEnabled()) {
            if (DEBUG) {
                Slog.d(TAG, "Secure lock device is already disabled.");
            }
            return SUCCESS;
        }

        int secureLockDeviceClientId = mStore.retrieveSecureLockDeviceClientId();
        int callingUserId = user.getIdentifier();

        // Verify calling user matches the user who enabled secure lock device
        // or is a system/admin user with override privileges
        if (secureLockDeviceClientId != UserHandle.USER_NULL
                && callingUserId != secureLockDeviceClientId) {
            Slog.w(TAG, "User " + callingUserId + " attempted to disable secure lock device "
                    + "enabled by user " + secureLockDeviceClientId);
            return ERROR_NOT_AUTHORIZED;
        }

        // (1) Call into system_server to reset allowed auth types
        // TODO (b/398058587): Reset strong auth flags for user
        disableSecurityFeatures(secureLockDeviceClientId);

        mStore.storeSecureLockDeviceDisabled();
        notifyAllSecureLockDeviceListenersEnabledStatusUpdated();
        Slog.d(TAG, "Secure lock device is disabled");

        return SUCCESS;
    }

    /**
     * @return true if secure lock device is enabled, false otherwise
     * @see AuthenticationPolicyManager#isSecureLockDeviceEnabled()
     */
    @Override
    public boolean isSecureLockDeviceEnabled() {
        if (!secureLockDevice()) {
            return false;
        }

        return mStore.retrieveSecureLockDeviceEnabled();
    }

    /**
     * Attempts to switch the target user to foreground if not already in the foreground before
     * enabling secure lock device.
     * Returns true on success, false otherwise
     *
     * @param targetUser userId of the user that is requesting to enable secure lock device
     * @return true if user was switched to foreground, false otherwise
     */
    private boolean switchUserToForeground(UserHandle targetUser) {
        // Switch to the user associated with the calling process if not currently in the foreground
        try {
            if (!mActivityManager.isProfileForeground(targetUser)) {
                Slog.i(TAG, "Target user " + targetUser + " is not currently in the "
                        + "foreground. Attempting switch before locking.");
                if (!mActivityManager.switchUser(targetUser)) {
                    Slog.e(TAG, "Failed to switch to user " + targetUser + ", returning error "
                            + ERROR_UNKNOWN);
                    return false;
                }
                Slog.i(TAG, "User switch to " + targetUser + " initiated.");
            }
        } catch (Exception e) {
            Slog.e(TAG, "Exception during user switch attempt", e);
            return false;
        }
        return true;
    }

    /**
     * Notifies all registered listeners about updates to secure lock device enabled / disabled
     * status.
     */
    private void notifyAllSecureLockDeviceListenersEnabledStatusUpdated() {
        boolean isSecureLockDeviceEnabled = isSecureLockDeviceEnabled();
        int count = mSecureLockDeviceStatusListeners.beginBroadcast();
        try {
            while (count > 0) {
                count--;
                ISecureLockDeviceStatusListener listener =
                        mSecureLockDeviceStatusListeners.getBroadcastItem(count);
                // Fetches the user who registered this listener
                UserHandle user =
                        (UserHandle) mSecureLockDeviceStatusListeners.getBroadcastCookie(count);
                if (user == null) {
                    Slog.w(TAG, "Couldn't find UserHandle for listener "
                            + listener.asBinder() + ", skipping notification");
                    continue;
                }

                @GetSecureLockDeviceAvailabilityRequestStatus
                int secureLockDeviceAvailability = getSecureLockDeviceAvailability(user);

                if (DEBUG) {
                    Slog.d(TAG, "Notifying listener " + listener.asBinder() + " for user "
                            + user.getIdentifier() + " of secure lock device status update: "
                            + "enabled = " + isSecureLockDeviceEnabled + ", available = "
                            + secureLockDeviceAvailability);
                }
                try {
                    listener.onSecureLockDeviceAvailableStatusChanged(secureLockDeviceAvailability);
                    listener.onSecureLockDeviceEnabledStatusChanged(isSecureLockDeviceEnabled);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to notify listener " + listener.asBinder() + " for "
                            + "user " + user.getIdentifier() + ", RemoteException thrown: ", e);
                } catch (Exception e) {
                    Slog.e(TAG, "Exception thrown by listener " + listener.asBinder() + " for "
                            + "user " + user.getIdentifier() + " during callback: ", e);
                    mSecureLockDeviceStatusListeners.unregister(listener);
                }
            }
        } finally {
            mSecureLockDeviceStatusListeners.finishBroadcast();
        }
    }

    private void disableSecurityFeatures(int userId) {
        mSecureLockDeviceSettingsManager.restoreOriginalSettings(userId);
    }

    /**
     * Notifies registered listeners associated with {@param targetUserId} about availability
     * updates to secure lock device. This is called on user-specific events like biometric
     * enrollment changes.
     *
     * @param userId the user id associated with the secure lock device availability status
     *               update. Only listeners registered to this userId will be notified.s
     */
    private void notifySecureLockDeviceAvailabilityForUser(int userId) {
        synchronized (mSecureLockDeviceStatusListenerLock) {
            final int count = mSecureLockDeviceStatusListeners.beginBroadcast();
            try {
                for (int i = 0; i < count; i++) {
                    ISecureLockDeviceStatusListener listener =
                            mSecureLockDeviceStatusListeners.getBroadcastItem(i);
                    UserHandle registeringUserHandle =
                            (UserHandle) mSecureLockDeviceStatusListeners.getBroadcastCookie(i);

                    // Skip listeners that are not affiliated with the target userId
                    if (registeringUserHandle == null
                            || registeringUserHandle.getIdentifier() != userId) {
                        continue;
                    }

                    @GetSecureLockDeviceAvailabilityRequestStatus
                    int secureLockDeviceAvailability = getSecureLockDeviceAvailability(
                            registeringUserHandle);

                    if (DEBUG) {
                        Slog.d(TAG, "Notifying listener " + listener.asBinder() + " for user "
                                + userId + " of secure lock device availability update: "
                                + secureLockDeviceAvailability);
                    }
                    try {
                        listener.onSecureLockDeviceAvailableStatusChanged(
                                secureLockDeviceAvailability);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Failed to notify listener " + listener.asBinder()
                                + " for user " + userId + ", RemoteException thrown: ", e);
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception thrown by listener " + listener.asBinder()
                                + " for user " + userId + " during callback: ", e);
                        mSecureLockDeviceStatusListeners.unregister(listener);
                    }
                }
            } finally {
                mSecureLockDeviceStatusListeners.finishBroadcast();
            }
        }
    }

    /**
     * @see AuthenticationPolicyManager#setSecureLockDeviceTestStatus(boolean)
     */
    @Override
    public void setSecureLockDeviceTestStatus(boolean isTestMode) {
        if (DEBUG) {
            Slog.d(TAG, "setSecureLockDeviceTestStatus(isTestMode = " + isTestMode + ")");
        }
        mSecureLockDeviceSettingsManager.setSkipSecurityFeaturesForTest(isTestMode);
    }

    /**
     * System service lifecycle.
     */
    public static final class Lifecycle extends SystemService {
        private final SecureLockDeviceService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mService = SecureLockDeviceService.create(context);
        }

        @Override
        public void onStart() {
            Slog.d(TAG, "Starting SecureLockDeviceService");
            mService.start();
            Slog.d(TAG, "Started SecureLockDeviceService");
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == PHASE_LOCK_SETTINGS_READY) {
                mService.onLockSettingsReady();
            } else if (phase == PHASE_BOOT_COMPLETED) {
                mService.onBootCompleted();
            }
        }
    }
}