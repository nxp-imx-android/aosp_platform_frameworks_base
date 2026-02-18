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
import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH;
import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_SECURE_CONTENT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_EMPTY;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_USER_INITIATED;

import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.CLOSE_ON_DISPLAY_EMPTY_TIMEOUT_MS;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.KEY_EVENT_DELAY_MS;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.LONG_PRESS_TIMEOUT_MULTIPLIER;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.PRODUCT_ID_DPAD;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.PRODUCT_ID_TOUCHSCREEN;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.SWIPE_STEPS;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.TOUCH_EVENT_DELAY_MS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.AppInteractionAttribution;
import android.app.AppOpsManager;
import android.app.IApplicationThread;
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
import android.view.DisplayAddress;
import android.view.DisplayInfo;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.inputmethod.IRemoteComputerControlInputConnection;
import com.android.server.LocalServices;
import com.android.server.appinteraction.AppInteractionService;
import com.android.server.input.InputManagerInternal;
import com.android.server.inputmethod.InputMethodManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;
import com.android.testing.wm.util.StubTransaction;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

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
@RunWith(JUnitParamsRunner.class)
public class ComputerControlSessionImplTest {
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
    private static final String ATTRIBUTION_TAG = "tag";
    private static final long GLOBAL_TIMEOUT_MILLIS = 10000L;
    private static final String AGENT_PACKAGE = "com.package";
    private static final ComponentName TEST_COMPONENT = new ComponentName(TARGET_PACKAGE_1,
            TARGET_CLASS);
    private static final ComponentName BLOCKED_COMPONENT = new ComponentName(
            UNDECLARED_TARGET_PACKAGE, ".Activity");
    private static final AppInteractionAttribution APP_INTERACTION_ATTRIBUTION =
            new AppInteractionAttribution.Builder(
                            AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                    .build();

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
    private PackageManager mOwnerPackageManager;
    @Mock
    private AppOpsManager mAppOpsManager;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private WindowManagerInternal mWindowManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private InputMethodManagerInternal mInputMethodManagerInternal;
    @Mock
    private InputManagerInternal mInputManagerInternal;
    @Mock
    private ActivityTaskManagerInternal mActivityTaskManagerInternal;
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
    @Mock
    private ComputerControlAllowlistController mAllowlistController;
    @Mock
    private AppInteractionService mAppInteractionService;
    @Mock
    private IApplicationThread mAppThread;

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
    @Captor
    private ArgumentCaptor<SurfaceControl> mSurfaceControlArgumentCaptor;
    @Captor
    private ArgumentCaptor<Consumer<Boolean>> mWindowsDrawnCallbackCaptor;

    private SurfaceControl.Transaction mTransaction;
    private AutoCloseable mMockitoSession;
    private ComputerControlSessionImpl mSession;
    private final IBinder mAppToken = new Binder();
    private final ComputerControlSessionParams mDefaultParams =
            new ComputerControlSessionParams.Builder()
                    .setName(ComputerControlSessionImplTest.class.getSimpleName())
                    .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                    .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                    .build();
    private final Context mContext =
            spy(new ContextWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext()));
    private final EditorInfo mEditorInfo = new EditorInfo();

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mTransaction = spy(new StubTransaction());

        final Context ownerContext = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        when(mContext.createContextAsUser(UserHandle.of(USER_ID), /* flags = */ 0))
                .thenReturn(ownerContext);
        when(ownerContext.getPackageManager()).thenReturn(mOwnerPackageManager);
        when(ownerContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOpsManager);

        final Context displayContext = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        doReturn(displayContext).when(mContext).createDisplayContext(any());
        doReturn(mWindowManager).when(displayContext).getSystemService(WindowManager.class);

        LocalServices.removeAllServicesForTest();
        LocalServices.addService(WindowManagerInternal.class, mWindowManagerInternal);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);
        LocalServices.addService(InputMethodManagerInternal.class, mInputMethodManagerInternal);
        LocalServices.addService(InputManagerInternal.class, mInputManagerInternal);
        LocalServices.addService(ActivityTaskManagerInternal.class, mActivityTaskManagerInternal);
        if (android.app.appfunctions.flags.Flags.enableAppInteractionApi()) {
            LocalServices.addService(AppInteractionService.class, mAppInteractionService);
        }
        ViewConfiguration.setInstanceForTesting(mContext, mViewConfiguration);

        when(mUserManagerInternal.getMainDisplayAssignedToUser(anyInt()))
                .thenReturn(MAIN_DISPLAY_ID);
        when(mInputMethodManagerInternal
                .getComputerControlInputConnectionData(anyInt(), eq(VIRTUAL_DISPLAY_ID)))
                .thenReturn(new InputMethodManagerInternal.ComputerControlInputConnectionData(
                        mRemoteComputerControlInputConnection, mEditorInfo));

        DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = DISPLAY_WIDTH;
        displayInfo.logicalHeight = DISPLAY_HEIGHT;
        displayInfo.logicalDensityDpi = DISPLAY_DPI;
        displayInfo.rotation = Surface.ROTATION_0;
        when(mDisplayManager.getDisplayInfo(MAIN_DISPLAY_ID)).thenReturn(displayInfo);
        when(mDisplayManager.getDisplayInfo(VIRTUAL_DISPLAY_ID)).thenReturn(displayInfo);

        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any()))
                .thenReturn(mVirtualDevice);

        when(mVirtualDevice.createVirtualDisplay(any(), any(), any())).thenReturn(mVirtualDisplay);
        when(mVirtualDisplay.getDisplay()).thenReturn(mDisplay);
        when(mDisplay.getDisplayId()).thenReturn(VIRTUAL_DISPLAY_ID);

        when(mVirtualDevice.createVirtualTouchscreen(any())).thenReturn(mVirtualTouchscreen);
        when(mVirtualDevice.createVirtualDpad(any())).thenReturn(mVirtualDpad);
        when(mViewConfiguration.getLongPressTimeoutMillis()).thenReturn(1000);
        when(mVirtualDevice.createVirtualAudioDevice(any(), any(), any())).thenReturn(
                mVirtualAudioDevice);
        when(mVirtualAudioDevice.startAudioCapture(any())).thenReturn(mAudioCapture);
        when(mVirtualAudioDevice.startAudioInjection(any())).thenReturn(mAudioInjection);
        when(mAllowlistController.isPackageAutomatable(anyString(), any())).thenReturn(true);
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
        assertTrue(mVirtualDeviceParamsArgumentCaptor.getValue().isLocalDeviceOnly());
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
        assertThat(virtualDisplayConfig.isIgnoreActivitySizeRestrictions()).isTrue();

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

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static Integer[] getAllDisplayRotations() {
        return new Integer[]{
                Surface.ROTATION_0,
                Surface.ROTATION_90,
                Surface.ROTATION_180,
                Surface.ROTATION_270,
        };
    }

    @Parameters(method = "getAllDisplayRotations")
    @Test
    public void createSession_inRotatedDisplay_createsVirtualDisplayInNaturalOrientation(
            @Surface.Rotation int rotation)
            throws Exception {
        mDisplayManager.getDisplayInfo(MAIN_DISPLAY_ID).rotation = rotation;

        createComputerControlSession(mDefaultParams);

        verify(mVirtualDevice).createVirtualDisplay(
                mVirtualDisplayConfigArgumentCaptor.capture(), any(), any());
        VirtualDisplayConfig virtualDisplayConfig = mVirtualDisplayConfigArgumentCaptor.getValue();
        assertThat(virtualDisplayConfig.getDensityDpi()).isEqualTo(DISPLAY_DPI);
        switch (rotation) {
            case Surface.ROTATION_0, Surface.ROTATION_180 -> {
                assertThat(virtualDisplayConfig.getHeight()).isEqualTo(DISPLAY_HEIGHT);
                assertThat(virtualDisplayConfig.getWidth()).isEqualTo(DISPLAY_WIDTH);

            }
            case Surface.ROTATION_90, Surface.ROTATION_270 -> {
                assertThat(virtualDisplayConfig.getHeight()).isEqualTo(DISPLAY_WIDTH);
                assertThat(virtualDisplayConfig.getWidth()).isEqualTo(DISPLAY_HEIGHT);
            }
        }
    }

    @Test
    public void createSession_usesConfiguredDisplayDimensions() throws Exception {
        // Setup: Define a secondary display with a specific physical address and dimensions
        final long physicalAddress = 123456789L;
        final int secondaryDisplayId = 55;
        final int secondaryWidth = 1234;
        final int secondaryHeight = 5678;
        DisplayInfo secondaryInfo = new DisplayInfo();
        secondaryInfo.logicalWidth = secondaryWidth;
        secondaryInfo.logicalHeight = secondaryHeight;
        secondaryInfo.logicalDensityDpi = DISPLAY_DPI;
        secondaryInfo.address = DisplayAddress.fromPhysicalDisplayId(physicalAddress);
        when(mDisplayManager.getDisplayIds(true)).thenReturn(
                new int[]{MAIN_DISPLAY_ID, secondaryDisplayId});
        when(mDisplayManager.getDisplayInfo(secondaryDisplayId)).thenReturn(secondaryInfo);

        createComputerControlSession(mDefaultParams, GLOBAL_TIMEOUT_MILLIS,
                String.valueOf(physicalAddress));

        verify(mVirtualDevice).createVirtualDisplay(
                mVirtualDisplayConfigArgumentCaptor.capture(), any(), any());
        VirtualDisplayConfig config = mVirtualDisplayConfigArgumentCaptor.getValue();
        assertThat(config.getWidth()).isEqualTo(secondaryWidth);
        assertThat(config.getHeight()).isEqualTo(secondaryHeight);
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
    public void createSession_doesNotCreateKeyboard() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDeviceFactory).createVirtualDevice(
                eq(mAppToken), any(), mVirtualDeviceParamsArgumentCaptor.capture());
        verify(mVirtualDevice, never()).createVirtualKeyboard(any());
    }

    @Test
    public void createSession_canCloseSessionBeforeInitializing() throws Exception {
        createComputerControlSessionWithoutInitializing(mDefaultParams,
                GLOBAL_TIMEOUT_MILLIS, /* referenceDisplayAddress= */ null);
        mSession.close();

        verify(mOnClosedListener).accept(eq(mSession));
    }

    @Test
    public void createSession_virtualDeviceCreationFails_throwsException() {
        // Setup: Configure the virtual device factory to throw a RuntimeException upon creation.
        // This simulates a failure during virtual device creation, for example, due to invalid
        // parameters or a system service issue. The change being tested wraps this call in
        // Binder.withCleanCallingIdentity() to prevent SecurityExceptions, so testing general
        // exception propagation is important.
        final RuntimeException expectedException = new RuntimeException("Creation failed");
        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any()))
                .thenThrow(expectedException);

        // Action & Verification: Assert that creating the session throws the same exception,
        // ensuring that creation failures are propagated to the caller.
        final RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> createComputerControlSessionWithoutInitializing(
                        mDefaultParams, GLOBAL_TIMEOUT_MILLIS,
                        /* referenceDisplayAddress= */ null));
        assertThat(thrown).isEqualTo(expectedException);
    }

    @Test
    public void createSession_noActivityPolicy() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDevice, never()).setDevicePolicy(eq(POLICY_TYPE_ACTIVITY), anyInt());
        verify(mVirtualDevice, never()).addActivityPolicyExemption(
                any(ActivityPolicyExemption.class));
    }

    @Test
    public void createSession_startsWatchingAppOps() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mAppOpsManager).startWatchingMode(
                eq(AppOpsManager.OP_COMPUTER_CONTROL), any(), any());
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
    public void closeSession_stopsWatchingAppOps() throws Exception {
        createComputerControlSession(mDefaultParams);

        mSession.close();

        verify(mAppOpsManager).stopWatchingMode(mSession);
    }

    @Test
    public void closeSessionInternal_closesWithProvidedReason() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.close(123);
        verify(mLifecycleCallback).onClosed(eq(123));
    }

    @Test
    public void onOpChanged_modeIgnored_closesSession() throws Exception {
        // Create a session.
        createComputerControlSession(mDefaultParams);
        when(mAppOpsManager.checkOpNoThrow(eq(AppOpsManager.OPSTR_COMPUTER_CONTROL), anyInt(),
                any())).thenReturn(AppOpsManager.MODE_IGNORED);

        // onOpChanged callback triggered.
        mSession.onOpChanged(AppOpsManager.OPSTR_COMPUTER_CONTROL, mSession.getOwnerPackageName(),
                USER_ID);

        // Verify the session is closed.
        verify(mVirtualDevice).close();
        verify(mOnClosedListener).accept(mSession);
        verify(mLifecycleCallback).onClosed(eq(CLOSE_REASON_USER_INITIATED));
    }

    @Test
    public void onOpChanged_modeNotIgnored_doesNothing() throws Exception {
        // Create a session.
        createComputerControlSession(mDefaultParams);
        when(mAppOpsManager.checkOpNoThrow(eq(AppOpsManager.OPSTR_COMPUTER_CONTROL), anyInt(),
                any())).thenReturn(AppOpsManager.MODE_ALLOWED);

        // onOpChanged callback triggered.
        mSession.onOpChanged(AppOpsManager.OPSTR_COMPUTER_CONTROL, mSession.getOwnerPackageName(),
                USER_ID);

        // Verify the session is not closed.
        verify(mVirtualDevice, never()).close();
        verify(mOnClosedListener, never()).accept(mSession);
        verify(mLifecycleCallback, never()).onClosed(eq(CLOSE_REASON_USER_INITIATED));
    }

    @Test
    public void onOpChanged_differentPackage_doesNothing() throws Exception {
        // Create a session.
        createComputerControlSession(mDefaultParams);
        when(mAppOpsManager.checkOpNoThrow(eq(AppOpsManager.OPSTR_COMPUTER_CONTROL), anyInt(),
                any())).thenReturn(AppOpsManager.MODE_IGNORED);

        // onOpChanged callback triggered.
        mSession.onOpChanged(AppOpsManager.OPSTR_COMPUTER_CONTROL, UNDECLARED_TARGET_PACKAGE,
                USER_ID);

        // Verify the session is not closed.
        verify(mVirtualDevice, never()).close();
        verify(mOnClosedListener, never()).accept(mSession);
        verify(mLifecycleCallback, never()).onClosed(eq(CLOSE_REASON_USER_INITIATED));
    }

    @Test
    public void onOpChanged_differentUser_doesNothing() throws Exception {
        // Create a session.
        createComputerControlSession(mDefaultParams);
        when(mAppOpsManager.checkOpNoThrow(eq(AppOpsManager.OPSTR_COMPUTER_CONTROL), anyInt(),
                any())).thenReturn(AppOpsManager.MODE_IGNORED);

        // onOpChanged callback triggered.
        mSession.onOpChanged(AppOpsManager.OPSTR_COMPUTER_CONTROL, mSession.getOwnerPackageName(),
                USER_ID + 1);

        // Verify the session is not closed.
        verify(mVirtualDevice, never()).close();
        verify(mOnClosedListener, never()).accept(mSession);
        verify(mLifecycleCallback, never()).onClosed(eq(CLOSE_REASON_USER_INITIATED));
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
    public void launchApplication_packageNotAllowlisted_throws() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mOwnerPackageManager.queryIntentActivities(any(), any()))
                .thenReturn(List.of(new ResolveInfo()));
        when(mAllowlistController.isPackageAutomatable(anyString(), any())).thenReturn(false);

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
        verify(mVirtualTouchscreen, timeout(2 * TOUCH_EVENT_DELAY_MS))
                .sendTouchEvent(argThat(
                        new MatchesTouchEvent(180, 400, VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen, timeout(2 * TOUCH_EVENT_DELAY_MS))
                .sendTouchEvent(argThat(
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


    private enum CancelingAction {
        TAP {
            @Override
            void execute(ComputerControlSessionImplTest test, ComputerControlSessionImpl session)
                    throws Exception {
                session.tap(1, 1);
            }
        },
        INSERT_TEXT {
            @Override
            void execute(ComputerControlSessionImplTest test, ComputerControlSessionImpl session)
                    throws Exception {
                session.insertText("text", false, false);
            }
        },
        PERFORM_ACTION {
            @Override
            void execute(ComputerControlSessionImplTest test, ComputerControlSessionImpl session)
                    throws Exception {
                session.performAction(ComputerControlSession.ACTION_GO_BACK);
            }
        },
        LAUNCH_APPLICATION {
            @Override
            void execute(ComputerControlSessionImplTest test, ComputerControlSessionImpl session)
                    throws Exception {
                when(test.mOwnerPackageManager.queryIntentActivities(any(), any()))
                        .thenReturn(List.of(new ResolveInfo()));
                session.launchApplication(TARGET_PACKAGE_1, TARGET_CLASS);
            }
        },
        ENTERING_BLOCKED_STATE {
            @Override
            void execute(ComputerControlSessionImplTest test, ComputerControlSessionImpl session)
                    throws Exception {
                verify(test.mVirtualDevice).addActivityListener(any(),
                        test.mActivityListenerArgumentCaptor.capture());
                test.mActivityListenerArgumentCaptor.getValue()
                        .onSecureWindowShown(
                                VIRTUAL_DISPLAY_ID, TEST_COMPONENT, test.mUserHandle);
            }
        },
        CLOSE {
            @Override
            void execute(ComputerControlSessionImplTest test, ComputerControlSessionImpl session)
                    throws Exception {
                session.close();
            }
        };

        abstract void execute(
                ComputerControlSessionImplTest test, ComputerControlSessionImpl session)
            throws Exception;
    }

    private static List<CancelingAction> getCancelingActionsForSwipe() {
        return List.of(CancelingAction.values());
    }

    private static List<CancelingAction> getCancelingActionsForInsertText() {
        return List.of(
                CancelingAction.TAP,
                CancelingAction.PERFORM_ACTION,
                CancelingAction.LAUNCH_APPLICATION,
                CancelingAction.ENTERING_BLOCKED_STATE,
                CancelingAction.CLOSE);
    }

    @Test
    @Parameters(method = "getCancelingActionsForInsertText")
    public void action_cancelsOngoingInsertText(CancelingAction action) throws Exception {
        assertActionCancelsOngoingInsertText(session -> action.execute(this, session));
    }

    @Test
    @Parameters(method = "getCancelingActionsForSwipe")
    public void action_cancelsOngoingSwipe(CancelingAction action) throws Exception {
        assertActionCancelsOngoingSwipe(session -> action.execute(this, session));
    }

    @Test
    public void insertText_callsCommitTextOnAvailableInputConnection() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mSession.insertText("text", false /* replaceExisting */, false /* commit */);
        verify(mRemoteComputerControlInputConnection).commitText(any(), eq("text"), eq(1));
    }

    @Test
    public void insertTextWithReplaceExisting_callsReplaceTextOnAvailableInputConnection()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mSession.insertText("text", true /* replaceExisting */, false /* commit */);
        verify(mRemoteComputerControlInputConnection).replaceText(any(), eq(0),
                eq(Integer.MAX_VALUE), eq("text"), eq(1));
    }

    @Test
    public void insertTextWithCommit_ifNoDefaultEditorAction_sendsEnterKeyOnInputConnection()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mEditorInfo.imeOptions = EditorInfo.IME_ACTION_NONE;

        mSession.insertText("text", false /* replaceExisting */, true /* commit */);
        verify(mRemoteComputerControlInputConnection).commitText(any(), eq("text"), eq(1));
        verify(mRemoteComputerControlInputConnection, timeout(2 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                any(), argThat(new MatchesKeyEvent(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN)));
        verify(mRemoteComputerControlInputConnection, timeout(2 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                any(), argThat(new MatchesKeyEvent(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP)));
    }

    @Test
    public void insertTextWithCommit_performsDefaultEditorAction()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mEditorInfo.imeOptions = EditorInfo.IME_ACTION_DONE;

        mSession.insertText("text", false /* replaceExisting */, true /* commit */);
        verify(mRemoteComputerControlInputConnection).commitText(any(), eq("text"), eq(1));
        verify(mRemoteComputerControlInputConnection,
                timeout(2 * KEY_EVENT_DELAY_MS)).performEditorAction(any(),
                eq(EditorInfo.IME_ACTION_DONE));
        verify(mRemoteComputerControlInputConnection, never()).sendKeyEvent(any(), any());
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
    public void createInteractiveMirror_successfullyReturnsInitializedMirror()
            throws Exception {
        createComputerControlSession(mDefaultParams);
        final var displayMirror = mockDisplayMirror();
        when(mWindowManagerInternal.createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID))
                .thenReturn(displayMirror);

        final var returnedMirrorSurface = Mockito.mock(SurfaceControl.class);
        IInteractiveMirror mirror = mSession.createInteractiveMirror(returnedMirrorSurface);

        final var mirrorSurface = displayMirror.getMirrorSurfaceControl();
        verify(mWindowManagerInternal).createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID);
        assertThat(mirror).isNotNull();
        verify(mTransaction).reparent(eq(mirrorSurface), mSurfaceControlArgumentCaptor.capture());
        verify(returnedMirrorSurface).copyFrom(eq(mSurfaceControlArgumentCaptor.getValue()), any());
        assertThat(mSurfaceControlArgumentCaptor.getValue()).isNotEqualTo(mirrorSurface);
        verify(mTransaction).setDropInputMode(eq(mirrorSurface), eq(DropInputMode.ALL));
        verify(mInputManagerInternal).setForceShowTouchesOnDisplay(VIRTUAL_DISPLAY_ID, true);
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
    public void closeInteractiveMirror_removesMirrorSurface() throws Exception {
        createComputerControlSession(mDefaultParams);
        final var displayMirror = mockDisplayMirror();
        when(mWindowManagerInternal.createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID))
                .thenReturn(displayMirror);
        final var returnedMirrorSurface = Mockito.mock(SurfaceControl.class);
        IInteractiveMirror mirror = mSession.createInteractiveMirror(returnedMirrorSurface);
        assertThat(mirror).isNotNull();
        verify(mWindowManagerInternal).createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID);
        verify(returnedMirrorSurface).copyFrom(mSurfaceControlArgumentCaptor.capture(), any());
        clearInvocations(mTransaction);

        mirror.close();

        verify(displayMirror).close();
        verify(mTransaction).remove(mSurfaceControlArgumentCaptor.getValue());
        verify(mInputManagerInternal).setForceShowTouchesOnDisplay(VIRTUAL_DISPLAY_ID, false);
    }

    @Test
    public void closeSession_removesAllInteractiveMirrors() throws Exception {
        createComputerControlSession(mDefaultParams);
        final var displayMirror1 = mockDisplayMirror();
        when(mWindowManagerInternal.createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID))
                .thenReturn(displayMirror1);
        IInteractiveMirror mirror1 = mSession.createInteractiveMirror(new SurfaceControl());
        assertThat(mirror1).isNotNull();
        verify(mInputManagerInternal).setForceShowTouchesOnDisplay(VIRTUAL_DISPLAY_ID, true);
        final var displayMirror2 = mockDisplayMirror();
        when(mWindowManagerInternal.createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID))
                .thenReturn(displayMirror2);
        IInteractiveMirror mirror2 = mSession.createInteractiveMirror(new SurfaceControl());
        assertThat(mirror2).isNotNull();
        verify(mWindowManagerInternal, times(2)).createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID);
        clearInvocations(mTransaction);

        mSession.close();

        verify(displayMirror1).close();
        verify(displayMirror2).close();
        verify(mInputManagerInternal).setForceShowTouchesOnDisplay(VIRTUAL_DISPLAY_ID, false);
    }

    @Test
    public void duplicateCloseInteractiveMirrorCall_doesNothing() throws Exception {
        createComputerControlSession(mDefaultParams);
        final var displayMirror = mockDisplayMirror();
        when(mWindowManagerInternal.createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID))
                .thenReturn(displayMirror);
        final var returnedMirrorSurface = Mockito.mock(SurfaceControl.class);
        IInteractiveMirror mirror = mSession.createInteractiveMirror(returnedMirrorSurface);
        assertThat(mirror).isNotNull();
        mirror.close();
        clearInvocations(mTransaction, displayMirror);

        mirror.close();
        mirror.close();
        mSession.close();

        verifyNoInteractions(displayMirror, mTransaction);
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
    public void onSecureWindowHidden_exitsBlockedState_afterRequestUnblock()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TARGET_PACKAGE_1);
        clearInvocations(mVirtualDisplay, mLifecycleCallback);

        activityListener.onSecureWindowHidden(VIRTUAL_DISPLAY_ID);

        verify(mLifecycleCallback, never()).onActive();

        mSession.requestUnblock();

        verify(mLifecycleCallback).onActive();
    }

    @Test
    @EnableFlags(android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_INTERACTION_API)
    public void onActivityLaunchRequested_whenActivityIsAllowed_reportsAppInteraction()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice)
                .addActivityListener(any(), mActivityListenerArgumentCaptor.capture());

        mActivityListenerArgumentCaptor
                .getValue()
                .onActivityLaunchRequested(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);

        verify(mAppInteractionService)
                .noteAppInteraction(
                        eq(AGENT_PACKAGE),
                        eq(TEST_COMPONENT.getPackageName()),
                        eq(APP_INTERACTION_ATTRIBUTION),
                        anyLong(),
                        eq(USER_ID));
    }

    @Test
    @EnableFlags(android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_INTERACTION_API)
    public void onActivityLaunchRequested_whenActivityIsBlocked_doesNotReportAppInteraction()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice)
                .addActivityListener(any(), mActivityListenerArgumentCaptor.capture());

        mActivityListenerArgumentCaptor
                .getValue()
                .onActivityLaunchRequested(VIRTUAL_DISPLAY_ID, BLOCKED_COMPONENT, USER_ID);

        verify(mAppInteractionService, never())
                .noteAppInteraction(any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisableFlags(android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_INTERACTION_API)
    public void onActivityLaunchRequested_whenFlagIsDisabled_doesNotCrash()
            throws RemoteException {
        assertThat(LocalServices.getService(AppInteractionService.class)).isNull();

        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice)
                .addActivityListener(any(), mActivityListenerArgumentCaptor.capture());

        mActivityListenerArgumentCaptor
                .getValue()
                .onActivityLaunchRequested(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);
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
    public void onTopActivityChanged_toAllowedPackage_exitsBlockedState_afterRequestUnblock()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        activityListener.onActivityLaunchRequested(VIRTUAL_DISPLAY_ID, BLOCKED_COMPONENT, USER_ID);
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
                UNDECLARED_TARGET_PACKAGE);
        clearInvocations(mLifecycleCallback);

        activityListener.onTopActivityChanged(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);

        verify(mLifecycleCallback, never()).onActive();

        mSession.requestUnblock();

        verify(mLifecycleCallback).onActive();
    }

    @Test
    public void onTopActivityChanged_toDisallowedPackage_remainsBlocked_afterRequestUnblock()
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

        mSession.requestUnblock();

        verify(mLifecycleCallback, never()).onActive();
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
        verifyNoInteractions(mLifecycleCallback);
        mSession.requestUnblock();
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH,
                UNDECLARED_TARGET_PACKAGE);
        clearInvocations(mLifecycleCallback);

        // Unblock secure window, should remain blocked.
        activityListener.onSecureWindowHidden(VIRTUAL_DISPLAY_ID);
        mSession.requestUnblock();
        verifyNoInteractions(mLifecycleCallback);

        // Unblock activity, session becomes active on next unblock request.
        activityListener.onTopActivityChanged(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);
        verify(mLifecycleCallback, never()).onActive();

        mSession.requestUnblock();

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
        mSession.requestUnblock();
        verifyNoInteractions(mLifecycleCallback);

        // Unblock activity, block reason changes after unblock request.
        activityListener.onTopActivityChanged(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);
        verifyNoInteractions(mLifecycleCallback);
        mSession.requestUnblock();
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TARGET_PACKAGE_1);
        clearInvocations(mLifecycleCallback);

        // Unblock secure window, should remain blocked.
        activityListener.onSecureWindowHidden(VIRTUAL_DISPLAY_ID);
        verify(mLifecycleCallback, never()).onActive();

        mSession.requestUnblock();

        verify(mLifecycleCallback).onActive();
    }

    @Test
    public void requestUnblock_whenNotBlocked_doesNothing() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        clearInvocations(mLifecycleCallback);

        mSession.requestUnblock();

        verify(mLifecycleCallback, never()).onActive();
    }

    @Test
    public void requestUnblock_exitsCallerInitiatedBlock() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mSession.notifyBlocked();
        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_CALLER_INITIATED, null);
        clearInvocations(mLifecycleCallback);

        mSession.requestUnblock();

        verify(mLifecycleCallback).onActive();
    }

    @Test
    public void notifyBlocked_entersBlockedState() throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.notifyBlocked();

        verify(mLifecycleCallback).onBlocked(BLOCK_REASON_CALLER_INITIATED, null);
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
    public void notifyActivityListener_beforeInitialization_doesNotSetSurface() {
        createComputerControlSessionWithoutInitializing(mDefaultParams, GLOBAL_TIMEOUT_MILLIS,
                /* referenceDisplayAddress= */ null);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());

        mActivityListenerArgumentCaptor.getValue().onDisplayEmpty(VIRTUAL_DISPLAY_ID);
        verify(mVirtualDisplay, never()).setSurface(any());
    }

    @Test
    public void blockedState_doesNotUpdateDisplaySurface() throws Exception {
        createComputerControlSession(mDefaultParams);
        clearInvocations(mVirtualDisplay);

        try (InBlockedState inBlockedState = new InBlockedState()) {
            // Entering the blocked state should not trigger a new surface update
            verify(mVirtualDisplay, never()).setSurface(any());
        }
        verify(mVirtualDisplay, never()).setSurface(any());
    }

    @Test
    public void activeState_allowsAllInteractions() throws Exception {
        createComputerControlSession(mDefaultParams);

        final var interactionDevices = List.of(
                mVirtualDpad,
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

    @Test
    public void handOverApplications_closesSession() throws Exception {
        createComputerControlSession(mDefaultParams);

        mSession.handOverApplications();

        verify(mLifecycleCallback).onClosed(CLOSE_REASON_SESSION_EMPTY);
    }

    @Test
    public void displayEmpty_closesSessionAfterTimeout() throws Exception {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();

        activityListener.onDisplayEmpty(VIRTUAL_DISPLAY_ID);

        verify(mLifecycleCallback, never()).onClosed(anyInt());
        verify(mLifecycleCallback, timeout(CLOSE_ON_DISPLAY_EMPTY_TIMEOUT_MS * 2)).onClosed(
                CLOSE_REASON_SESSION_EMPTY);
    }

    @Test
    public void transientDisplayEmpty_doesNotCloseSession() throws Exception {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();

        activityListener.onDisplayEmpty(VIRTUAL_DISPLAY_ID);
        activityListener.onTopActivityChanged(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, USER_ID);

        verify(mLifecycleCallback,
                timeout(CLOSE_ON_DISPLAY_EMPTY_TIMEOUT_MS * 2).times(0))
                .onClosed(anyInt());
    }

    @Test
    public void requestScreenshot_enablesHardwareRendererOutput() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mWindowManagerInternal.requestHardwareRendererOutputEnabled(anyInt(), anyLong(), any(),
                any())).thenReturn(true);

        boolean result = mSession.requestScreenshot();

        assertThat(result).isTrue();
        verify(mWindowManagerInternal).requestHardwareRendererOutputEnabled(eq(VIRTUAL_DISPLAY_ID),
                eq(1000L), any(), any());
    }

    @Test
    public void requestScreenshot_inBlockedState_returnsTrue() throws Exception {
        createComputerControlSession(mDefaultParams);

        when(mWindowManagerInternal.requestHardwareRendererOutputEnabled(
                anyInt(), anyLong(), any(), any())).thenReturn(true);

        try (InBlockedState inBlockedState = new InBlockedState()) {
            boolean result = mSession.requestScreenshot();
            assertThat(result).isTrue();
            verify(mWindowManagerInternal).requestHardwareRendererOutputEnabled(
                    eq(VIRTUAL_DISPLAY_ID), anyLong(), any(), any());
        }
    }

    @Test
    public void requestScreenshot_alreadyWaiting_returnsFalse() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mWindowManagerInternal.requestHardwareRendererOutputEnabled(anyInt(), anyLong(), any(),
                any())).thenReturn(true);

        mSession.requestScreenshot();
        boolean result = mSession.requestScreenshot();

        assertThat(result).isFalse();
        verify(mWindowManagerInternal, times(1)).requestHardwareRendererOutputEnabled(
                anyInt(), anyLong(), any(), any());
    }

    @Test
    public void requestScreenshot_callbackDisablesHardwareRendererOutput() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mWindowManagerInternal.requestHardwareRendererOutputEnabled(anyInt(), anyLong(), any(),
                any())).thenReturn(true);

        mSession.requestScreenshot();

        verify(mWindowManagerInternal).requestHardwareRendererOutputEnabled(anyInt(), anyLong(),
                mWindowsDrawnCallbackCaptor.capture(), any());

        Consumer<Boolean> callback = mWindowsDrawnCallbackCaptor.getValue();
        callback.accept(true);

        verify(mWindowManagerInternal)
                .requestHardwareRendererOutputDisabled(eq(VIRTUAL_DISPLAY_ID));

        // Should be able to request again
        mSession.requestScreenshot();
        verify(mWindowManagerInternal, times(2)).requestHardwareRendererOutputEnabled(
                anyInt(), anyLong(), any(), any());
    }

    @Test
    public void requestScreenshot_withInteractiveMirror_doesNotDisableHardwareRendererOutput()
            throws Exception {
        createComputerControlSession(mDefaultParams);
        when(mWindowManagerInternal.requestHardwareRendererOutputEnabled(anyInt(), anyLong(), any(),
                any())).thenReturn(true);
        setupMockMirror();

        IInteractiveMirror mirror = mSession.createInteractiveMirror(new SurfaceControl());
        assertThat(mirror).isNotNull();

        mSession.requestScreenshot();
        verify(mWindowManagerInternal, times(2)).requestHardwareRendererOutputEnabled(
                anyInt(), anyLong(), mWindowsDrawnCallbackCaptor.capture(), any());

        Consumer<Boolean> callback = mWindowsDrawnCallbackCaptor.getValue();
        callback.accept(true);

        verify(mWindowManagerInternal, never()).requestHardwareRendererOutputDisabled(anyInt());
    }

    @Test
    public void createInteractiveMirror_enablesHardwareRendererOutput() throws Exception {
        createComputerControlSession(mDefaultParams);
        setupMockMirror();

        mSession.createInteractiveMirror(new SurfaceControl());

        verify(mWindowManagerInternal).requestHardwareRendererOutputEnabled(eq(VIRTUAL_DISPLAY_ID),
                eq(0L), any(), any());
    }

    @Test
    public void closeInteractiveMirror_disablesHardwareRendererOutput() throws Exception {
        createComputerControlSession(mDefaultParams);
        setupMockMirror();
        IInteractiveMirror mirror = mSession.createInteractiveMirror(new SurfaceControl());
        clearInvocations(mWindowManagerInternal);

        mirror.close();

        verify(mWindowManagerInternal)
                .requestHardwareRendererOutputDisabled(eq(VIRTUAL_DISPLAY_ID));
    }

    @Test
    public void closeInteractiveMirror_pendingScreenshot_doesNotDisableHardwareRendererOutput()
            throws Exception {
        createComputerControlSession(mDefaultParams);
        when(mWindowManagerInternal.requestHardwareRendererOutputEnabled(anyInt(), anyLong(), any(),
                any())).thenReturn(true);
        setupMockMirror();

        IInteractiveMirror mirror = mSession.createInteractiveMirror(new SurfaceControl());
        mSession.requestScreenshot();
        clearInvocations(mWindowManagerInternal);

        mirror.close();

        verify(mWindowManagerInternal, never()).requestHardwareRendererOutputDisabled(anyInt());
    }

    @Test
    public void updatePowerState_blockedState_sleepsDevice() throws Exception {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        clearInvocations(mVirtualDevice);

        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);

        verify(mVirtualDevice).goToSleep();
    }

    @Test
    public void updatePowerState_activeState_wakesDevice() throws Exception {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        // Transition to blocked first to ensure we can wake up.
        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        verify(mVirtualDevice).goToSleep();
        clearInvocations(mVirtualDevice);

        activityListener.onSecureWindowHidden(VIRTUAL_DISPLAY_ID);
        mSession.requestUnblock();

        verify(mVirtualDevice).wakeUp();
    }

    @Test
    public void updatePowerState_blockedState_withMirror_doesNotSleep() throws Exception {
        createComputerControlSession(mDefaultParams);
        setupMockMirror();
        mSession.createInteractiveMirror(new SurfaceControl());
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        clearInvocations(mVirtualDevice);

        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);

        verify(mVirtualDevice, never()).goToSleep();
    }

    @Test
    public void updatePowerState_addMirrorWhileBlocked_wakesDevice() throws Exception {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        verify(mVirtualDevice).goToSleep();
        clearInvocations(mVirtualDevice);

        setupMockMirror();
        mSession.createInteractiveMirror(new SurfaceControl());

        verify(mVirtualDevice).wakeUp();
    }

    @Test
    public void updatePowerState_removeMirrorWhileBlocked_sleepsDevice() throws Exception {
        createComputerControlSession(mDefaultParams);
        setupMockMirror();
        IInteractiveMirror mirror = mSession.createInteractiveMirror(new SurfaceControl());
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        clearInvocations(mVirtualDevice);

        mirror.close();

        verify(mVirtualDevice).goToSleep();
    }

    @Test
    public void updatePowerState_closeSession_doesNotChangePowerState() throws Exception {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice).addActivityListener(any(),
                mActivityListenerArgumentCaptor.capture());
        final var activityListener = mActivityListenerArgumentCaptor.getValue();
        // Block to sleep
        activityListener.onSecureWindowShown(VIRTUAL_DISPLAY_ID, TEST_COMPONENT, mUserHandle);
        verify(mVirtualDevice).goToSleep();
        clearInvocations(mVirtualDevice);

        mSession.close();

        verify(mVirtualDevice, never()).wakeUp();
        verify(mVirtualDevice, never()).goToSleep();
    }

    private void setupMockMirror() {
        WindowManagerInternal.DisplayMirror displayMirror = mockDisplayMirror();
        when(mWindowManagerInternal.createMirrorForDisplayContent(VIRTUAL_DISPLAY_ID))
                .thenReturn(displayMirror);
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
            clearInvocations(mLifecycleCallback);
        }
    }

    private void createComputerControlSession(ComputerControlSessionParams params) {
        createComputerControlSession(params, GLOBAL_TIMEOUT_MILLIS);
    }

    private void createComputerControlSession(
            ComputerControlSessionParams params, long globalSessionTimeoutDurationMs) {
        createComputerControlSession(params,
                globalSessionTimeoutDurationMs, /* referenceDisplayAddress = */ null);
    }

    private void createComputerControlSession(
            ComputerControlSessionParams params, long globalSessionTimeoutDurationMs,
            String referenceDisplayAddress) {
        createComputerControlSessionWithoutInitializing(params, globalSessionTimeoutDurationMs,
                referenceDisplayAddress);
        mSession.initialize(mLifecycleCallback, mClientSurface);
    }

    private void createComputerControlSessionWithoutInitializing(
            ComputerControlSessionParams params, long globalSessionTimeoutDurationMs,
            String referenceDisplayAddress) {
        DisplayManagerGlobal displayManagerGlobal = new DisplayManagerGlobal(mDisplayManager);
        displayManagerGlobal.disableLocalDisplayInfoCaches();
        mSession = new ComputerControlSessionImpl(
                mContext, displayManagerGlobal, mAllowlistController, mViewConfiguration,
                globalSessionTimeoutDurationMs, () -> mTransaction, mAppToken, params, mAppThread,
                new AttributionSource(UserHandle.getUid(USER_ID, 0), AGENT_PACKAGE,
                        ATTRIBUTION_TAG),
                mVirtualDeviceFactory, mOnClosedListener, Runnable::run, referenceDisplayAddress);
    }

    private void assertActionCancelsOngoingSwipe(Interactor action) throws Exception {
        createComputerControlSession(mDefaultParams);

        // Start a swipe, which is asynchronous.
        mSession.swipe(100, 200, 300, 400);

        // Perform the action.
        action.interact(mSession);

        // Verify the ongoing swipe was cancelled.
        verify(mVirtualTouchscreen).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));
    }

    private void assertActionCancelsOngoingInsertText(Interactor action) throws Exception {
        createComputerControlSession(mDefaultParams);
        mEditorInfo.imeOptions = EditorInfo.IME_ACTION_DONE;

        // Start an insertText with a delayed commit action.
        mSession.insertText("text", false /* replaceExisting */, true /* commit */);

        // Perform the action.
        action.interact(mSession);

        // Verify the commit action was cancelled.
        verify(mRemoteComputerControlInputConnection, after(KEY_EVENT_DELAY_MS * 2).never())
                .performEditorAction(any(), anyInt());
        verify(mRemoteComputerControlInputConnection, never()).sendKeyEvent(any(), any());
    }

    @SuppressLint("MissingPermission")
    private void assertLaunchedApplication(String packageName) {
        // Verifying resolution.
        verify(mOwnerPackageManager).queryIntentActivities(mIntentArgumentCaptor.capture(), any());
        assertLaunchIntent(mIntentArgumentCaptor.getValue(), packageName);
        // Verifying start.
        verify(mActivityTaskManagerInternal).startActivityAsUser(
                eq(mAppThread), eq(AGENT_PACKAGE), eq(ATTRIBUTION_TAG),
                mIntentArgumentCaptor.capture(), isNull(), eq(Intent.FLAG_ACTIVITY_NEW_TASK),
                mBundleArgumentCaptor.capture(), eq(USER_ID));
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

        @Override
        public String toString() {
            return "MatchesTouchEvent{"
                    + "mX=" + mX
                    + ", mY=" + mY
                    + ", mAction=" + mAction
                    + ", mToolType=" + mToolType
                    + '}';
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

    private static WindowManagerInternal.DisplayMirror mockDisplayMirror() {
        final var mirror = Mockito.mock(WindowManagerInternal.DisplayMirror.class);
        when(mirror.getMirrorSurfaceControl()).thenReturn(new SurfaceControl());
        return mirror;
    }
}
