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

import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipChange;

import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.transition.Transitions;

/**
 * Handles PiP bounds change transitions.
 */
public class PipBoundsChangeHandler implements Transitions.TransitionHandler {
    public static final String PIP_START_TX = "pip_start_tx";
    public static final String PIP_FINISH_TX = "pip_finish_tx";
    public static final String PIP_DESTINATION_BOUNDS = "pip_dest_bounds";
    public static final String ANIMATING_BOUNDS_CHANGE_DURATION =
            "animating_bounds_change_duration";
    public static final int BOUNDS_CHANGE_JUMPCUT_DURATION = 0;

    private final PipTransitionState mPipTransitionState;
    private int mAnimatingBoundsChangeDuration = BOUNDS_CHANGE_JUMPCUT_DURATION;

    public PipBoundsChangeHandler(PipTransitionState pipTransitionState) {
        mPipTransitionState = pipTransitionState;
    }

    /**
     * Sets the duration for the bounds change animation.
     */
    public void setAnimatingBoundsChangeDuration(int duration) {
        mAnimatingBoundsChangeDuration = duration;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        return null;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }

        // We expect the PiP activity as a separate change in a config-at-end transition;
        // only flings are not using config-at-end for resize bounds changes
        TransitionInfo.Change pipActivityChange = PipTransitionUtils.getDeferConfigActivityChange(
                info, pipChange.getTaskInfo().getToken());
        if (pipActivityChange != null) {
            // Transform calculations use PiP params by default, so make sure they are null to
            // default to using bounds for scaling calculations instead.
            pipChange.getTaskInfo().pictureInPictureParams = null;
            PipTransitionUtils.prepareConfigAtEndActivity(
                    startTransaction, finishTransaction, pipChange, pipActivityChange);
        }

        SurfaceControl pipLeash = pipChange.getLeash();
        startTransaction.setWindowCrop(pipLeash, pipChange.getEndAbsBounds().width(),
                pipChange.getEndAbsBounds().height());

        // Classes interested in continuing the animation would subscribe to this state update
        // getting info such as endBounds, startTx, and finishTx as an extra Bundle
        // Once done state needs to be updated to CHANGED_PIP_BOUNDS via
        // {@link com.android.wm.shell.pip2.phone.PipScheduler}.
        Bundle extra = new Bundle();
        extra.putParcelable(PIP_START_TX, startTransaction);
        extra.putParcelable(PIP_FINISH_TX, finishTransaction);
        extra.putParcelable(PIP_DESTINATION_BOUNDS, pipChange.getEndAbsBounds());
        extra.putInt(ANIMATING_BOUNDS_CHANGE_DURATION, mAnimatingBoundsChangeDuration);

        mPipTransitionState.setState(PipTransitionState.CHANGING_PIP_BOUNDS, extra);
        return true;
    }
}
