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

import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH;
import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_SECURE_CONTENT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_CALLER_INITIATED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;

import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.LifecycleState.Active;
import android.companion.virtual.computercontrol.LifecycleState.Blocked;
import android.companion.virtual.computercontrol.LifecycleState.Closed;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class SessionLifecycleTest {

    private static final String TEST_PKG = "com.test.pkg";

    @Mock
    private IComputerControlLifecycleCallback mRemoteCallback;
    @Mock
    private ComputerControlSession.LifecycleCallback mLocalCallback;

    private AutoCloseable mMockitoSession;
    private SessionLifecycle mLifecycle;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mLifecycle = new SessionLifecycle(mLocalCallback);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void initializeLifecycle_startsInActiveState() throws Exception {
        mLifecycle.initializeWithRemoteCallback(mRemoteCallback);

        verify(mRemoteCallback).onActive();
        verify(mLocalCallback).onActive();
        assertThat(mLifecycle.getCurrentState()).isInstanceOf(Active.class);
    }

    @Test
    public void addRemoteCallbackTwice_throwsIllegalStateException() {
        mLifecycle.initializeWithRemoteCallback(mRemoteCallback);

        assertThrows(IllegalStateException.class,
                () -> mLifecycle.initializeWithRemoteCallback(mRemoteCallback));
    }

    @Test
    public void updateLifecycle_entersClosedState() {
        initializeCallbacksAndReset();

        final var state = mLifecycle.updateLifecycleState(
                (config) -> config.mClosed = new Closed(CLOSE_REASON_CALLER_INITIATED));
        assertThat(state).isInstanceOf(Closed.class);
        assertThat(((Closed) state).reason).isEqualTo(CLOSE_REASON_CALLER_INITIATED);
    }

    @Test
    public void updateLifecycle_canBeCalledBeforeInitialization() {
        mLifecycle.updateLifecycleState((config) -> {});

        verify(mLocalCallback).onActive();
        assertThat(mLifecycle.getCurrentState()).isInstanceOf(Active.class);
    }

    @Test
    public void updateLifecycle_secureWindowVisibility_controlsBlockedState() throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState(
                (config) -> config.mSecureWindowPackage = TEST_PKG);

        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_SECURE_CONTENT);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);

        state = mLifecycle.updateLifecycleState((config) -> config.mSecureWindowPackage = null);
        assertThat(state).isInstanceOf(Active.class);
        verify(mLocalCallback).onActive();
        verify(mRemoteCallback).onActive();
    }

    @Test
    public void updateLifecycle_blockedActivityVisibility_controlsBlockedState() throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState(
                (config) -> config.mBlockingActivityPackage = TEST_PKG);

        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);

        state = mLifecycle.updateLifecycleState((config) -> config.mBlockingActivityPackage = null);

        assertThat(state).isInstanceOf(Active.class);
        verify(mLocalCallback).onActive();
        verify(mRemoteCallback).onActive();
    }

    @Test
    public void updateLifecycle_withBlockedActivityAndSecureWindow_entersBlockedState()
            throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = TEST_PKG;
            config.mSecureWindowPackage = TEST_PKG;
        });

        // The disallowed activity block reason overrides the secure content block reason.
        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
    }

    @Test
    public void updateLifecycle_blockReasonCanChange() throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = TEST_PKG;
            config.mSecureWindowPackage = null;
        });

        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);

        state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = null;
            config.mSecureWindowPackage = TEST_PKG;
        });

        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_SECURE_CONTENT);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);
    }

    private void initializeCallbacksAndReset() {
        mLifecycle.initializeWithRemoteCallback(mRemoteCallback);
        Mockito.reset(mRemoteCallback, mLocalCallback);
    }
}
