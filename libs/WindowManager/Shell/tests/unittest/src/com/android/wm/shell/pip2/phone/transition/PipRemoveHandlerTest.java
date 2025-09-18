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

import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_TO_BACK;

import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.IBinder;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.Flags;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.util.StubTransaction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;

/**
 * Unit test against {@link PipRemoveHandler}
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(ParameterizedAndroidJunit4.class)
public class PipRemoveHandlerTest {
    @Mock private Context mMockContext;
    @Mock private PipSurfaceTransactionHelper mMockPipSurfaceTransactionHelper;
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private PipBoundsState mMockPipBoundsState;
    @Mock private IBinder mMockTransitionToken;
    @Mock private StubTransaction mStartT;
    @Mock private StubTransaction mFinishT;
    @Mock private SurfaceControl mPipLeash;
    @Mock private PipAlphaAnimator mMockPipAlphaAnimator;

    @Captor private ArgumentCaptor<Runnable> mAnimatorCallbackArgumentCaptor;

    private static final Rect PIP_BOUNDS = new Rect(0, 0, 100, 100);
    private static final int TASK_ID = 1;
    private static final String SAMPLE_PACKAGE_NAME = "com.google.example";
    private static final String SAMPLE_CLASS_NAME = "com.google.example.activity";

    private PipRemoveHandler mPipRemoveHandler;
    private ActivityManager.RunningTaskInfo mPipTaskInfo;
    private WindowContainerToken mPipTaskToken;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_PIP2, Flags.FLAG_ENABLE_PIP_BOX_SHADOWS);
    }

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    public PipRemoveHandlerTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getResources()).thenReturn(mock(Resources.class));

        mPipTaskInfo = createPipTaskInfo(TASK_ID);
        mPipTaskToken = mPipTaskInfo.getToken();
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(mPipTaskToken);

        mPipRemoveHandler = new PipRemoveHandler(mMockContext, mMockPipSurfaceTransactionHelper,
                mMockPipTransitionState, mMockPipBoundsState);
        mPipRemoveHandler.setPipAlphaAnimatorSupplier(
                (context, helper, leash, start, finish, type) -> mMockPipAlphaAnimator);
    }

    @Test
    public void startAnimation_notRemoveTransition_returnsFalse() {
        final TransitionInfo info = new TransitionInfoBuilder(WindowManager.TRANSIT_OPEN)
                .addChange(WindowManager.TRANSIT_OPEN, mPipTaskInfo)
                .build();

        boolean result = mPipRemoveHandler.startAnimation(mMockTransitionToken, info, mStartT,
                mFinishT, (wct) -> {});

        assertFalse(result);
        verify(mMockPipTransitionState, never()).setState(
                eq(PipTransitionState.EXITING_PIP));
    }

    @Test
    public void startAnimation_removeWithFadeout_startsFadeOutAnimation() {
        final TransitionInfo info = getRemovePipTransitionInfo(TRANSIT_REMOVE_PIP, TRANSIT_TO_BACK);
        mPipRemoveHandler.setPendingRemoveWithFadeout(true);

        boolean result = mPipRemoveHandler.startAnimation(mMockTransitionToken, info, mStartT,
                mFinishT, (wct) -> {});

        assertTrue(result);
        verify(mMockPipTransitionState).setState(PipTransitionState.EXITING_PIP);
        verify(mMockPipAlphaAnimator).start();
    }

    @Test
    public void startAnimation_removeWithoutFadeout_appliesTransactionImmediately() {
        final TransitionInfo info = getRemovePipTransitionInfo(TRANSIT_REMOVE_PIP, TRANSIT_TO_BACK);
        mPipRemoveHandler.setPendingRemoveWithFadeout(false);
        when(mMockPipTransitionState.getState()).thenReturn(PipTransitionState.EXITING_PIP);

        boolean result = mPipRemoveHandler.startAnimation(mMockTransitionToken, info, mStartT,
                mFinishT, (wct) -> {});

        assertTrue(result);
        verify(mStartT).setAlpha(mPipLeash, 0f);
        verify(mStartT).apply();
        verify(mMockPipTransitionState).setState(PipTransitionState.EXITED_PIP);
    }

    @Test
    public void startAnimation_pipClosing_clearsLastPipComponent() {
        final TransitionInfo info = getRemovePipTransitionInfo(TRANSIT_CLOSE, TRANSIT_CLOSE);

        mPipRemoveHandler.startAnimation(mMockTransitionToken, info, mStartT, mFinishT,
                (wct) -> {});

        verify(mMockPipBoundsState).setLastPipComponentName(null);
    }

    @Test
    public void startAnimation_pipMovedToBack_startsRemoveAnimation() {
        final TransitionInfo info = getRemovePipTransitionInfo(TRANSIT_TO_BACK, TRANSIT_TO_BACK);

        boolean result = mPipRemoveHandler.startAnimation(mMockTransitionToken, info, mStartT,
                mFinishT, (wct) -> {});

        assertTrue(result);
        verify(mMockPipTransitionState).setState(PipTransitionState.EXITING_PIP);
    }

    @Test
    public void startAnimation_pipDismissed_startsRemoveAnimation() {
        final TransitionInfo info = getRemovePipTransitionInfo(TRANSIT_REMOVE_PIP, TRANSIT_TO_BACK);

        boolean result = mPipRemoveHandler.startAnimation(mMockTransitionToken, info, mStartT,
                mFinishT, (wct) -> {});

        assertTrue(result);
        verify(mMockPipTransitionState).setState(PipTransitionState.EXITING_PIP);
    }

    private TransitionInfo getRemovePipTransitionInfo(@WindowManager.TransitionType int type,
            int mode) {
        final TransitionInfo info = new TransitionInfoBuilder(type)
                .addChange(mode, mPipTaskInfo)
                .build();
        final TransitionInfo.Change pipChange = info.getChange(mPipTaskToken);
        pipChange.setStartAbsBounds(PIP_BOUNDS);
        pipChange.setLeash(mPipLeash);
        return info;
    }

    private static ActivityManager.RunningTaskInfo createPipTaskInfo(int taskId) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.topActivity = new ComponentName(SAMPLE_PACKAGE_NAME, SAMPLE_CLASS_NAME);
        taskInfo.token = mock(WindowContainerToken.class);
        return taskInfo;
    }
}
