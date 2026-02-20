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

package com.android.server.security.authenticationpolicy.agent;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.KeyguardManager;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.DeviceId;
import android.companion.DevicePresenceEvent;
import android.companion.ICompanionDeviceManager;
import android.companion.IOnAssociationsChangedListener;
import android.companion.IOnDevicePresenceEventListener;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.os.Handler;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.locksettings.LockSettingsInternal;
import com.android.server.locksettings.LockSettingsStateListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AgentAuthServiceTest {

    private static final int USER_ID = 10;
    private static final long AUTH_INTERVAL = TimeUnit.MINUTES.toMillis(30);

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private Context mMockContext;
    @Mock
    private ICompanionDeviceManager mMockCDMService;
    @Mock
    private BiometricManager mBiometricManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private LockSettingsInternal mLockSettings;
    private CompanionDeviceManager mCompanionDeviceManager;
    private Handler mHandler;
    private AgentAuthService mService;
    private FakeClock mClock;
    private MockContentResolver mContentResolver;

    private IOnAssociationsChangedListener mAssocListener;
    private IOnDevicePresenceEventListener mPresenceListener;

    @Before
    public void setUp() throws Exception {
        mContentResolver = new MockContentResolver();
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());

        when(mMockContext.getUserId()).thenReturn(USER_ID);
        when(mMockContext.getContentResolver()).thenReturn(mContentResolver);
        when(mMockCDMService.getAllAssociationsForUser(anyInt())).thenReturn(
                Collections.emptyList());

        // Capture listeners only for our USER_ID
        doAnswer(inv -> {
            int uid = inv.getArgument(1);
            if (uid == USER_ID) {
                mAssocListener = inv.getArgument(0);
            }
            return null;
        }).when(mMockCDMService).addOnAssociationsChangedListener(any(), anyInt());

        doAnswer(inv -> {
            int uid = inv.getArgument(3);
            if (uid == USER_ID) {
                mPresenceListener = inv.getArgument(2);
            }
            return null;
        }).when(mMockCDMService).setOnDevicePresenceEventListener(any(), any(), any(), anyInt());

        when(mKeyguardManager.isDeviceLocked(anyInt())).thenReturn(true);

        mCompanionDeviceManager = new CompanionDeviceManager(mMockCDMService, mMockContext);
        mHandler = new Handler(TestableLooper.get(this).getLooper());

        mClock = new FakeClock();

        mService = new AgentAuthService(mMockContext, mHandler, mClock, AUTH_INTERVAL);

        // Initialize for USER_ID, flushing each step to avoid task removal
        mService.start(mLockSettings, mBiometricManager, mKeyguardManager, mCompanionDeviceManager);
        TestableLooper.get(this).processAllMessages();

        mService.initInBackgroundForUser(USER_ID);
        TestableLooper.get(this).processAllMessages();
    }

    @After
    public void tearDown() {
        FakeSettingsProvider.clearSettingsProvider();
    }

    @Test
    public void testIsAgentAuthorized_noSession_returnsFalse() {
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, createDeviceId("1"))).isFalse();
    }

    @Test
    public void testIsAgentAuthorized_unlockedConnection_returnsTrue() throws RemoteException {
        // Simulate device is UNLOCKED
        when(mKeyguardManager.isDeviceLocked(USER_ID)).thenReturn(false);

        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isTrue();
    }

    @Test
    public void testIsAgentAuthorized_authorizedSession_returnsTrue() throws RemoteException {
        // Simulate connection with recent auth
        mClock.setNow(TimeUnit.HOURS.toMillis(1));
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(TimeUnit.HOURS.toMillis(1) - 1000);

        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isTrue();
    }

    @Test
    public void testIsAgentAuthorized_staleAuth_returnsFalse() throws RemoteException {
        // Simulate connection with stale auth
        mClock.setNow(TimeUnit.HOURS.toMillis(1));
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(TimeUnit.HOURS.toMillis(1) - AUTH_INTERVAL - 1000);

        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isFalse();
    }

    @Test
    public void testIsAgentAuthorized_wrongUser_returnsFalse() throws RemoteException {
        mClock.setNow(TimeUnit.HOURS.toMillis(1));
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(TimeUnit.HOURS.toMillis(1) - 1000);

        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);

        assertThat(mService.isAgentAuthorized(USER_ID + 1, Context.DEVICE_ID_DEFAULT, deviceId)).isFalse();
    }

    @Test
    public void testUserSwitch_clearsSessions() throws RemoteException {
        mClock.setNow(TimeUnit.HOURS.toMillis(1));
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(TimeUnit.HOURS.toMillis(1) - 1000);

        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isTrue();

        // Switch user
        mService.initInBackgroundForUser(USER_ID + 1);
        TestableLooper.get(this).processAllMessages();

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isFalse();
    }

    @Test
    public void testStrongAuth_promotesSession() throws RemoteException {
        // Capture the listener registered in start()
        ArgumentCaptor<LockSettingsStateListener> captor =
                ArgumentCaptor.forClass(LockSettingsStateListener.class);
        verify(mLockSettings).registerLockSettingsStateListener(captor.capture());
        LockSettingsStateListener listener = captor.getValue();

        // Simulate connection with stale auth (unauthorized)
        mClock.setNow(TimeUnit.HOURS.toMillis(1));
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(TimeUnit.HOURS.toMillis(1) - AUTH_INTERVAL - 1000);

        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isFalse();

        // Now simulate strong auth
        when(mBiometricManager.getLastAuthenticationTime(eq(USER_ID), anyInt()))
                .thenReturn(TimeUnit.HOURS.toMillis(1));

        listener.onAuthenticationSucceeded(USER_ID);
        TestableLooper.get(this).processAllMessages();

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isTrue();
    }

    @Test
    public void testSetOverride_updatesSession() throws RemoteException {
        DeviceId deviceId = createDeviceId("123");
        triggerAgentConnected(123, deviceId);
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isFalse();

        // Override to true
        boolean result = mService.setOverride(USER_ID, 123, true);
        assertThat(result).isTrue();
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isTrue();

        // Override to false
        result = mService.setOverride(USER_ID, 123, false);
        assertThat(result).isFalse();
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, deviceId)).isFalse();
    }

    @Test
    public void testSetOverride_nonExistentSession_returnsFalse() {
        boolean result = mService.setOverride(USER_ID, 999, true);
        assertThat(result).isFalse();
    }

    @Test
    public void testIsAgentAuthorized_localAgent_unlocked_returnsTrue() {
        // Simulate device is UNLOCKED
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(false);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, null)).isTrue();
    }

    @Test
    public void testIsAgentAuthorized_localAgent_locked_returnsFalse() {
        // Simulate device is LOCKED
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);

        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, null)).isFalse();
    }

    @Test
    public void testIsAgentAuthorized_localAgent_perDevice_returnsStatusForThatDevice() {
        int deviceId = 5;
        // Simulate device 5 is UNLOCKED
        when(mKeyguardManager.isDeviceLocked(USER_ID, deviceId)).thenReturn(false);
        // Simulate default device (Context.DEVICE_ID_DEFAULT) is LOCKED
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(true);

        assertThat(mService.isAgentAuthorized(USER_ID, deviceId, null)).isTrue();
        assertThat(mService.isAgentAuthorized(USER_ID, Context.DEVICE_ID_DEFAULT, null)).isFalse();
    }

    @Test
    public void testIsAgentAuthorized_localAgent_wrongUser_returnsFalse() {
        // Simulate device is UNLOCKED for current user
        when(mKeyguardManager.isDeviceLocked(USER_ID, Context.DEVICE_ID_DEFAULT)).thenReturn(false);

        when(mKeyguardManager.isDeviceLocked(USER_ID + 1, Context.DEVICE_ID_DEFAULT)).thenReturn(true);
        assertThat(mService.isAgentAuthorized(USER_ID + 1, Context.DEVICE_ID_DEFAULT, null)).isFalse();

        when(mKeyguardManager.isDeviceLocked(USER_ID + 1, Context.DEVICE_ID_DEFAULT)).thenReturn(false);
        assertThat(mService.isAgentAuthorized(USER_ID + 1, Context.DEVICE_ID_DEFAULT, null)).isTrue();
    }

    private void triggerAgentConnected(int associationId, DeviceId deviceId) throws RemoteException {
        assertThat(mAssocListener).isNotNull();

        AssociationInfo association = new AssociationInfo.Builder(associationId, USER_ID, "pkg")
                .setDisplayName("Device")
                .setRemoteAiAgentSupported(true)
                .setDeviceId(deviceId)
                .build();

        when(mMockCDMService.getAssociationByDeviceId(USER_ID, deviceId)).thenReturn(association);

        mAssocListener.onAssociationsChanged(List.of(association));
        TestableLooper.get(this).processAllMessages();

        assertThat(mPresenceListener).isNotNull();

        DevicePresenceEvent event = new DevicePresenceEvent.Builder()
                .setAssociationId(associationId)
                .setEvent(DevicePresenceEvent.EVENT_BT_CONNECTED)
                .build();

        mPresenceListener.onDevicePresence(event);
        TestableLooper.get(this).processAllMessages();
    }

    private DeviceId createDeviceId(String customId) {
        return new DeviceId.Builder().setCustomId(customId).build();
    }

    private static class FakeClock extends Clock {
        private long mNowMillis;

        void setNow(long millis) {
            mNowMillis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(mNowMillis);
        }

        @Override
        public long millis() {
            return mNowMillis;
        }
    }
}
