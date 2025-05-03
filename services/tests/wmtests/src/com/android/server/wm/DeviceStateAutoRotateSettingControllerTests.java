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

package com.android.server.wm;

import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.getUriFor;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.devicestate.DeviceState;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.util.SparseIntArray;

import androidx.test.filters.SmallTest;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.internal.util.test.FakeSettingsProviderRule;
import com.android.server.wm.utils.DeviceStateTestUtils;
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager.DeviceStateAutoRotateSettingListener;
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManagerImpl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test class for {@link DeviceStateAutoRotateSettingController}.
 *
 * <p>Build/Install/Run: atest WmTests:DeviceStateAutoRotateSettingControllerTests
 */
@SmallTest
@Presubmit
// TODO(b/413639166): Add testcase to check if class under test behaves expectedly when
//  deviceState = -1
public class DeviceStateAutoRotateSettingControllerTests {
    private static final int ACCELEROMETER_ROTATION_OFF = 0;
    private static final int ACCELEROMETER_ROTATION_ON = 1;
    private static final String FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING = "1:2:3:2";
    private static final String FOLDED_LOCKED_OPEN_UNLOCKED_SETTING = "1:1:3:2";
    private static final String FOLDED_LOCKED_OPEN_LOCKED_SETTING = "1:1:3:1";
    private static final String FOLDED_UNLOCKED_OPEN_LOCKED_SETTING = "1:2:3:1";

    @Rule
    public final FakeSettingsProviderRule rule = FakeSettingsProvider.rule();

    @Mock
    private DeviceStateAutoRotateSettingManagerImpl mMockAutoRotateSettingManager;
    @Mock
    private DeviceStateController mMockDeviceStateController;
    @Mock
    private ContentResolver mMockResolver;

    @Captor
    private ArgumentCaptor<DeviceStateAutoRotateSettingListener> mSettingListenerArgumentCaptor;
    @Captor
    private ArgumentCaptor<DeviceStateController.DeviceStateListener> mDeviceStateListenerCaptor;
    @Captor
    private ArgumentCaptor<ContentObserver> mAccelerometerRotationSettingObserver;

    private final TestLooper mTestLooper = new TestLooper();
    private DeviceStateAutoRotateSettingController mDeviceStateAutoRotateSettingController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final Context mockContext = mock(Context.class);
        when(mockContext.getContentResolver()).thenReturn(mMockResolver);
        final FakeSettingsProvider fakeSettingsProvider = new FakeSettingsProvider();
        when(mMockResolver.acquireProvider(Settings.AUTHORITY))
                .thenReturn(fakeSettingsProvider.getIContentProvider());
        final Looper looper = mTestLooper.getLooper();
        final Handler handler = new Handler(looper);

        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_OFF);
        when(mMockAutoRotateSettingManager.getRotationLockSetting()).thenAnswer(invocation ->
                createDeviceStateAutoRotateSettingMap(DEVICE_STATE_ROTATION_LOCK_LOCKED,
                        DEVICE_STATE_ROTATION_LOCK_LOCKED));

        mDeviceStateAutoRotateSettingController = new DeviceStateAutoRotateSettingController(
                mockContext, looper, handler, mMockDeviceStateController,
                mMockAutoRotateSettingManager);
        mTestLooper.dispatchAll();

        verify(mMockAutoRotateSettingManager).registerListener(
                mSettingListenerArgumentCaptor.capture());
        verify(mMockResolver).registerContentObserver(
                eq(getUriFor(ACCELEROMETER_ROTATION)), anyBoolean(),
                mAccelerometerRotationSettingObserver.capture(), eq(UserHandle.USER_CURRENT));
        verify(mMockDeviceStateController).registerDeviceStateCallback(
                mDeviceStateListenerCaptor.capture(), any());

        setDeviceState(DeviceStateTestUtils.FOLDED);
    }

    @Test
    public void requestDSAutoRotateSettingChange_updatesDeviceStateAutoRotateSetting() {
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);

    }

    @Test
    public void requestAccelerometerRotationChange_updatesAccelerometerRotation() {
        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(true);
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
    }

    @Test
    public void requestDSAutoRotateSettingChange_curDeviceState_updatesAccelerometerRotation() {
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
    }

    @Test
    public void requestAccelerometerRotationChange_updatesDSAutoRotateSetting() {
        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(true);
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void accelerometerRotationSettingChanged_updatesDSAutoRotateSetting() {
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);

        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void dSAutoRotateSettingChanged_updatesAccelerometerRotation() {
        when(mMockAutoRotateSettingManager.getRotationLockSetting()).thenAnswer(invocation ->
                createDeviceStateAutoRotateSettingMap(DEVICE_STATE_ROTATION_LOCK_UNLOCKED,
                        DEVICE_STATE_ROTATION_LOCK_LOCKED));

        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
    }

    @Test
    public void onDeviceStateChange_updatesAccelerometerRotation() {
        when(mMockAutoRotateSettingManager.getRotationLockSetting()).thenAnswer(invocation ->
                createDeviceStateAutoRotateSettingMap(DEVICE_STATE_ROTATION_LOCK_LOCKED,
                        DEVICE_STATE_ROTATION_LOCK_UNLOCKED));
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        setDeviceState(DeviceStateTestUtils.OPEN);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
    }

    @Test
    public void requestDSAutoRotateSettingChange_nonCurDeviceState_noUpdateAccelerometerRotation() {
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.OPEN.getIdentifier(), true);
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_OFF);
    }

    @Test
    public void dSAutoRotateCorrupted_writesDefaultSettingWhileRespectingAccelerometerRotation() {
        when(mMockAutoRotateSettingManager.getRotationLockSetting()).thenAnswer(invocation -> null);
        when(mMockAutoRotateSettingManager.getDefaultRotationLockSetting()).thenReturn(
                createDeviceStateAutoRotateSettingMap(DEVICE_STATE_ROTATION_LOCK_UNLOCKED,
                        DEVICE_STATE_ROTATION_LOCK_UNLOCKED));

        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING);
    }

    @Test
    public void multipleSettingChanges_accelerometerRotationSettingTakesPrecedenceWhenConflict() {
        when(mMockAutoRotateSettingManager.getRotationLockSetting()).thenAnswer(invocation ->
                createDeviceStateAutoRotateSettingMap(DEVICE_STATE_ROTATION_LOCK_UNLOCKED,
                        DEVICE_STATE_ROTATION_LOCK_LOCKED));
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        // Change accelerometer rotation setting to locked
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_OFF);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_LOCKED_OPEN_LOCKED_SETTING);

        // Change device state auto rotate setting to unlocked for both states
        when(mMockAutoRotateSettingManager.getRotationLockSetting()).thenAnswer(invocation ->
                createDeviceStateAutoRotateSettingMap(DEVICE_STATE_ROTATION_LOCK_LOCKED,
                        DEVICE_STATE_ROTATION_LOCK_UNLOCKED));
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING);
    }

    @Test
    public void multipleDeviceStateChanges_updatesAccelerometerRotationForRespectiveDeviceState() {
        when(mMockAutoRotateSettingManager.getRotationLockSetting()).thenAnswer(invocation ->
                createDeviceStateAutoRotateSettingMap(DEVICE_STATE_ROTATION_LOCK_UNLOCKED,
                        DEVICE_STATE_ROTATION_LOCK_LOCKED));
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        setDeviceState(DeviceStateTestUtils.OPEN);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_OFF);

        setDeviceState(DeviceStateTestUtils.FOLDED);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
    }

    private void setAccelerometerRotationSetting(int accelerometerRotationSetting) {
        Settings.System.putIntForUser(mMockResolver, ACCELEROMETER_ROTATION,
                accelerometerRotationSetting, UserHandle.USER_CURRENT);
    }

    private void setDeviceState(DeviceState deviceState) {
        mDeviceStateListenerCaptor.getValue().onDeviceStateChanged(
                DeviceStateController.DeviceStateEnum.UNKNOWN, deviceState);
        mTestLooper.dispatchAll();
    }

    private SparseIntArray createDeviceStateAutoRotateSettingMap(int foldedAutoRotateValue,
            int unfoldedAutoRotateValue) {
        final SparseIntArray deviceStateAutoRotateSetting = new SparseIntArray();
        deviceStateAutoRotateSetting.put(DeviceStateTestUtils.FOLDED.getIdentifier(),
                foldedAutoRotateValue);
        deviceStateAutoRotateSetting.put(DeviceStateTestUtils.OPEN.getIdentifier(),
                unfoldedAutoRotateValue);
        return deviceStateAutoRotateSetting;
    }

    private void verifyAccelerometerRotationSettingSet(int expectedAccelerometerRotationSetting) {
        int accelerometerRotationSetting = Settings.System.getIntForUser(mMockResolver,
                ACCELEROMETER_ROTATION,  /* def= */ -1, UserHandle.USER_CURRENT);
        assertThat(accelerometerRotationSetting).isEqualTo(expectedAccelerometerRotationSetting);
    }

    private void verifyDeviceStateAutoRotateSettingSet(
            String expectedDeviceStateAutoRotateSetting) {
        final String settingValue = Settings.Secure.getStringForUser(mMockResolver,
                DEVICE_STATE_ROTATION_LOCK, UserHandle.USER_CURRENT);
        assertThat(settingValue).isEqualTo(expectedDeviceStateAutoRotateSetting);
    }
}
