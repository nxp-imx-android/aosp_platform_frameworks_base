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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LifecycleStateTrackerTest {

    @Mock
    private ComputerControlSession.LifecycleCallback mMockCallback;

    private LifecycleStateTracker mLifecycle;
    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mLifecycle = new LifecycleStateTracker();
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void addCallback_startsInActiveState() {
        mLifecycle.addCallback(mMockCallback);

        verifyNoInteractions(mMockCallback);
    }

    @Test
    public void onClose_notifiesCallback() {
        mLifecycle.addCallback(mMockCallback);

        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);

        verify(mMockCallback).onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);
    }

    @Test
    public void onClose_doesNotChangeCloseReason() {
        mLifecycle.addCallback(mMockCallback);

        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);
        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT);

        verify(mMockCallback, times(1)).onClosed(
                eq(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED));
    }
}
