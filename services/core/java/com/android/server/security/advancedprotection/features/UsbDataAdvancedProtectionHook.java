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

package com.android.server.security.advancedprotection.features;

import static android.app.Notification.EXTRA_SUBSTITUTE_APP_NAME;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_USER_PRESENT;
import static android.hardware.usb.UsbManager.ACTION_USB_PORT_CHANGED;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB;
import static android.hardware.usb.UsbPortStatus.DATA_ROLE_NONE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_DISABLED_FORCE;
import static android.hardware.usb.UsbPortStatus.POWER_ROLE_SINK;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_CONNECTED;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_DISCONNECTED;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_UNKNOWN;
import static android.hardware.usb.InternalUsbDataSignalDisableReason.USB_DISABLE_REASON_APM;

import android.annotation.IntDef;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.hardware.usb.ParcelableUsbPort;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.SystemClock;
import android.security.Flags;
import android.util.Slog;
import android.content.pm.PackageManager;

import com.android.server.LocalServices;
import java.lang.Runnable;

import android.security.advancedprotection.AdvancedProtectionFeature;
import android.security.advancedprotection.AdvancedProtectionProtoEnums;

import com.android.internal.R;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.security.advancedprotection.AdvancedProtectionService;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;

/**
 * AAPM Feature for managing and protecting USB data signal from attacks.
 *
 * @hide
 */
public class UsbDataAdvancedProtectionHook extends AdvancedProtectionHook {
    private static final String TAG = "AdvancedProtectionUsb";

    private static final String APM_USB_FEATURE_NOTIF_CHANNEL = "APM_USB_SERVICE_NOTIF_CHANNEL";
    private static final String CHANNEL_NAME = "ApmUsbProtectionUiNotificationChannel";
    private static final String USB_DATA_PROTECTION_ENABLE_SYSTEM_PROPERTY =
            "ro.usb.data_protection.disable_when_locked.supported";
    private static final String USB_DATA_PROTECTION_REPLUG_REQUIRED_UPON_ENABLE_SYSTEM_PROPERTY =
            "ro.usb.data_protection.disable_when_locked.replug_required_upon_enable";
    private static final String
            USB_DATA_PROTECTION_POWER_BRICK_CONNECTION_CHECK_TIMEOUT_SYSTEM_PROPERTY =
                    "ro.usb.data_protection.disable_when_locked.power_brick_connection_check_timeout";
    private static final String USB_DATA_PROTECTION_PD_COMPLIANCE_CHECK_TIMEOUT_SYSTEM_PROPERTY =
            "ro.usb.data_protection.disable_when_locked.pd_compliance_check_timeout";
    private static final String
            USB_DATA_PROTECTION_DATA_REQUIRED_FOR_HIGH_POWER_CHARGE_SYSTEM_PROPERTY =
                    "ro.usb.data_protection.disable_when_locked.data_required_for_high_power_charge";
    private static final String ACTION_SILENCE_NOTIFICATION =
            "com.android.server.security.advancedprotection.features.silence";
    private static final String EXTRA_SILENCE_DATA_NOTIFICATION = "silence_data_notification";
    private static final String EXTRA_SILENCE_POWER_NOTIFICATION = "silence_power_notification";

    private static final int NOTIFICATION_CHARGE = 0;
    private static final int NOTIFICATION_CHARGE_DATA = 1;
    private static final int NOTIFICATION_DATA = 2;

    private static final int DELAY_DISABLE_MILLIS = 15000;
    private static final int USB_DATA_CHANGE_MAX_RETRY_ATTEMPTS = 3;
    private static final long USB_PORT_POWER_BRICK_CONNECTION_CHECK_TIMEOUT_DEFAULT_MILLIS = 3000;
    private static final long USB_PD_COMPLIANCE_CHECK_TIMEOUT_DEFAULT_MILLIS = 1000;

    @IntDef({NOTIFICATION_CHARGE, NOTIFICATION_CHARGE_DATA, NOTIFICATION_DATA})
    private @interface NotificationType {}

    private static final Map<Integer, Integer> NOTIFICATION_TYPE_TO_TITLE =
            Map.of(
                    NOTIFICATION_CHARGE,
                    R.string.usb_apm_usb_plugged_in_when_locked_notification_title,
                    NOTIFICATION_CHARGE_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_notification_title,
                    NOTIFICATION_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_notification_title);
    private static final Map<Integer, Integer> NOTIFICATION_TYPE_TO_TITLE_WITH_REPLUG =
            Map.of(
                    NOTIFICATION_CHARGE,
                    R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_title,
                    NOTIFICATION_CHARGE_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_title,
                    NOTIFICATION_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_title);

    private static final Map<Integer, Integer> NOTIFICATION_TYPE_TO_TEXT =
            Map.of(
                    NOTIFICATION_CHARGE,
                    R.string.usb_apm_usb_plugged_in_for_power_brick_notification_text,
                    NOTIFICATION_CHARGE_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_low_power_charge_notification_text,
                    NOTIFICATION_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_notification_text);
    private static final Map<Integer, Integer> NOTIFICATION_TYPE_TO_TEXT_WITH_REPLUG =
            Map.of(
                    NOTIFICATION_CHARGE,
                    R.string.usb_apm_usb_plugged_in_for_power_brick_replug_notification_text,
                    NOTIFICATION_CHARGE_DATA,
                    R.string
                            .usb_apm_usb_plugged_in_when_locked_low_power_charge_replug_notification_text,
                    NOTIFICATION_DATA,
                    R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_text);

    // We use handlers for tasks that may need to be updated by broadcasts events.
    private final Handler mDelayedDisableHandler = new Handler(Looper.getMainLooper());
    private final Handler mDelayedNotificationHandler = new Handler(Looper.getMainLooper());

    private AdvancedProtectionFeature mFeature =
            new AdvancedProtectionFeature(FEATURE_ID_DISALLOW_USB);

    private final Context mContext;

    private UsbManager mUsbManager;
    private IUsbManagerInternal mUsbManagerInternal;
    private BroadcastReceiver mUsbProtectionBroadcastReceiver;
    private KeyguardManager mKeyguardManager;
    private NotificationManager mNotificationManager;
    private NotificationChannel mNotificationChannel;
    private AdvancedProtectionService mAdvancedProtectionService;

    private UsbPortStatus mLastUsbPortStatus;

    // TODO(b/418846176):  Move these to a system property
    private long mUsbPortPowerBrickConnectionCheckTimeoutMillis;
    private long mUsbPortPdComplianceCheckTimeoutMillis;

    private boolean mCanSetUsbDataSignal = false;
    private boolean mDataRequiredForHighPowerCharge = false;
    private boolean mReplugRequiredUponEnable = false;
    private boolean mSilenceDataNotification = false;
    private boolean mSilencePowerNotification = false;
    private boolean mBroadcastReceiverIsRegistered = false;
    private boolean mIsAfterFirstUnlock = false;

    public UsbDataAdvancedProtectionHook(
            Context context, boolean enabled, AdvancedProtectionService advancedProtectionService) {
        super(context, enabled);
        mContext = context;
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                && !mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY)) {
            return;
        }
        mUsbManager = Objects.requireNonNull(mContext.getSystemService(UsbManager.class));
        mNotificationManager =
                Objects.requireNonNull(mContext.getSystemService(NotificationManager.class));
        mAdvancedProtectionService = advancedProtectionService;
        mUsbManagerInternal =
                Objects.requireNonNull(LocalServices.getService(IUsbManagerInternal.class));
        mKeyguardManager = Objects.requireNonNull(mContext.getSystemService(KeyguardManager.class));
        mCanSetUsbDataSignal = canSetUsbDataSignal();
        onAdvancedProtectionChanged(enabled);
    }

    @Override
    public AdvancedProtectionFeature getFeature() {
        return mFeature;
    }

    @Override
    public boolean isAvailable() {
        boolean usbDataProtectionEnabled =
                SystemProperties.getBoolean(USB_DATA_PROTECTION_ENABLE_SYSTEM_PROPERTY, false);
        if (!usbDataProtectionEnabled) {
            Slog.d(TAG, "USB data protection is disabled through system property");
        }
        return Flags.aapmFeatureUsbDataProtection()
                && (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST) || mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY))
                && mAdvancedProtectionService.isUsbDataProtectionEnabled()
                && mCanSetUsbDataSignal
                && usbDataProtectionEnabled;
    }

    @Override
    public void onAdvancedProtectionChanged(boolean enabled) {
        if (!isAvailable() && enabled) {
            Slog.w(TAG, "AAPM USB data protection feature is disabled");
            return;
        }
        Slog.i(TAG, "onAdvancedProtectionChanged: " + enabled);
        if (enabled) {
            if (mUsbProtectionBroadcastReceiver == null) {
                initialize();
            }
            if (!mBroadcastReceiverIsRegistered) {
                registerReceiver();
            }
            setUsbDataSignalIfPossible(false);
        } else {
            if (mBroadcastReceiverIsRegistered) {
                unregisterReceiver();
            }
            setUsbDataSignalIfPossible(true);
        }
    }

    private void initialize() {
        mDataRequiredForHighPowerCharge =
                SystemProperties.getBoolean(
                        USB_DATA_PROTECTION_DATA_REQUIRED_FOR_HIGH_POWER_CHARGE_SYSTEM_PROPERTY,
                        false);
        mReplugRequiredUponEnable =
                SystemProperties.getBoolean(
                        USB_DATA_PROTECTION_REPLUG_REQUIRED_UPON_ENABLE_SYSTEM_PROPERTY, false);
        mUsbPortPowerBrickConnectionCheckTimeoutMillis =
                SystemProperties.getLong(
                        USB_DATA_PROTECTION_POWER_BRICK_CONNECTION_CHECK_TIMEOUT_SYSTEM_PROPERTY,
                        USB_PORT_POWER_BRICK_CONNECTION_CHECK_TIMEOUT_DEFAULT_MILLIS);
        mUsbPortPdComplianceCheckTimeoutMillis =
                SystemProperties.getLong(
                        USB_DATA_PROTECTION_PD_COMPLIANCE_CHECK_TIMEOUT_SYSTEM_PROPERTY,
                        USB_PD_COMPLIANCE_CHECK_TIMEOUT_DEFAULT_MILLIS);
        initializeNotifications();
        mUsbProtectionBroadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        try {
                            if (ACTION_USER_PRESENT.equals(intent.getAction())
                                    && !mKeyguardManager.isKeyguardLocked()) {
                                mIsAfterFirstUnlock = true;
                                mDelayedDisableHandler.removeCallbacksAndMessages(null);
                                cleanUpNotificationHandlerTasks();
                                setUsbDataSignalIfPossible(true);

                            } else if (ACTION_SCREEN_OFF.equals(intent.getAction())
                                    && mKeyguardManager.isKeyguardLocked()) {
                                setUsbDataSignalIfPossible(false);

                            } else if (ACTION_USB_PORT_CHANGED.equals(intent.getAction())) {
                                UsbPortStatus portStatus =
                                        intent.getParcelableExtra(
                                                UsbManager.EXTRA_PORT_STATUS, UsbPortStatus.class);

                                // If we cannot retrieve the port status, then we skip this event.
                                if (portStatus == null) {
                                    Slog.w(
                                            TAG,
                                            "UsbPort Changed: USB Data protection failed to"
                                                    + " retrieve port status");
                                    return;
                                }
                                mLastUsbPortStatus = portStatus;

                                if (Build.IS_DEBUGGABLE) {
                                    dumpUsbDevices(portStatus);
                                }

                                if (mKeyguardManager.isKeyguardLocked()) {
                                    updateDelayedDisableTask(portStatus);
                                }

                                if (!portStatus.isConnected()) {
                                    cleanUpNotificationHandlerTasks();
                                    clearExistingNotification();

                                    /*
                                     * Due to limitations of current APIs, we cannot cannot fully
                                     * rely on power brick and pd compliance check to be accurate
                                     * until it's passed the check timeouts unless the value is
                                     * POWER_BRICK_STATUS_CONNECTED or isCompliant=true
                                     * respectively.
                                     */
                                } else if (portStatus.getCurrentPowerRole() == POWER_ROLE_SINK) {
                                    long pbCheckDuration =
                                            portStatus.getPowerBrickConnectionStatus()
                                                            != POWER_BRICK_STATUS_CONNECTED
                                                    ? mUsbPortPowerBrickConnectionCheckTimeoutMillis
                                                    : 0;
                                    long pdCheckDuration =
                                            !portStatus.isPdCompliant()
                                                    ? mUsbPortPdComplianceCheckTimeoutMillis
                                                    : 0;
                                    long delayTimeMillis = pbCheckDuration + pdCheckDuration;
                                    if (delayTimeMillis <= 0) {
                                        cleanUpNotificationHandlerTasks();
                                        determineUsbChargeStateAndSendNotification(portStatus);
                                    } else {
                                        updateDelayedNotificationTask(delayTimeMillis);
                                    }
                                } else {
                                    cleanUpNotificationHandlerTasks();
                                    createAndSendNotificationIfDeviceIsLocked(
                                            portStatus, NOTIFICATION_DATA);
                                }
                            }
                        } catch (Exception e) {
                            Slog.e(TAG, "USB Data protection failed with: " + e.getMessage());
                        }
                    }

                    private void updateDelayedNotificationTask(long delayTimeMillis) {
                        if (!mDelayedNotificationHandler.hasMessagesOrCallbacks()
                                && delayTimeMillis > 0) {
                            boolean taskPosted =
                                    mDelayedNotificationHandler.postDelayed(
                                            () -> {
                                                determineUsbChargeStateAndSendNotification(
                                                        mLastUsbPortStatus);
                                            },
                                            delayTimeMillis);
                            if (!taskPosted) {
                                Slog.w(TAG, "Delayed Disable Task: Failed to post task");
                            }
                        }
                    }

                    private void updateDelayedDisableTask(UsbPortStatus portStatus) {
                        // For recovered intermittent/unreliable USB connections
                        if (usbPortIsConnectedAndDataEnabled(portStatus)) {
                            mDelayedDisableHandler.removeCallbacksAndMessages(null);
                        } else if (!mDelayedDisableHandler.hasMessagesOrCallbacks()) {
                            boolean taskPosted =
                                    mDelayedDisableHandler.postDelayed(
                                            () -> {
                                                if (mKeyguardManager.isKeyguardLocked()) {
                                                    setUsbDataSignalIfPossible(false);
                                                }
                                            },
                                            DELAY_DISABLE_MILLIS);
                            if (!taskPosted) {
                                Slog.w(TAG, "Delayed Disable Task: Failed to post task");
                            }
                        }
                    }

                    private void determineUsbChargeStateAndSendNotification(
                            UsbPortStatus portStatus) {
                        clearExistingNotification();

                        if (portStatus.getPowerBrickConnectionStatus()
                                == POWER_BRICK_STATUS_CONNECTED) {
                            if (mDataRequiredForHighPowerCharge) {
                                createAndSendNotificationIfDeviceIsLocked(
                                        portStatus, NOTIFICATION_CHARGE);
                            } else {
                                mAdvancedProtectionService.logDialogShown(
                                        AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_USB,
                                        AdvancedProtectionProtoEnums
                                                .DIALOGUE_TYPE_BLOCKED_INTERACTION_SILENT,
                                        false);
                            }
                        } else {
                            if (portStatus.isPdCompliant() && !mDataRequiredForHighPowerCharge) {
                                createAndSendNotificationIfDeviceIsLocked(
                                        portStatus, NOTIFICATION_DATA);
                            } else {
                                createAndSendNotificationIfDeviceIsLocked(
                                        portStatus, NOTIFICATION_CHARGE_DATA);
                            }
                        }
                    }

                    private void cleanUpNotificationHandlerTasks() {
                        mDelayedNotificationHandler.removeCallbacksAndMessages(null);
                    }

                    private boolean usbPortIsConnectedAndDataEnabled(UsbPortStatus portStatus) {
                        return portStatus != null
                                && portStatus.isConnected()
                                && portStatus.getUsbDataStatus()
                                        != UsbPortStatus.DATA_STATUS_DISABLED_FORCE;
                    }

                    // TODO:(b/401540215) Remove this as part of pre-release cleanup
                    private void dumpUsbDevices(UsbPortStatus portStatus) {
                        Map<String, UsbDevice> portStatusMap = mUsbManager.getDeviceList();
                        for (UsbDevice device : portStatusMap.values()) {
                            Slog.d(TAG, "Device: " + device.getDeviceName());
                        }
                        UsbAccessory[] accessoryList = mUsbManager.getAccessoryList();
                        if (accessoryList != null) {
                            for (UsbAccessory accessory : accessoryList) {
                                Slog.d(TAG, "Accessory: " + accessory.toString());
                            }
                        }
                    }
                };
    }

    private void initializeNotifications() {
        if (mNotificationManager.getNotificationChannel(APM_USB_FEATURE_NOTIF_CHANNEL) == null) {
            mNotificationChannel =
                    new NotificationChannel(
                            APM_USB_FEATURE_NOTIF_CHANNEL,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(mNotificationChannel);
        }
    }

    private void createAndSendNotificationIfDeviceIsLocked(
            UsbPortStatus portStatus, @NotificationType int notificationType) {
        if ((notificationType == NOTIFICATION_CHARGE
                        && mSilenceDataNotification
                        && mSilencePowerNotification)
                || (notificationType == NOTIFICATION_CHARGE && mSilencePowerNotification)
                || (notificationType == NOTIFICATION_DATA && mSilenceDataNotification)) {
            return;
        } else if (!mKeyguardManager.isKeyguardLocked()
                || !usbPortIsConnectedWithDataDisabled(portStatus)) {
            mAdvancedProtectionService.logDialogShown(
                    AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_USB,
                    AdvancedProtectionProtoEnums.DIALOGUE_TYPE_BLOCKED_INTERACTION_SILENT,
                    false);
            return;
        }

        String notificationTitle;
        String notificationBody;
        if (mReplugRequiredUponEnable) {
            notificationTitle =
                    mContext.getString(
                            NOTIFICATION_TYPE_TO_TITLE_WITH_REPLUG.get(notificationType));
            notificationBody =
                    mContext.getString(NOTIFICATION_TYPE_TO_TEXT_WITH_REPLUG.get(notificationType));
        } else {
            notificationTitle =
                    mContext.getString(NOTIFICATION_TYPE_TO_TITLE.get(notificationType));
            notificationBody = mContext.getString(NOTIFICATION_TYPE_TO_TEXT.get(notificationType));
        }

        Intent silenceIntent = new Intent(ACTION_SILENCE_NOTIFICATION);
        silenceIntent.putExtra(EXTRA_SILENCE_POWER_NOTIFICATION, true);
        sendNotification(
                notificationTitle,
                notificationBody,
                PendingIntent.getBroadcast(
                        mContext, 0, silenceIntent, PendingIntent.FLAG_IMMUTABLE),
                getAtomUsbNotificationType(notificationType));

        mAdvancedProtectionService.logDialogShown(
                AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_USB,
                AdvancedProtectionProtoEnums.DIALOGUE_TYPE_BLOCKED_INTERACTION,
                false);
    }

    private int getAtomUsbNotificationType(@NotificationType int internalNotificationType) {
        switch (internalNotificationType) {
            case NOTIFICATION_CHARGE_DATA:
                return AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_CHARGE_DATA;
            case NOTIFICATION_CHARGE:
                return AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_CHARGE;
            case NOTIFICATION_DATA:
                return AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_DATA;
            default:
                return AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_UNKNOWN;
        }
    }

    private void sendNotification(
            String title,
            String message,
            PendingIntent silencePendingIntent,
            int notificationType) {
        Bundle notificationExtras = new Bundle();
        notificationExtras.putString(
                EXTRA_SUBSTITUTE_APP_NAME,
                mContext.getString(
                        R.string.usb_apm_usb_plugged_in_when_locked_notification_app_title));

        Notification.Builder notif =
                new Notification.Builder(mContext, APM_USB_FEATURE_NOTIF_CHANNEL)
                        .setSmallIcon(R.drawable.ic_security_privacy_notification_badge)
                        .setColor(
                                mContext.getColor(
                                        R.color.security_privacy_notification_tint_normal))
                        .setContentTitle(title)
                        .setStyle(new Notification.BigTextStyle().bigText(message))
                        .setAutoCancel(true)
                        .addExtras(notificationExtras);
        if (silencePendingIntent != null) {
            notif.addAction(
                    0,
                    mContext.getString(
                            R.string
                                    .usb_apm_usb_plugged_in_when_locked_notification_silence_action_text),
                    silencePendingIntent);
        }

        // Intent may fail to initialize in BFU state, so we may need to initialize it lazily.
        PendingIntent helpPendingIntent = createHelpPendingIntent();
        if (helpPendingIntent != null) {
            notif.setContentIntent(helpPendingIntent);
        }
        UserHandle userHandle =
                mIsAfterFirstUnlock
                        ? UserHandle.of(ActivityManager.getCurrentUser())
                        : mContext.getUser();
        mNotificationManager.notifyAsUser(
                TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER, notif.build(), userHandle);
        FrameworkStatsLog.write(
                FrameworkStatsLog.ADVANCED_PROTECTION_USB_NOTIFICATION_DISPLAYED, notificationType);
    }

    private void clearExistingNotification() {
        mNotificationManager.cancel(TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER);
    }

    private boolean usbPortIsConnectedWithDataDisabled(UsbPortStatus portStatus) {
        return portStatus != null
                && portStatus.isConnected()
                && portStatus.getUsbDataStatus() == DATA_STATUS_DISABLED_FORCE;
    }

    private void setUsbDataSignalIfPossible(boolean status) {
        /*
         * We check if there is already an existing USB connection and skip the USB
         * disablement if there is one unless it is in BFU state.
         */
        if (!status && deviceHaveUsbDataConnection() && mIsAfterFirstUnlock) {
            Slog.i(TAG, "USB Data protection toggle skipped due to existing USB connection");
            return;
        }

        int usbChangeStateReattempts = 0;
        while (usbChangeStateReattempts < USB_DATA_CHANGE_MAX_RETRY_ATTEMPTS) {
            try {
                if (mUsbManagerInternal.enableUsbDataSignal(status, USB_DISABLE_REASON_APM)) {
                    break;
                } else {
                    Slog.e(TAG, "USB Data protection toggle attempt failed");
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "RemoteException thrown when calling enableUsbDataSignal", e);
            }
            usbChangeStateReattempts += 1;
        }

        // Log the error if the USB change state failed at least once.
        if (usbChangeStateReattempts > 0) {
            FrameworkStatsLog.write(
                    FrameworkStatsLog.ADVANCED_PROTECTION_USB_STATE_CHANGE_ERROR_REPORTED,
                    /* desired_signal_state */ status,
                    /* retries_occurred */ usbChangeStateReattempts);
        }
        if (status) {
            clearExistingNotification();
        }
    }

    private boolean deviceHaveUsbDataConnection() {
        for (UsbPort usbPort : mUsbManager.getPorts()) {
            if (Build.IS_DEBUGGABLE) {
                Slog.i(
                        TAG,
                        "setUsbDataSignal: false, Port status: " + usbPort.getStatus() == null
                                ? "null"
                                : usbPort.getStatus().toString());
            }
            if (usbPortIsConnectedWithDataEnabled(usbPort)) {
                return true;
            }
        }
        return false;
    }

    private boolean usbPortIsConnectedWithDataEnabled(UsbPort usbPort) {
        UsbPortStatus usbPortStatus = usbPort.getStatus();
        return usbPortStatus != null
                && usbPortStatus.isConnected()
                && usbPortStatus.getCurrentDataRole() != DATA_ROLE_NONE;
    }

    private void registerReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(ACTION_USER_PRESENT);
        filter.addAction(ACTION_SCREEN_OFF);
        filter.addAction(UsbManager.ACTION_USB_PORT_CHANGED);

        mContext.registerReceiverAsUser(
                mUsbProtectionBroadcastReceiver, UserHandle.ALL, filter, null, null);

        mContext.registerReceiverAsUser(
                new NotificationSilenceReceiver(),
                UserHandle.ALL,
                new IntentFilter(ACTION_SILENCE_NOTIFICATION),
                null,
                null,
                Context.RECEIVER_NOT_EXPORTED);

        mBroadcastReceiverIsRegistered = true;
    }

    private void unregisterReceiver() {
        mContext.unregisterReceiver(mUsbProtectionBroadcastReceiver);
        mBroadcastReceiverIsRegistered = false;
    }

    // TODO:(b/428090717) Fix intent resolution during boot time
    private PendingIntent createHelpPendingIntent() {
        String helpIntentActivityUri =
                mContext.getString(R.string.config_help_url_action_disabled_by_advanced_protection);
        try {
            Intent helpIntent = Intent.parseUri(helpIntentActivityUri, Intent.URI_INTENT_SCHEME);
            if (helpIntent == null) {
                Slog.w(TAG, "Failed to parse help intent " + helpIntentActivityUri);
                return null;
            }
            helpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivity(helpIntent, 0);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return PendingIntent.getActivityAsUser(
                        mContext,
                        0,
                        helpIntent,
                        PendingIntent.FLAG_IMMUTABLE,
                        null,
                        UserHandle.of(ActivityManager.getCurrentUser()));
            } else {
                Slog.w(TAG, "Failed to resolve help intent " + resolveInfo);
            }
        } catch (URISyntaxException e) {
            Slog.e(TAG, "Failed to create help intent", e);
            return null;
        }

        return null;
    }

    private boolean canSetUsbDataSignal() {
        if (Build.IS_DEBUGGABLE) {
            Slog.i(TAG, "USB_HAL_VERSION: " + mUsbManager.getUsbHalVersion());
        }
        return mUsbManager.getUsbHalVersion() >= UsbManager.USB_HAL_V1_3;
    }

    private class NotificationSilenceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SILENCE_NOTIFICATION.equals(intent.getAction())) {
                if (intent.getBooleanExtra(EXTRA_SILENCE_DATA_NOTIFICATION, false)) {
                    mSilenceDataNotification = true;
                }
                if (intent.getBooleanExtra(EXTRA_SILENCE_POWER_NOTIFICATION, false)) {
                    mSilencePowerNotification = true;
                }
                sendNotification(
                        mContext.getString(R.string.usb_apm_usb_notification_silenced_title),
                        mContext.getString(R.string.usb_apm_usb_notification_silenced_text),
                        null,
                        AdvancedProtectionProtoEnums.USB_NOTIFICATION_TYPE_SILENCE);
            }
        }
    }
}
