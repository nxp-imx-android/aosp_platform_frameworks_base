/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY;

import android.app.ActivityManager;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.transition.Transitions;

/**
 * Observer used to identify tasks that are opening or moving to front. If a bubble activity is
 * currently opened when this happens, we'll collapse the bubbles.
 */
public class BubblesTransitionObserver implements Transitions.TransitionObserver {

    @NonNull
    private final BubbleController mBubbleController;
    @NonNull
    private final BubbleData mBubbleData;

    public BubblesTransitionObserver(@NonNull BubbleController controller,
            @NonNull BubbleData bubbleData) {
        mBubbleController = controller;
        mBubbleData = bubbleData;
    }

    @Override
    public void onTransitionReady(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {

        // --- Pre-conditions (Loop-invariant checks) ---
        // If bubbles aren't expanded, are animating, or no bubble is selected,
        // we don't need to process any transitions for collapsing.
        if (mBubbleController.isStackAnimating()
                || !mBubbleData.isExpanded()
                || mBubbleData.getSelectedBubble() == null) {
            return;
        }

        final int expandedTaskId = mBubbleData.getSelectedBubble().getTaskId();
        // If expanded task id is invalid, we don't need to process any transitions for collapsing.
        if (expandedTaskId == INVALID_TASK_ID) {
            return;
        }

        final int bubbleViewDisplayId = mBubbleController.getCurrentViewDisplayId();
        for (TransitionInfo.Change change : info.getChanges()) {
            // We only care about opens / move to fronts.
            if (!TransitionUtil.isOpeningType(change.getMode())) {
                continue;
            }
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            // We only handle task transitions.
            if (taskInfo == null || taskInfo.taskId == INVALID_TASK_ID) {
                continue;
            }
            // If the opening task id is the same as the expanded bubble, skip collapsing
            // because it is our bubble that is opening.
            if (taskInfo.taskId == expandedTaskId) {
                continue;
            }
            // If the opening task is on a different display, skip collapsing because the task
            // opening does not visually overlap with the bubbles.
            if (taskInfo.displayId != bubbleViewDisplayId) {
                continue;
            }
            // If the opening task was launched by another bubble, skip collapsing the existing one
            // since BubbleTransitions will start a new bubble for it
            if (BubbleAnythingFlagHelper.enableCreateAnyBubble()
                    && mBubbleController.shouldBeAppBubble(taskInfo)) {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TransitionObserver.onTransitionReady(): "
                        + "skipping app bubble for taskId=%d", taskInfo.taskId);
                continue;
            }

            mBubbleData.setExpanded(false);
            return;
        }
    }

    @Override
    public void onTransitionStarting(@NonNull IBinder transition) {

    }

    @Override
    public void onTransitionMerged(@NonNull IBinder merged, @NonNull IBinder playing) {

    }

    @Override
    public void onTransitionFinished(@NonNull IBinder transition, boolean aborted) {

    }
}
