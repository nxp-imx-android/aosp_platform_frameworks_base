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

import static com.android.internal.inputmethod.SoftInputShowHideReason.HIDE_SOFT_INPUT;
import static com.android.internal.inputmethod.SoftInputShowHideReason.SHOW_SOFT_INPUT;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.os.Binder;
import android.os.RemoteException;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;
import android.view.inputmethod.ImeTracker;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.annotations.GuardedBy;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the behavior of {@link DefaultImeVisibilityApplier} when performing or applying the IME
 * visibility state.
 *
 * <p>Build/Install/Run:
 * atest FrameworksInputMethodSystemServerTests:DefaultImeVisibilityApplierTest
 */
@RunWith(AndroidJUnit4.class)
public class DefaultImeVisibilityApplierTest extends InputMethodManagerServiceTestBase {

    private final DeviceFlagsValueProvider mFlagsValueProvider = new DeviceFlagsValueProvider();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule(mFlagsValueProvider);
    private DefaultImeVisibilityApplier mVisibilityApplier;

    @Before
    public void setUp() throws RemoteException {
        super.setUp();
        synchronized (ImfLock.class) {
            mVisibilityApplier = mInputMethodManagerService.getVisibilityApplierLocked();
            setAttachedClientLocked(requireNonNull(
                    mInputMethodManagerService.getClientStateLocked(mMockInputMethodClient)));
        }
    }

    @Test
    public void testPerformShowIme() throws Exception {
        synchronized (ImfLock.class) {
            mVisibilityApplier.performShowIme(new Binder() /* showInputToken */,
                    ImeTracker.Token.empty(), SHOW_SOFT_INPUT, mUserId);
        }
        verifyShowSoftInput(false, true);
    }

    @Test
    public void testPerformHideIme() throws Exception {
        synchronized (ImfLock.class) {
            mVisibilityApplier.performHideIme(new Binder() /* hideInputToken */,
                    ImeTracker.Token.empty(), HIDE_SOFT_INPUT, mUserId);
        }
        verifyHideSoftInput(false, true);
    }

    @Test
    public void testShowImeScreenshot() {
        synchronized (ImfLock.class) {
            mVisibilityApplier.showImeScreenshot(mWindowToken, Display.DEFAULT_DISPLAY, mUserId);
        }

        verify(mMockImeTargetVisibilityPolicy).showImeScreenshot(eq(mWindowToken),
                eq(Display.DEFAULT_DISPLAY));
    }

    @Test
    public void testRemoveImeScreenshot() {
        synchronized (ImfLock.class) {
            mVisibilityApplier.removeImeScreenshot(mWindowToken, Display.DEFAULT_DISPLAY, mUserId);
        }

        verify(mMockImeTargetVisibilityPolicy).removeImeScreenshot(eq(Display.DEFAULT_DISPLAY));
    }

    @GuardedBy("ImfLock.class")
    private void setAttachedClientLocked(@Nullable ClientState cs) {
        mInputMethodManagerService.getUserData(mUserId).mCurClient = cs;
    }
}
