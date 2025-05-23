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
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.security.Flags;
import android.util.Slog;

import com.android.server.LocalServices;
import java.lang.Runnable;

import java.util.function.Consumer;
import java.util.concurrent.Executor;

import android.security.advancedprotection.AdvancedProtectionFeature;

import com.android.internal.R;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.util.FrameworkStatsLog;

import java.util.Map;
import java.util.Objects;
import android.net.Uri;

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
            "ro.usb.data_protection.apm.replug_required_upon_enable";
    private static final String
            USB_DATA_PROTECTION_DATA_REQUIRED_FOR_HIGH_POWER_CHARGE_SYSTEM_PROPERTY =
                    "ro.usb.data_protection.apm.data_required_for_high_power_charge";
    private static final String ACTION_SILENCE_NOTIFICATION =
            "com.android.server.security.advancedprotection.features.silence";
    private static final String EXTRA_SILENCE_DATA_NOTIFICATION = "silence_data_notification";
    private static final String EXTRA_SILENCE_POWER_NOTIFICATION = "silence_power_notification";

    private static final int DELAY_DISABLE_MS = 3000;
    private static final int OS_USB_DISABLE_REASON_LOCKDOWN_MODE = 1;
    private static final int USB_DATA_CHANGE_MAX_RETRY_ATTEMPTS = 3;

    private final Context mContext;
    private final Handler mDelayedDisableHandler = new Handler(Looper.getMainLooper());

    private UsbManager mUsbManager;
    private IUsbManagerInternal mUsbManagerInternal;
    private BroadcastReceiver mUsbProtectionBroadcastReceiver;
    private KeyguardManager mKeyguardManager;
    private NotificationManager mNotificationManager;
    private NotificationChannel mNotificationChannel;

    private boolean mCanSetUsbDataSignal = false;
    private boolean mDataRequiredForHighPowerCharge = false;
    private boolean mReplugRequiredUponEnable = false;
    private boolean mSilenceDataNotification = false;
    private boolean mSilencePowerNotification = false;
    private AdvancedProtectionFeature mFeature =
            new AdvancedProtectionFeature(FEATURE_ID_DISALLOW_USB);

    private boolean mBroadcastReceiverIsRegistered = false;
    private PendingIntent mHelpPendingIntent;
    private boolean mIsAfterFirstUnlock = false;

    public UsbDataAdvancedProtectionHook(Context context, boolean enabled) {
        super(context, enabled);
        mContext = context;
        mUsbManager = mContext.getSystemService(UsbManager.class);
        mUsbManagerInternal =
                Objects.requireNonNull(LocalServices.getService(IUsbManagerInternal.class));
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
                // TODO(b/418846176): Set fallback default to false once product flag is set b/418846176
                SystemProperties.getBoolean(USB_DATA_PROTECTION_ENABLE_SYSTEM_PROPERTY, true);
        if (!usbDataProtectionEnabled) {
            Slog.d(TAG, "USB data protection is disabled through system property");
        }
        return Flags.aapmFeatureUsbDataProtection()
                && mCanSetUsbDataSignal
                && usbDataProtectionEnabled;
    }

    @Override
    public void onAdvancedProtectionChanged(boolean enabled) {
        if (!isAvailable()) {
            Slog.w(TAG, "AAPM USB data protection feature is disabled");
            return;
        }
        Slog.i(TAG, "onAdvancedProtectionChanged: " + enabled);
        if (enabled) {
            Slog.i(TAG, "onAdvancedProtectionChanged: enabled");
            if (mUsbProtectionBroadcastReceiver == null) {
                initialize();
            }
            if (!mBroadcastReceiverIsRegistered) {
                registerReceiver();
                setupNotificationIntents();
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
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
        mDataRequiredForHighPowerCharge =
                SystemProperties.getBoolean(
                        USB_DATA_PROTECTION_DATA_REQUIRED_FOR_HIGH_POWER_CHARGE_SYSTEM_PROPERTY,
                        false);
        mReplugRequiredUponEnable =
                SystemProperties.getBoolean(
                        USB_DATA_PROTECTION_REPLUG_REQUIRED_UPON_ENABLE_SYSTEM_PROPERTY, false);
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
                                setUsbDataSignalIfPossible(true);

                            } else if (ACTION_SCREEN_OFF.equals(intent.getAction())
                                    && mKeyguardManager.isKeyguardLocked()) {
                                setUsbDataSignalIfPossible(false);

                            } else if (ACTION_USB_PORT_CHANGED.equals(intent.getAction())) {
                                if (Build.IS_DEBUGGABLE) {
                                    dumpUsbDevices();
                                }

                                UsbPortStatus portStatus =
                                        intent.getParcelableExtra(
                                                UsbManager.EXTRA_PORT_STATUS, UsbPortStatus.class);

                                if (mKeyguardManager.isKeyguardLocked()) {
                                    updateDelayedDisableTask(portStatus);
                                }

                                if (usbIsNotConnected(portStatus)) {
                                    clearExistingNotification();
                                } else {
                                    if (isUsbPortConnectionPowerBrick(portStatus)) {
                                        // For cases where high speed chargers do not use USB-PD PPS
                                        if (mDataRequiredForHighPowerCharge) {
                                            sendPowerNotificationIfDeviceLocked(portStatus);
                                        }
                                    } else {
                                        if (mDataRequiredForHighPowerCharge
                                                && isUsbPowerSink(portStatus)) {
                                            sendPowerAndDataNotificationIfDeviceLocked(portStatus);
                                        } else {
                                            sendDataNotificationIfDeviceLocked(portStatus);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Slog.e(TAG, "USB Data protection failed with: " + e.getMessage());
                        }
                    }

                    private boolean usbIsNotConnected(UsbPortStatus portStatus) {
                        return portStatus != null && !portStatus.isConnected();
                    }

                    private boolean isUsbPortConnectionPowerBrick(UsbPortStatus portStatus) {
                        return portStatus != null
                                && portStatus.getPowerBrickConnectionStatus()
                                        == UsbPortStatus.POWER_BRICK_STATUS_CONNECTED;
                    }

                    private boolean isUsbPowerSink(UsbPortStatus portStatus) {
                        return portStatus != null
                                && portStatus.getCurrentPowerRole()
                                        == UsbPortStatus.POWER_ROLE_SINK;
                    }

                    private void updateDelayedDisableTask(UsbPortStatus portStatus) {
                        // For recovered intermittent/unreliable USB connections
                        if (usbPortIsConnectedAndDataEnabled(portStatus)) {
                            mDelayedDisableHandler.removeCallbacksAndMessages(null);
                        } else if (!mDelayedDisableHandler.hasMessagesOrCallbacks()) {
                            mDelayedDisableHandler.postDelayed(
                                    () -> {
                                        if (mKeyguardManager.isKeyguardLocked()) {
                                            setUsbDataSignalIfPossible(false);
                                        }
                                    },
                                    DELAY_DISABLE_MS);
                        }
                    }

                    private boolean usbPortIsConnectedAndDataEnabled(UsbPortStatus portStatus) {
                        return portStatus != null
                                && portStatus.isConnected()
                                && portStatus.getUsbDataStatus()
                                        != UsbPortStatus.DATA_STATUS_DISABLED_FORCE;
                    }

                    // TODO: b/401540215 Remove this as part of pre-release cleanup
                    private void dumpUsbDevices() {
                        Slog.d(TAG, "dumpUsbDevices: ");
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
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        if (mNotificationManager.getNotificationChannel(APM_USB_FEATURE_NOTIF_CHANNEL) == null) {
            mNotificationChannel =
                    new NotificationChannel(
                            APM_USB_FEATURE_NOTIF_CHANNEL,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(mNotificationChannel);
        }
    }

    private void sendNotification(
            String title, String message, PendingIntent silencePendingIntent) {
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

        if (mHelpPendingIntent != null) {
            notif.setContentIntent(mHelpPendingIntent);
        }

        mNotificationManager.notify(
                TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER, notif.build());
    }

    private void clearExistingNotification() {
        mNotificationManager.cancel(TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER);
    }

    // Verify any changes here will also require changes in
    // sendPowerAndDataNotificationIfDeviceLocked, sendDataNotificationIfDeviceLocked
    private void sendPowerNotificationIfDeviceLocked(UsbPortStatus portStatus) {
        if (!mSilencePowerNotification
                && mKeyguardManager.isKeyguardLocked()
                && usbPortIsConnectedWithDataDisabled(portStatus)) {

            String notificationBody;
            if (mReplugRequiredUponEnable) {
                notificationBody =
                        mContext.getString(
                                R.string
                                        .usb_apm_usb_plugged_in_for_power_brick_replug_notification_text);
            } else {
                notificationBody =
                        mContext.getString(
                                R.string.usb_apm_usb_plugged_in_for_power_brick_notification_text);
            }

            Intent silenceIntent = new Intent(ACTION_SILENCE_NOTIFICATION);
            silenceIntent.putExtra(EXTRA_SILENCE_POWER_NOTIFICATION, true);
            sendNotification(
                    mContext.getString(
                            R.string.usb_apm_usb_plugged_in_when_locked_notification_title),
                    notificationBody,
                    PendingIntent.getBroadcast(
                            mContext, 0, silenceIntent, PendingIntent.FLAG_IMMUTABLE));
        }
    }

    // Verify any changes here will also require changes in sendDataNotificationIfDeviceLocked,
    // sendPowerNotificationIfDeviceLocked
    private void sendPowerAndDataNotificationIfDeviceLocked(UsbPortStatus portStatus) {
        if (mSilencePowerNotification && mSilenceDataNotification) return;
        if (mKeyguardManager.isKeyguardLocked() && usbPortIsConnectedWithDataDisabled(portStatus)) {
            String notificationBody;
            if (mReplugRequiredUponEnable) {
                notificationBody =
                        mContext.getString(
                                R.string
                                        .usb_apm_usb_plugged_in_when_locked_low_power_charge_replug_notification_text);
            } else {
                notificationBody =
                        mContext.getString(
                                R.string
                                        .usb_apm_usb_plugged_in_when_locked_low_power_charge_notification_text);
            }

            Intent silenceIntent = new Intent(ACTION_SILENCE_NOTIFICATION);
            silenceIntent.putExtra(EXTRA_SILENCE_DATA_NOTIFICATION, true);
            silenceIntent.putExtra(EXTRA_SILENCE_POWER_NOTIFICATION, true);
            sendNotification(
                    mContext.getString(
                            R.string.usb_apm_usb_plugged_in_when_locked_notification_title),
                    notificationBody,
                    PendingIntent.getBroadcast(
                            mContext, 0, silenceIntent, PendingIntent.FLAG_IMMUTABLE));
        }
    }

    // Verify any changes here will also require changes in
    // sendPowerAndDataNotificationIfDeviceLocked, sendPowerNotificationIfDeviceLocked
    private void sendDataNotificationIfDeviceLocked(UsbPortStatus portStatus) {
        if (!mSilenceDataNotification
                && mKeyguardManager.isKeyguardLocked()
                && usbPortIsConnectedWithDataDisabled(portStatus)) {

            String notificationBody;
            if (mReplugRequiredUponEnable) {
                notificationBody =
                        mContext.getString(
                                R.string
                                        .usb_apm_usb_plugged_in_when_locked_replug_notification_text);
            } else {
                notificationBody =
                        mContext.getString(
                                R.string.usb_apm_usb_plugged_in_when_locked_notification_text);
            }

            Intent silenceIntent = new Intent(ACTION_SILENCE_NOTIFICATION);
            silenceIntent.putExtra(EXTRA_SILENCE_DATA_NOTIFICATION, true);
            sendNotification(
                    mContext.getString(
                            R.string.usb_apm_usb_plugged_in_when_locked_notification_title),
                    notificationBody,
                    PendingIntent.getBroadcast(
                            mContext, 0, silenceIntent, PendingIntent.FLAG_IMMUTABLE));
        }
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
        if (!status && (deviceHaveUsbDataConnection() && mIsAfterFirstUnlock)) {
            return;
        }

        int usbChangeStateReattempts = 0;
        while (usbChangeStateReattempts < USB_DATA_CHANGE_MAX_RETRY_ATTEMPTS) {
            try {
                if (mUsbManagerInternal.enableUsbDataSignal(
                        status, OS_USB_DISABLE_REASON_LOCKDOWN_MODE)) {
                    break;
                } else {
                    Slog.e(TAG, "USB Data protection toggle attemptfailed");
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
        return usbPort.getStatus() != null
                && usbPort.getStatus().isConnected()
                && usbPort.getStatus().getCurrentDataRole() != DATA_ROLE_NONE;
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

    private void setupNotificationIntents() {
        try {
            String helpIntentActivityUri =
                    mContext.getString(
                            R.string.config_help_url_action_disabled_by_advanced_protection);
            Intent helpIntent = Intent.parseUri(helpIntentActivityUri, Intent.URI_INTENT_SCHEME);
            if (helpIntent == null) {
                Slog.w(TAG, "Failed to parse help intent " + helpIntentActivityUri);
                return;
            }
            helpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivity(helpIntent, 0);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                mHelpPendingIntent =
                        PendingIntent.getActivityAsUser(
                                mContext,
                                0,
                                helpIntent,
                                PendingIntent.FLAG_IMMUTABLE,
                                null,
                                UserHandle.ALL);
            } else {
                Slog.w(TAG, "Failed to resolve help intent " + resolveInfo);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to setup notification intents", e);
        }
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
                        null);
            }
        }
    }
}
