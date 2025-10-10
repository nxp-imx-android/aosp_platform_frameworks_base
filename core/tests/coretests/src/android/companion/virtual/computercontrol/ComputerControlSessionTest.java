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

package android.companion.virtual.computercontrol;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.os.RemoteException;
import android.util.Size;
import android.view.DisplayInfo;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.IAccessibilityManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionTest {

    private static final int DISPLAY_ID = 42;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final String TARGET_PACKAGE = "com.android.foo";
    private static final String TARGET_CLASS = "com.android.foo.FooActivity";

    @Mock
    private IComputerControlSession mMockSession;
    @Mock
    private IDisplayManager mDisplayManager;
    @Mock
    private IAccessibilityManager mAccessibilityManager;
    @Mock
    private IVirtualDisplayCallback mVirtualDisplayCallback;
    @Mock
    private IInteractiveMirror mMockInteractiveMirror;
    @Mock
    private Runnable mMockOnClosedRunnable;

    private ComputerControlSession mSession;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() throws RemoteException {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = WIDTH;
        displayInfo.logicalHeight = HEIGHT;
        when(mDisplayManager.getDisplayInfo(DISPLAY_ID)).thenReturn(displayInfo);

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AccessibilityManager accessibilityManager = new AccessibilityManager(
                context, context.getMainThreadHandler(), mAccessibilityManager, 0, true);

        mSession = new ComputerControlSession(DISPLAY_ID, mVirtualDisplayCallback, mMockSession,
                accessibilityManager, mMockOnClosedRunnable,
                new DisplayManagerGlobal(mDisplayManager));
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void performAction_performsAction() throws RemoteException {
        mSession.performAction(ComputerControlSession.ACTION_GO_BACK);
        verify(mMockSession).performAction(eq(ComputerControlSession.ACTION_GO_BACK));
    }

    @Test
    public void insertText_insertsText() throws RemoteException {
        mSession.insertText("text", true, true);
        verify(mMockSession).insertText(eq("text"), eq(true), eq(true));
    }

    @Test
    public void createInteractiveMirror_returns() throws RemoteException {
        when(mMockSession.createInteractiveMirror(any()))
                .thenReturn(mMockInteractiveMirror);
        InteractiveMirror mirror = mSession.createInteractiveMirror();
        assertThat(mirror).isNotNull();
    }

    @Test
    public void getDisplaySize_returns() {
        assertThat(mSession.getDisplaySize()).isEqualTo(new Size(WIDTH, HEIGHT));
    }

    @Test
    public void close_closesSession() throws RemoteException {
        mSession.close();
        verify(mMockSession).close();
    }

    @Test
    public void launchApplication_launchesApplication() throws RemoteException {
        mSession.launchApplication(TARGET_PACKAGE);
        verify(mMockSession).launchApplication(TARGET_PACKAGE, null);
    }

    @Test
    public void launchApplication_withComponentName_launchesApplication() throws RemoteException {
        mSession.launchApplication(new ComponentName(TARGET_PACKAGE, TARGET_CLASS));
        verify(mMockSession).launchApplication(TARGET_PACKAGE, TARGET_CLASS);
    }

    @Test
    public void handOverApplications_handsOverApplications() throws RemoteException {
        mSession.handOverApplications();
        verify(mMockSession).handOverApplications();
    }

    @Test
    public void tap_taps() throws RemoteException {
        mSession.tap(1, 2);
        verify(mMockSession).tap(eq(1), eq(2));
    }

    @Test
    public void tapNotInRange_throws() {
        assertThrows(IllegalArgumentException.class, () -> mSession.tap(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> mSession.tap(1, -2));
    }

    @Test
    public void swipe_swipes() throws RemoteException {
        mSession.swipe(1, 2, 3, 4);
        verify(mMockSession).swipe(eq(1), eq(2), eq(3), eq(4));
    }

    @Test
    public void swipeNotInRange_throws() {
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(-1, 2, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, -2, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, 2, -3, 4));
        assertThrows(IllegalArgumentException.class, () -> mSession.swipe(1, 2, 3, -4));
    }

    @Test
    public void longPress_longPresses() throws RemoteException {
        mSession.longPress(1, 2);
        verify(mMockSession).longPress(eq(1), eq(2));
    }

    @Test
    public void longPressNotInRange_throws() {
        assertThrows(IllegalArgumentException.class, () -> mSession.longPress(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> mSession.longPress(1, -2));
    }

    @Test
    public void setLifecycleCallback_providesLifecycleCallbacks() throws RemoteException {
        ComputerControlSession.LifecycleCallback mockCallback = Mockito.mock(
                ComputerControlSession.LifecycleCallback.class);

        mSession.setLifecycleCallback(new TestExecutor(), mockCallback);
        ArgumentCaptor<IComputerControlLifecycleCallback> lifecycleCallbackCaptor =
                ArgumentCaptor.forClass(IComputerControlLifecycleCallback.class);
        verify(mMockSession).setLifecycleCallback(lifecycleCallbackCaptor.capture());

        lifecycleCallbackCaptor.getValue().onClosed(123);
        verify(mockCallback).onClosed(eq(123));
        verify(mMockOnClosedRunnable).run();
    }

    @Test
    public void attachNotificationInfo_attachesNotificationInfo() throws RemoteException {
        final int notificationId = 5;
        final String notificationTag = "hello";
        mSession.attachNotificationInfo(notificationId, notificationTag);
        verify(mMockSession).attachNotificationInfo(eq(notificationId), eq(notificationTag));
    }

    @Test
    public void clearLifecycleCallback_stopsLifecycleCallbacks() throws RemoteException {
        ComputerControlSession.LifecycleCallback mockCallback = Mockito.mock(
                ComputerControlSession.LifecycleCallback.class);
        mSession.setLifecycleCallback(new TestExecutor(), mockCallback);
        ArgumentCaptor<IComputerControlLifecycleCallback> lifecycleCallbackCaptor =
                ArgumentCaptor.forClass(IComputerControlLifecycleCallback.class);
        verify(mMockSession).setLifecycleCallback(lifecycleCallbackCaptor.capture());

        mSession.clearLifecycleCallback();

        lifecycleCallbackCaptor.getValue().onClosed(123);
        verify(mockCallback, never()).onClosed(anyInt());
        verify(mMockOnClosedRunnable).run();
    }

    /**
     * A mock Executor that runs the runnable immediately on the calling thread.
     */
    private static final class TestExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
