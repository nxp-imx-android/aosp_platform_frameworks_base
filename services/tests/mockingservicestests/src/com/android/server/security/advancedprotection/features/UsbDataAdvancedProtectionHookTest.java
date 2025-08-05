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

import static android.hardware.usb.InternalUsbDataSignalDisableReason.USB_DISABLE_REASON_APM;
import static android.hardware.usb.UsbPortStatus.DATA_ROLE_HOST;
import static android.hardware.usb.UsbPortStatus.DATA_ROLE_NONE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_DISABLED_FORCE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_ENABLED;
import static android.hardware.usb.UsbPortStatus.MODE_DFP;
import static android.hardware.usb.UsbPortStatus.MODE_NONE;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_CONNECTED;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_DISCONNECTED;
import static android.hardware.usb.UsbPortStatus.POWER_BRICK_STATUS_UNKNOWN;
import static android.hardware.usb.UsbPortStatus.POWER_ROLE_SINK;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.usb.IUsbManager;
import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionProtoEnums;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.internal.R;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.security.advancedprotection.AdvancedProtectionService;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Unit tests for {@link UsbDataAdvancedProtectionHook}.
 *
 * <p>atest FrameworksMockingServicesTests:UsbDataAdvancedProtectionHookTest
 */
@SuppressLint("VisibleForTests")
@RunWith(AndroidJUnit4.class)
public class UsbDataAdvancedProtectionHookTest {

    private static final String TAG = "AdvancedProtectionUsb";
    private static final String HELP_URL_ACTION = "android.settings.TEST_HELP";
    private static final String ACTION_SILENCE_NOTIFICATION =
            "com.android.server.security.advancedprotection.features.silence";
    private static final long TEST_TIMEOUT_MS = 2000;
    private static final int PD_COMPLIANT_ROLE_COMBINATIONS = 433;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(PendingIntent.class)
                    .mockStatic(ActivityManager.class)
                    .mockStatic(SystemProperties.class)
                    .mockStatic(Settings.Secure.class)
                    .mockStatic(Intent.class)
                    .build();

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mContext;

    @Mock private AdvancedProtectionService mAdvancedProtectionService;
    @Mock private IUsbManagerInternal mUsbManagerInternal;
    @Mock private KeyguardManager mKeyguardManager;
    @Mock private NotificationManager mNotificationManager;
    @Mock private Handler mDelayDisableHandler;
    @Mock private Handler mDelayedNotificationHandler;
    @Mock private UsbManager mUsbManager;

    @Captor private ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;
    @Captor private ArgumentCaptor<Runnable> mRunnableCaptor;
    @Captor private ArgumentCaptor<Notification> mNotificationCaptor;
    @Captor private ArgumentCaptor<Integer> mNotificationIdCaptor;
    @Captor private ArgumentCaptor<String> mNotificationTagCaptor;
    @Captor private ArgumentCaptor<UserHandle> mUserHandleCaptor;
    @Captor private ArgumentCaptor<IntentFilter> mIntentFilterCaptor;

    private UsbDataAdvancedProtectionHook mUsbDataHook;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    public void setupAndEnableFeature(boolean replugRequired, boolean dataForHighPower)
            throws RemoteException {
        mUsbDataHook =
                new UsbDataAdvancedProtectionHook(
                        mContext,
                        mAdvancedProtectionService,
                        mUsbManager,
                        mUsbManagerInternal,
                        mKeyguardManager,
                        mNotificationManager,
                        mDelayDisableHandler,
                        mDelayedNotificationHandler,
                        true, // canSetUsbDataSignal
                        true); // afterFirstUnlock

        doReturn(replugRequired)
                .when(
                        () ->
                                SystemProperties.getBoolean(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.replug_required_upon_enable"),
                                        anyBoolean()));
        doReturn(dataForHighPower)
                .when(
                        () ->
                                SystemProperties.getBoolean(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.data_required_for_high_power_charge"),
                                        anyBoolean()));
        doReturn(true)
                .when(
                        () ->
                                SystemProperties.getBoolean(
                                        eq("ro.usb.data_protection.disable_when_locked.supported"),
                                        anyBoolean()));
        doReturn(TEST_TIMEOUT_MS)
                .when(
                        () ->
                                SystemProperties.getLong(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.power_brick_connection_check_timeout"),
                                        anyLong()));
        doReturn(TEST_TIMEOUT_MS)
                .when(
                        () ->
                                SystemProperties.getLong(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.pd_compliance_check_timeout"),
                                        anyLong()));
        doReturn(TEST_TIMEOUT_MS).when(() -> SystemProperties.getLong(anyString(), anyLong()));

        // Used for notification builder
        doReturn(1)
                .when(
                        () ->
                                Settings.Secure.getIntForUser(
                                        any(ContentResolver.class),
                                        anyString(),
                                        anyInt(),
                                        anyInt()));
        when(mContext.getString(anyInt())).thenReturn("Test String");
        when(mContext.getString(
                        eq(R.string.config_help_url_action_disabled_by_advanced_protection)))
                .thenReturn(HELP_URL_ACTION);
        when(mAdvancedProtectionService.isUsbDataProtectionEnabled()).thenReturn(true);

        when(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST))
                .thenReturn(true);
        when(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY))
                .thenReturn(true);
        when(mUsbManagerInternal.enableUsbDataSignal(anyBoolean(), anyInt())).thenReturn(true);
        setupMocksForSilenceIntent();
        mUsbDataHook.onAdvancedProtectionChanged(true);
    }

    private void setupMocksForSilenceIntent() throws RemoteException {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        Intent mockHelpIntent = mock(Intent.class);
        doReturn(mockHelpIntent)
                .when(() -> Intent.parseUri(eq(HELP_URL_ACTION), eq(Intent.URI_INTENT_SCHEME)));
        when(mContext.getPackageManager().resolveActivity(eq(mockHelpIntent), eq(0)))
                .thenReturn(resolveInfo);
        PendingIntent mockHelpPendingIntent = mock(PendingIntent.class);
        doReturn(mockHelpPendingIntent)
                .when(
                        () ->
                                PendingIntent.getActivityAsUser(
                                        eq(mContext),
                                        eq(0),
                                        eq(mockHelpIntent),
                                        eq(PendingIntent.FLAG_IMMUTABLE),
                                        isNull(),
                                        any(UserHandle.class)));
    }

    @Test
    @DisableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void isAvailable_whenFeatureFlagDisabled_doesNothing() throws RemoteException {
        setupAndEnableFeature(false, false);

        verifyNoInteractions(mUsbManagerInternal);
        verifyNoInteractions(mUsbManager);
        verifyNoInteractions(mNotificationManager);
    }

    @Test
    @DisableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void isAvailable_whenFeatureFlagDisabled_returnsFalse() throws RemoteException {
        setupAndEnableFeature(false, false);

        assertFalse(mUsbDataHook.isAvailable());
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void isAvailable_whenFeatureFlagEnabledAndDeviceSupportsFeature_returnsTrue()
            throws RemoteException {
        setupAndEnableFeature(false, false);

        assertTrue(mUsbDataHook.isAvailable());
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void onAdvancedProtectionChanged_whenEnabled_registersReceiverAndDisablesUsb()
            throws RemoteException {
        clearAllUsbConnections();
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        setupAndEnableFeature(false, false);

        // Verify receiver is registered for the correct user and with the correct intent filters
        verify(mContext, times(1))
                .registerReceiverAsUser(
                        any(BroadcastReceiver.class),
                        mUserHandleCaptor.capture(),
                        mIntentFilterCaptor.capture(),
                        isNull(),
                        isNull());

        // Silence receiver
        verify(mContext, times(1))
                .registerReceiverAsUser(
                        any(BroadcastReceiver.class),
                        mUserHandleCaptor.capture(),
                        mIntentFilterCaptor.capture(),
                        isNull(),
                        isNull(),
                        anyInt());

        IntentFilter mainFilter = mIntentFilterCaptor.getAllValues().get(0);
        assertEquals(UserHandle.ALL, mUserHandleCaptor.getAllValues().get(0));

        assertEquals(3, mainFilter.countActions());
        assertTrue(mainFilter.hasAction(Intent.ACTION_USER_PRESENT));
        assertTrue(mainFilter.hasAction(Intent.ACTION_SCREEN_OFF));
        assertTrue(mainFilter.hasAction(UsbManager.ACTION_USB_PORT_CHANGED));

        IntentFilter silenceFilter = mIntentFilterCaptor.getAllValues().get(1);
        // Verify it's registered for all users.
        assertEquals(UserHandle.ALL, mUserHandleCaptor.getAllValues().get(1));
        // Verify it listens for the specific silence action.
        assertEquals(1, silenceFilter.countActions());
        assertTrue(silenceFilter.hasAction(ACTION_SILENCE_NOTIFICATION));

        verify(mUsbManagerInternal).enableUsbDataSignal(eq(false), eq(USB_DISABLE_REASON_APM));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void onAdvancedProtectionChanged_whenDisabled_unregistersReceiverAndEnablesUsb()
            throws RemoteException {
        setupAndEnableFeature(false, false);

        mUsbDataHook.onAdvancedProtectionChanged(false);

        verify(mContext).unregisterReceiver(any(BroadcastReceiver.class));
        verify(mUsbManagerInternal).enableUsbDataSignal(eq(true), eq(USB_DISABLE_REASON_APM));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void userPresentAndUnlocked_enablesUsbAndClearsTasks()
            throws RemoteException {
        setupAndEnableFeature(false, false);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(false);
        BroadcastReceiver receiver = getAndCaptureReceiver();

        receiver.onReceive(mContext, new Intent(Intent.ACTION_USER_PRESENT));

        verify(mDelayDisableHandler).removeCallbacksAndMessages(isNull());
        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mUsbManagerInternal).enableUsbDataSignal(eq(true), eq(USB_DISABLE_REASON_APM));
    }

    private void clearAllUsbConnections() {
        UsbPort mockUsbPort = new UsbPort(mUsbManager, "temp", 0, 0, true, true, true, 0);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        MODE_NONE, 0, DATA_ROLE_NONE, 0, 0, 0, DATA_STATUS_ENABLED, false, 0);
        when(mUsbManager.getPorts()).thenReturn(List.of(mockUsbPort));
        when(mockUsbPort.getStatus()).thenReturn(mockUsbPortStatus);
    }

    private void addUsbConnection(int powerRole, int powerBrickStatus) {
        UsbPort mockUsbPort = new UsbPort(mUsbManager, "temp", 0, 0, true, true, true, 0);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        MODE_DFP, // currently connected
                        powerRole,
                        DATA_ROLE_HOST,
                        0,
                        0,
                        0,
                        DATA_STATUS_ENABLED,
                        false,
                        powerBrickStatus);
        when(mockUsbPort.getStatus()).thenReturn(mockUsbPortStatus);
        when(mUsbManager.getPorts()).thenReturn(List.of(mockUsbPort));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void screenOffAndLocked_withNoConnectedDevice_disablesUsb()
            throws RemoteException {
        setupAndEnableFeature(false, false);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        clearAllUsbConnections();
        BroadcastReceiver receiver = getAndCaptureReceiver();

        receiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_OFF));

        verify(mUsbManagerInternal, times(1))
                .enableUsbDataSignal(eq(false), eq(USB_DISABLE_REASON_APM));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void screenOffAndLocked_withConnectedDevice_doesNothing()
            throws RemoteException {
        setupAndEnableFeature(false, false);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        addUsbConnection(
                UsbPortStatus.POWER_ROLE_SINK, UsbPortStatus.POWER_BRICK_STATUS_DISCONNECTED);

        receiver.onReceive(mContext, new Intent(Intent.ACTION_SCREEN_OFF));

        verify(mUsbManagerInternal, never()).enableUsbDataSignal(anyBoolean(), anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_disconnected_clearsNotifications()
            throws RemoteException {
        setupAndEnableFeature(false, false);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        UsbPortStatus mockUsbPortStatus = new UsbPortStatus(0, 0, 0, 0, 0, 0, 0, false, 0);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mNotificationManager).cancel(TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER);
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_lockedAndDisconnected_delaysDisableUsb()
            throws RemoteException {
        setupAndEnableFeature(false, false);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(0, 0, 0, 0, 0, 0, DATA_STATUS_DISABLED_FORCE, false, 0);
        clearAllUsbConnections();
        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);
        verify(mDelayDisableHandler).postDelayed(mRunnableCaptor.capture(), anyLong());
        mRunnableCaptor.getValue().run();

        verify(mUsbManagerInternal).enableUsbDataSignal(eq(false), eq(USB_DISABLE_REASON_APM));
        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mNotificationManager).cancel(TAG, SystemMessage.NOTE_USB_DATA_PROTECTION_REMINDER);
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void
            usbPortChanged_lockedAndPowerBrickConnectedAndPdCompliant_dataRequiredForHighPowerCharge_sendsChargeNotification()
                    throws RemoteException {
        setupAndEnableFeature(false, true); // Data required for high power charge
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        MODE_DFP,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        UsbPortStatus.POWER_BRICK_STATUS_CONNECTED);

        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler, never()).postDelayed(any(), anyInt());
        verify(mNotificationManager)
                .notifyAsUser(
                        mNotificationTagCaptor.capture(),
                        mNotificationIdCaptor.capture(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));
        assertEquals(TAG, mNotificationTagCaptor.getValue());

        verify(mAdvancedProtectionService)
                .logDialogShown(
                        eq(AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_USB),
                        eq(AdvancedProtectionProtoEnums.DIALOGUE_TYPE_BLOCKED_INTERACTION),
                        eq(false));
        checkNotificationIntents(mNotificationCaptor.getValue());
    }

    private void checkNotificationIntents(Notification notification) {
        assertNotNull(notification);
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_lockedAndPdCompliant_sendsDataNotification()
            throws RemoteException {
        setupAndEnableFeature(false, false); // Data NOT required for high power charge
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_DISCONNECTED);

        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);
        verify(mDelayedNotificationHandler).postDelayed(mRunnableCaptor.capture(), anyLong());
        mRunnableCaptor.getValue().run();

        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));

        assertNotNull(mNotificationCaptor.getValue());
        checkNotificationIntents(mNotificationCaptor.getValue());
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void
            usbPortChanged_notPowerBrickConnectedOrPdCompliant_sendsChargeDataNotification()
                    throws RemoteException {
        setupAndEnableFeature(false, false);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        0,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_DISCONNECTED);

        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler).postDelayed(mRunnableCaptor.capture(), anyLong());
        mRunnableCaptor.getValue().run();
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(), anyInt(), any(Notification.class), any(UserHandle.class));
        verify(mAdvancedProtectionService)
                .logDialogShown(
                        eq(AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_USB),
                        eq(AdvancedProtectionProtoEnums.DIALOGUE_TYPE_BLOCKED_INTERACTION),
                        eq(false));
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));

        checkNotificationIntents(mNotificationCaptor.getValue());
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void usbPortChanged_pendingChecks_postsDelayedNotification()
            throws RemoteException {
        setupAndEnableFeature(false, false);
        doReturn(TEST_TIMEOUT_MS)
                .when(
                        () ->
                                SystemProperties.getLong(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.power_brick_connection_check_timeout"),
                                        anyLong()));
        doReturn(TEST_TIMEOUT_MS)
                .when(
                        () ->
                                SystemProperties.getLong(
                                        eq(
                                                "ro.usb.data_protection.disable_when_locked.pd_compliance_check_timeout"),
                                        anyLong()));

        mUsbDataHook.onAdvancedProtectionChanged(true);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        0, // status is pending
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_UNKNOWN); // status is pending
        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);
        when(mContext.getUser()).thenReturn(UserHandle.ALL); // ActivityManager.getCurrentUser();

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler)
                .postDelayed(mRunnableCaptor.capture(), eq(TEST_TIMEOUT_MS + TEST_TIMEOUT_MS));
        mRunnableCaptor.getValue().run();
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(), anyInt(), any(Notification.class), any(UserHandle.class));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void setUsbDataSignal_retriesOnFailure() throws Exception {
        setupAndEnableFeature(false, false);
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        when(mUsbManagerInternal.enableUsbDataSignal(anyBoolean(), anyInt()))
                .thenReturn(false) // Fail first
                .thenReturn(false) // Fail second
                .thenReturn(true); // Succeed third

        mUsbDataHook.onAdvancedProtectionChanged(false);

        verify(mUsbManagerInternal, times(3)).enableUsbDataSignal(eq(true), eq(1));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void notification_replugRequired_showsCorrectText() throws RemoteException {
        setupAndEnableFeature(true, true); // Replug required
        when(mContext.getString(
                        R.string.usb_apm_usb_plugged_in_when_locked_replug_notification_title))
                .thenReturn("Replug Title");
        when(mContext.getString(
                        R.string.usb_apm_usb_plugged_in_for_power_brick_replug_notification_text))
                .thenReturn("Replug Text");

        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_CONNECTED);
        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mDelayedNotificationHandler).removeCallbacksAndMessages(isNull());
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));
        Notification notification = mNotificationCaptor.getValue();
        assertEquals("Replug Title", notification.extras.getString(Notification.EXTRA_TITLE));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void notificationSilenceReceiver_silencesNotifications() throws RemoteException {
        setupAndEnableFeature(false, false);

        // Get the second registered receiver, which is the NotificationSilenceReceiver
        BroadcastReceiver silenceReceiver =
                getAndCaptureSilenceReceiver(); // mBroadcastReceiverCaptor.getAllValues().get(1);

        Intent silenceIntent = new Intent(ACTION_SILENCE_NOTIFICATION);
        silenceIntent.putExtra("silence_power_notification", true);

        silenceReceiver.onReceive(mContext, silenceIntent);

        // Verify silenced notification is shown
        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));
        // Check that a subsequent power notification would be suppressed
        // Simulate a power brick connection event again
        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_CONNECTED);

        BroadcastReceiver mainReceiver = getAndCaptureReceiver();
        Intent powerIntent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        powerIntent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);
        mainReceiver.onReceive(mContext, powerIntent);

        // We expect only one notification (the "silenced" one). If another was sent, this would
        // fail.
        verify(mNotificationManager, times(1))
                .notifyAsUser(
                        anyString(), anyInt(), any(Notification.class), any(UserHandle.class));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION)
    public void helpIntent_isCreatedAndAddedToNotification() throws RemoteException {
        setupAndEnableFeature(false, true);

        when(mKeyguardManager.isKeyguardLocked()).thenReturn(true);
        UsbPortStatus mockUsbPortStatus =
                new UsbPortStatus(
                        1,
                        POWER_ROLE_SINK,
                        DATA_ROLE_HOST,
                        PD_COMPLIANT_ROLE_COMBINATIONS,
                        0,
                        0,
                        DATA_STATUS_DISABLED_FORCE,
                        false,
                        POWER_BRICK_STATUS_CONNECTED);

        BroadcastReceiver receiver = getAndCaptureReceiver();
        Intent intent = new Intent(UsbManager.ACTION_USB_PORT_CHANGED);
        intent.putExtra(UsbManager.EXTRA_PORT_STATUS, mockUsbPortStatus);

        receiver.onReceive(mContext, intent);

        verify(mNotificationManager)
                .notifyAsUser(
                        anyString(),
                        anyInt(),
                        mNotificationCaptor.capture(),
                        any(UserHandle.class));
        Notification notification = mNotificationCaptor.getValue();
        // Verify help intent is attached
        assertNotNull(notification.contentIntent);
    }

    /** Helper to capture the main broadcast receiver. */
    public BroadcastReceiver getAndCaptureReceiver() {
        verify(mContext, atLeastOnce())
                .registerReceiverAsUser(
                        mBroadcastReceiverCaptor.capture(),
                        any(UserHandle.class),
                        any(IntentFilter.class),
                        isNull(),
                        isNull());
        return mBroadcastReceiverCaptor.getAllValues().get(0);
    }

    /** Helper to capture the silence broadcast receiver. */
    public BroadcastReceiver getAndCaptureSilenceReceiver() {
        verify(mContext, atLeastOnce())
                .registerReceiverAsUser(
                        mBroadcastReceiverCaptor.capture(),
                        any(UserHandle.class),
                        any(IntentFilter.class),
                        isNull(),
                        isNull(),
                        anyInt());
        return mBroadcastReceiverCaptor.getAllValues().get(0);
    }
}
