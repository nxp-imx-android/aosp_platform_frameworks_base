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

package com.android.wm.shell.pip2.phone.transition;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.WindowManager.TRANSIT_CHANGE;

import static com.android.wm.shell.pip2.phone.transition.PipBoundsChangeHandler.ANIMATING_BOUNDS_CHANGE_DURATION;
import static com.android.wm.shell.pip2.phone.transition.PipBoundsChangeHandler.PIP_DESTINATION_BOUNDS;
import static com.android.wm.shell.pip2.phone.transition.PipBoundsChangeHandler.PIP_FINISH_TX;
import static com.android.wm.shell.pip2.phone.transition.PipBoundsChangeHandler.PIP_START_TX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test against {@link PipBoundsChangeHandler}
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner.class)
public class PipBoundsChangeHandlerTest {
    @Mock private PipTransitionState mPipTransitionState;
    @Mock private SurfaceControl mPipLeash;
    @Mock private SurfaceControl.Transaction mStartTx;
    @Mock private SurfaceControl.Transaction mFinishTx;
    @Mock private Transitions.TransitionFinishCallback mFinishCallback;
    @Mock private WindowContainerToken mPipTaskToken;

    @Captor private ArgumentCaptor<Bundle> mBundleArgumentCaptor;

    private PipBoundsChangeHandler mPipBoundsChangeHandler;

    private static final Rect START_BOUNDS = new Rect(0, 0, 100, 100);
    private static final Rect END_BOUNDS = new Rect(0, 0, 200, 200);
    private static final int ANIMATION_DURATION = 300;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPipBoundsChangeHandler = new PipBoundsChangeHandler(mPipTransitionState);
        when(mStartTx.setWindowCrop(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(mStartTx);
    }

    @Test
    public void startAnimation_updatesPipTransitionState() {
        final IBinder transition = new Binder();
        final TransitionInfo info = createTransitionInfo();

        mPipBoundsChangeHandler.setAnimatingBoundsChangeDuration(ANIMATION_DURATION);
        boolean result = mPipBoundsChangeHandler.startAnimation(transition, info, mStartTx,
                mFinishTx, mFinishCallback);

        assertTrue(result);

        // Verify that the state is updated to CHANGING_PIP_BOUNDS
        verify(mPipTransitionState).setState(eq(PipTransitionState.CHANGING_PIP_BOUNDS),
                mBundleArgumentCaptor.capture());

        // Verify the contents of the bundle
        Bundle extra = mBundleArgumentCaptor.getValue();
        assertEquals(mStartTx, extra.getParcelable(PIP_START_TX,
                SurfaceControl.Transaction.class));
        assertEquals(mFinishTx, extra.getParcelable(PIP_FINISH_TX,
                SurfaceControl.Transaction.class));
        assertEquals(END_BOUNDS, extra.getParcelable(PIP_DESTINATION_BOUNDS, Rect.class));
        assertEquals(ANIMATION_DURATION, extra.getInt(ANIMATING_BOUNDS_CHANGE_DURATION));
    }

    @Test
    public void startAnimation_noPipChange_returnsFalse() {
        final IBinder transition = new Binder();
        // Create a transition info without a PiP change
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CHANGE).build();

        boolean result = mPipBoundsChangeHandler.startAnimation(transition, info, mStartTx,
                mFinishTx, mFinishCallback);

        assertFalse(result);
    }

    private TransitionInfo createTransitionInfo() {
        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0);
        final TransitionInfo.Change pipChange = new TransitionInfo.Change(mPipTaskToken, mPipLeash);
        pipChange.setTaskInfo(createPipTaskInfo());
        pipChange.setStartAbsBounds(START_BOUNDS);
        pipChange.setEndAbsBounds(END_BOUNDS);
        info.addChange(pipChange);
        return info;
    }

    private ActivityManager.RunningTaskInfo createPipTaskInfo() {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = 1;
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_PINNED);
        taskInfo.token = mPipTaskToken;
        return taskInfo;
    }
}
