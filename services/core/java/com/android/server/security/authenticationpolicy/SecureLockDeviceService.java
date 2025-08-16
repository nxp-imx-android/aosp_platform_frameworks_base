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

import static android.app.StatusBarManager.DISABLE2_NOTIFICATION_SHADE;
import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.app.StatusBarManager.DISABLE2_SYSTEM_ICONS;
import static android.app.StatusBarManager.DISABLE_BACK;
import static android.app.StatusBarManager.DISABLE_EXPAND;
import static android.app.StatusBarManager.DISABLE_HOME;
import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ALERTS;
import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_ONGOING_CALL_CHIP;
import static android.app.StatusBarManager.DISABLE_SEARCH;
import static android.content.Context.STATUS_BAR_SERVICE;
import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static android.hardware.usb.InternalUsbDataSignalDisableReason.USB_DISABLE_REASON_LOCKDOWN_MODE;
import static android.hardware.usb.UsbPort.ENABLE_USB_DATA_SUCCESS;
import static android.os.UserManager.DISALLOW_CHANGE_WIFI_STATE;
import static android.os.UserManager.DISALLOW_CONFIG_WIFI;
import static android.os.UserManager.DISALLOW_DEBUGGING_FEATURES;
import static android.os.UserManager.DISALLOW_OUTGOING_CALLS;
import static android.os.UserManager.DISALLOW_SMS;
import static android.os.UserManager.DISALLOW_USB_FILE_TRANSFER;
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
import android.app.ActivityTaskManager;
import android.app.StatusBarManager;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.biometrics.BiometricEnrollmentStatus;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricStateListener;
import android.hardware.biometrics.SensorProperties;
import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.nfc.NfcAdapter;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.AuthenticationPolicyManager.DisableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.EnableSecureLockDeviceRequestStatus;
import android.security.authenticationpolicy.AuthenticationPolicyManager.GetSecureLockDeviceAvailabilityRequestStatus;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.security.authenticationpolicy.ISecureLockDeviceStatusListener;
import android.util.AtomicFile;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.Xml;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.IVoiceInteractionManagerService;
import com.android.internal.statusbar.IStatusBarService;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final ActivityTaskManager mActivityTaskManager;
    @Nullable private final BiometricManager mBiometricManager;
    private final ContentResolver mContentResolver;
    private final Context mContext;
    @Nullable private final FaceManager mFaceManager;
    @Nullable private final FingerprintManager mFingerprintManager;
    private final IBinder mToken = new LockTaskToken();
    private final IStatusBarService mStatusBarService;
    private final IVoiceInteractionManagerService mVoiceInteractionManagerService;
    private final PowerManager mPowerManager;
    @NonNull private final Object mSecureLockDeviceStatusListenerLock = new Object();
    private final StatusBarManager mStatusBarManager;
    @NonNull private final SecureLockDeviceStore mStore;

    private final RemoteCallbackList<ISecureLockDeviceStatusListener>
            mSecureLockDeviceStatusListeners = new RemoteCallbackList<>();

    // Not final because initialized after SecureLockDeviceService in SystemServer
    private ActivityManager mActivityManager;
    private AuthenticationPolicyService mAuthenticationPolicyService;
    private DevicePolicyManager mDevicePolicyManager;
    private IUsbManagerInternal mUsbManagerInternal;
    private NfcAdapter mNfcAdapter;
    private UsbManager mUsbManager;
    private WindowManagerInternal mWindowManagerInternal;

    private boolean mSkipSecurityFeaturesForTest = false;

    private static final int DISABLE_FLAGS =
            // Flag to make the status bar not expandable
            DISABLE_EXPAND
                    // Flag to hide notification icons and scrolling ticker text.
                    | DISABLE_NOTIFICATION_ICONS
                    // Flag to disable incoming notification alerts.  This will not block
                    // icons, but it will block sound, vibrating and other visual or aural
                    // notifications.
                    | DISABLE_NOTIFICATION_ALERTS
                    // Flag to hide only the home button.
                    | DISABLE_HOME
                    // Flag to hide only the back button.
                    | DISABLE_BACK
                    // Flag to disable the global search gesture.
                    | DISABLE_SEARCH
                    // Flag to disable the ongoing call chip.
                    | DISABLE_ONGOING_CALL_CHIP;

    private static final int DISABLE2_FLAGS =
            // Setting this flag disables quick settings completely
            DISABLE2_QUICK_SETTINGS
                    // Flag to hide system icons.
                    | DISABLE2_SYSTEM_ICONS
                    // Flag to disable notification shade
                    | DISABLE2_NOTIFICATION_SHADE;

    // Map of secure settings keys to their values when secure lock device is enabled
    private static final Map<String, Integer> SECURE_SETTINGS_MAP = Map.of(
            Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, 0,
            Settings.Secure.DOUBLE_TAP_POWER_BUTTON_GESTURE_ENABLED, 0,
            Settings.Secure.CAMERA_GESTURE_DISABLED, 1,
            Settings.Secure.CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED, 1,
            Settings.Secure.CAMERA_LIFT_TRIGGER_ENABLED, 0,
            Settings.Secure.LOCK_SCREEN_WEATHER_ENABLED, 0,
            Settings.Secure.LOCKSCREEN_SHOW_CONTROLS, 0,
            Settings.Secure.LOCKSCREEN_SHOW_WALLET, 0,
            Settings.Secure.LOCK_SCREEN_SHOW_QR_CODE_SCANNER, 0,
            Settings.Secure.GLANCEABLE_HUB_ENABLED, 0
    );

    // Map of system settings keys to their values when secure lock device is enabled
    private static final Map<String, Integer> SYSTEM_SETTINGS_MAP = Map.of(
            Settings.System.BLUETOOTH_DISCOVERABILITY, 0,
            Settings.System.LOCK_TO_APP_ENABLED, 0
    );

    // Map of adb settings keys to their values when secure lock device is enabled
    private static final Map<String, Integer> ADB_SETTINGS_MAP = Map.of(
            Settings.Global.ADB_ENABLED, 0,
            Settings.Global.ADB_WIFI_ENABLED, 0
    );

    // Map of global settings keys to their values when secure lock device is enabled
    private static final Map<String, Integer> USER_SWITCHING_SETTINGS_MAP = Map.of(
            Settings.Global.ADD_USERS_WHEN_LOCKED, 0,
            Settings.Global.USER_SWITCHER_ENABLED, 0,
            Settings.Global.ALLOW_USER_SWITCHING_WHEN_SYSTEM_USER_LOCKED, 0
    );

    private static final Set<String> DEVICE_POLICY_RESTRICTIONS = new HashSet<>(Arrays.asList(
            DISALLOW_USB_FILE_TRANSFER,
            DISALLOW_DEBUGGING_FEATURES,
            DISALLOW_CHANGE_WIFI_STATE,
            DISALLOW_CONFIG_WIFI,
            DISALLOW_OUTGOING_CALLS,
            DISALLOW_SMS,
            DISALLOW_USER_SWITCH
    ));

    private static final String NFC_ENABLED_KEY = "nfc_allowed";
    private static final String USB_ENABLED_KEY = "usb_enabled";
    private static final String DEVICE_POLICY_RESTRICTIONS_KEY = "device_policy_restrictions";
    private static final String DISABLE_FLAGS_KEY = "disable_flags";

    // Used to snapshot and store the original state of settings when secure lock device is enabled,
    // and restore to this state when secure lock device is disabled.
    private Map<String, SettingState> mSettingsStateMap = new HashMap<>();

    SecureLockDeviceService(@NonNull Context context) {
        mActivityTaskManager = ActivityTaskManager.getInstance();
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mBiometricManager = mContext.getSystemService(BiometricManager.class);
        mFaceManager = mContext.getSystemService(FaceManager.class);
        mFingerprintManager = mContext.getSystemService(FingerprintManager.class);
        mPowerManager = context.getSystemService(PowerManager.class);
        mStatusBarManager = context.getSystemService(StatusBarManager.class);
        mStatusBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(STATUS_BAR_SERVICE));
        mStore = new SecureLockDeviceStore(IoThread.getHandler());
        mVoiceInteractionManagerService = IVoiceInteractionManagerService.Stub.asInterface(
                ServiceManager.getService(Context.VOICE_INTERACTION_MANAGER_SERVICE));
    }

    /**
     * Creates map of all settings (as keys) to their expected values when secure lock device is
     * enabled.
     */
    private void initializeSettingsMap() {
        SECURE_SETTINGS_MAP.forEach((key, value) -> {
            mSettingsStateMap.put(key, new SettingState(
                    key, /* settingKey */
                    value, /* secureLockDeviceValue */
                    SettingState.SettingType.SECURE_SETTING
            ));
        });
        SYSTEM_SETTINGS_MAP.forEach((key, value) -> {
            mSettingsStateMap.put(key, new SettingState(
                    key, /* settingKey */
                    value, /* secureLockDeviceValue */
                    SettingState.SettingType.SYSTEM_SETTING
            ));
        });
        ADB_SETTINGS_MAP.forEach((key, value) -> {
            mSettingsStateMap.put(key, new SettingState(
                    key, /* settingKey */
                    value, /* secureLockDeviceValue */
                    SettingState.SettingType.ADB_SETTING
            ));
        });
        USER_SWITCHING_SETTINGS_MAP.forEach((key, value) -> {
            mSettingsStateMap.put(key, new SettingState(
                    key, /* settingKey */
                    value, /* secureLockDeviceValue */
                    SettingState.SettingType.USER_SWITCHING_SETTING
            ));
        });
        mSettingsStateMap.put(DEVICE_POLICY_RESTRICTIONS_KEY, new SettingState(
                DEVICE_POLICY_RESTRICTIONS_KEY, /* settingKey */
                true, /* secureLockDeviceValue */
                SettingState.SettingType.DEVICE_POLICY_RESTRICTION
        ));
        mSettingsStateMap.put(DISABLE_FLAGS_KEY, new SettingState(
                DISABLE_FLAGS_KEY, /* settingKey */
                new Pair<>(DISABLE_FLAGS, DISABLE2_FLAGS), /* secureLockDeviceValue */
                SettingState.SettingType.DISABLE_FLAGS
        ));
        mSettingsStateMap.put(NFC_ENABLED_KEY, new SettingState(
                NFC_ENABLED_KEY, /* settingKey */
                false, /* secureLockDeviceValue */
                SettingState.SettingType.NFC_STATE
        ));
        mSettingsStateMap.put(USB_ENABLED_KEY, new SettingState(
                USB_ENABLED_KEY, /* settingKey */
                false, /* secureLockDeviceValue */
                SettingState.SettingType.USB_STATE
        ));
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

        // TODO (b/398058587): Set strong auth flags for user to configure allowed auth types

        enableSecurityFeatures(secureLockDeviceClientId);
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
        mNfcAdapter = NfcAdapter.getDefaultAdapter(mContext);
        if (mNfcAdapter == null) {
            Slog.d(TAG, "NFC not supported or available on this device.");
        }
        mUsbManager = mContext.getSystemService(UsbManager.class);
        mUsbManagerInternal = LocalServices.getService(IUsbManagerInternal.class);

        initializeSettingsMap();
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

        // (2) Call into framework to configure secure lock 2FA lockscreen
        // update, UI & string updates
        // TODO (b/396639472, b/396642040): implement 2FA lockscreen update
        int userId = user.getIdentifier();
        enableSecurityFeatures(userId);

        mStore.storeSecureLockDeviceEnabled(userId, mSettingsStateMap);
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
        disableSecurityFeatures();

        mStore.storeSecureLockDeviceDisabled();
        notifyAllSecureLockDeviceListenersEnabledStatusUpdated();
        Slog.d(TAG, "Secure lock device is disabled");

        return SUCCESS;
    }

    private void applyUserSwitchingRestrictions() {
        if (mSkipSecurityFeaturesForTest) {
            Log.d(TAG, "Skipping setting user switching global settings for test.");
        } else {
            USER_SWITCHING_SETTINGS_MAP.forEach((key, value) -> {
                SettingState state = mSettingsStateMap.get(key);
                if (state == null) {
                    Log.e(TAG, "Null SettingState found for key " + key);
                    return;
                }
                try {
                    int originalValue = Settings.Global.getInt(mContentResolver, key);
                    state.setOriginalValue(originalValue);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting original value for secure setting " + key, e);
                }

                try {
                    int secureLockDeviceValue = (int) state.getSecureLockDeviceValue();
                    Settings.Global.putInt(mContentResolver, key, secureLockDeviceValue);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting secure setting " + key, e);
                }
            });
        }

        if (mDevicePolicyManager != null) {
            SettingState state = mSettingsStateMap.get(DEVICE_POLICY_RESTRICTIONS_KEY);
            try {
                Bundle originalValue = mDevicePolicyManager.getUserRestrictionsGlobally();
                state.setOriginalValue(originalValue);
            } catch (Exception e) {
                Log.e(TAG, "Error setting original value for user device policy "
                        + "restrictions.");
            }
            mDevicePolicyManager.addUserRestrictionGlobally(TAG, DISALLOW_USER_SWITCH);
        } else {
            Slog.w(TAG, "DevicePolicyManager not available: cannot set user switching "
                    + "restriction.");
        }
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
     * Attempts to switch the target user to foreground if not already in the foreground before
     * enabling secure lock device.
     * Returns true on success, false otherwise
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

        // After switching to the calling user, disable user switching from the UI.
        applyUserSwitchingRestrictions();
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

    private void enableSecurityFeatures(int userId) {
        mSettingsStateMap.forEach((setting, settingState) -> {
            SettingState.SettingType settingType = settingState.mType;
            if (settingType == SettingState.SettingType.SECURE_SETTING) {
                try {
                    int defaultValue = 1 - (int) settingState.mSecureLockDeviceValue;
                    int originalValue  = Settings.Secure.getIntForUser(mContentResolver, setting,
                            defaultValue, userId);
                    settingState.setOriginalValue(originalValue);
                } catch (Exception e) {
                    Log.e(TAG, "Error retrieving original value for secure setting "
                            + setting, e);
                }

                try {
                    int secureLockDeviceValue = (int) settingState
                            .getSecureLockDeviceValue();
                    Settings.Secure.putInt(mContentResolver, setting,
                            secureLockDeviceValue);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting secure setting " + setting, e);
                }
            } else if (settingType == SettingState.SettingType.SYSTEM_SETTING) {
                try {
                    int defaultValue = 1 - (int) settingState.mSecureLockDeviceValue;
                    int originalValue = Settings.System.getIntForUser(mContentResolver, setting,
                            defaultValue, userId);
                    settingState.setOriginalValue(originalValue);
                } catch (Exception e) {
                    Log.e(TAG, "Error retrieving original value for system setting "
                            + setting, e);
                }

                try {
                    int secureLockDeviceValue = (int) settingState
                            .getSecureLockDeviceValue();
                    Settings.System.putInt(mContentResolver, setting,
                            secureLockDeviceValue);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting system setting " + setting, e);
                }
            } else if (settingType == SettingState.SettingType.ADB_SETTING) {
                if (mSkipSecurityFeaturesForTest) {
                    Log.d(TAG, "Skipping setting ADB setting " + setting + " for test.");
                } else {
                    try {
                        int defaultValue = 1 - (int) settingState.mSecureLockDeviceValue;
                        int originalValue = Settings.Global.getInt(mContentResolver, setting,
                                defaultValue);
                        settingState.setOriginalValue(originalValue);
                    } catch (Exception e) {
                        Log.e(TAG, "Error retrieving original value for ADB setting "
                                + setting, e);
                    }

                    try {
                        int secureLockDeviceValue = (int) settingState.getSecureLockDeviceValue();
                        Settings.Global.putInt(mContentResolver, setting,
                                secureLockDeviceValue);
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting ADB setting " + setting, e);
                    }
                }
            } else if (settingType == SettingState.SettingType.DEVICE_POLICY_RESTRICTION) {
                // Original value already set in disableUserSwitching
                DEVICE_POLICY_RESTRICTIONS.forEach(restriction -> {
                    if (restriction.equals(DISALLOW_USER_SWITCH) || (mSkipSecurityFeaturesForTest
                            && (restriction.equals(DISALLOW_USB_FILE_TRANSFER)
                            || restriction.equals(DISALLOW_DEBUGGING_FEATURES)))
                    ) {
                        Log.e(TAG, "Skipping setting device policy restriction " + restriction
                                + " for test.");
                    } else {
                        mDevicePolicyManager.addUserRestrictionGlobally(TAG, restriction);
                    }
                });
            } else if (settingType == SettingState.SettingType.NFC_STATE) {
                if (mNfcAdapter == null) {
                    Slog.w(TAG, "Nfc adapter is null, cannot disable NFC.");
                } else if (mSkipSecurityFeaturesForTest) {
                    Slog.d(TAG, "Skipping disabling NFC for test mode.");
                } else {
                    try {
                        boolean originalState = mNfcAdapter.isEnabled();
                        settingState.setOriginalValue(originalState);
                    } catch (Exception e) {
                        Log.e(TAG, "Error retrieving original value for NFC "
                                + "enabled: ", e);
                    }

                    try {
                        // Attempt to disable NFC
                        if (!mNfcAdapter.disable()) {
                            Slog.w(TAG, "Failed to disable NFC");
                        } else {
                            Slog.i(TAG, "NFC disabled for Secure Lock Device.");
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception trying to disable NFC", e);
                    }
                }
            } else if (settingType == SettingState.SettingType.USB_STATE) {
                if (mUsbManagerInternal == null) {
                    Slog.e(TAG, "IUsbManagerInternal is null, cannot disable USB");
                } else if (mSkipSecurityFeaturesForTest) {
                    Slog.d(TAG, "Skipping disabling USB for test mode.");
                } else {
                    try {
                        Map<UsbPort, Boolean> usbPortsEnabledStatus =
                                getUsbPortsEnabledStatus();
                        settingState.setOriginalValue(usbPortsEnabledStatus);
                    } catch (Exception e) {
                        Log.e(TAG, "Error retrieving original value for USB "
                                + "enabled: ", e);
                    }

                    try {
                        if (!mUsbManagerInternal.enableUsbDataSignal(false,
                                USB_DISABLE_REASON_LOCKDOWN_MODE)) {
                            Slog.w(TAG, "Failed to disable USB data signal via "
                                    + "internal manager.");
                        } else {
                            Slog.i(TAG, "USB data signal disabled for Secure Lock "
                                    + "Device.");
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception trying to disable USB data signal", e);
                    }
                }
            } else if (settingType == SettingState.SettingType.DISABLE_FLAGS) {
                // Disable status bar expansion, notifications, home button, back gestures, search
                // gestures, call chips.
                try {
                    StatusBarManager.DisableInfo originalState = mStatusBarManager.getDisableInfo();
                    settingState.setOriginalValue(originalState);
                } catch (Exception e) {
                    Slog.e(TAG, "Error disabling status bar features", e);
                }

                try {
                    mStatusBarService.disable(DISABLE_FLAGS, mToken,
                            mContext.getPackageName());
                    mStatusBarService.disable2(DISABLE2_FLAGS, mToken,
                            mContext.getPackageName());
                } catch (Exception e) {
                    Slog.e(TAG, "Error disabling status bar features", e);
                }
            }
        });

        // Stop app pinning via ActivityTaskManager
        try {
            mActivityTaskManager.stopSystemLockTaskMode();
        } catch (Exception e) {
            Slog.e(TAG, "Error stopping system lock task mode", e);
        }

        // Temporarily disable assistant access
        try {
            mVoiceInteractionManagerService.setDisabled(true);
        } catch (Exception e) {
            Slog.e(TAG, "Error disabling assistant access", e);
        }
    }

    private Map<UsbPort, Boolean> getUsbPortsEnabledStatus() {
        List<UsbPort> ports = mUsbManager.getPorts();
        Map<UsbPort, Boolean> usbPortsEnabledStatus = new HashMap<>();
        ports.forEach(port -> {
            UsbPortStatus portStatus = port.getStatus();
            if (portStatus != null) {
                int usbDataStatus =  portStatus.getUsbDataStatus();
                boolean isPortEnabled = (usbDataStatus & UsbPortStatus.DATA_STATUS_DISABLED_FORCE)
                        == 0;
                usbPortsEnabledStatus.put(port, isPortEnabled);
            }
        });
        return usbPortsEnabledStatus;
    }

    private void disableSecurityFeatures() {
        mSettingsStateMap.forEach((setting, settingState) -> {
            SettingState.SettingType settingType = settingState.mType;
            if (settingType == SettingState.SettingType.SECURE_SETTING) {
                try {
                    int originalValue = (int) settingState.mOriginalValue;
                    Settings.Secure.putInt(mContentResolver, setting, originalValue);
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring original value for secure setting "
                            + setting, e);
                }
            } else if (settingType == SettingState.SettingType.SYSTEM_SETTING) {
                try {
                    int originalValue = (int) settingState.mOriginalValue;
                    Settings.System.putInt(mContentResolver, setting, originalValue);
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring original value for system setting "
                            + setting, e);
                }
            } else if (settingType == SettingState.SettingType.ADB_SETTING) {
                if (mSkipSecurityFeaturesForTest) {
                    Log.d(TAG, "Skipping restoring ADB setting " + setting
                            + " for test.");
                } else {
                    try {
                        int originalValue = (int) settingState.mOriginalValue;
                        Settings.Global.putInt(mContentResolver, setting, originalValue);
                    } catch (Exception e) {
                        Log.e(TAG, "Error restoring original value for ADB setting "
                                + setting, e);
                    }
                }
            } else if (settingType == SettingState.SettingType.USER_SWITCHING_SETTING) {
                if (mSkipSecurityFeaturesForTest) {
                    Log.d(TAG, "Skipping restoring user switching global settings for test.");
                } else {
                    try {
                        int originalValue = (int) settingState.mOriginalValue;
                        Settings.Global.putInt(mContentResolver, setting, originalValue);
                    } catch (Exception e) {
                        Log.e(TAG, "Error restoring original value for user switching setting "
                                + setting, e);
                    }
                }
            } else if (settingType == SettingState.SettingType.DEVICE_POLICY_RESTRICTION) {
                try {
                    Bundle originalValue = (Bundle) settingState.mOriginalValue;
                    DEVICE_POLICY_RESTRICTIONS.forEach(restriction -> {
                        if (mSkipSecurityFeaturesForTest
                                && (restriction.equals(DISALLOW_USB_FILE_TRANSFER)
                                || restriction.equals(DISALLOW_DEBUGGING_FEATURES))
                        ) {
                            Log.e(TAG, "Skipping restoring device policy restriction "
                                    + restriction + " for test.");
                        } else if (mDevicePolicyManager != null
                                && !originalValue.getBoolean(setting)) {
                            mDevicePolicyManager.clearUserRestrictionGlobally(TAG, restriction);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring original value for device policy "
                            + "restrictions: " + setting, e);
                }
            } else if (settingType == SettingState.SettingType.NFC_STATE) {
                if (mNfcAdapter == null) {
                    Slog.w(TAG, "Nfc adapter is null, cannot restore NFC state.");
                } else if (mSkipSecurityFeaturesForTest) {
                    Slog.d(TAG, "Skipping restoring NFC state for test mode.");
                } else {
                    try {
                        boolean originalState = (boolean) settingState.mOriginalValue;
                        if (originalState) {
                            if (!mNfcAdapter.enable()) {
                                Slog.w(TAG, "Failed to re-enable NFC");
                            } else {
                                Slog.i(TAG, "NFC state restored upon disabling secure lock "
                                        + "device.");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error restoring original value for NFC enabled: ", e);
                    }
                }
            } else if (settingType == SettingState.SettingType.USB_STATE) {
                if (mUsbManagerInternal == null) {
                    Slog.e(TAG, "IUsbManagerInternal is null, cannot restore USB state.");
                } else if (mSkipSecurityFeaturesForTest) {
                    Slog.d(TAG, "Skipping restoring USB state for test mode.");
                } else {
                    try {
                        Map<UsbPort, Boolean> originalValue =
                                (Map<UsbPort, Boolean>) settingState.getOriginalValue();
                        for (UsbPort port: originalValue.keySet()) {
                            if (originalValue.get(port)) {
                                int result = port.enableUsbData(true);
                                if (result == ENABLE_USB_DATA_SUCCESS) {
                                    Slog.i(TAG, "Re-enabled USB data signal via internal "
                                            + "manager.");
                                } else {
                                    Slog.w(TAG, "Failed to re-enable USB data signal via "
                                            + "internal manager.");
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error restoring original value for USB "
                                + "enabled: ", e);
                    }
                }
            } else if (settingType == SettingState.SettingType.DISABLE_FLAGS) {
                // Restore status bar security features to original state.
                try {
                    Pair<Integer, Integer> originalState = ((StatusBarManager.DisableInfo)
                            settingState.mOriginalValue).toFlags();
                    mStatusBarService.disable(originalState.first, mToken,
                            mContext.getPackageName());
                    mStatusBarService.disable2(originalState.second, mToken,
                            mContext.getPackageName());
                } catch (Exception e) {
                    Slog.e(TAG, "Error restoring original value for status bar features", e);
                }
            }
        });

        // Re-enable assistant access
        try {
            mVoiceInteractionManagerService.setDisabled(false);
        } catch (Exception e) {
            Slog.e(TAG, "Error re-enabling assistant access", e);
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
        setSkipSecurityFeaturesForTest(isTestMode);
    }

    private void setSkipSecurityFeaturesForTest(boolean skipSecurityFeaturesForTest) {
        mSkipSecurityFeaturesForTest = skipSecurityFeaturesForTest;
    }

    static class SettingState {
        final String mSettingKey;
        final Object mSecureLockDeviceValue; // The value to set when secure lock device is enabled
        Object mOriginalValue; // The value to restore to when secure lock device is disabled
        final SettingType mType;

        enum SettingType {
            SECURE_SETTING,
            SYSTEM_SETTING,
            USER_SWITCHING_SETTING,
            ADB_SETTING,
            DEVICE_POLICY_RESTRICTION,
            NFC_STATE,
            USB_STATE,
            DISABLE_FLAGS,
        }

        SettingState(String settingKey, Object secureLockDeviceValue, SettingType type) {
            this.mSettingKey = settingKey;
            this.mSecureLockDeviceValue = secureLockDeviceValue;
            this.mType = type;
        }

        public void setOriginalValue(Object originalValue) {
            this.mOriginalValue = originalValue;
        }

        public String getSettingKey() {
            return mSettingKey;
        }
        public Object getSecureLockDeviceValue() {
            return mSecureLockDeviceValue; }

        public Object getOriginalValue() {
            return mOriginalValue;
        }

        public SettingType getType() {
            return mType;
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
        private static final String XML_TAG_ORIGINAL_SETTINGS = "original-settings";
        private static final String XML_TAG_SETTING = "setting";
        private static final String XML_TAG_SETTING_ORIGINAL_VALUE = "setting-original-value";
        private static final String XML_ATTR_SETTING_KEY = "setting-key";
        private static final String XML_ATTR_SETTING_TYPE = "setting-type";

        private final AtomicFile mStateFile;
        private final Handler mHandler;
        private final Object mFileLock = new Object();

        private Map<String, SettingState> mSettingsStateMap = new HashMap<>();
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
                        switch (tagName) {
                            case XML_TAG_ENABLED -> mIsEnabled = Boolean.parseBoolean(
                                    parser.nextText());
                            case XML_TAG_CLIENT_ID -> mClientUserId = Integer.parseInt(
                                    parser.nextText());
                            case XML_TAG_ORIGINAL_SETTINGS -> loadOriginalSettingsFromXml(parser);
                            case null, default -> {
                                Slog.w(TAG, "Unknown tag in state file: " + tagName);
                                XmlUtils.skipCurrentTag(parser);
                            }
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

        private void loadOriginalSettingsFromXml(TypedXmlPullParser parser)
                throws IOException, XmlPullParserException {
            int depth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, depth)) {
                if (XML_TAG_SETTING.equals(parser.getName())) {
                    String key = parser.getAttributeValue(null, XML_ATTR_SETTING_KEY);
                    String settingTypeStr = parser.getAttributeValue(null,
                            XML_ATTR_SETTING_TYPE);
                    SettingState.SettingType type;
                    Object originalValue = null;

                    try {
                        type = SettingState.SettingType.valueOf(settingTypeStr);
                    } catch (IllegalArgumentException e) {
                        Slog.w(TAG, "Unknown setting type: " + settingTypeStr + " for key " + key);
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }

                    int settingDepth = parser.getDepth();
                    while (XmlUtils.nextElementWithin(parser, settingDepth)) {
                        if (XML_TAG_SETTING_ORIGINAL_VALUE.equals(parser.getName())) {
                            if (type == SettingState.SettingType.SECURE_SETTING
                                    || type == SettingState.SettingType.SYSTEM_SETTING
                                    || type == SettingState.SettingType.ADB_SETTING
                                    || type == SettingState.SettingType.USER_SWITCHING_SETTING) {
                                try {
                                    originalValue = Integer.parseInt(parser.nextText());
                                } catch (Exception e) {
                                    Slog.w(TAG,
                                            "Failed to parse integer for " + key + ", type " + type
                                                    + ".");
                                }
                            } else if (type == SettingState.SettingType.NFC_STATE) {
                                originalValue = Boolean.parseBoolean(parser.nextText());
                            } else if (type == SettingState.SettingType.DEVICE_POLICY_RESTRICTION) {
                                String base64String = parser.nextText();
                                if (base64String != null && !base64String.isEmpty()) {
                                    byte[] bytes = Base64.decode(base64String, Base64.NO_WRAP
                                            | Base64.NO_PADDING);
                                    Parcel parcel = Parcel.obtain();
                                    try {
                                        parcel.unmarshall(bytes, 0, bytes.length);
                                        parcel.setDataPosition(0);
                                        originalValue = Bundle.CREATOR.createFromParcel(parcel);
                                    } catch (Exception e) {
                                        Slog.e(TAG, "Error unmarshalling Bundle for key " + key, e);
                                    } finally {
                                        parcel.recycle();
                                    }
                                }
                            } else if (type == SettingState.SettingType.DISABLE_FLAGS) {
                                int disable1Val = 0;
                                int disable2Val = 0;
                                int disableFlagsDepth = parser.getDepth();
                                while (XmlUtils.nextElementWithin(parser, disableFlagsDepth)) {
                                    if ("disable1".equals(parser.getName())) {
                                        disable1Val = Integer.parseInt(parser.nextText());
                                    } else if ("disable2".equals(parser.getName())) {
                                        disable2Val = Integer.parseInt(parser.nextText());
                                    } else {
                                        XmlUtils.skipCurrentTag(parser);
                                    }
                                }
                                originalValue = new StatusBarManager.DisableInfo(disable1Val,
                                        disable2Val);
                            } else if (type == SettingState.SettingType.USB_STATE) {
                                Map<String, Boolean> usbPortEnabledStates = new HashMap<>();
                                int portMapInnerDepth = parser.getDepth();
                                while (XmlUtils.nextElementWithin(parser, portMapInnerDepth)) {
                                    if ("port".equals(parser.getName())) {
                                        String portId = parser.getAttributeValue(null,
                                                "id");
                                        String enabledStr = parser.getAttributeValue(null,
                                                "enabled");
                                        if (portId != null && enabledStr != null) {
                                            usbPortEnabledStates.put(portId,
                                                    Boolean.parseBoolean(enabledStr));
                                        }
                                    } else {
                                        XmlUtils.skipCurrentTag(parser);
                                    }
                                }
                                originalValue = usbPortEnabledStates;
                            } else {
                                Slog.w(TAG, "No deserialization logic for type " + type + ","
                                        + " key " + key);
                                XmlUtils.skipCurrentTag(parser);
                            }
                            break;
                        } else {
                            Slog.w(TAG, "Unexpected tag inside <setting>: "
                                    + parser.getName());
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    if (key != null && originalValue != null) {
                        SettingState settingState = mSettingsStateMap.get(key);
                        if (settingState != null) {
                            settingState.setOriginalValue(originalValue);
                        }
                    } else if (DEBUG) {
                        Slog.d(TAG, "Skipped loading setting: key=" + key + ", "
                                + "type=" + type);
                    }
                }
            }
        }

        /**
         * Updates the in-memory state and schedules a write to the persistent file.
         * @param enabled The new enabled state.
         * @param settingsStateMap Map with snapshot of current state of settings
         * @param userId The userId associated with the client enabling secure lock device state,
         *              or USER_NULL if disabled.
         */
        private void updateStateAndWriteToFile(boolean enabled,
                Map<String, SettingState> settingsStateMap, int userId) {
            synchronized (mFileLock) {
                boolean changed = (mIsEnabled != enabled) || (mClientUserId != userId)
                        || (mSettingsStateMap != settingsStateMap);

                if (changed) {
                    mIsEnabled = enabled;
                    mSettingsStateMap = settingsStateMap;
                    mClientUserId = userId;
                    mHandler.post(() -> writeToFileInternal(enabled, settingsStateMap, userId));
                }
            }
        }

        /**
         * Writes to the secure lock device state atomic file.
         */
        private void writeToFileInternal(boolean enabled,
                Map<String, SettingState> settingsStateMap, int clientId) {
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

                if (settingsStateMap != null) {
                    serializer.startTag(null, XML_TAG_ORIGINAL_SETTINGS);
                    for (Map.Entry<String, SettingState> entry : settingsStateMap.entrySet()) {
                        String key = entry.getKey();
                        SettingState state = entry.getValue();
                        SettingState.SettingType type = state.getType();
                        Object originalValue = state.getOriginalValue();

                        if (originalValue == null) continue;

                        serializer.startTag(null, XML_TAG_SETTING);
                        serializer.attribute(null, XML_ATTR_SETTING_KEY, key);
                        serializer.attribute(null, XML_ATTR_SETTING_TYPE,
                                state.getType().name());
                        serializer.startTag(null, XML_TAG_SETTING_ORIGINAL_VALUE);
                        if (type == SettingState.SettingType.SECURE_SETTING
                                || type == SettingState.SettingType.SYSTEM_SETTING
                                || type == SettingState.SettingType.ADB_SETTING
                                || type == SettingState.SettingType.USER_SWITCHING_SETTING
                                || type == SettingState.SettingType.NFC_STATE
                        ) {
                            serializer.attribute(null, XML_ATTR_SETTING_TYPE, type.name());
                            serializer.text(originalValue.toString());
                        } else if (type == SettingState.SettingType.DEVICE_POLICY_RESTRICTION) {
                            serializer.attribute(null, XML_ATTR_SETTING_TYPE, type.name());
                            Bundle bundle = (Bundle) originalValue;
                            Parcel parcel = Parcel.obtain();
                            try {
                                bundle.writeToParcel(parcel, 0);
                                byte[] bytes = parcel.marshall();
                                String base64String = Base64.encodeToString(bytes,
                                        Base64.NO_WRAP | Base64.NO_PADDING);
                                serializer.text(base64String);
                            } finally {
                                parcel.recycle();
                            }
                        } else if (type == SettingState.SettingType.DISABLE_FLAGS) {
                            serializer.attribute(null, XML_ATTR_SETTING_TYPE, type.name());
                            Pair<Integer, Integer> info =
                                    ((StatusBarManager.DisableInfo) originalValue).toFlags();
                            serializer.startTag(null, "disable1")
                                    .text(String.valueOf(info.first))
                                    .endTag(null, "disable1");
                            serializer.startTag(null, "disable2")
                                    .text(String.valueOf(info.second))
                                    .endTag(null, "disable2");
                        }  else if (type == SettingState.SettingType.USB_STATE) {
                            serializer.attribute(null, XML_ATTR_SETTING_TYPE, type.name());
                            Map<UsbPort, Boolean> usbPortsEnabledStatus =
                                    (Map<UsbPort, Boolean>) originalValue;
                            for (Map.Entry<UsbPort, Boolean> mapEntry :
                                    usbPortsEnabledStatus.entrySet()) {
                                UsbPort port = mapEntry.getKey();
                                boolean isEnabled = mapEntry.getValue();
                                if (port != null && port.getId() != null) {
                                    serializer.startTag(null, "port");
                                    serializer.attribute(null, "id", port.getId());
                                    serializer.attribute(null, "enabled",
                                            String.valueOf(isEnabled));
                                    serializer.endTag(null, "port");
                                }
                            }
                        }
                        serializer.endTag(null, XML_TAG_SETTING_ORIGINAL_VALUE);
                        serializer.endTag(null, XML_TAG_SETTING);
                    }
                    serializer.endTag(null, XML_TAG_ORIGINAL_SETTINGS);
                }

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
            mSettingsStateMap.clear();
        }

        /**
         * Updates the current Global settings to reflect Secure Lock Device being enabled.
         * @param userId the userId of the client that enabled secure lock device
         * @param settingsStateMap Map with snapshot of current state of settings
         */
        void storeSecureLockDeviceEnabled(int userId, Map<String, SettingState> settingsStateMap) {
            if (DEBUG) {
                Slog.d(TAG, "Storing SLD enabled by user: " + userId);
            }
            updateStateAndWriteToFile(/* enabled= */ true, /* settingsStateMap= */ settingsStateMap,
                    /* userId= */ userId);
        }

        /**
         * Updates the current Global settings to reflect Secure Lock Device being disabled.
         */
        void storeSecureLockDeviceDisabled() {
            if (DEBUG) {
                Slog.d(TAG, "Storing SLD disabled.");
            }
            updateStateAndWriteToFile(/* enabled= */ false, /* settingsStateMap= */ null,
                    /* userId= */ UserHandle.USER_NULL);
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

    /** Marker class for the token used to disable keyguard. */
    private static class LockTaskToken extends Binder {
        private LockTaskToken() {}
    }
}