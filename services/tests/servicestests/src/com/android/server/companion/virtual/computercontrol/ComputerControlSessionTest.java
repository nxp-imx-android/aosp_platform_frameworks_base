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
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.content.AttributionSource;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionTest {

    private static final String PERMISSION_CONTROLLER_PACKAGE = "permission.controller.package";

    private static final int VIRTUAL_DISPLAY_ID = 42;

    @Mock
    private PackageManager mPackageManager;
    @Mock
    private ComputerControlSessionProcessor.VirtualDeviceFactory mVirtualDeviceFactory;
    @Mock
    private WindowManagerInternal mWindowManagerInternal;
    @Mock
    private ComputerControlSessionImpl.OnClosedListener mOnClosedListener;
    @Mock
    private IVirtualDevice mVirtualDevice;
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
    private final ComputerControlSessionParams mParams = new ComputerControlSessionParams.Builder()
            .setName(ComputerControlSessionTest.class.getSimpleName())
            .build();
    private ComputerControlSessionImpl mSession;

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        when(mPackageManager.getPermissionControllerPackageName())
                .thenReturn(PERMISSION_CONTROLLER_PACKAGE);
        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any(), any()))
                .thenReturn(mVirtualDevice);
        when(mVirtualDevice.createVirtualDisplay(any(), any())).thenReturn(VIRTUAL_DISPLAY_ID);
        mSession = new ComputerControlSessionImpl(mAppToken, mParams,
                AttributionSource.myAttributionSource(), mPackageManager, mVirtualDeviceFactory,
                mWindowManagerInternal, mOnClosedListener);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void createSessionWithoutDisplaySurface_appliesCorrectParams() throws Exception {
        verify(mVirtualDeviceFactory).createVirtualDevice(
                eq(mAppToken), any(), mVirtualDeviceParamsArgumentCaptor.capture(), any());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue().getName())
                .isEqualTo(mParams.getName());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue()
                .getDevicePolicy(POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);

        verify(mVirtualDevice).addActivityPolicyExemption(
                mActivityPolicyExemptionArgumentCaptor.capture());
        assertThat(mActivityPolicyExemptionArgumentCaptor.getValue().getPackageName())
                .isEqualTo(PERMISSION_CONTROLLER_PACKAGE);

        verify(mVirtualDevice).createVirtualDisplay(
                mVirtualDisplayConfigArgumentCaptor.capture(), any());
        VirtualDisplayConfig virtualDisplayConfig = mVirtualDisplayConfigArgumentCaptor.getValue();
        assertThat(virtualDisplayConfig.getName()).contains(mParams.getName());

        Display display =
                DisplayManagerGlobal.getInstance().getRealDisplay(Display.DEFAULT_DISPLAY);
        DisplayInfo displayInfo = new DisplayInfo();
        display.getDisplayInfo(displayInfo);
        assertThat(virtualDisplayConfig.getDensityDpi()).isEqualTo(displayInfo.logicalDensityDpi);
        assertThat(virtualDisplayConfig.getHeight()).isEqualTo(displayInfo.logicalHeight);
        assertThat(virtualDisplayConfig.getWidth()).isEqualTo(displayInfo.logicalWidth);
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
        assertThat(virtualDpadConfig.getInputDeviceName()).contains(mParams.getName());

        verify(mVirtualDevice).createVirtualKeyboard(
                mVirtualKeyboardConfigArgumentCaptor.capture(), any());
        VirtualKeyboardConfig virtualKeyboardConfig =
                mVirtualKeyboardConfigArgumentCaptor.getValue();
        assertThat(virtualKeyboardConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualKeyboardConfig.getInputDeviceName()).contains(mParams.getName());

        verify(mVirtualDevice).createVirtualTouchscreen(
                mVirtualTouchscreenConfigArgumentCaptor.capture(), any());
        VirtualTouchscreenConfig virtualTouchscreenConfig =
                mVirtualTouchscreenConfigArgumentCaptor.getValue();
        assertThat(virtualTouchscreenConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualTouchscreenConfig.getWidth()).isEqualTo(displayInfo.logicalWidth);
        assertThat(virtualTouchscreenConfig.getHeight()).isEqualTo(displayInfo.logicalHeight);
        assertThat(virtualTouchscreenConfig.getInputDeviceName()).contains(mParams.getName());
    }

    @Test
    public void closeSession_closesVirtualDevice() throws Exception {
        mSession.close();
        verify(mVirtualDevice).setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
        verify(mVirtualDevice).close();
        verify(mOnClosedListener).onClosed(mSession.asBinder());
    }

    @Test
    public void getVirtualDisplayId_returnsCreatedDisplay() {
        assertThat(mSession.getVirtualDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
    }

    @Test
    public void createSession_disablesAnimationsOnDisplay() {
        verify(mWindowManagerInternal).setAnimationsDisabledForDisplay(eq(VIRTUAL_DISPLAY_ID),
                eq(true));
    }
}
