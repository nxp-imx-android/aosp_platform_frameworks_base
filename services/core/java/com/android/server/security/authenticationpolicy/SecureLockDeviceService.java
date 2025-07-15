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

import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static android.os.UserManager.DISALLOW_USER_SWITCH;
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
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.hardware.biometrics.BiometricEnrollmentStatus;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricStateListener;
import android.hardware.biometrics.SensorProperties;
import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.AuthenticationPolicyManager.DisableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.EnableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.GetSecureLockDeviceAvailabilityRequestStatus;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.security.authenticationpolicy.ISecureLockDeviceStatusListener;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Slog;
import android.util.Xml;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.IoThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.wm.WindowManagerInternal;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * System service for remotely calling secure lock on the device.
 *
 * Callers will access this class via
 * {@link com.android.server.security.authenticationpolicy.AuthenticationPolicyService}.
 *
 * @see AuthenticationPolicyService
 * @see AuthenticationPolicyManager#enableSecureLockDevice
 * @see AuthenticationPolicyManager#disableSecureLockDevice
 * @hide
 */
public class SecureLockDeviceService extends SecureLockDeviceServiceInternal {
    private static final String TAG = "SecureLockDeviceService";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE && Log.isLoggable(TAG, Log.DEBUG);
    private final Context mContext;
    @Nullable private final BiometricManager mBiometricManager;
    @Nullable private final FaceManager mFaceManager;
    @Nullable private final FingerprintManager mFingerprintManager;
    private final PowerManager mPowerManager;
    @NonNull private final SecureLockDeviceStore mStore;
    private final RemoteCallbackList<ISecureLockDeviceStatusListener>
            mSecureLockDeviceStatusListeners = new RemoteCallbackList<>();

    // Not final because initialized after SecureLockDeviceService in SystemServer
    private ActivityManager mActivityManager;
    private AuthenticationPolicyService mAuthenticationPolicyService;
    private DevicePolicyManager mDevicePolicyManager;
    private WindowManagerInternal mWindowManagerInternal;

    SecureLockDeviceService(@NonNull Context context) {
        mContext = context;
        mBiometricManager = mContext.getSystemService(BiometricManager.class);
        mFaceManager = mContext.getSystemService(FaceManager.class);
        mFingerprintManager = mContext.getSystemService(FingerprintManager.class);
        mPowerManager = context.getSystemService(PowerManager.class);
        mStore = new SecureLockDeviceStore(IoThread.getHandler());
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
        UserHandle callingUser = UserHandle.of(mStore.retrieveSecureLockDeviceClientId());
        boolean result = switchCallingUserToForeground(callingUser);
        if (!result) {
            if (DEBUG) {
                Slog.d(TAG, "Failed to switch calling user to foreground.");
            }
            return false;
        }

        // TODO (b/398058587): Set strong auth flags for user to configure allowed auth types

        // TODO (b/396680098): Enable security features

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

    /**
     * @see AuthenticationPolicyManager#unregisterSecureLockDeviceStatusListener
     */
    @Override
    public void unregisterSecureLockDeviceStatusListener(
            @NonNull ISecureLockDeviceStatusListener listener) {
        if (mSecureLockDeviceStatusListeners.unregister(listener)) {
            if (DEBUG) {
                Slog.d(TAG, "Unregistered listener: " + listener.asBinder());
            }
        } else {
            Slog.w(TAG, "Failed to unregister listener: " + listener.asBinder());
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
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
    }

    @VisibleForTesting
    void onBootCompleted() {
        if (DEBUG) {
            Slog.d(TAG, "onBootCompleted()");
        }
        mAuthenticationPolicyService = LocalServices.getService(AuthenticationPolicyService.class);

        if (mAuthenticationPolicyService == null) {
            Slog.w(TAG, "AuthenticationPolicyService not found, listeners will not be "
                    + "notified of secure lock device status updates.");
        }
        listenForBiometricEnrollmentChanges();
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
     * @see AuthenticationPolicyManager#getSecureLockDeviceAvailability()
     * @param user {@link UserHandle} to check that secure lock device is available fo
     * @return {@link GetSecureLockDeviceAvailabilityRequestStatus} int indicating whether secure
     * lock device is available for the calling user
     *
     * @hide
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
     * @see AuthenticationPolicyManager#enableSecureLockDevice
     * @param user {@link UserHandle} of caller requesting to enable secure lock device
     * @param params {@link EnableSecureLockDeviceParams} for caller to supply params related
     *               to the secure lock device request
     * @return {@link EnableSecureLockDeviceRequestStatus} int indicating the result of the
     * secure lock device request
     *
     * @hide
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
        boolean result = switchCallingUserToForeground(user);
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

        // (2) Call into framework to configure secure lock 2FA lockscreen
        // update, UI & string updates
        // TODO (b/396639472, b/396642040): implement 2FA lockscreen update
        // (3) Call into framework to configure keyguard security updates
        // TODO (b/396680098): Implement enabling security features
        mStore.storeSecureLockDeviceEnabled(user.getIdentifier());
        notifyAllSecureLockDeviceListenersEnabledStatusUpdated();
        Slog.d(TAG, "Secure lock device is enabled");

        return SUCCESS;
    }

    /**
     * @see AuthenticationPolicyManager#disableSecureLockDevice
     * @param user {@link UserHandle} of caller requesting to disable secure lock device
     * @param params {@link DisableSecureLockDeviceParams} for caller to supply params related
     *               to the secure lock device request
     * @return {@link DisableSecureLockDeviceRequestStatus} int indicating the result of the
     * secure lock device request
     *
     * @hide
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

        int enableSecureLockDeviceUserId = mStore.retrieveSecureLockDeviceClientId();
        int callingUserId = user.getIdentifier();

        // Verify calling user matches the user who enabled secure lock device
        // or is a system/admin user with override privileges
        if (enableSecureLockDeviceUserId != UserHandle.USER_NULL
                && callingUserId != enableSecureLockDeviceUserId) {
            Slog.w(TAG, "User " + callingUserId + " attempted to disable secure lock device "
                    + "enabled by user " + enableSecureLockDeviceUserId);
            return ERROR_NOT_AUTHORIZED;
        }

        // (1) Call into system_server to reset allowed auth types
        // TODO (b/398058587): Reset strong auth flags for user
        // (2) Call into framework to disable secure lock 2FA lockscreen, reset UI
        // & string updates
        // TODO (b/396639472, b/396642040): Disable 2FA lockscreen, reset UI
        // (3) Call into framework to revert keyguard security updates
        // TODO (b/396680098): Implement disabling security features

        // Re-allow user switching from the UI
        if (mDevicePolicyManager != null) {
            mDevicePolicyManager.clearUserRestrictionGlobally(TAG, DISALLOW_USER_SWITCH);
        } else {
            Slog.w(TAG, "DevicePolicyManager not available: cannot clear user switching "
                    + "restriction.");
        }

        mStore.storeSecureLockDeviceDisabled();
        notifyAllSecureLockDeviceListenersEnabledStatusUpdated();
        Slog.d(TAG, "Secure lock device is disabled");

        return SUCCESS;
    }

    /**
     * @see AuthenticationPolicyManager#isSecureLockDeviceEnabled()
     * @return true if secure lock device is enabled, false otherwise
     */
    @Override
    public boolean isSecureLockDeviceEnabled() {
        if (!secureLockDevice()) {
            return false;
        }

        return mStore.retrieveSecureLockDeviceEnabled();
    }

    /**
     * Attempts to switch the calling user to foreground if not already in the foreground before
     * enabling secure lock device.
     * Returns true on success, false otherwise
     * @param targetUser userId of caller requesting to enable secure lock device
     * @return true if user was switched to foreground, false otherwise
     */
    private boolean switchCallingUserToForeground(UserHandle targetUser) {
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

        // After switching to the calling user, disable user switching from the UI.
        mDevicePolicyManager.addUserRestrictionGlobally(TAG, DISALLOW_USER_SWITCH);
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
                            + "user "  + user.getIdentifier() + ", RemoteException thrown: ", e);
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

    /**
     * Notifies registered listeners associated with {@param targetUserId} about availability
     * updates to secure lock device. This is called on user-specific events like biometric
     * enrollment changes.
     *
     * @param userId the user id associated with the secure lock device availability status
     *               update. Only listeners registered to this userId will be notified.s
     */
    private void notifySecureLockDeviceAvailabilityForUser(int userId) {
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
                    listener.onSecureLockDeviceAvailableStatusChanged(secureLockDeviceAvailability);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to notify listener " + listener.asBinder() + " for "
                            + "user " + userId + ", RemoteException thrown: ", e);
                } catch (Exception e) {
                    Slog.e(TAG, "Exception thrown by listener " + listener.asBinder() + " for "
                            + "user " + userId + " during callback: ", e);
                    mSecureLockDeviceStatusListeners.unregister(listener);
                }
            }
        } finally {
            mSecureLockDeviceStatusListeners.finishBroadcast();
        }
    }

    /**
     * Stores the current state of Secure Lock Device in GlobalSettings.
     */
    @VisibleForTesting
    static class SecureLockDeviceStore {
        private static final String FILE_NAME = "secure_lock_device_state.xml";
        private static final String XML_TAG_ROOT = "secure-lock-device-state";
        private static final String XML_TAG_ENABLED = "enabled";
        private static final String XML_TAG_CLIENT_ID = "client-id";

        private final AtomicFile mStateFile;
        private final Handler mHandler;
        private final Object mFileLock = new Object();

        private boolean mIsEnabled = false;
        private int mClientUserId = UserHandle.USER_NULL;

        SecureLockDeviceStore(Handler handler) {
            mHandler = handler;

            File systemDir = Environment.getDataSystemDirectory();
            File filePath = new File(systemDir, FILE_NAME);
            mStateFile = new AtomicFile(filePath);

            if (DEBUG) {
                Slog.d(TAG, "SecureLockDeviceStore initialized at " + filePath.getAbsolutePath());
            }

            try {
                loadStateFromFile();
            } catch (Exception e) {
                Slog.e(TAG, "Exception during loadStateFromFile(): ", e);
                synchronized (mFileLock) {
                    resetToDefaults();
                }
            }
        }

        /**
         * Loads the persisted state (isEnabled and clientId) from the XML file.
         * If the file doesn't exist or is corrupted, it defaults to a disabled state.
         */
        private void loadStateFromFile() {
            synchronized (mFileLock) {
                resetToDefaults();

                if (!mStateFile.getBaseFile().exists()) {
                    Slog.d(TAG, "Secure lock device state file does not exist.");
                    return;
                }

                try (FileInputStream fis = mStateFile.openRead()) {
                    TypedXmlPullParser parser = Xml.resolvePullParser(fis);
                    XmlUtils.beginDocument(parser, XML_TAG_ROOT);
                    int outerDepth = parser.getDepth();

                    while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                        String tagName = parser.getName();
                        if (XML_TAG_ENABLED.equals(tagName)) {
                            mIsEnabled = Boolean.parseBoolean(parser.nextText());
                        } else if (XML_TAG_CLIENT_ID.equals(tagName)) {
                            mClientUserId = Integer.parseInt(parser.nextText());
                        } else {
                            Slog.w(TAG, "Unknown tag in state file: " + tagName);
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    if (DEBUG) {
                        Slog.d(TAG,
                                "Loaded state: isEnabled=" + mIsEnabled + ", clientId="
                                        + mClientUserId);
                    }
                } catch (IOException | XmlPullParserException | NumberFormatException e) {
                    Slog.e(TAG, "Error reading secure lock device state file, resetting to "
                            + "defaults.", e);
                    resetToDefaults();
                }
            }
        }

        /**
         * Updates the in-memory state and schedules a write to the persistent file.
         * @param enabled The new enabled state.
         * @param userId The userId associated with the client enabling secure lock device state,
         *              or USER_NULL if disabled.
         */
        private void updateStateAndWriteToFile(boolean enabled, int userId) {
            synchronized (mFileLock) {
                boolean changed = (mIsEnabled != enabled) || (mClientUserId != userId);

                if (changed) {
                    mIsEnabled = enabled;
                    mClientUserId = userId;
                    mHandler.post(() -> writeToFileInternal(enabled, userId));
                }
            }
        }

        /**
         * Writes to the secure lock device state atomic file.
         */
        private void writeToFileInternal(boolean enabled, int clientId) {
            FileOutputStream fos = null;
            try {
                fos = mStateFile.startWrite();
                TypedXmlSerializer serializer = Xml.resolveSerializer(fos);
                serializer.setOutput(fos, StandardCharsets.UTF_8.name());

                serializer.startDocument(null, true);
                serializer.startTag(null, XML_TAG_ROOT);

                serializer.startTag(null, XML_TAG_ENABLED);
                serializer.text(Boolean.toString(enabled));
                serializer.endTag(null, XML_TAG_ENABLED);

                serializer.startTag(null, XML_TAG_CLIENT_ID);
                serializer.text(Integer.toString(clientId));
                serializer.endTag(null, XML_TAG_CLIENT_ID);

                serializer.endTag(null, XML_TAG_ROOT);
                serializer.endDocument();

                mStateFile.finishWrite(fos);
                fos = null; // Indicates success to finally block

                if (DEBUG) {
                    Slog.d(TAG, "Saved state: isEnabled=" + enabled + ", clientId=" + clientId);
                }
            } catch (IOException e) {
                Slog.e(TAG, "Error writing secure lock device state file: ", e);
                if (fos != null) {
                    mStateFile.failWrite(fos);
                }
            } finally {
                if (fos != null) {
                    Slog.e(TAG, "Failure during write to secure lock device state file, "
                            + "closing file output stream.");
                    mStateFile.failWrite(fos);
                }
            }
        }

        /**
         * Resets the current state to defaults in the case of error parsing the file.
         */
        private void resetToDefaults() {
            mIsEnabled = false;
            mClientUserId = UserHandle.USER_NULL;
        }

        /**
         * Updates the current Global settings to reflect Secure Lock Device being enabled.
         * @param userId the userId of the client that enabled secure lock device
         */
        void storeSecureLockDeviceEnabled(int userId) {
            if (DEBUG) {
                Slog.d(TAG, "Storing SLD enabled by user: " + userId);
            }
            updateStateAndWriteToFile(/* enabled= */ true, /* userId= */ userId);
        }

        /**
         * Updates the current Global settings to reflect Secure Lock Device being disabled.
         */
        void storeSecureLockDeviceDisabled() {
            if (DEBUG) {
                Slog.d(TAG, "Storing SLD disabled.");
            }
            updateStateAndWriteToFile(false, UserHandle.USER_NULL);
        }

        /**
         * Retrieves the current state of whether Secure Lock Device in enabled or disabled in
         * GlobalSettings.
         * @return true if Secure Lock Device is enabled, false otherwise
         */
        boolean retrieveSecureLockDeviceEnabled() {
            synchronized (mFileLock) {
                return mIsEnabled;
            }
        }

        /**
         * Retrieves the user id of the client that enabled secure lock device, or
         * {@link UserHandle.USER_NULL} if secure lock device is disabled.
         * @return userId of the client that enabled secure lock device, or
         * {@link UserHandle.USER_NULL} if secure lock device is disabled
         */
        int retrieveSecureLockDeviceClientId() {
            synchronized (mFileLock) {
                return mClientUserId;
            }
        }
    }

    /**
     * System service lifecycle.
     */
    public static final class Lifecycle extends SystemService {
        private final SecureLockDeviceService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mService = new SecureLockDeviceService(context);
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
