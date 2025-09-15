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
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PIP;

import static com.android.wm.shell.pip.PipTransitionController.ANIM_TYPE_ALPHA;
import static com.android.wm.shell.pip2.phone.PipTransitionState.ENTERING_PIP;
import static com.android.wm.shell.pip2.phone.PipTransitionState.SCHEDULED_ENTER_PIP;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.desktopmode.DesktopPipTransitionController;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.phone.PipInteractionHandler;
import com.android.wm.shell.pip2.phone.PipScheduler;
import com.android.wm.shell.pip2.phone.PipTaskListener;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.util.StubTransaction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/**
 * Unit test against {@link PipEnterHandler}
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner.class)
public class PipEnterHandlerTest {
    @Mock private Context mContext;
    @Mock private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    @Mock private PipBoundsState mPipBoundsState;
    @Mock private PipBoundsAlgorithm mPipBoundsAlgorithm;
    @Mock private PipTransitionState mPipTransitionState;
    @Mock private PipDisplayLayoutState mPipDisplayLayoutState;
    @Mock private PipDesktopState mPipDesktopState;
    @Mock private PipTaskListener mPipTaskListener;
    @Mock private PipScheduler mPipScheduler;
    @Mock private DesktopPipTransitionController mDesktopPipTransitionController;
    @Mock private ContentPipHandler mContentPipHandler;
    @Mock private PipInteractionHandler mPipInteractionHandler;
    @Mock private DisplayController mDisplayController;
    private SurfaceControl mPipLeash;
    @Mock private WindowContainerToken mPipTaskToken;

    private static final Rect APP_BOUNDS = new Rect(0, 0, 500, 1000);
    private static final Rect DEST_BOUNDS = new Rect(0, 0, 100, 100);
    private static final int ENTER_DURATION = 100;

    private PipEnterHandler mPipEnterHandler;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mPipLeash = new SurfaceControl.Builder()
                .setName("PipEnterHandlerTest")
                .setCallsite("PipEnterHandlerTest")
                .build();

        final Resources res = mock(Resources.class);
        when(res.getInteger(eq(R.integer.config_pipEnterAnimationDuration)))
                .thenReturn(ENTER_DURATION);
        when(mContext.getResources()).thenReturn(res);
        when(mPipBoundsAlgorithm.getEntryDestinationBounds()).thenReturn(DEST_BOUNDS);
        when(mPipDisplayLayoutState.getDisplayBounds()).thenReturn(new Rect(0, 0, 1080, 1920));

        mPipEnterHandler = new PipEnterHandler(mContext, mPipSurfaceTransactionHelper,
                mPipBoundsState, mPipBoundsAlgorithm, mPipTransitionState, mPipDisplayLayoutState,
                mPipDesktopState, mPipTaskListener, mPipScheduler,
                Optional.of(mDesktopPipTransitionController), mContentPipHandler,
                mPipInteractionHandler, mDisplayController);
    }

    @Test
    public void handleRequest_enterPip_returnsWct() {
        final TransitionRequestInfo request = new TransitionRequestInfo(TRANSIT_PIP, null, null);
        final TransitionRequestInfo.PipChange pipChange =
                mock(TransitionRequestInfo.PipChange.class);
        when(pipChange.getTaskInfo()).thenReturn(createPipTaskInfo(1));
        when(pipChange.getTaskFragmentToken()).thenReturn(mPipTaskToken);
        request.setPipChange(pipChange);

        final WindowContainerTransaction wct = mPipEnterHandler.handleRequest(new Binder(),
                request);

        verify(mPipTransitionState).setState(SCHEDULED_ENTER_PIP);
        verify(mPipTaskListener).setPictureInPictureParams(any());
        verify(mPipBoundsState).setBoundsStateForEntry(any(), any(), any(), any());
        verify(mPipBoundsState).setBounds(DEST_BOUNDS);
        assertNotNull(wct);
    }

    @Test
    public void startAnimation_legacyEnter_startsAlphaAnimator() {
        mPipEnterHandler.setEnterAnimationType(ANIM_TYPE_ALPHA);
        final TransitionInfo info = createEnterPipTransitionInfo(TRANSIT_OPEN,
                new PictureInPictureParams.Builder().build());
        when(mPipTransitionState.getPinnedTaskLeash()).thenReturn(mPipLeash);

        // In legacy enter, there is no separate activity change, but a placeholder is used.
        // We need to ensure it has end bounds to avoid NPE.
        final TransitionInfo.Change placeholderChange = new TransitionInfo.Change(null, null);
        placeholderChange.setEndAbsBounds(new Rect(DEST_BOUNDS));
        info.addChange(placeholderChange);

        mPipEnterHandler.startAnimation(new Binder(), info, new StubTransaction(),
                new StubTransaction(), (wct) -> {});

        verify(mPipInteractionHandler).begin(any(),
                eq(PipInteractionHandler.INTERACTION_ENTER_PIP));
        verify(mPipTransitionState).setState(eq(ENTERING_PIP), any(Bundle.class));
    }

    private TransitionInfo createEnterPipTransitionInfo(@WindowManager.TransitionType int type,
            PictureInPictureParams params) {
        final ActivityManager.RunningTaskInfo taskInfo = createPipTaskInfo(1);
        taskInfo.pictureInPictureParams = params;
        final TransitionInfo info = new TransitionInfoBuilder(type)
                .addChange(TRANSIT_OPEN, taskInfo).build();
        final TransitionInfo.Change pipChange = info.getChanges().get(0);
        pipChange.setStartAbsBounds(APP_BOUNDS);
        pipChange.setEndAbsBounds(DEST_BOUNDS);
        pipChange.setLeash(mPipLeash);
        return info;
    }

    private ActivityManager.RunningTaskInfo createPipTaskInfo(int taskId) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.topActivity = new ComponentName("com.android.test", "TestActivity");
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_PINNED);
        taskInfo.token = mPipTaskToken;
        return taskInfo;
    }
}
