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

import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getChangeByToken;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;

import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.transition.Transitions;

/**
 * Handles removing PiP transitions.
 */
public class PipRemoveHandler implements Transitions.TransitionHandler {
    private final Context mContext;
    private final PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private final PipTransitionState mPipTransitionState;
    private final PipBoundsState mPipBoundsState;

    @Nullable
    private Transitions.TransitionFinishCallback mFinishCallback;
    private boolean mPendingRemoveWithFadeout;
    private PipAlphaAnimatorSupplier mPipAlphaAnimatorSupplier;

    public PipRemoveHandler(Context context,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipTransitionState pipTransitionState,
            PipBoundsState pipBoundsState) {
        mContext = context;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mPipTransitionState = pipTransitionState;
        mPipBoundsState = pipBoundsState;
        mPipAlphaAnimatorSupplier = PipAlphaAnimator::new;
    }

    /**
     * Sets whether to fade out the PiP window on removal.
     */
    public void setPendingRemoveWithFadeout(boolean withFadeout) {
        mPendingRemoveWithFadeout = withFadeout;
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
        if (!isRemovePipTransition(info)) {
            return false;
        }

        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        return startRemoveAnimation(info, startTransaction, finishTransaction, finishCallback);
    }

    private boolean startRemoveAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getChangeByToken(info,
                mPipTransitionState.getPipTaskToken());
        if (pipChange == null) {
            return false;
        }
        mFinishCallback = finishCallback;

        if (isPipClosing(info)) {
            // If PiP is removed via a close (e.g. finishing of the activity), then
            // clear out the PiP cache related to that activity component (e.g. reentry state).
            mPipBoundsState.setLastPipComponentName(null /* lastPipComponentName */);
        }

        final Rect startBounds = pipChange.getStartAbsBounds();
        startTransaction.setWindowCrop(pipChange.getLeash(),
                startBounds.width(), startBounds.height());
        finishTransaction.setAlpha(pipChange.getLeash(), 0f);
        if (mPendingRemoveWithFadeout) {
            PipAlphaAnimator animator = mPipAlphaAnimatorSupplier.get(mContext,
                    mPipSurfaceTransactionHelper,
                    pipChange.getLeash(),
                    startTransaction, finishTransaction, PipAlphaAnimator.FADE_OUT);
            animator.setAnimationEndCallback(this::finishTransition);
            animator.start();
        } else {
            // Jumpcut to a faded-out PiP if no fadeout animation was requested.
            startTransaction.setAlpha(pipChange.getLeash(), 0f);
            startTransaction.apply();
            finishTransition();
        }
        mPendingRemoveWithFadeout = false;
        return true;
    }

    private boolean isRemovePipTransition(@NonNull TransitionInfo info) {
        if (mPipTransitionState.getPipTaskToken() == null) {
            // PiP removal makes sense if enter-PiP has cached a valid pinned task token.
            return false;
        }
        TransitionInfo.Change pipChange = info.getChange(mPipTransitionState.getPipTaskToken());
        if (pipChange == null) {
            // Search for the PiP change by token since the windowing mode might be FULLSCREEN now.
            return false;
        }

        boolean isPipMovedToBack = info.getType() == TRANSIT_TO_BACK
                && pipChange.getMode() == TRANSIT_TO_BACK;
        // If PiP is dismissed by user (i.e. via dismiss button in PiP menu)
        boolean isPipDismissed = info.getType() == TRANSIT_REMOVE_PIP
                && pipChange.getMode() == TRANSIT_TO_BACK;
        // PiP is being removed if the pinned task is either moved to back, closed, or dismissed.
        return isPipMovedToBack || isPipClosing(info) || isPipDismissed;
    }

    private boolean isPipClosing(@NonNull TransitionInfo info) {
        if (mPipTransitionState.getPipTaskToken() == null) {
            // PiP removal makes sense if enter-PiP has cached a valid pinned task token.
            return false;
        }
        TransitionInfo.Change pipChange = info.getChange(mPipTransitionState.getPipTaskToken());
        TransitionInfo.Change pipActivityChange = info.getChanges().stream().filter(change ->
                        change.getTaskInfo() == null && change.getParent() != null
                                && change.getParent() == mPipTransitionState.getPipTaskToken())
                .findFirst().orElse(null);

        boolean isPipTaskClosed = pipChange != null
                && pipChange.getMode() == TRANSIT_CLOSE;
        boolean isPipActivityClosed = pipActivityChange != null
                && pipActivityChange.getMode() == TRANSIT_CLOSE;
        return isPipTaskClosed || isPipActivityClosed;
    }

    private void finishTransition() {
        final int currentState = mPipTransitionState.getState();
        if (currentState == PipTransitionState.EXITING_PIP) {
            mPipTransitionState.setState(PipTransitionState.EXITED_PIP);
        }

        if (mFinishCallback != null) {
            final Transitions.TransitionFinishCallback finishCallback = mFinishCallback;
            mFinishCallback = null;
            finishCallback.onTransitionFinished(null /* finishWct */);
        }
    }

    @VisibleForTesting
    interface PipAlphaAnimatorSupplier {
        PipAlphaAnimator get(@NonNull Context context,
                @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
                @NonNull SurfaceControl leash,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                int animationType);
    }

    @VisibleForTesting
    void setPipAlphaAnimatorSupplier(PipAlphaAnimatorSupplier supplier) {
        mPipAlphaAnimatorSupplier = supplier;
    }
}
