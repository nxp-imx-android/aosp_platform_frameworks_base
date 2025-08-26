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

package com.android.server.companion.virtual.computercontrol;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.DisplayInfo;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionTest {
    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String PERMISSION_CONTROLLER_PACKAGE = "permission.controller.package";

    private static final int VIRTUAL_DISPLAY_ID = 42;
    private static final int DISPLAY_WIDTH = 600;
    private static final int DISPLAY_HEIGHT = 1000;
    private static final int DISPLAY_DPI = 480;
    private static final String TARGET_PACKAGE_1 = "com.android.foo";
    private static final String TARGET_PACKAGE_2 = "com.android.bar";
    private static final String TARGET_PACKAGE_3 = "com.android.foobar";
    private static final List<String> TARGET_PACKAGE_NAMES =
            List.of(TARGET_PACKAGE_1, TARGET_PACKAGE_2);
    private static final List<String> PACKAGES_WITHOUT_LAUNCHER_ACTIVITY = List.of(
            TARGET_PACKAGE_3);
    private static final String UNDECLARED_TARGET_PACKAGE = "com.android.baz";

    @Mock
    private ComputerControlSessionProcessor.VirtualDeviceFactory mVirtualDeviceFactory;
    @Mock
    private ComputerControlSessionImpl.Injector mInjector;
    @Mock
    private ComputerControlSessionImpl.OnClosedListener mOnClosedListener;
    @Mock
    private IVirtualDevice mVirtualDevice;
    @Mock
    private IVirtualInputDevice mVirtualTouchscreen;
    @Captor
    private ArgumentCaptor<VirtualDeviceParams> mVirtualDeviceParamsArgumentCaptor;
    @Captor
    private ArgumentCaptor<ActivityPolicyExemption> mActivityPolicyExemptionArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualDisplayConfig> mVirtualDisplayConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualTouchscreenConfig> mVirtualTouchscreenConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualDpadConfig> mVirtualDpadConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualKeyboardConfig> mVirtualKeyboardConfigArgumentCaptor;

    private AutoCloseable mMockitoSession;
    private final IBinder mAppToken = new Binder();
    private final ComputerControlSessionParams mDefaultParams =
            new ComputerControlSessionParams.Builder()
                    .setName(ComputerControlSessionTest.class.getSimpleName())
                    .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                    .build();
    private ComputerControlSessionImpl mSession;

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = DISPLAY_WIDTH;
        displayInfo.logicalHeight = DISPLAY_HEIGHT;
        displayInfo.logicalDensityDpi = DISPLAY_DPI;
        when(mInjector.getDisplayInfo(anyInt())).thenReturn(displayInfo);

        when(mInjector.getPermissionControllerPackageName())
                .thenReturn(PERMISSION_CONTROLLER_PACKAGE);
        when(mInjector.getAllApplicationsWithoutLauncherActivity())
                .thenReturn(PACKAGES_WITHOUT_LAUNCHER_ACTIVITY);
        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any(), any()))
                .thenReturn(mVirtualDevice);
        when(mVirtualDevice.createVirtualDisplay(any(), any())).thenReturn(VIRTUAL_DISPLAY_ID);
        when(mVirtualDevice.createVirtualTouchscreen(any(), any())).thenReturn(mVirtualTouchscreen);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void createSessionWithoutDisplaySurface_appliesCorrectParams() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDeviceFactory).createVirtualDevice(
                eq(mAppToken), any(), mVirtualDeviceParamsArgumentCaptor.capture(), any());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue().getName())
                .isEqualTo(mDefaultParams.getName());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue()
                .getDevicePolicy(POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);

        verify(mVirtualDevice).createVirtualDisplay(
                mVirtualDisplayConfigArgumentCaptor.capture(), any());
        VirtualDisplayConfig virtualDisplayConfig = mVirtualDisplayConfigArgumentCaptor.getValue();
        assertThat(virtualDisplayConfig.getName()).contains(mDefaultParams.getName());

        assertThat(virtualDisplayConfig.getDensityDpi()).isEqualTo(DISPLAY_DPI);
        assertThat(virtualDisplayConfig.getHeight()).isEqualTo(DISPLAY_HEIGHT);
        assertThat(virtualDisplayConfig.getWidth()).isEqualTo(DISPLAY_WIDTH);
        assertThat(virtualDisplayConfig.getSurface()).isNull();

        int expectedDisplayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
        assertThat(virtualDisplayConfig.getFlags()).isEqualTo(expectedDisplayFlags);

        verify(mVirtualDevice).setDisplayImePolicy(
                VIRTUAL_DISPLAY_ID, WindowManager.DISPLAY_IME_POLICY_HIDE);

        verify(mVirtualDevice).createVirtualDpad(
                mVirtualDpadConfigArgumentCaptor.capture(), any());
        VirtualDpadConfig virtualDpadConfig = mVirtualDpadConfigArgumentCaptor.getValue();
        assertThat(virtualDpadConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualDpadConfig.getInputDeviceName()).contains(mDefaultParams.getName());

        verify(mVirtualDevice).createVirtualKeyboard(
                mVirtualKeyboardConfigArgumentCaptor.capture(), any());
        VirtualKeyboardConfig virtualKeyboardConfig =
                mVirtualKeyboardConfigArgumentCaptor.getValue();
        assertThat(virtualKeyboardConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualKeyboardConfig.getInputDeviceName()).contains(mDefaultParams.getName());

        verify(mVirtualDevice).createVirtualTouchscreen(
                mVirtualTouchscreenConfigArgumentCaptor.capture(), any());
        VirtualTouchscreenConfig virtualTouchscreenConfig =
                mVirtualTouchscreenConfigArgumentCaptor.getValue();
        assertThat(virtualTouchscreenConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualTouchscreenConfig.getWidth()).isEqualTo(DISPLAY_WIDTH);
        assertThat(virtualTouchscreenConfig.getHeight()).isEqualTo(DISPLAY_HEIGHT);
        assertThat(virtualTouchscreenConfig.getInputDeviceName()).contains(
                mDefaultParams.getName());
    }

    @Test
    @DisableFlags(value = {Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_RELAXED,
            Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT})
    public void createSession_noActivityPolicy() throws Exception {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice, never()).setDevicePolicy(eq(POLICY_TYPE_ACTIVITY), anyInt());

        verify(mVirtualDevice).addActivityPolicyExemption(
                argThat(new MatchesActivityPolicyExcemption(PERMISSION_CONTROLLER_PACKAGE)));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_RELAXED)
    public void createSession_strictActivityPolicy() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDevice).setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

        for (String expected : TARGET_PACKAGE_NAMES) {
            verify(mVirtualDevice).addActivityPolicyExemption(
                    argThat(new MatchesActivityPolicyExcemption(expected)));
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_RELAXED)
    public void createSession_strictActivityPolicy_removesPermissionController() throws Exception {
        List<String> targetPackageNames = List.of(TARGET_PACKAGE_1, PERMISSION_CONTROLLER_PACKAGE);
        createComputerControlSession(new ComputerControlSessionParams.Builder()
                .setTargetPackageNames(targetPackageNames)
                .setName(ComputerControlSessionTest.class.getSimpleName())
                .build());

        verify(mVirtualDevice).setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

        verify(mVirtualDevice).addActivityPolicyExemption(
                argThat(new MatchesActivityPolicyExcemption(TARGET_PACKAGE_1)));
        verify(mVirtualDevice, never()).addActivityPolicyExemption(
                argThat(new MatchesActivityPolicyExcemption(PERMISSION_CONTROLLER_PACKAGE)));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_RELAXED)
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    public void createSession_relaxedActivityPolicy() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDevice).setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

        List<String> targetPackageNames = new ArrayList<>(mDefaultParams.getTargetPackageNames());
        targetPackageNames.addAll(PACKAGES_WITHOUT_LAUNCHER_ACTIVITY);

        for (String expected : targetPackageNames) {
            verify(mVirtualDevice).addActivityPolicyExemption(
                    argThat(new MatchesActivityPolicyExcemption(expected)));
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_RELAXED)
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    public void createSession_relaxedActivityPolicy_removesPermissionController() throws Exception {
        createComputerControlSession(new ComputerControlSessionParams.Builder()
                .setName(ComputerControlSessionTest.class.getSimpleName())
                .setTargetPackageNames(List.of(TARGET_PACKAGE_1, PERMISSION_CONTROLLER_PACKAGE))
                .build());

        verify(mVirtualDevice).setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

        List<String> targetPackageNames = new ArrayList<>();
        targetPackageNames.add(TARGET_PACKAGE_1);
        targetPackageNames.addAll(PACKAGES_WITHOUT_LAUNCHER_ACTIVITY);
        for (String expected : targetPackageNames) {
            verify(mVirtualDevice).addActivityPolicyExemption(
                    argThat(new MatchesActivityPolicyExcemption(expected)));
        }
        verify(mVirtualDevice, never()).addActivityPolicyExemption(
                argThat(new MatchesActivityPolicyExcemption(PERMISSION_CONTROLLER_PACKAGE)));
    }

    @Test
    @EnableFlags(value = {Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_RELAXED,
            Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT})
    public void createSession_bothActivityPolicies() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDevice).setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

        for (String expected : TARGET_PACKAGE_NAMES) {
            verify(mVirtualDevice).addActivityPolicyExemption(
                    argThat(new MatchesActivityPolicyExcemption(expected)));
        }
    }

    @Test
    public void closeSession_closesVirtualDevice() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.close();
        verify(mVirtualDevice).setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
        verify(mVirtualDevice).close();
        verify(mOnClosedListener).onClosed(mSession.asBinder());
    }

    @Test
    public void getVirtualDisplayId_returnsCreatedDisplay() {
        createComputerControlSession(mDefaultParams);
        assertThat(mSession.getVirtualDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
    }

    @Test
    public void createSession_disablesAnimationsOnDisplay() {
        createComputerControlSession(mDefaultParams);
        verify(mInjector).disableAnimationsForDisplay(VIRTUAL_DISPLAY_ID);
    }

    @Test
    public void launchApplication_launchesApplication() {
        createComputerControlSession(mDefaultParams);
        mSession.launchApplication(TARGET_PACKAGE_1);
        verify(mInjector).launchApplicationOnDisplayAsUser(
                eq(TARGET_PACKAGE_1), eq(VIRTUAL_DISPLAY_ID), any());
    }

    @Test
    public void launchApplication_undeclaredPackage_throws() {
        createComputerControlSession(mDefaultParams);
        assertThrows(IllegalArgumentException.class,
                () -> mSession.launchApplication(UNDECLARED_TARGET_PACKAGE));
    }

    @Test
    public void tap_sendsTouchscreenEvents() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.tap(60, 200);
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(60, 200, VirtualTouchEvent.ACTION_DOWN)));
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(60, 200, VirtualTouchEvent.ACTION_UP)));
    }

    @Test
    public void swipe_sendsTouchscreenEvents() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.swipe(60, 200, 180, 400);
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(60, 200, VirtualTouchEvent.ACTION_DOWN)));
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(60, 200, VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen,
                timeout(ComputerControlSessionImpl.SWIPE_EVENT_DELAY_MS
                        * (ComputerControlSessionImpl.SWIPE_STEPS))
                        .times(ComputerControlSessionImpl.SWIPE_STEPS))
                .sendTouchEvent(argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen, timeout(ComputerControlSessionImpl.SWIPE_EVENT_DELAY_MS))
                .sendTouchEvent(argThat(
                        new MatchesTouchEvent(180, 400, VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(180, 400, VirtualTouchEvent.ACTION_UP)));
    }

    private void createComputerControlSession(ComputerControlSessionParams params) {
        mSession = new ComputerControlSessionImpl(mAppToken, params,
                AttributionSource.myAttributionSource(), mVirtualDeviceFactory, mOnClosedListener,
                mInjector);
    }

    private static class MatchesActivityPolicyExcemption implements
            ArgumentMatcher<ActivityPolicyExemption> {

        private final String mPackageName;

        MatchesActivityPolicyExcemption(String packageName) {
            mPackageName = packageName;
        }

        @Override
        public boolean matches(ActivityPolicyExemption argument) {
            return mPackageName.equals(argument.getPackageName());
        }
    }

    private static class MatchesTouchEvent implements ArgumentMatcher<VirtualTouchEvent> {

        private final int mX;
        private final int mY;
        private final int mAction;

        MatchesTouchEvent(int action) {
            mX = -1;
            mY = -1;
            mAction = action;
        }

        MatchesTouchEvent(int x, int y, int action) {
            mX = x;
            mY = y;
            mAction = action;
        }

        @Override
        public boolean matches(VirtualTouchEvent event) {
            if (event.getMajorAxisSize() != 1
                    || event.getPointerId() != 4
                    || event.getPressure() != 255
                    || event.getToolType() != VirtualTouchEvent.TOOL_TYPE_FINGER
                    || event.getAction() != mAction) {
                return false;
            }
            if (mX == -1 || mY == -1) {
                return true;
            }
            return mX == event.getX() && mY == event.getY();
        }
    }
}
