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

package com.android.wm.shell.pip2.tv;

import static android.view.WindowManager.TRANSIT_PIP;

import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipChange;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip.tv.TvPipBoundsAlgorithm;
import com.android.wm.shell.pip.tv.TvPipBoundsState;
import com.android.wm.shell.pip.tv.TvPipMenuController;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipEnterAnimator;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.pip2.phone.transition.PipTransitionUtils;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

/**
 * A skeleton placeholder for the TV PiP2 Transition handler.
 * It does not handle any incoming requests.
 */
public class TvPipTransition extends PipTransitionController {
    private static final String TAG = "TvPip2Transition";

    // Used when for ENTERING_PIP state update.
    private static final String PIP_TASK_LEASH = "pip_task_leash";
    private static final String PIP_TASK_INFO = "pip_task_info";

    //
    // Dependencies
    //

    private final Context mContext;
    private final PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private final TvPipMenuController mTvPipMenuController;
    private final PipTransitionState mPipTransitionState;

    //
    // Transition caches
    //

    @Nullable
    private IBinder mEnterPipTransition;

    //
    // Internal state and relevant cached info
    //

    private Transitions.TransitionFinishCallback mFinishCallback;

    public TvPipTransition(
            Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            TvPipBoundsState tvPipBoundsState,
            TvPipMenuController tvPipMenuController,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            PipTransitionState pipTransitionState) {
        super(shellInit, shellTaskOrganizer, transitions, tvPipBoundsState, tvPipMenuController,
                tvPipBoundsAlgorithm);
        mContext = context;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mTvPipMenuController = tvPipMenuController;
        mPipTransitionState = pipTransitionState;
    }

    @Override
    protected void onInit() {
        if (PipFlags.isPip2ExperimentEnabled()) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: V2 Flag is ON, registering TvPip2Transition handler.", TAG);
            mTransitions.addHandler(this);
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        // Only look for a direct pip enter request
        if (requestHasPipEnter(request)) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: handle PiP enter request", TAG);
            // Cache the token so we can identify this transition in startAnimation().
            mEnterPipTransition = transition;
            mPipTransitionState.setState(PipTransitionState.SCHEDULED_ENTER_PIP);
            return getEnterPipTransaction(request.getPipChange());
        }
        return null;
    }

    private WindowContainerTransaction getEnterPipTransaction(
            @NonNull TransitionRequestInfo.PipChange pipChange) {
        final ActivityManager.RunningTaskInfo pipTask = pipChange.getTaskInfo();

        PictureInPictureParams pipParams = pipTask.pictureInPictureParams;
        mPipBoundsState.setBoundsStateForEntry(pipTask.topActivity, pipTask.topActivityInfo,
                pipParams, mPipBoundsAlgorithm);

        // Calculate the final PiP bounds.
        final Rect entryBounds =
                mPipBoundsAlgorithm.getEntryDestinationBoundsIgnoringKeepClearAreas();

        // Create the transaction that tells the core to pin task and move to final bounds.
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.movePipActivityToPinnedRootTask(pipTask.token, entryBounds);
        wct.deferConfigToTransitionEnd(pipTask.token);
        return wct;
    }

    private boolean startEnterAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        mFinishCallback = finishCallback;

        TransitionInfo.Change pipActivityChange = PipTransitionUtils.getDeferConfigActivityChange(
                info, pipChange.getTaskInfo().getToken());
        if (pipActivityChange == null) {
            return false;
        }

        final SurfaceControl leash = pipChange.getLeash();
        mTvPipMenuController.attach(leash);

        final Rect destinationBounds = pipChange.getEndAbsBounds();
        final PipEnterAnimator animator = new PipEnterAnimator(mContext,
                mPipSurfaceTransactionHelper, leash, startTransaction, finishTransaction,
                destinationBounds, Surface.ROTATION_0);

        // Config-at-end transitions need to have their activities transformed before starting
        // the animation; this makes the buffer seem like it's been updated to final size.
        prepareConfigAtEndActivity(startTransaction, finishTransaction, pipChange,
                pipActivityChange);

        animator.setAnimationStartCallback(() -> animator.setEnterStartState(pipChange));
        animator.setAnimationEndCallback(() -> {
            // Show menu, rounded corners, and shadow at the end of the animation.
            mPipSurfaceTransactionHelper.round(finishTransaction, leash, false);
            mPipSurfaceTransactionHelper.shadow(finishTransaction, leash, false);
            mTvPipMenuController.movePipMenu(finishTransaction, destinationBounds, 1f);

            finishTransition();
        });

        animator.start();
        return true;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // Check if this is the enter transition we handled in handleRequest().
        if (transition == mEnterPipTransition || info.getType() == TRANSIT_PIP) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: starting PiP enter animation", TAG);
            mEnterPipTransition = null; // Clear the cached token.
            TransitionInfo.Change pipChange = getPipChange(info);

            Bundle extra = new Bundle();
            extra.putParcelable(PIP_TASK_LEASH, pipChange.getLeash());
            extra.putParcelable(PIP_TASK_INFO, pipChange.getTaskInfo());
            mPipTransitionState.setState(PipTransitionState.ENTERING_PIP, extra);
            return startEnterAnimation(info, startTransaction, finishTransaction, finishCallback);
        }
        return false;
    }

    private void prepareConfigAtEndActivity(@NonNull SurfaceControl.Transaction startTx,
            @NonNull SurfaceControl.Transaction finishTx,
            @NonNull TransitionInfo.Change pipChange,
            @NonNull TransitionInfo.Change pipActivityChange) {
        PointF initActivityScale = new PointF();
        PointF initActivityPos = new PointF();
        PipUtils.calcEndTransform(pipActivityChange, pipChange, initActivityScale,
                initActivityPos);
        startTx.setCrop(pipActivityChange.getLeash(), null);
        startTx.setScale(pipActivityChange.getLeash(), initActivityScale.x,
                initActivityScale.y);
        startTx.setPosition(pipActivityChange.getLeash(), initActivityPos.x,
                initActivityPos.y);

        finishTx.setCrop(pipActivityChange.getLeash(), null);
        finishTx.setScale(pipActivityChange.getLeash(), initActivityScale.x,
                initActivityScale.y);
        finishTx.setPosition(pipActivityChange.getLeash(), initActivityPos.x,
                initActivityPos.y);
    }

    @Override
    public void finishTransition() {
        final int currentState = mPipTransitionState.getState();
        int nextState = PipTransitionState.UNDEFINED;
        switch (currentState) {
            case PipTransitionState.ENTERING_PIP:
                nextState = PipTransitionState.ENTERED_PIP;
                break;
            case PipTransitionState.CHANGING_PIP_BOUNDS:
                nextState = PipTransitionState.CHANGED_PIP_BOUNDS;
                break;
            case PipTransitionState.EXITING_PIP:
                nextState = PipTransitionState.EXITED_PIP;
                break;
        }

        if (nextState == PipTransitionState.UNDEFINED) {
            ProtoLog.wtf(WM_SHELL_PICTURE_IN_PICTURE, "%s: "
                    + "PipTransitionState resolved to an undefined state in "
                    + "finishTransition(). callers=%s", TAG, Debug.getCallers(4));
        }

        mPipTransitionState.setState(nextState);

        if (mFinishCallback != null) {
            // Need to unset mFinishCallback first because onTransitionFinished can re-enter this
            // handler if there is a pending PiP animation.
            final Transitions.TransitionFinishCallback finishCallback = mFinishCallback;
            mFinishCallback = null;
            finishCallback.onTransitionFinished(null /* finishWct */);
        }
    }
}
