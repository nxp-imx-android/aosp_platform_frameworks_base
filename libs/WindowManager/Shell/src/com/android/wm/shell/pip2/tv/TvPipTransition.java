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

import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_BACK;

import static com.android.wm.shell.common.pip.PipMenuController.ALPHA_NO_CHANGE;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getChangeByToken;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipChange;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;
import static com.android.wm.shell.transition.Transitions.TRANSIT_PIP_BOUNDS_CHANGE;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;

import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.Preconditions;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip.tv.TvPipBoundsAlgorithm;
import com.android.wm.shell.pip.tv.TvPipBoundsState;
import com.android.wm.shell.pip.tv.TvPipInterpolators;
import com.android.wm.shell.pip.tv.TvPipMenuController;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.pip2.animation.PipEnterAnimator;
import com.android.wm.shell.pip2.animation.PipResizeAnimator;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.pip2.phone.transition.PipTransitionUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

/**
 * TV PiP2 Transition handler.
 */
public class TvPipTransition extends PipTransitionController implements
        PipTransitionState.PipTransitionStateChangedListener {
    private static final String TAG = "TvPip2Transition";

    // Used when for ENTERING_PIP state update.
    private static final String PIP_TASK_LEASH = "pip_task_leash";
    private static final String PIP_TASK_INFO = "pip_task_info";

    // Used for PiP CHANGING_BOUNDS state update.
    static final String PIP_START_TX = "pip_start_tx";
    static final String PIP_FINISH_TX = "pip_finish_tx";
    static final String PIP_DESTINATION_BOUNDS = "pip_dest_bounds";
    static final String ANIMATING_BOUNDS_CHANGE_DURATION =
            "animating_bounds_change_duration";
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
    private IBinder mEnterTransition;
    @Nullable
    private IBinder mRemoveTransition;
    @Nullable
    private IBinder mBoundsChangeTransition;
    private int mBoundsChangeDuration;

    //
    // Internal state and relevant cached info
    //

    private Transitions.TransitionFinishCallback mFinishCallback;
    private ValueAnimator mCurrentAnimator;
    private boolean mPendingRemoveWithFadeout;
    private final long mExitFadeOutDuration;

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
        mPipTransitionState.addPipTransitionStateChangedListener(this);
        mExitFadeOutDuration = context.getResources().getInteger(
                R.integer.config_tvPipExitFadeOutDuration);
    }

    @Override
    protected void onInit() {
        if (PipFlags.isPip2ExperimentEnabled()) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: V2 Flag is ON, registering TvPip2Transition handler.", TAG);
            mTransitions.addHandler(this);
        }
    }

    @Override
    public void startRemoveTransition(WindowContainerTransaction wct, boolean withFadeout) {
        if (wct == null) {
            return;
        }
        mPendingRemoveWithFadeout = withFadeout;
        mRemoveTransition = mTransitions.startTransition(TRANSIT_REMOVE_PIP, wct, this);
    }

    @Override
    public void startPipBoundsChangeTransition(WindowContainerTransaction wct, int duration) {
        if (wct == null) {
            return;
        }
        mBoundsChangeTransition = mTransitions.startTransition(TRANSIT_PIP_BOUNDS_CHANGE, wct,
                this);
        mBoundsChangeDuration = duration;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: merge animation", TAG);
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.end();
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (mPipTransitionState.getState() == PipTransitionState.SCHEDULED_ENTER_PIP) {
            // An enter PiP transition has already been scheduled and is waiting to be played.
            return null;
        }
        if (requestHasPipEnter(request)) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: handle PiP enter request", TAG);
            // Cache the token so we can identify this transition in startAnimation().
            mEnterTransition = transition;
            mPipTransitionState.setState(PipTransitionState.SCHEDULED_ENTER_PIP);
            return getEnterPipTransaction(request.getPipChange());
        } else if (TransitionUtil.isClosingType(request.getType())
                && request.getTriggerTask() != null
                && request.getTriggerTask().getToken().equals(
                        mPipTransitionState.getPipTaskToken())) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: handle PiP remove request", TAG);
            mRemoveTransition = transition;
            return new WindowContainerTransaction();
        }
        return null;
    }

    private WindowContainerTransaction getEnterPipTransaction(
            @NonNull TransitionRequestInfo.PipChange pipChange) {
        final ActivityManager.RunningTaskInfo pipTask = pipChange.getTaskInfo();

        final TvPipBoundsState tvPipBoundsState = (TvPipBoundsState) mPipBoundsState;
        PictureInPictureParams pipParams = pipTask.pictureInPictureParams;
        tvPipBoundsState.setBoundsStateForEntry(pipTask.topActivity, pipTask.topActivityInfo,
                pipParams, mPipBoundsAlgorithm);

        // Temporarily add insets for menu border and edu text height during bounds calculation
        // since the task leash is not yet available and menu cannot be attached here.
        int pipMenuBorderWidth = mContext.getResources()
                .getDimensionPixelSize(R.dimen.pip_menu_border_width);
        tvPipBoundsState.setPipMenuPermanentDecorInsets(Insets.of(-pipMenuBorderWidth,
                -pipMenuBorderWidth, -pipMenuBorderWidth, -pipMenuBorderWidth));
        final int pipEduTextHeight = mContext.getResources()
                .getDimensionPixelSize(R.dimen.pip_menu_edu_text_view_height);
        tvPipBoundsState.setPipMenuTemporaryDecorInsets(Insets.of(0, 0, 0,
                -pipEduTextHeight));

        // Calculate the final PiP bounds.
        final Rect entryBounds =
                mPipBoundsAlgorithm.getEntryDestinationBoundsIgnoringKeepClearAreas();
        tvPipBoundsState.setBounds(entryBounds);

        // Undo the insets.
        tvPipBoundsState.setPipMenuPermanentDecorInsets(Insets.NONE);
        tvPipBoundsState.setPipMenuTemporaryDecorInsets(Insets.NONE);

        // Create the transaction that tells the core to pin task and move to final bounds.
        WindowContainerToken token = pipChange.getTaskFragmentToken();
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.movePipActivityToPinnedRootTask(token, entryBounds);
        wct.deferConfigToTransitionEnd(token);
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

        mCurrentAnimator = animator;
        animator.start();
        return true;
    }

    private boolean startBoundsChangeAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        mFinishCallback = finishCallback;
        final SurfaceControl leash = pipChange.getLeash();
        final Rect startBounds = pipChange.getStartAbsBounds();
        final Rect destinationBounds = pipChange.getEndAbsBounds();

        final PipResizeAnimator animator = new PipResizeAnimator(mContext,
                mPipSurfaceTransactionHelper, leash, startTransaction, finishTransaction,
                destinationBounds, startBounds, destinationBounds, mBoundsChangeDuration,
                /* delta= */ 0);

        // Set the sync listener used to move the menu.
        animator.setSyncMenuListener((tx, animatedRect) -> {
            mTvPipMenuController.movePipMenu(tx, animatedRect, ALPHA_NO_CHANGE);
        });

        animator.setAnimationEndCallback(() -> {
            mPipBoundsState.setBounds(destinationBounds);
            finishTransition();
        });

        mCurrentAnimator = animator;
        animator.start();
        return true;
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

        final Rect startBounds = pipChange.getStartAbsBounds();
        startTransaction.setWindowCrop(pipChange.getLeash(),
                startBounds.width(), startBounds.height());
        finishTransaction.setAlpha(pipChange.getLeash(), 0f);

        if (mPendingRemoveWithFadeout) {
            mPendingRemoveWithFadeout = false;
            final PipAlphaAnimator animator = new PipAlphaAnimator(
                    mContext, mPipSurfaceTransactionHelper, pipChange.getLeash(),
                    startTransaction, finishTransaction, PipAlphaAnimator.FADE_OUT);
            animator.setInterpolator(TvPipInterpolators.EXIT);
            animator.setDuration(mExitFadeOutDuration);     // Override the phone alpha duration.

            animator.addUpdateListener(animation -> {
                float alpha = (float) animation.getAnimatedValue();
                mTvPipMenuController.movePipMenu(null, null, alpha);
            });

            animator.setAnimationEndCallback(() -> {
                finishTransition();
            });
            mCurrentAnimator = animator;
            animator.start();
        } else {
            startTransaction.setAlpha(pipChange.getLeash(), 0f);
            startTransaction.apply();
            finishTransition();
        }
        return true;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (isPipTransitionAnimationOngoing()) {
            // Prevent another transition from disrupting ongoing transition.
            Log.wtf(TAG, String.format("""
                    PipTransition tried to startAnimation while another PiP animation was playing.
                    callers=%s""", Debug.getCallers(4)));
            return false;
        }

        // Check if this is the enter transition we handled in handleRequest().
        if (transition == mEnterTransition || info.getType() == TRANSIT_PIP) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: starting PiP enter animation", TAG);
            mEnterTransition = null; // Clear the cached token.
            TransitionInfo.Change pipChange = getPipChange(info);

            Bundle extra = new Bundle();
            extra.putParcelable(PIP_TASK_LEASH, pipChange.getLeash());
            extra.putParcelable(PIP_TASK_INFO, pipChange.getTaskInfo());
            mPipTransitionState.setState(PipTransitionState.ENTERING_PIP, extra);
            return startEnterAnimation(info, startTransaction, finishTransaction, finishCallback);
        } else if (transition == mBoundsChangeTransition) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: starting PiP bounds change animation", TAG);
            mBoundsChangeTransition = null;
            TransitionInfo.Change pipChange = getPipChange(info);

            Bundle extra = new Bundle();
            extra.putParcelable(PIP_START_TX, startTransaction);
            extra.putParcelable(PIP_FINISH_TX, finishTransaction);
            extra.putParcelable(PIP_DESTINATION_BOUNDS, pipChange.getEndAbsBounds());
            extra.putInt(ANIMATING_BOUNDS_CHANGE_DURATION, mBoundsChangeDuration);

            mPipTransitionState.setState(PipTransitionState.CHANGING_PIP_BOUNDS, extra);
            return startBoundsChangeAnimation(info, startTransaction, finishTransaction,
                    finishCallback);
        } else if (transition == mRemoveTransition || isRemovePipTransition(info)) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: starting PiP remove animation", TAG);
            mRemoveTransition = null;
            mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
            return startRemoveAnimation(info, startTransaction, finishTransaction, finishCallback);
        }
        return false;
    }

    private boolean isPipTransitionAnimationOngoing() {
        // There is a one-to-one mapping between when finish callback is still cached
        // and when there is an ongoing transition animation.
        return mFinishCallback != null;
    }

    private boolean isRemovePipTransition(@NonNull TransitionInfo info) {
        if (mPipTransitionState.getPipTaskToken() == null) {
            return false;
        }
        TransitionInfo.Change pipChange = info.getChange(mPipTransitionState.getPipTaskToken());
        if (pipChange == null) {
            return false;
        }

        final int mode = pipChange.getMode();
        return info.getType() == TRANSIT_REMOVE_PIP || mode == TRANSIT_CLOSE
                || mode == TRANSIT_TO_BACK;
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
    public void end() {
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
            mCurrentAnimator = null;
        }
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

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.ENTERING_PIP:
                Preconditions.checkState(extra != null,
                        "No extra bundle for " + mPipTransitionState);

                mPipTransitionState.setPinnedTaskLeash(extra.getParcelable(
                        PIP_TASK_LEASH, SurfaceControl.class));
                mPipTransitionState.setPipTaskInfo(extra.getParcelable(
                        PIP_TASK_INFO, TaskInfo.class));

                boolean hasValidTokenAndLeash = mPipTransitionState.getPipTaskToken() != null
                        && mPipTransitionState.getPinnedTaskLeash() != null;
                Preconditions.checkState(hasValidTokenAndLeash,
                        "Unexpected state for " + mPipTransitionState);
                break;
            case PipTransitionState.EXITED_PIP:
                mPipTransitionState.setPinnedTaskLeash(null);
                mPipTransitionState.setPipTaskInfo(null);
                mPipTransitionState.setPipCandidateTaskInfo(null);
                break;
            case PipTransitionState.CHANGING_PIP_BOUNDS:
                Preconditions.checkState(mPipTransitionState.getPinnedTaskLeash() != null,
                        "Unexpected state for " + mPipTransitionState);
                break;
        }
    }
}
