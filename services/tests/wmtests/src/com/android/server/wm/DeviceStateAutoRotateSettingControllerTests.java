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
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_UNLOCKED;
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.getUriFor;

import static com.android.internal.view.RotationPolicy.NATURAL_ROTATION;
import static com.android.server.wm.DisplayRotation.NO_UPDATE_USER_ROTATION;
import static com.android.server.wm.DisplayRotation.USE_CURRENT_ROTATION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.devicestate.DeviceState;
import android.os.Handler;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.util.SparseIntArray;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.internal.util.test.FakeSettingsProviderRule;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.wm.utils.DeviceStateTestUtils;
import com.android.settingslib.devicestate.AndroidSecureSettings;
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager.DeviceStateAutoRotateSettingListener;
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManagerImpl;
import com.android.settingslib.devicestate.PostureDeviceStateConverter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/**
 * Test class for {@link DeviceStateAutoRotateSettingController}.
 *
 * <p>Build/Install/Run: atest WmTests:DeviceStateAutoRotateSettingControllerTests
 */
@SmallTest
@Presubmit
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
    private DeviceStateController mMockDeviceStateController;
    @Mock
    private ContentResolver mMockResolver;
    @Mock
    private Resources mMockResources;
    @Mock
    private WindowManagerService mMockWm;
    @Mock
    private DisplayRotation mMockDisplayRotation;

    @Captor
    private ArgumentCaptor<DeviceStateAutoRotateSettingListener> mSettingListenerArgumentCaptor;
    @Captor
    private ArgumentCaptor<DeviceStateController.DeviceStateListener> mDeviceStateListenerCaptor;
    @Captor
    private ArgumentCaptor<ContentObserver> mAccelerometerRotationSettingObserver;

    private final TestLooper mTestLooper = new TestLooper();
    private DeviceStateAutoRotateSettingController mDeviceStateAutoRotateSettingController;
    private DeviceStateAutoRotateSettingManagerImpl mSpyAutoRotateSettingManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final Handler handler = new Handler(mTestLooper.getLooper());
        final Context mockContext = mock(Context.class);
        final FakeSettingsProvider fakeSettingsProvider = new FakeSettingsProvider();
        final RootWindowContainer mockRoot = mock(RootWindowContainer.class);
        final DisplayContent mockDisplayContent = mock(DisplayContent.class);
        final ActivityTaskManagerService atmService = mock(ActivityTaskManagerService.class);

        // Setup context
        when(mockContext.getContentResolver()).thenReturn(mMockResolver);
        when(mockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getStringArray(
                R.array.config_perDeviceStateRotationLockDefaults)).thenReturn(new String[]{});
        when(mMockResolver.acquireProvider(Settings.AUTHORITY)).thenReturn(
                fakeSettingsProvider.getIContentProvider());

        // Setup WindowManagerServices
        WindowTestsBase.setFieldValue(mMockWm, "mContext", mockContext);
        mockRoot.mService = atmService;
        WindowTestsBase.setFieldValue(atmService, "mGlobalLock", new WindowManagerGlobalLock());
        WindowTestsBase.setFieldValue(mMockWm, "mRoot", mockRoot);
        when(mockRoot.getDefaultDisplay()).thenReturn(mockDisplayContent);
        when(mockDisplayContent.getDisplayRotation()).thenReturn(mMockDisplayRotation);

        mSpyAutoRotateSettingManager = spy(
                new DeviceStateAutoRotateSettingManagerImpl(mockContext, mock(Executor.class),
                        new AndroidSecureSettings(mMockResolver), handler,
                        mock(PostureDeviceStateConverter.class)));

        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_OFF);
        setDeviceStateAutoRotateSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING);

        mDeviceStateAutoRotateSettingController = new DeviceStateAutoRotateSettingController(
                mMockDeviceStateController,
                mSpyAutoRotateSettingManager,
                mMockWm) {
            @Override
            Handler getHandler() {
                return handler;
            }
        };
        mTestLooper.dispatchAll();

        verify(mSpyAutoRotateSettingManager).registerListener(
                mSettingListenerArgumentCaptor.capture());
        verify(mMockResolver).registerContentObserver(
                eq(getUriFor(ACCELEROMETER_ROTATION)), anyBoolean(),
                mAccelerometerRotationSettingObserver.capture(), eq(UserHandle.USER_CURRENT));
        verify(mMockDeviceStateController).registerDeviceStateCallback(
                mDeviceStateListenerCaptor.capture(), any());
    }

    @Test
    public void requestDSAutoRotateSettingChange_updatesDeviceStateAutoRotateSetting() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);

    }

    @Test
    public void requestAccelerometerRotationChange_updatesAccelerometerRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(true,
                USE_CURRENT_ROTATION);
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
    }

    @Test
    public void requestDSAutoRotateSettingChange_curDeviceState_updatesAccelerometerRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
    }

    @Test
    public void requestAccelerometerRotationChange_updatesDSAutoRotateSetting() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(true,
                USE_CURRENT_ROTATION);
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void accelerometerRotationSettingChanged_updatesDSAutoRotateSetting() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);

        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void dSAutoRotateSettingChanged_updatesAccelerometerRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setDeviceStateAutoRotateSetting(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);

        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
    }

    @Test
    public void onDeviceStateChange_updatesAccelerometerRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setDeviceStateAutoRotateSetting(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        setDeviceState(DeviceStateTestUtils.OPEN);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
    }

    @Test
    public void requestDSAutoRotateSettingChange_nonCurDeviceState_noUpdateAccelerometerRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.OPEN.getIdentifier(), true);
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_OFF);
    }

    @Test
    public void dSAutoRotateCorrupted_writesDefaultSettingWhileRespectingAccelerometerRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setDeviceStateAutoRotateSetting("invalid");
        when(mSpyAutoRotateSettingManager.getDefaultRotationLockSetting()).thenReturn(
                createDeviceStateAutoRotateSettingMap(DEVICE_STATE_ROTATION_LOCK_UNLOCKED,
                        DEVICE_STATE_ROTATION_LOCK_UNLOCKED));

        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING);
    }

    @Test
    public void multipleSettingChanges_accelerometerRotationSettingTakesPrecedenceWhenConflict() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setDeviceStateAutoRotateSetting(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        // Change accelerometer rotation setting to locked
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_OFF);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_LOCKED_OPEN_LOCKED_SETTING);

        // Change device state auto rotate setting to unlocked for both states
        setDeviceStateAutoRotateSetting(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING);
    }

    @Test
    public void multipleDeviceStateChanges_updatesAccelerometerRotationForRespectiveDeviceState() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setDeviceStateAutoRotateSetting(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        setDeviceState(DeviceStateTestUtils.OPEN);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_OFF);

        setDeviceState(DeviceStateTestUtils.FOLDED);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void requestAccelerometerRotationChange_dSUnavailable_noSettingUpdate() {
        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(true,
                USE_CURRENT_ROTATION);
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_OFF);
        verifyDeviceStateAutoRotateSettingSet(FOLDED_LOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void requestDSAutoRotateSettingChange_dSUnavailable_noSettingUpdate() {
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mTestLooper.dispatchAll();

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_OFF);
        verifyDeviceStateAutoRotateSettingSet(FOLDED_LOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void requestAccelerometerRotationChange_dSUnavailable_writeAfterReceivingDSUpdate() {
        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(true,
                USE_CURRENT_ROTATION);
        mTestLooper.dispatchAll();

        setDeviceState(DeviceStateTestUtils.FOLDED);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void requestDSAutoRotateSettingChange_dSUnavailable_writeAfterReceivingDSUpdate() {
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mTestLooper.dispatchAll();

        setDeviceState(DeviceStateTestUtils.FOLDED);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_LOCKED_SETTING);
    }

    @Test
    public void dSUnavailable_sendMultipleRequests_accelerometerPrecedesAfterReceivingDSUpdate() {
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(false,
                USE_CURRENT_ROTATION);
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.OPEN.getIdentifier(), true);
        mTestLooper.dispatchAll();

        setDeviceState(DeviceStateTestUtils.FOLDED);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_OFF);
        verifyDeviceStateAutoRotateSettingSet(FOLDED_LOCKED_OPEN_UNLOCKED_SETTING);
    }

    @Test
    public void dSUnavailable_sendMultipleRequests_dSAutoRotatePrecedesAfterReceivingDSUpdate() {
        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(false,
                USE_CURRENT_ROTATION);
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.OPEN.getIdentifier(), true);
        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mTestLooper.dispatchAll();

        setDeviceState(DeviceStateTestUtils.FOLDED);

        verifyAccelerometerRotationSettingSet(ACCELEROMETER_ROTATION_ON);
        verifyDeviceStateAutoRotateSettingSet(FOLDED_UNLOCKED_OPEN_UNLOCKED_SETTING);
    }

    @Test
    public void requestAccelerometerRotationChange_turnOnAccelerometer_setUserRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);

        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(true,
                Surface.ROTATION_90);
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_FREE), eq(Surface.ROTATION_90), any());
    }

    @Test
    public void requestAccelerometerRotationChange_turnOffAccelerometer_setUserRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(
                false, Surface.ROTATION_180);
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_LOCKED), eq(Surface.ROTATION_180), any());
    }

    @Test
    public void requestAccelerometerRotationChange_withCurrentRotation_setUserRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        mTestLooper.dispatchAll();

        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(true,
                USE_CURRENT_ROTATION);
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_FREE), eq(USE_CURRENT_ROTATION), any());
    }

    @Test
    public void requestAccelerometerRotationChange_withNaturalRotation_setUserRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(
                false, NATURAL_ROTATION);
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_LOCKED), eq(NATURAL_ROTATION), any());
    }

    @Test
    public void requestDSAutoRotateSettingChange_turnsOnAccelerometer_setUserRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);

        mDeviceStateAutoRotateSettingController.requestDeviceStateAutoRotateSettingChange(
                DeviceStateTestUtils.FOLDED.getIdentifier(), true);
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_FREE), eq(NO_UPDATE_USER_ROTATION), any());
    }

    @Test
    public void requestAccelerometerRotationChange_noChangeInAccelerometer_setUserRotation() {
        setDeviceState(DeviceStateTestUtils.FOLDED);

        mDeviceStateAutoRotateSettingController.requestAccelerometerRotationSettingChange(
                false, Surface.ROTATION_90);
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_LOCKED), eq(Surface.ROTATION_90), any());
    }

    @Test
    public void foldableConfigTrue_locksRotation_usesCurrentRotation() {
        when(mMockResources.getBoolean(com.android.internal.R.bool.config_allowAllRotations))
                .thenReturn(true);
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_useCurrentRotationOnRotationLockChange))
                .thenReturn(true);
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        setDeviceStateAutoRotateSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_LOCKED), eq(USE_CURRENT_ROTATION),
                any());
    }

    @Test
    public void allRotationsAllowedConfigFalse_locksRotation_usesCurrentRotation() {
        when(mMockResources.getBoolean(com.android.internal.R.bool.config_allowAllRotations))
                .thenReturn(false);
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_useCurrentRotationOnRotationLockChange))
                .thenReturn(true);
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        setDeviceStateAutoRotateSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_LOCKED), eq(USE_CURRENT_ROTATION),
                any());
    }

    @Test
    public void useCurrentRotationConfigFalse_locksRotation_usesCurrentRotation() {
        when(mMockResources.getBoolean(com.android.internal.R.bool.config_allowAllRotations))
                .thenReturn(true);
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_useCurrentRotationOnRotationLockChange))
                .thenReturn(false);
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        setDeviceStateAutoRotateSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_LOCKED), eq(USE_CURRENT_ROTATION), any());
    }

    @Test
    public void foldableConfigFalse_locksRotation_usesNaturalRotation() {
        when(mMockResources.getBoolean(com.android.internal.R.bool.config_allowAllRotations))
                .thenReturn(false);
        when(mMockResources.getBoolean(
                com.android.internal.R.bool.config_useCurrentRotationOnRotationLockChange))
                .thenReturn(false);
        setDeviceState(DeviceStateTestUtils.FOLDED);
        setAccelerometerRotationSetting(ACCELEROMETER_ROTATION_ON);
        mAccelerometerRotationSettingObserver.getValue().onChange(false);
        mTestLooper.dispatchAll();

        setDeviceStateAutoRotateSetting(FOLDED_LOCKED_OPEN_LOCKED_SETTING);
        mSettingListenerArgumentCaptor.getValue().onSettingsChanged();
        mTestLooper.dispatchAll();

        verify(mMockDisplayRotation).setUserRotationSetting(
                eq(WindowManagerPolicy.USER_ROTATION_LOCKED), eq(NATURAL_ROTATION), any());
    }

    private void setDeviceStateAutoRotateSetting(String deviceStateAutoRotateSetting) {
        Settings.Secure.putStringForUser(mMockResolver, DEVICE_STATE_ROTATION_LOCK,
                deviceStateAutoRotateSetting, UserHandle.USER_CURRENT);
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
