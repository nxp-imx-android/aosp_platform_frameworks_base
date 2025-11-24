/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.inputmethod;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethodInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.inputmethod.InputBindResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class InputMethodBindingControllerTest extends InputMethodManagerServiceTestBase {

    private static final String PACKAGE_NAME = "com.android.frameworks.inputmethodtests";
    private static final String TEST_SERVICE_NAME =
            "com.android.server.inputmethod.InputMethodBindingControllerTest"
                    + "$EmptyInputMethodService";
    private static final String TEST_IME_ID = PACKAGE_NAME + "/" + TEST_SERVICE_NAME;
    private static final long TIMEOUT_IN_SECONDS = 5L * Build.HW_TIMEOUT_MULTIPLIER;

    private InputMethodBindingController mBindingController;
    private Instrumentation mInstrumentation;
    private final int mImeConnectionBindFlags =
            InputMethodBindingController.IME_CONNECTION_BIND_FLAGS
                    & ~Context.BIND_SCHEDULE_LIKE_TOP_APP
                    & ~Context.BIND_ALMOST_PERCEPTIBLE;
    private final long mImeBackgroundBindFlags =
            InputMethodBindingController.IME_BACKGROUND_BIND_FLAGS & ~Context.BIND_ALLOW_FREEZE;

    private final DeviceFlagsValueProvider mFlagsValueProvider = new DeviceFlagsValueProvider();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule(mFlagsValueProvider);

    public static class EmptyInputMethodService extends InputMethodService {}

    @Before
    public void setUp() throws RemoteException {
        super.setUp();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        // Remove flag Context.BIND_SCHEDULE_LIKE_TOP_APP and Context.BIND_ALLOW_FREEZE because in
        // tests we are not calling from system.
        synchronized (ImfLock.class) {
            mBindingController = new InputMethodBindingController(mUserId,
                    mInputMethodManagerService, mImeConnectionBindFlags, mImeBackgroundBindFlags);
        }
    }

    @After
    public void tearDown() {
        super.tearDown();
        synchronized (ImfLock.class) {
            mBindingController.unbindCurrentMethod();
        }
    }

    @Test
    public void testBindCurrentMethod_noIme() {
        synchronized (ImfLock.class) {
            mBindingController.setSelectedMethodId(null);
            InputBindResult result = mBindingController.bindCurrentMethod();
            assertThat(result).isEqualTo(InputBindResult.NO_IME);
        }
    }

    @Test
    public void testBindCurrentMethod_unknownId() {
        synchronized (ImfLock.class) {
            mBindingController.setSelectedMethodId("unknown ime id");
        }
        assertThrows(IllegalArgumentException.class, () -> {
            synchronized (ImfLock.class) {
                mBindingController.bindCurrentMethod();
            }
        });
    }

    @Test
    public void testBindCurrentMethod_notConnected() {
        synchronized (ImfLock.class) {
            mBindingController.setSelectedMethodId(TEST_IME_ID);
            doReturn(false)
                    .when(mContext)
                    .bindServiceAsUser(
                            any(Intent.class),
                            any(ServiceConnection.class),
                            anyInt(),
                            any(UserHandle.class));
            doNothing().when(mContext).unbindService(any(ServiceConnection.class));

            InputBindResult result = mBindingController.bindCurrentMethod();
            assertThat(result).isEqualTo(InputBindResult.IME_NOT_CONNECTED);
            verify(mContext, times(1)).unbindService(any(ServiceConnection.class));
        }
    }

    /**
     * Verifies the controller state after binding both the main visible connections, and then
     * unbinding both.
     */
    @Test
    public void testBindAndUnbindMethod() throws Exception {
        // Bind with main connection
        testBindCurrentMethodWithMainConnection(false /* wasBound */);

        // Bind with visible connection
        testBindCurrentMethodWithVisibleConnection();

        // Unbind both main and visible connections
        testUnbindCurrentMethod(true /* isVisible */);
    }

    /**
     * Verifies the controller state after binding both the main and visible connections,
     * then setting it inactive, and then setting it active again.
     */
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @Test
    public void testBindAndSetInactiveAndSetActive() throws Exception {
        // Bind with main connection
        testBindCurrentMethodWithMainConnection(false /* wasBound */);

        // Bind with visible connection
        testBindCurrentMethodWithVisibleConnection();

        verifySetInactiveWhileBound(true /* isVisible */);

        verifySetActiveWhileBound(true /* wasBoundBeforeInactive */);
    }

    /**
     * Verifies the controller state after setting it inactive, then setting it active, and then
     * binding the main connection.
     */
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @Test
    public void testSetInactiveAndSetActiveAndBind() throws Exception {
        verifySetInactiveWhileNotBound();

        verifySetActiveWhileNotBound(false /* wasUnbound */);

        testBindCurrentMethodWithMainConnection(false /* wasBound */);
    }

    /**
     * Verifies the controller state after setting it inactive, then binding the main connection,
     * and then setting it active
     */
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @Test
    public void testSetInactiveAndBindAndSetActive() throws Exception {
        verifySetInactiveWhileNotBound();

        testBindWhileNotActive();

        verifySetActiveWhileNotBound(false /* wasBoundBeforeInactive */);

        testBindCurrentMethodWithMainConnection(false /* wasBound */);
    }

    /**
     * Verifies the controller state after binding the main connection, setting it inactive,
     * unbinding the main connection, then setting it active, and then binding the main connection
     * again.
     */
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @Test
    public void testBindAndSetInactiveAndUnbindAndSetActiveAndBind() throws Exception {
        testBindCurrentMethodWithMainConnection(false /* wasBound */);

        verifySetInactiveWhileBound(false /* isVisible */);

        testUnbindCurrentMethod(false /* isVisible */);

        verifySetActiveWhileNotBound(true /* wasUnbound */);

        testBindCurrentMethodWithMainConnection(true /* wasBound */);
    }

    /**
     * Verifies the state after setting the controller inactive while it has the main connection.
     *
     * @param isVisible whether the controller has the visible connection.
     */
    private void verifySetInactiveWhileBound(boolean isVisible) {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setInactive();
            }
        });

        synchronized (ImfLock.class) {
            // Set inactive but keep inactive binding.
            assertThat(mBindingController.hasBackgroundConnection()).isTrue();
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.isVisibleBound()).isFalse();
            assertThat(mBindingController.isActive()).isFalse();
            // Unbind visible connection and main connection.
            if (isVisible) {
                verify(mContext, times(2)).unbindService(any(ServiceConnection.class));
            } else {
                verify(mContext, times(1)).unbindService(any(ServiceConnection.class));
            }
            verify(mMockWindowManagerInternal, times(1)).setImeWindowToken(
                    eq(null) /* token */, eq(mBindingController.getCurTokenDisplayId()));
            assertThat(mBindingController.getCurToken()).isNotNull();
            assertThat(mBindingController.getCurId()).isNotNull();
            assertThat(mBindingController.getCurMethod()).isNotNull();
            assertThat(mBindingController.getCurMethodUid()).isNotEqualTo(Process.INVALID_UID);
        }
    }

    /**
     * Verifies the state after setting the controller inactive while it does not have the main
     * connection.
     */
    private void verifySetInactiveWhileNotBound() {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setInactive();
            }
        });

        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasBackgroundConnection()).isFalse();
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.isVisibleBound()).isFalse();
            assertThat(mBindingController.isActive()).isFalse();
            verify(mContext, never()).unbindService(any(ServiceConnection.class));
            verify(mMockWindowManagerInternal, never()).setImeWindowToken(
                    any(IBinder.class) /* token */, anyInt() /* displayId */);
            assertThat(mBindingController.getCurToken()).isNull();
            assertThat(mBindingController.getCurId()).isNull();
            assertThat(mBindingController.getCurMethod()).isNull();
            assertThat(mBindingController.getCurMethodUid()).isEqualTo(Process.INVALID_UID);
        }
    }

    /**
     * Verifies the state after setting the controller active while it has the main connection.
     *
     * @param wasBoundBeforeInactive whether the controller previously had the main connection
     *                               before it was made inactive.
     */
    private void verifySetActiveWhileBound(boolean wasBoundBeforeInactive) {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setActive();
            }
        });

        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasBackgroundConnection()).isTrue();
            assertThat(mBindingController.hasMainConnection()).isTrue();
            assertThat(mBindingController.isVisibleBound()).isFalse();
            assertThat(mBindingController.isActive()).isTrue();
            if (wasBoundBeforeInactive) {
                // No further unbinds (just two from previous setInactive).
                verify(mContext, times(2)).unbindService(any(ServiceConnection.class));
                // Binds main connection again.
                verify(mContext, times(2)).bindServiceAsUser(
                        any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                        eq(mImeConnectionBindFlags) /* flags */, any(UserHandle.class) /* user */);
                // ImeToken is first set when bound, and set again when made active.
                verify(mMockWindowManagerInternal, times(2)).setImeWindowToken(
                        eq(mBindingController.getCurToken()),
                        eq(mBindingController.getCurTokenDisplayId()));
            } else {
                verify(mContext, never()).unbindService(any(ServiceConnection.class));
                // No further binds from setActive (just one from previous binding).
                verify(mContext, times(1)).bindServiceAsUser(
                        any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                        eq(mImeConnectionBindFlags) /* flags */, any(UserHandle.class) /* user */);
                verify(mMockWindowManagerInternal, times(1)).setImeWindowToken(
                        eq(mBindingController.getCurToken()),
                        eq(mBindingController.getCurTokenDisplayId()));
            }
            assertThat(mBindingController.getCurToken()).isNotNull();
            assertThat(mBindingController.getCurId()).isNotNull();
            assertThat(mBindingController.getCurMethod()).isNotNull();
            assertThat(mBindingController.getCurMethodUid()).isNotEqualTo(Process.INVALID_UID);
        }
    }

    /**
     * Verifies the state after setting the controller active while it does not have the main
     * connection.
     *
     * @param wasUnbound whether the controller previously had the main connection, and then had it
     *                   unbound.
     */
    private void verifySetActiveWhileNotBound(boolean wasUnbound) {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setActive();
            }
        });

        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.isVisibleBound()).isFalse();
            assertThat(mBindingController.isActive()).isTrue();
            if (wasUnbound) {
                // Unbound main connection and background connection.
                verify(mContext, times(2)).unbindService(any(ServiceConnection.class));
            } else {
                verify(mContext, never()).unbindService(any(ServiceConnection.class));
                verify(mContext, never()).bindServiceAsUser(
                        any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                        anyInt() /* flags */, any(UserHandle.class) /* user */);
                verify(mMockWindowManagerInternal, never()).setImeWindowToken(
                        any(IBinder.class) /* token */, anyInt() /* displayId */);
            }
            assertThat(mBindingController.getCurToken()).isNull();
            assertThat(mBindingController.getCurId()).isNull();
            assertThat(mBindingController.getCurMethod()).isNull();
            assertThat(mBindingController.getCurMethodUid()).isEqualTo(Process.INVALID_UID);
        }
    }

    private void testBindCurrentMethodWithMainConnection(boolean wasBound) throws Exception {
        final InputMethodInfo info;
        synchronized (ImfLock.class) {
            mBindingController.setDisplayIdToShowIme(DEFAULT_DISPLAY);
            mBindingController.setSelectedMethodId(TEST_IME_ID);
            info = InputMethodSettingsRepository.get(mUserId).getMethodMap().get(TEST_IME_ID);
        }
        assertThat(info).isNotNull();
        assertThat(info.getId()).isEqualTo(TEST_IME_ID);
        assertThat(info.getServiceName()).isEqualTo(TEST_SERVICE_NAME);

        // Bind input method with main connection. It is called on another thread because we should
        // wait for onServiceConnected() to finish.
        final var latch = new CountDownLatch(1);
        InputBindResult result = callOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setLatchForTesting(latch);
                return mBindingController.bindCurrentMethod();
            }
        });

        if (wasBound) {
            verify(mContext, times(2)).bindServiceAsUser(
                    any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                    eq(mImeConnectionBindFlags) /* flags */, any(UserHandle.class) /* user */);
        } else {
            verify(mContext, times(1)).bindServiceAsUser(
                    any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                    eq(mImeConnectionBindFlags) /* flags */, any(UserHandle.class) /* user */);
        }
        assertThat(result.result).isEqualTo(InputBindResult.ResultCode.SUCCESS_WAITING_IME_BINDING);
        assertThat(result.id).isEqualTo(info.getId());
        synchronized (ImfLock.class) {
            if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)) {
                assertThat(mBindingController.hasBackgroundConnection()).isTrue();
            }
            assertThat(mBindingController.hasMainConnection()).isTrue();
            assertThat(mBindingController.getCurId()).isEqualTo(info.getId());
            assertThat(mBindingController.getCurToken()).isNotNull();
            assertThat(mBindingController.getCurTokenDisplayId()).isEqualTo(DEFAULT_DISPLAY);
            if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)) {
                verify(mMockWindowManagerInternal, times(1)).addImeWindowToken(
                        eq(mBindingController.getCurToken()),
                        eq(mBindingController.getCurTokenDisplayId()),
                        eq(mBindingController.getUserId()), eq(null) /* options */);
                verify(mMockWindowManagerInternal, times(1)).setImeWindowToken(
                        eq(mBindingController.getCurToken()),
                        eq(mBindingController.getCurTokenDisplayId()));
            } else {
                verify(mMockWindowManagerInternal, times(1)).addWindowToken(
                        eq(mBindingController.getCurToken()), eq(TYPE_INPUT_METHOD),
                        eq(mBindingController.getCurTokenDisplayId()), eq(null) /* options */);
            }
        }
        // Wait for onServiceConnected()
        boolean completed = latch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            fail("Timed out waiting for onServiceConnected()");
        }

        // Verify onServiceConnected() is called and bound successfully.
        synchronized (ImfLock.class) {
            assertThat(mBindingController.getCurMethod()).isNotNull();
            assertThat(mBindingController.getCurMethodUid()).isNotEqualTo(Process.INVALID_UID);
        }
    }

    private void testBindWhileNotActive() throws Exception {
        final InputMethodInfo info;
        synchronized (ImfLock.class) {
            mBindingController.setDisplayIdToShowIme(DEFAULT_DISPLAY);
            mBindingController.setSelectedMethodId(TEST_IME_ID);
            info = InputMethodSettingsRepository.get(mUserId).getMethodMap().get(TEST_IME_ID);
        }
        assertThat(info).isNotNull();
        assertThat(info.getId()).isEqualTo(TEST_IME_ID);
        assertThat(info.getServiceName()).isEqualTo(TEST_SERVICE_NAME);

        // Bind input method with main connection. It is called on another thread because we should
        // wait for onServiceConnected() to finish.
        final var latch = new CountDownLatch(1);
        InputBindResult result = callOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setLatchForTesting(latch);
                return mBindingController.bindCurrentMethod();
            }
        });

        verify(mContext, never()).bindServiceAsUser(
                any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                eq(mImeConnectionBindFlags) /* flags */, any(UserHandle.class) /* user */);
        assertThat(result.result).isEqualTo(InputBindResult.ResultCode.ERROR_NO_IME);
        assertThat(result.id).isNull();
        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasBackgroundConnection()).isFalse();
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.getCurId()).isNull();
            assertThat(mBindingController.getCurToken()).isNull();
            assertThat(mBindingController.getCurTokenDisplayId()).isEqualTo(INVALID_DISPLAY);
            verify(mMockWindowManagerInternal, never()).addWindowToken(
                    any(IBinder.class) /* token */, anyInt() /* type */, anyInt() /* displayId */,
                    any(Bundle.class) /* options */);
            verify(mMockWindowManagerInternal, never()).setImeWindowToken(
                    any(IBinder.class) /* token */, anyInt() /* displayId */);
        }
        // Wait for onServiceConnected()
        boolean completed = latch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        if (completed) {
            fail("onServiceConnected() should not be received for inactive bindings");
        }

        synchronized (ImfLock.class) {
            assertThat(mBindingController.getCurMethod()).isNull();
            assertThat(mBindingController.getCurMethodUid()).isEqualTo(Process.INVALID_UID);
        }
    }

    private void testBindCurrentMethodWithVisibleConnection() {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setCurrentMethodVisible();
            }
        });
        // Bind input method with visible connection
        verify(mContext, times(1)).bindServiceAsUser(
                any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                eq(InputMethodBindingController.IME_VISIBLE_BIND_FLAGS) /* flags */,
                any(UserHandle.class) /* user */);
        synchronized (ImfLock.class) {
            assertThat(mBindingController.isVisibleBound()).isTrue();
        }
    }

    private void testUnbindCurrentMethod(boolean isVisible) {
        final IBinder token = mBindingController.getCurToken();
        final int tokenDisplayId = mBindingController.getCurTokenDisplayId();
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.unbindCurrentMethod();
            }
        });

        synchronized (ImfLock.class) {
            // Unbind both main connection and visible connection
            if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)) {
                assertThat(mBindingController.hasBackgroundConnection()).isFalse();
            }
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.isVisibleBound()).isFalse();
            final int backgroundConnection =
                    mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME) ? 1 : 0;
            if (isVisible) {
                verify(mContext, times(2 + backgroundConnection))
                        .unbindService(any(ServiceConnection.class));
            } else {
                verify(mContext, times(1 + backgroundConnection))
                        .unbindService(any(ServiceConnection.class));
            }
            verify(mMockWindowManagerInternal, times(1)).removeWindowToken(eq(token),
                    eq(true) /* removeWindows */, eq(false)/* animateExit */, eq(tokenDisplayId));
            assertThat(mBindingController.getCurToken()).isNull();
            assertThat(mBindingController.getCurTokenDisplayId()).isEqualTo(INVALID_DISPLAY);
            assertThat(mBindingController.getCurId()).isNull();
            assertThat(mBindingController.getCurMethod()).isNull();
            assertThat(mBindingController.getCurMethodUid()).isEqualTo(Process.INVALID_UID);
        }
    }

    private static <V> V callOnMainSync(Callable<V> callable) {
        AtomicReference<V> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            try {
                                result.set(callable.call());
                            } catch (Exception e) {
                                throw new RuntimeException("Exception was thrown", e);
                            }
                        });
        return result.get();
    }
}
