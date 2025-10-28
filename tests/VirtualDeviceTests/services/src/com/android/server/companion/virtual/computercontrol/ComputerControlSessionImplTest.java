/*
 * Copyright 2025 The Android Open Source Project
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
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_BLOCKED_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;
import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH;
import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_SECURE_CONTENT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT;

import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.KEY_EVENT_DELAY_MS;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.LONG_PRESS_TIMEOUT_MULTIPLIER;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.PRODUCT_ID_DPAD;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.PRODUCT_ID_KEYBOARD;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.PRODUCT_ID_TOUCHSCREEN;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.SWIPE_STEPS;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.TOUCH_EVENT_DELAY_MS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.AudioCapture;
import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.IInteractiveMirror;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.gui.DropInputMode;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.inputmethod.IRemoteComputerControlInputConnection;
import com.android.server.LocalServices;
import com.android.server.input.InputManagerInternal;
import com.android.server.inputmethod.InputMethodManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.testutils.StubTransaction;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.function.Consumer;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionImplTest {
    private static final String PERMISSION_CONTROLLER_PACKAGE = "permission.controller.package";
    private static final int USER_ID = UserHandle.USER_SYSTEM;
    private static final int MAIN_DISPLAY_ID = 41;
    private static final int VIRTUAL_DISPLAY_ID = 42;
    private static final int DISPLAY_WIDTH = 600;
    private static final int DISPLAY_HEIGHT = 1000;
    private static final int DISPLAY_DPI = 480;
    private static final int LONG_PRESS_STEP_COUNT = 5;
    private static final String TARGET_PACKAGE_1 = "com.android.foo";
    private static final String TARGET_PACKAGE_2 = "com.android.bar";
    private static final List<String> TARGET_PACKAGE_NAMES =
            List.of(TARGET_PACKAGE_1, TARGET_PACKAGE_2);
    private static final String UNDECLARED_TARGET_PACKAGE = "com.android.baz";
    private static final String TARGET_CLASS = "com.android.foo.FooActivity";
    private static final long GLOBAL_TIMEOUT_MILLIS = 10000L;
    private static final ComponentName TEST_COMPONENT = new ComponentName(TARGET_PACKAGE_1,
            TARGET_CLASS);
    private static final ComponentName BLOCKED_COMPONENT = new ComponentName(
            UNDECLARED_TARGET_PACKAGE, ".Activity");
    @FunctionalInterface
    private interface Interactor {
        void interact(ComputerControlSessionImpl t) throws Exception;
    }

    private static final List<Interactor> ALL_INTERACTIONS = List.of(
            (session) -> session.tap(0, 0),
            (session) -> session.swipe(0, 0, 1, 1),
            (session) -> session.longPress(0, 0),
            (session) -> session.performAction(ComputerControlSession.ACTION_GO_BACK),
            (session) -> session.insertText("text", true, true)
    );

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Mock
    private IDisplayManager mDisplayManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private PackageManager mOwnerPackageManager;
    @Mock
    private WindowManagerInternal mWindowManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private InputMethodManagerInternal mInputMethodManagerInternal;
    @Mock
    private InputManagerInternal mInputManagerInternal;
    @Mock
    private ViewConfiguration mViewConfiguration;
    @Mock
    private ComputerControlSessionProcessor.VirtualDeviceFactory mVirtualDeviceFactory;
    @Mock
    private IComputerControlLifecycleCallback mLifecycleCallback;
    @Mock
    private Surface mClientSurface;
    @Mock
    private Consumer<ComputerControlSessionImpl> mOnClosedListener;
    @Mock
    private VirtualDevice mVirtualDevice;
    @Mock
    private VirtualDisplay mVirtualDisplay;
    @Mock
    private Display mDisplay;
    @Mock
    private IRemoteComputerControlInputConnection mRemoteComputerControlInputConnection;
    @Mock
    private VirtualDpad mVirtualDpad;
    @Mock
    private VirtualKeyboard mVirtualKeyboard;
    @Mock
    private VirtualTouchscreen mVirtualTouchscreen;
    @Mock
    private VirtualAudioDevice mVirtualAudioDevice;
    @Mock
    private AudioInjection mAudioInjection;
    @Mock
    private AudioCapture mAudioCapture;
    @Mock
    private UserHandle mUserHandle;
    @Mock
    private IntentSender mIntentSender;

    @Captor
    private ArgumentCaptor<Intent> mIntentArgumentCaptor;
    @Captor
    private ArgumentCaptor<Bundle> mBundleArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualDeviceParams> mVirtualDeviceParamsArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualDisplayConfig> mVirtualDisplayConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualTouchscreenConfig> mVirtualTouchscreenConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualDpadConfig> mVirtualDpadConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualKeyboardConfig> mVirtualKeyboardConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualDeviceManager.ActivityListener> mActivityListenerArgumentCaptor;

    private SurfaceControl.Transaction mTransaction;
    private AutoCloseable mMockitoSession;
    private ComputerControlSessionImpl mSession;
    private final IBinder mAppToken = new Binder();
    private final ComputerControlSessionParams mDefaultParams =
            new ComputerControlSessionParams.Builder()
                    .setName(ComputerControlSessionImplTest.class.getSimpleName())
                    .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                    .build();
    private final Context mContext =
            spy(new ContextWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext()));
    private final Context mOwnerContext =
            spy(new ContextWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext()));

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mTransaction = spy(new StubTransaction());

        when(mContext.createContextAsUser(UserHandle.of(USER_ID), /* flags = */ 0))
                .thenReturn(mOwnerContext);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mOwnerContext.getPackageManager()).thenReturn(mOwnerPackageManager);

        LocalServices.removeAllServicesForTest();
        LocalServices.addService(WindowManagerInternal.class, mWindowManagerInternal);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);
        LocalServices.addService(InputMethodManagerInternal.class, mInputMethodManagerInternal);
        LocalServices.addService(InputManagerInternal.class, mInputManagerInternal);
        ViewConfiguration.setInstanceForTesting(mContext, mViewConfiguration);

        when(mUserManagerInternal.getMainDisplayAssignedToUser(anyInt()))
                .thenReturn(MAIN_DISPLAY_ID);
        when(mInputMethodManagerInternal
                .getComputerControlInputConnection(anyInt(), eq(VIRTUAL_DISPLAY_ID)))
                .thenReturn(mRemoteComputerControlInputConnection);

        DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = DISPLAY_WIDTH;
        displayInfo.logicalHeight = DISPLAY_HEIGHT;
        displayInfo.logicalDensityDpi = DISPLAY_DPI;
        when(mDisplayManager.getDisplayInfo(MAIN_DISPLAY_ID)).thenReturn(displayInfo);
        when(mDisplayManager.getDisplayInfo(VIRTUAL_DISPLAY_ID)).thenReturn(displayInfo);

        when(mPackageManager.getPermissionControllerPackageName())
                .thenReturn(PERMISSION_CONTROLLER_PACKAGE);
        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any()))
                .thenReturn(mVirtualDevice);

        when(mVirtualDevice.createVirtualDisplay(any(), any(), any())).thenReturn(mVirtualDisplay);
        when(mVirtualDisplay.getDisplay()).thenReturn(mDisplay);
        when(mDisplay.getDisplayId()).thenReturn(VIRTUAL_DISPLAY_ID);

        when(mVirtualDevice.createVirtualTouchscreen(any())).thenReturn(mVirtualTouchscreen);
        when(mVirtualDevice.createVirtualKeyboard(any())).thenReturn(mVirtualKeyboard);
        when(mVirtualDevice.createVirtualDpad(any())).thenReturn(mVirtualDpad);
        when(mViewConfiguration.getLongPressTimeoutMillis()).thenReturn(1000);
        when(mVirtualDevice.createVirtualAudioDevice(any(), any(), any())).thenReturn(
                mVirtualAudioDevice);
        when(mVirtualAudioDevice.startAudioCapture(any())).thenReturn(mAudioCapture);
        when(mVirtualAudioDevice.startAudioInjection(any())).thenReturn(mAudioInjection);
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
        mMockitoSession.close();
    }

    @Test
    public void createSession_appliesCorrectParams() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDeviceFactory).createVirtualDevice(
                eq(mAppToken), any(), mVirtualDeviceParamsArgumentCaptor.capture());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue().getName())
                .isEqualTo(mDefaultParams.getName());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue()
                .getDevicePolicy(POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue()
                .getDevicePolicy(POLICY_TYPE_BLOCKED_ACTIVITY))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue()
                .getDevicePolicy(POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);

        verify(mVirtualDevice).createVirtualDisplay(
                mVirtualDisplayConfigArgumentCaptor.capture(), any(), any());
        VirtualDisplayConfig virtualDisplayConfig = mVirtualDisplayConfigArgumentCaptor.getValue();
        assertThat(virtualDisplayConfig.getName()).contains(mDefaultParams.getName());

        assertThat(virtualDisplayConfig.getDensityDpi()).isEqualTo(DISPLAY_DPI);
        assertThat(virtualDisplayConfig.getHeight()).isEqualTo(DISPLAY_HEIGHT);
        assertThat(virtualDisplayConfig.getWidth()).isEqualTo(DISPLAY_WIDTH);
        assertThat(virtualDisplayConfig.getSurface()).isNull();

        final int expectedDisplayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
        assertThat(virtualDisplayConfig.getFlags()).isEqualTo(expectedDisplayFlags);

        verify(mVirtualDevice).setDisplayImePolicy(
                VIRTUAL_DISPLAY_ID, WindowManager.DISPLAY_IME_POLICY_HIDE);

        verify(mVirtualDevice).createVirtualDpad(mVirtualDpadConfigArgumentCaptor.capture());
        VirtualDpadConfig virtualDpadConfig = mVirtualDpadConfigArgumentCaptor.getValue();
        assertThat(virtualDpadConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualDpadConfig.getInputDeviceName()).contains(mDefaultParams.getName());
        assertThat(virtualDpadConfig.getProductId()).isEqualTo(PRODUCT_ID_DPAD);

        verify(mVirtualDevice).createVirtualTouchscreen(
                mVirtualTouchscreenConfigArgumentCaptor.capture());
        VirtualTouchscreenConfig virtualTouchscreenConfig =
                mVirtualTouchscreenConfigArgumentCaptor.getValue();
        assertThat(virtualTouchscreenConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualTouchscreenConfig.getWidth()).isEqualTo(DISPLAY_WIDTH);
        assertThat(virtualTouchscreenConfig.getHeight()).isEqualTo(DISPLAY_HEIGHT);
        assertThat(virtualTouchscreenConfig.getInputDeviceName()).contains(
                mDefaultParams.getName());
        assertThat(virtualTouchscreenConfig.getProductId()).isEqualTo(PRODUCT_ID_TOUCHSCREEN);
    }

    @Test
    public void createSession_doesNotSetAllowedUsers() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDeviceFactory).createVirtualDevice(
                eq(mAppToken), any(), mVirtualDeviceParamsArgumentCaptor.capture());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue()
                .getAllowedUsers()).isEmpty();
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void createSession_createsKeyboard() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDeviceFactory).createVirtualDevice(
                eq(mAppToken), any(), mVirtualDeviceParamsArgumentCaptor.capture());
        verify(mVirtualDevice).createVirtualKeyboard(
                mVirtualKeyboardConfigArgumentCaptor.capture());
        VirtualKeyboardConfig virtualKeyboardConfig =
                mVirtualKeyboardConfigArgumentCaptor.getValue();
        assertThat(virtualKeyboardConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualKeyboardConfig.getInputDeviceName()).contains(mDefaultParams.getName());
        assertThat(virtualKeyboardConfig.getProductId()).isEqualTo(PRODUCT_ID_KEYBOARD);
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void createSession_doesNotCreateKeyboard() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDeviceFactory).createVirtualDevice(
                eq(mAppToken), any(), mVirtualDeviceParamsArgumentCaptor.capture());
        verify(mVirtualDevice, never()).createVirtualKeyboard(any());
    }

    @Test
    public void createSession_canCloseSessionBeforeInitializing() throws Exception {
        createComputerControlSessionWithoutInitializing(mDefaultParams, GLOBAL_TIMEOUT_MILLIS);
        mSession.close();

        verify(mOnClosedListener).accept(eq(mSession));
    }

    @Test
    public void createSession_noActivityPolicy() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDevice, never()).setDevicePolicy(eq(POLICY_TYPE_ACTIVITY), anyInt());
        verify(mVirtualDevice, never()).addActivityPolicyExemption(
                any(ActivityPolicyExemption.class));
    }

    @Test
    public void closeSession_closesVirtualDevice() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.close();
        verify(mVirtualDevice).close();
        verify(mOnClosedListener).accept(mSession);
        verify(mLifecycleCallback).onClosed(eq(CLOSE_REASON_CALLER_INITIATED));
    }

    @Test
    public void closeSessionInternal_closesWithProvidedReason() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.close(123);
        verify(mLifecycleCallback).onClosed(eq(123));
    }

    @Test
    public void getVirtualDisplayId_returnsCreatedDisplay() {
        createComputerControlSession(mDefaultParams);
        assertThat(mSession.getVirtualDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
    }

    @Test
    public void createSession_disablesAnimationsOnDisplay() {
        createComputerControlSession(mDefaultParams);
        verify(mWindowManagerInternal).setAnimationsDisabledForDisplay(VIRTUAL_DISPLAY_ID, true);
    }

    @Test
    public void createSession_setsForceShowTouchesOnDisplay() {
        createComputerControlSession(mDefaultParams);
        verify(mInputManagerInternal).setForceShowTouchesOnDisplay(VIRTUAL_DISPLAY_ID, true);
    }

    @Test
    public void launchApplication_launchesApplication() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mOwnerPackageManager.queryIntentActivities(any(), any()))
                .thenReturn(List.of(new ResolveInfo()));

        mSession.launchApplication(TARGET_PACKAGE_1, TARGET_CLASS);

        assertLaunchedApplication(TARGET_PACKAGE_1);
    }

    @Test
    public void launchApplication_doesNotAddExemption() throws Exception {
        createComputerControlSession(mDefaultParams);
        when(mOwnerPackageManager.queryIntentActivities(any(), any()))
                .thenReturn(List.of(new ResolveInfo()));

        mSession.launchApplication(UNDECLARED_TARGET_PACKAGE, TARGET_CLASS);

        verify(mVirtualDevice, never()).addActivityPolicyExemption(
                any(ActivityPolicyExemption.class));
        assertLaunchedApplication(UNDECLARED_TARGET_PACKAGE);
    }

    @Test
    public void launchApplication_noLaunchIntent_throws() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mOwnerPackageManager.queryIntentActivities(any(), any())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> mSession.launchApplication(TARGET_PACKAGE_1, TARGET_CLASS));
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
                timeout(TOUCH_EVENT_DELAY_MS * (SWIPE_STEPS + 1)).times(SWIPE_STEPS))
                .sendTouchEvent(argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen, timeout(TOUCH_EVENT_DELAY_MS))
                .sendTouchEvent(argThat(
                        new MatchesTouchEvent(180, 400, VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(180, 400, VirtualTouchEvent.ACTION_UP)));
    }

    @Test
    public void longPress_sendsTouchscreenEvents() throws Exception {
        when(mViewConfiguration.getLongPressTimeoutMillis()).thenReturn(
                LONG_PRESS_STEP_COUNT
                        * (int) (TOUCH_EVENT_DELAY_MS / LONG_PRESS_TIMEOUT_MULTIPLIER));
        createComputerControlSession(mDefaultParams);

        mSession.longPress(100, 200);
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(100, 200, VirtualTouchEvent.ACTION_DOWN)));
        verify(mVirtualTouchscreen, timeout(TOUCH_EVENT_DELAY_MS * (LONG_PRESS_STEP_COUNT + 1)))
                .sendTouchEvent(
                        argThat(new MatchesTouchEvent(100, 200, VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen, timeout(TOUCH_EVENT_DELAY_MS * (LONG_PRESS_STEP_COUNT + 2)))
                .sendTouchEvent(argThat(
                        new MatchesTouchEvent(100, 200, VirtualTouchEvent.ACTION_UP)));
    }

    @Test
    public void newTouchGesture_cancelsOngoingGesture() throws Exception {
        createComputerControlSession(mDefaultParams);

        mSession.swipe(100, 200, 300, 400);

        mSession.swipe(400, 300, 200, 100);
        verify(mVirtualTouchscreen, times(1)).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));

        mSession.longPress(100, 200);
        verify(mVirtualTouchscreen, times(2)).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));

        mSession.longPress(300, 400);
        verify(mVirtualTouchscreen, times(3)).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));

        mSession.tap(500, 600);
        verify(mVirtualTouchscreen, times(4)).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertText_sendsCharacterKeysToVirtualKeyboard() throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.insertText("t", false /* replaceExisting */, false /* commit */);
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_T,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_T,
                        VirtualKeyEvent.ACTION_UP)));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextWithReplaceExisting_sendsDeleteTextSequenceToVirtualKeyboard()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.insertText("text", true /* replaceExisting */, false /* commit */);
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_A,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_A,
                        VirtualKeyEvent.ACTION_UP)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT,
                        VirtualKeyEvent.ACTION_UP)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_DEL,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_DEL,
                        VirtualKeyEvent.ACTION_UP)));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextWithCommit_sendsEnterKeyToVirtualKeyboard() throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.insertText("", false /* replaceExisting */, true /* commit */);
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_ENTER,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_ENTER,
                        VirtualKeyEvent.ACTION_UP)));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertText_callsCommitTextOnAvailableInputConnection() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mSession.insertText("text", false /* replaceExisting */, false /* commit */);
        verify(mRemoteComputerControlInputConnection).commitText(any(), eq("text"), eq(1));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextWithReplaceExisting_callsReplaceTextOnAvailableInputConnection()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mSession.insertText("text", true /* replaceExisting */, false /* commit */);
        verify(mRemoteComputerControlInputConnection).replaceText(any(), eq(0),
                eq(Integer.MAX_VALUE), eq("text"), eq(1));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextWithCommit_sendEnterKeyOnAvailableInputConnection()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.insertText("text", false /* replaceExisting */, true /* commit */);
        verify(mRemoteComputerControlInputConnection).commitText(any(), eq("text"), eq(1));
        verify(mRemoteComputerControlInputConnection).sendKeyEvent(any(),
                argThat(new MatchesKeyEvent(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN)));
        verify(mRemoteComputerControlInputConnection).sendKeyEvent(any(),
                argThat(new MatchesKeyEvent(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP)));
    }

    @Test
    public void performActionBack_injectsBackKey()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.performAction(ComputerControlSession.ACTION_GO_BACK);
        verify(mVirtualDpad).sendKeyEvent(argThat(
                new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualDpad).sendKeyEvent(argThat(
                new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_UP)));
    }

    @Test
    public void sessionCloses_afterGlobalTimeout() throws Exception {
        createComputerControlSession(mDefaultParams, /* globalSessionTimeoutDurationMs = */ 100L);

        verify(mLifecycleCallback, timeout(2 * 100L)).onClosed(eq(CLOSE_REASON_SESSION_TIMED_OUT));
    }

    @Test
    public void createInteractiveMirror_successfullyReturnsMirrorWithInputDisabled()
            throws Exception {
        createComputerControlSession(mDefaultParams);
        final var mirrorSurface = new SurfaceControl();
        when(mWindowManagerInternal.createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID))
                .thenReturn(mirrorSurface);

        final var returnedMirrorSurface = new SurfaceControl();
        IInteractiveMirror mirror = mSession.createInteractiveMirror(returnedMirrorSurface);

        verify(mWindowManagerInternal).createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID);
        assertThat(mirror).isNotNull();
        verify(mTransaction).setDropInputMode(eq(mirrorSurface), eq(DropInputMode.ALL));
    }

    @Test
    public void createInteractiveMirror_whenMirroringFails_returnsNull() throws Exception {
        createComputerControlSession(mDefaultParams);
        when(mWindowManagerInternal.createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID))
                .thenReturn(null);

        final var returnedMirrorSurface = new SurfaceControl();
        IInteractiveMirror mirror = mSession.createInteractiveMirror(returnedMirrorSurface);

        verify(mWindowManagerInternal).createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID);
        assertThat(mirror).isNull();
    }

    @Test
    public void attachNotificationInfo_attachesNotificationInfo() throws Exception {
        createComputerControlSession(mDefaultParams);
        final int notificationId = 5;
        final String notificationTag = "hello";

        mSession.attachNotificationInfo(notificationId, notificationTag);

        ComputerControlSessionImpl.NotificationInfo info = mSession.getNotificationInfo();
        assertThat(info).isEqualTo(
                new ComputerControlSessionImpl.NotificationInfo(notificationId, notificationTag));
    }

    @Test
    public void attachNotificationInfo_alreadyAttached_throwsException() throws Exception {
        createComputerControlSession(mDefaultParams);
        final int notificationId = 5;
        final String notificationTag = "hello";

        mSession.attachNotificationInfo(notificationId, notificationTag);

        assertThrows(IllegalStateException.class,
                () -> mSession.attachNotificationInfo(3, "hello2"));
    }

    @Test
    public void onSecureWindowShown_entersBlockedState()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());

        mActivityListenerArgumentCaptor.getValue().onSecureWindowShown(VIRTUAL_DISPLAY_ID,
                TEST_COMPONENT, mUserHandle);

        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TARGET_PACKAGE_1);
    }

    @Test
    public void onSecureWindowHidden_exitsBlockedState()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TARGET_PACKAGE_1);
        clearInvocations(mVirtualDisplay, mLifecycleCallback);

        activityListener.onSecureWindowHidden(VIRTUAL_DISPLAY_ID);

        verify(mLifecycleCallback).onActive();
    }

    @Test
    public void onActivityLaunchRequested_whenActivityIsBlocked_entersBlockedState()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());

        mActivityListenerArgumentCaptor.getValue().onActivityLaunchRequested(VIRTUAL_DISPLAY_ID,
                BLOCKED_COMPONENT, USER_ID);

        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
                UNDECLARED_TARGET_PACKAGE);
    }

    @Test
    public void onTopActivityChanged_toAllowedPackage_exitsBlockedState() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        activityListener.onActivityLaunchRequested(VIRTUAL_DISPLAY_ID, BLOCKED_COMPONENT, USER_ID);
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
                UNDECLARED_TARGET_PACKAGE);
        clearInvocations(mLifecycleCallback);

        activityListener.onTopActivityChanged(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);

        verify(mLifecycleCallback).onActive();
    }

    @Test
    public void onTopActivityChanged_toDisallowedPackage_doesNotExitBlockedState()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        activityListener.onActivityLaunchRequested(VIRTUAL_DISPLAY_ID, BLOCKED_COMPONENT, USER_ID);
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
                UNDECLARED_TARGET_PACKAGE);
        clearInvocations(mLifecycleCallback);

        activityListener.onTopActivityChanged(VIRTUAL_DISPLAY_ID, BLOCKED_COMPONENT, USER_ID);

        verifyNoInteractions(mLifecycleCallback);
    }

    @Test
    public void blockedState_activityBlockReasonIsPreferred() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();

        // First secure window, then blocked activity.
        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TARGET_PACKAGE_1);
        clearInvocations(mLifecycleCallback);

        activityListener.onActivityLaunchRequested(VIRTUAL_DISPLAY_ID, BLOCKED_COMPONENT, USER_ID);
        // onBlocked should be called again with the preferred reason.
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
                UNDECLARED_TARGET_PACKAGE);
        clearInvocations(mLifecycleCallback);

        // Unblock secure window, should remain blocked.
        activityListener.onSecureWindowHidden(VIRTUAL_DISPLAY_ID);
        verifyNoInteractions(mLifecycleCallback);

        // Unblock activity, should be active.
        activityListener.onTopActivityChanged(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);
        verify(mLifecycleCallback).onActive();
    }

    @Test
    public void blockedState_activityBlockReasonIsPreferred_reverseOrder() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();

        // First blocked activity, then secure window.
        activityListener.onActivityLaunchRequested(VIRTUAL_DISPLAY_ID, BLOCKED_COMPONENT, USER_ID);
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
                UNDECLARED_TARGET_PACKAGE);
        clearInvocations(mLifecycleCallback);

        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        // onBlocked should not be called again.
        verify(mLifecycleCallback, never()).onBlocked(anyInt(), any());
        clearInvocations(mLifecycleCallback);

        // Unblock activity, should remain blocked with secure window reason.
        activityListener.onTopActivityChanged(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TARGET_PACKAGE_1);
        verify(mLifecycleCallback, never()).onActive();
        clearInvocations(mLifecycleCallback);

        // Unblock secure window, should unblock.
        activityListener.onSecureWindowHidden(VIRTUAL_DISPLAY_ID);
        verify(mLifecycleCallback).onActive();
    }

    @Test
    public void cannotEnterBlockedState_afterSessionIsClosed() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();

        mSession.close();
        clearInvocations(mLifecycleCallback);

        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        activityListener.onActivityLaunchBlocked(VIRTUAL_DISPLAY_ID, BLOCKED_COMPONENT, mUserHandle,
                mIntentSender);

        verify(mLifecycleCallback, never()).onBlocked(anyInt(), any());
    }

    @Test
    public void notifyActivityListener_beforeInitialization_setsNullSurface()
            throws RemoteException {
        createComputerControlSessionWithoutInitializing(mDefaultParams, GLOBAL_TIMEOUT_MILLIS);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());

        mActivityListenerArgumentCaptor.getValue().onDisplayEmpty(VIRTUAL_DISPLAY_ID);
        verify(mVirtualDisplay).setSurface(isNull());
    }

    @Test
    public void blockedState_updatesDisplaySurface() throws Exception {
        createComputerControlSession(mDefaultParams);
        clearInvocations(mVirtualDisplay);

        try (InBlockedState inBlockedState = new InBlockedState()) {
            verify(mVirtualDisplay).setSurface(not(eq(mClientSurface)));
        }
        verify(mVirtualDisplay).setSurface(eq(mClientSurface));
    }

    @Test
    public void activeState_allowsAllInteractions() throws Exception {
        createComputerControlSession(mDefaultParams);

        // Enter and exit the blocked state to ensure interactions are re-enabled when active.
        try (InBlockedState inBlockedState = new InBlockedState()) {
            // Do nothing.
        }

        final var interactionDevices = List.of(
                mVirtualDpad,
                mVirtualKeyboard,
                mVirtualTouchscreen,
                mRemoteComputerControlInputConnection
        );

        for (Interactor interactor : ALL_INTERACTIONS) {
            interactionDevices.forEach(Mockito::clearInvocations);

            interactor.interact(mSession);

            assertTrue("There were no interactions with any devices in the active state",
                    interactionDevices.stream()
                            .map((d) -> Mockito.mockingDetails(d).getInvocations().size())
                            .anyMatch((i) -> (i > 0)));
        }
    }

    @Test
    public void blockedState_allowsNoInteractions() throws Exception {
        createComputerControlSession(mDefaultParams);

        final var interactionDevices = List.of(
                mVirtualDpad,
                mVirtualKeyboard,
                mVirtualTouchscreen,
                mRemoteComputerControlInputConnection
        );

        try (InBlockedState inBlockedState = new InBlockedState()) {
            for (Interactor interactor : ALL_INTERACTIONS) {
                interactionDevices.forEach(Mockito::clearInvocations);

                interactor.interact(mSession);

                assertTrue("There was an unexpected interaction with a device in blocked state",
                        interactionDevices.stream()
                                .map((d) -> Mockito.mockingDetails(d).getInvocations().size())
                                .allMatch((i) -> (i == 0)));
            }
        }
    }

    /** A default way to enter the blocked state to test block state functionality. */
    private class InBlockedState implements AutoCloseable {
        InBlockedState() throws Exception {
            verify(mVirtualDevice).addActivityListener(any(),
                    mActivityListenerArgumentCaptor.capture());

            mActivityListenerArgumentCaptor.getValue().onSecureWindowShown(VIRTUAL_DISPLAY_ID,
                    TEST_COMPONENT, mUserHandle);
            verify(mLifecycleCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TARGET_PACKAGE_1);
            clearInvocations(mLifecycleCallback);
        }

        @Override
        public void close() throws Exception {
            mActivityListenerArgumentCaptor.getValue().onSecureWindowHidden(VIRTUAL_DISPLAY_ID);
            verify(mLifecycleCallback).onActive();
            clearInvocations(mLifecycleCallback);
        }
    }

    private void createComputerControlSession(ComputerControlSessionParams params) {
        createComputerControlSession(params, GLOBAL_TIMEOUT_MILLIS);
    }

    private void createComputerControlSession(
            ComputerControlSessionParams params, long globalSessionTimeoutDurationMs) {
        createComputerControlSessionWithoutInitializing(params, globalSessionTimeoutDurationMs);
        mSession.initialize(mLifecycleCallback, mClientSurface);
    }

    private void createComputerControlSessionWithoutInitializing(
            ComputerControlSessionParams params, long globalSessionTimeoutDurationMs) {
        DisplayManagerGlobal displayManagerGlobal = new DisplayManagerGlobal(mDisplayManager);
        displayManagerGlobal.disableLocalDisplayInfoCaches();
        mSession = new ComputerControlSessionImpl(
                mContext, displayManagerGlobal, mViewConfiguration, globalSessionTimeoutDurationMs,
                () -> mTransaction, mAppToken, params,
                new AttributionSource(UserHandle.getUid(USER_ID, 0), "com.package", "tag"),
                mVirtualDeviceFactory, mOnClosedListener);
    }

    @SuppressLint("MissingPermission")
    private void assertLaunchedApplication(String packageName) {
        // Verifying resolution.
        verify(mOwnerPackageManager).queryIntentActivities(mIntentArgumentCaptor.capture(), any());
        assertLaunchIntent(mIntentArgumentCaptor.getValue(), packageName);
        // Verifying start.
        verify(mContext).startActivityAsUser(
                mIntentArgumentCaptor.capture(), mBundleArgumentCaptor.capture(), any());
        assertLaunchIntent(mIntentArgumentCaptor.getValue(), packageName);
        assertThat(
                ActivityOptions.fromBundle(mBundleArgumentCaptor.getValue()).getLaunchDisplayId())
                .isEqualTo(VIRTUAL_DISPLAY_ID);
    }

    private void assertLaunchIntent(Intent intent, String packageName) {
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MAIN);
        assertThat(intent.getCategories()).containsExactly(Intent.CATEGORY_LAUNCHER);
        assertThat(intent.getComponent()).isEqualTo(new ComponentName(packageName, TARGET_CLASS));
    }

    private static final class MatchesActivityPolicyExemption implements
            ArgumentMatcher<ActivityPolicyExemption> {
        private final String mPackageName;

        MatchesActivityPolicyExemption(String packageName) {
            mPackageName = packageName;
        }

        @Override
        public boolean matches(ActivityPolicyExemption argument) {
            return mPackageName.equals(argument.getPackageName());
        }
    }

    private static final class MatchesTouchEvent implements ArgumentMatcher<VirtualTouchEvent> {
        private final int mX;
        private final int mY;
        private final int mAction;
        private final int mToolType;

        MatchesTouchEvent(int action) {
            this(-1, -1, action);
        }

        MatchesTouchEvent(int x, int y, int action) {
            mX = x;
            mY = y;
            mAction = action;
            mToolType =
                    mAction == VirtualTouchEvent.ACTION_CANCEL ? VirtualTouchEvent.TOOL_TYPE_PALM
                            : VirtualTouchEvent.TOOL_TYPE_FINGER;
        }

        @Override
        public boolean matches(VirtualTouchEvent event) {
            if (event.getMajorAxisSize() != 1
                    || event.getPointerId() != 4
                    || event.getPressure() != 255
                    || event.getAction() != mAction
                    || event.getToolType() != mToolType) {
                return false;
            }
            if (mX == -1 || mY == -1) {
                return true;
            }
            return mX == event.getX() && mY == event.getY();
        }
    }

    private static final class MatchesVirtualKeyEvent implements ArgumentMatcher<VirtualKeyEvent> {
        private final int mKeyCode;
        private final int mAction;

        MatchesVirtualKeyEvent(int keyCode, int action) {
            mKeyCode = keyCode;
            mAction = action;
        }

        @Override
        public boolean matches(VirtualKeyEvent event) {
            return event.getKeyCode() == mKeyCode && event.getAction() == mAction;
        }
    }

    private static final class MatchesKeyEvent implements ArgumentMatcher<KeyEvent> {
        private final int mKeyCode;

        private final int mAction;

        MatchesKeyEvent(int keyCode, int action) {
            mKeyCode = keyCode;
            mAction = action;
        }

        @Override
        public boolean matches(KeyEvent event) {
            return mKeyCode == event.getKeyCode() && mAction == event.getAction();
        }
    }
}
