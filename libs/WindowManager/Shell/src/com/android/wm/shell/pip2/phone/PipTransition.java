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

package com.android.wm.shell.pip2.phone;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getChangeByToken;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipChange;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP_TO_SPLIT;
import static com.android.wm.shell.transition.Transitions.TRANSIT_PIP_BOUNDS_CHANGE;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;
import static com.android.wm.shell.transition.Transitions.transitTypeToString;

import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.app.TaskInfo;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.Preconditions;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ComponentUtils;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMenuController;
import com.android.wm.shell.desktopmode.DesktopPipTransitionController;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.phone.transition.ContentPipHandler;
import com.android.wm.shell.pip2.phone.transition.PipBoundsChangeHandler;
import com.android.wm.shell.pip2.phone.transition.PipDisplayChangeObserver;
import com.android.wm.shell.pip2.phone.transition.PipEnterHandler;
import com.android.wm.shell.pip2.phone.transition.PipExpandHandler;
import com.android.wm.shell.pip2.phone.transition.PipRemoveHandler;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import java.util.Optional;

/**
 * Implementation of transitions for PiP on phone.
 */
public class PipTransition extends PipTransitionController implements
        PipTransitionState.PipTransitionStateChangedListener,
        PipDisplayLayoutState.DisplayIdListener {
    private static final String TAG = PipTransition.class.getSimpleName();

    // Used when for ENTERING_PIP state update.
    private static final String PIP_TASK_LEASH = "pip_task_leash";
    private static final String PIP_TASK_INFO = "pip_task_info";

    //
    // Dependencies
    //

    private Context mContext;
    private final PipTransitionState mPipTransitionState;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipInteractionHandler mPipInteractionHandler;
    private final PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private final PipBoundsState mPipBoundsState;

    //
    // Transition caches
    //
    @Nullable
    private IBinder mEnterTransition;
    @Nullable
    private IBinder mExitViaExpandTransition;
    @Nullable
    private IBinder mBoundsChangeTransition;


    //
    // Internal state and relevant cached info
    //
    private final PipEnterHandler mEnterHandler;
    private final PipExpandHandler mExpandHandler;
    private final PipDisplayChangeObserver mPipDisplayChangeObserver;
    private final PipBoundsChangeHandler mBoundsChangeHandler;
    private final PipRemoveHandler mRemoveHandler;

    private Transitions.TransitionFinishCallback mFinishCallback;

    private ValueAnimator mTransitionAnimator;

    public PipTransition(
            Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            PipBoundsState pipBoundsState,
            PipMenuController pipMenuController,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipTaskListener pipTaskListener,
            PipScheduler pipScheduler,
            PipTransitionState pipTransitionState,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipUiStateChangeController pipUiStateChangeController,
            DisplayController displayController,
            Optional<SplitScreenController> splitScreenControllerOptional,
            PipDesktopState pipDesktopState,
            Optional<DesktopPipTransitionController> desktopPipTransitionController,
            PipInteractionHandler pipInteractionHandler) {
        super(shellInit, shellTaskOrganizer, transitions, pipBoundsState, pipMenuController,
                pipBoundsAlgorithm);

        mContext = context;
        pipScheduler.setPipTransitionController(this);
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDisplayLayoutState.addDisplayIdListener(this);
        mPipInteractionHandler = pipInteractionHandler;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mPipBoundsState = pipBoundsState;

        mBoundsChangeHandler = new PipBoundsChangeHandler(pipTransitionState);
        mExpandHandler = new PipExpandHandler(mContext, pipSurfaceTransactionHelper,
                pipBoundsState, pipBoundsAlgorithm,
                pipTransitionState, pipDisplayLayoutState, pipDesktopState, pipInteractionHandler,
                pipScheduler, splitScreenControllerOptional, displayController);
        ContentPipHandler contentPipHandler = new ContentPipHandler(mContext,
                pipSurfaceTransactionHelper,
                pipTransitionState);
        mPipDisplayChangeObserver = new PipDisplayChangeObserver(pipTransitionState,
                pipBoundsState);
        mRemoveHandler = new PipRemoveHandler(context, pipSurfaceTransactionHelper,
                pipTransitionState, pipBoundsState);
        mEnterHandler = new PipEnterHandler(context, pipSurfaceTransactionHelper, pipBoundsState,
                pipBoundsAlgorithm, pipTransitionState, pipDisplayLayoutState, pipDesktopState,
                pipTaskListener, pipScheduler, desktopPipTransitionController,
                contentPipHandler, pipInteractionHandler, displayController);
    }

    @Override
    protected void onInit() {
        if (PipFlags.isPip2ExperimentEnabled()) {
            mTransitions.addHandler(this);
            mTransitions.registerObserver(mPipDisplayChangeObserver);
        }
    }

    @Override
    public void onDisplayIdChanged(@NonNull Context context) {
        mContext = context;
        mExpandHandler.onDisplayIdChanged(context);
    }

    @Override
    protected boolean isInSwipePipToHomeTransition() {
        return mPipTransitionState.isInSwipePipToHomeTransition();
    }

    //
    // Transition collection stage lifecycle hooks
    //

    @Override
    public void startExpandTransition(
            WindowContainerTransaction wct, boolean toSplit, boolean hasFirstHandler) {
        if (wct == null) return;
        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        // If PiP wasn't visible, we don't necessarily want to animate using this handler, so we
        // only force it if it was visible.
        Transitions.TransitionHandler handler = hasFirstHandler ? this : null;
        mExitViaExpandTransition = mTransitions.startTransition(toSplit ? TRANSIT_EXIT_PIP_TO_SPLIT
                : TRANSIT_EXIT_PIP, wct, handler);
    }

    @Override
    public void startRemoveTransition(WindowContainerTransaction wct, boolean withFadeout) {
        if (wct == null) return;
        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        mRemoveHandler.setPendingRemoveWithFadeout(withFadeout);
        mTransitions.startTransition(TRANSIT_REMOVE_PIP, wct, this);
    }

    @Override
    public void startPipBoundsChangeTransition(WindowContainerTransaction wct, int duration) {
        if (wct == null) {
            return;
        }
        mBoundsChangeHandler.setAnimatingBoundsChangeDuration(duration);
        mBoundsChangeTransition = mTransitions.startTransition(TRANSIT_PIP_BOUNDS_CHANGE, wct,
                this);
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        final WindowContainerTransaction enterWct = mEnterHandler.handleRequest(transition,
                request);
        if (enterWct != null) {
            mEnterTransition = transition;
            return enterWct;
        }

        final WindowContainerTransaction exitViaExpandWct = mExpandHandler.handleRequest(transition,
                request);
        if (exitViaExpandWct != null) {
            mExitViaExpandTransition = transition;
            return exitViaExpandWct;
        }

        return null;
    }

    @Override
    public void augmentRequest(@NonNull IBinder transition, @NonNull TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWct) {
        if (mEnterHandler.augmentRequest(transition, request, outWct)) {
            mEnterTransition = transition;
        }
    }

    //
    // Transition playing stage lifecycle hooks
    //

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (info.getType() == TRANSIT_EXIT_PIP) {
            end();
        }
        mExpandHandler.mergeAnimation(transition, info, startT, finishT, mergeTarget,
                finishCallback);
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        if (aborted && (transition == mEnterTransition
                || mPipTransitionState.getState() == PipTransitionState.SCHEDULED_ENTER_PIP)) {
            onTransitionAborted();
        }
        if (aborted && transition == mBoundsChangeTransition) {
            onTransitionAborted();
        }
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // Other targets might have default transforms applied that are not relevant when
        // playing PiP transitions, so reset those transforms if needed.
        prepareOtherTargetTransforms(info, startTransaction, finishTransaction);

        // This PiP transition might have caused a previous PiP to be dismissed. If so, we need
        // to clean up the PiP state.
        cleanUpPrevPipIfPresent(info, startTransaction, finishTransaction);

        if (transition == mEnterTransition || info.getType() == TRANSIT_PIP) {
            mEnterTransition = null;
            return mEnterHandler.startAnimation(transition, info, startTransaction,
                    finishTransaction, finishCallback);
        }

        if (transition == mExitViaExpandTransition) {
            mExitViaExpandTransition = null;
            return mExpandHandler.startAnimation(transition, info, startTransaction,
                    finishTransaction, finishCallback);
        } else if (transition == mBoundsChangeTransition) {
            mBoundsChangeTransition = null;
            mFinishCallback = finishCallback;
            return mBoundsChangeHandler.startAnimation(transition, info, startTransaction,
                    finishTransaction, (wct) -> finishTransition());
        }

        if (mRemoveHandler.startAnimation(transition, info, startTransaction, finishTransaction,
                finishCallback)) {
            return true;
        }

        if (shouldCleanUp(info)) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "Cleaning up previously pinned task since in a different windowing mode: %s",
                    mPipTransitionState);
            cleanUpState(finishCallback);
            return true;
        }

        // For any unhandled transition, make sure the PiP surface is properly updated,
        // i.e. corner and shadow radius.
        syncPipSurfaceState(info, startTransaction, finishTransaction);
        return false;
    }

    private boolean shouldCleanUp(TransitionInfo info) {
        // Clean up state if task no longer in PIP windowing mode.
        TransitionInfo.Change pipChange = getChangeByToken(info,
                mPipTransitionState.getPipTaskToken());
        return pipChange != null && pipChange.getTaskInfo() != null
                && pipChange.getTaskInfo().getWindowingMode() != WINDOWING_MODE_PINNED;
    }

    @Override
    public void cleanUpState() {
        cleanUpState(/* finishCallback= */ null);
    }

    private void cleanUpState(@Nullable Transitions.TransitionFinishCallback finishCallback) {
        mFinishCallback = finishCallback;
        mPipBoundsState.setLastPipComponentName(null /* lastPipComponentName */);
        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        finishTransition();
    }

    @Override
    public boolean requestHasPipEnter(@NonNull TransitionRequestInfo request) {
        return request.getType() == TRANSIT_PIP || request.getPipChange() != null;
    }

    @Override
    public boolean isEnteringPip(@NonNull TransitionInfo.Change change,
            @WindowManager.TransitionType int transitType) {
        if (mPipTransitionState.getState() == PipTransitionState.SCHEDULED_ENTER_PIP
                && change.getTaskInfo() != null
                && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED) {
            // TRANSIT_TO_FRONT, though uncommon with triggering PiP, should semantically also
            // be allowed to animate if the task in question is pinned already - see b/308054074.
            if (transitType == TRANSIT_PIP || transitType == TRANSIT_OPEN
                    || transitType == TRANSIT_TO_FRONT) {
                return true;
            }
            // This can happen if the request to enter PIP happens when we are collecting for
            // another transition, such as TRANSIT_CHANGE (display rotation).
            if (transitType == TRANSIT_CHANGE) {
                return true;
            }

            // Please file a bug to handle the unexpected transition type.
            android.util.Slog.e(TAG, "Found new PIP in transition with mis-matched type="
                    + transitTypeToString(transitType), new Throwable());
        }
        return false;
    }


    @Override
    public void end() {
        if (mTransitionAnimator != null && mTransitionAnimator.isRunning()) {
            mTransitionAnimator.end();
            mTransitionAnimator = null;
        }
    }

    @Override
    public boolean syncPipSurfaceState(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        final TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) return false;

        // add shadow and corner radii
        final SurfaceControl leash = pipChange.getLeash();
        final boolean isInPip = mPipTransitionState.isInPip();

        mPipSurfaceTransactionHelper.round(startTransaction, leash, isInPip)
                .shadow(startTransaction, leash, isInPip);
        mPipSurfaceTransactionHelper.round(finishTransaction, leash, isInPip)
                .shadow(finishTransaction, leash, isInPip);

        return true;
    }

    //
    // Various helpers to resolve transition requests and infos
    //

    private void prepareOtherTargetTransforms(TransitionInfo info,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction) {
        // For opening type transitions, if there is a change of mode TO_FRONT/OPEN,
        // make sure that change has alpha of 1f, since it's init state might be set to alpha=0f
        // by the Transitions framework to simplify Task opening transitions.
        if (TransitionUtil.isOpeningType(info.getType())) {
            for (TransitionInfo.Change change : info.getChanges()) {
                if (change.getLeash() == null) continue;
                if (TransitionUtil.isOpeningMode(change.getMode())) {
                    startTransaction.setAlpha(change.getLeash(), 1f);
                }
            }
        }
    }

    /**
     * This is called by [startAnimation] when a enter PiP transition is received, and before
     * mPipTransitionState is updated with the incoming PiP task info. If a change is found
     * for the previous PiP with change TO_BACK, the previous PiP was dismissed by Core. We want to
     * update the state in PipTransitionState so everything is cleaned up and also ensure the
     * previous PiP is no longer visible.
     */
    private void cleanUpPrevPipIfPresent(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTx,
            @NonNull SurfaceControl.Transaction finishTx) {
        TransitionInfo.Change previousPipChange = null;
        TaskInfo previousPipTaskInfo = mPipTransitionState.getPipTaskInfo();
        if (previousPipTaskInfo == null) {
            return;
        }

        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().getTaskId() == previousPipTaskInfo.getTaskId()
                    && TransitionUtil.isClosingMode(change.getMode())) {
                previousPipChange = change;
                break;
            }
        }

        if (previousPipChange == null) {
            return;
        }

        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                "cleanUpPrevPipIfPresent: Previous PiP with taskId=%d found with closing mode, "
                        + "clean up PiP state",
                previousPipTaskInfo.getTaskId());

        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        mPipTransitionState.setState(PipTransitionState.EXITED_PIP);
        startTx.setAlpha(previousPipChange.getLeash(), 0);
        finishTx.setAlpha(previousPipChange.getLeash(), 0);
    }

    /**
     * Sets the type of animation to run upon entering PiP.
     *
     * By default, {@link PipTransition} uses various signals from Transitions to figure out
     * the animation type. But we should provide the ability to override this animation type to
     * mixed handlers, for instance, as they can split up and modify incoming {@link TransitionInfo}
     * to pass it onto multiple {@link Transitions.TransitionHandler}s.
     */
    @Override
    public void setEnterAnimationType(@AnimationType int type) {
        mEnterHandler.setEnterAnimationType(type);
    }

    //
    // Miscellaneous callbacks and listeners
    //

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
            Log.wtf(TAG, String.format("""
                        PipTransitionState resolved to an undefined state in finishTransition().
                        callers=%s""", Debug.getCallers(4)));
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
    public void onTransitionAborted() {
        final int currentState = mPipTransitionState.getState();
        int nextState = PipTransitionState.UNDEFINED;
        switch (currentState) {
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                nextState = PipTransitionState.CHANGED_PIP_BOUNDS;
                break;
            case PipTransitionState.SCHEDULED_ENTER_PIP:
                if (mPipTransitionState.getPipTaskToken() != null) {
                    nextState = PipTransitionState.ENTERED_PIP;
                } else {
                    removePipCandidateTaskIfNeeded();
                    nextState = PipTransitionState.EXITED_PIP;
                }
                break;
        }

        if (nextState == PipTransitionState.UNDEFINED) {
            Log.wtf(TAG, String.format("""
                        PipTransitionState resolved to an undefined state in abortTransition().
                        callers=%s""", Debug.getCallers(4)));
        }

        mPipTransitionState.setState(nextState);
    }

    private void removePipCandidateTaskIfNeeded() {
        if (mPipTransitionState.getState() != PipTransitionState.SCHEDULED_ENTER_PIP
                || mPipTransitionState.getPipCandidateTaskInfo() == null
                || mPipTransitionState.getPipCandidateTaskInfo().getToken() == null) {
            return;
        }

        // Enter PiP was scheduled but PiP handler didn't handle it properly.
        // So try to remove the PiP candidate we had received via transition request,
        // because Core might have put the activity in PiP and not resolved the task as a target
        // (this could happen if the display is asleep, which disqualifies PiP task as invisible).
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.removeTask(mPipTransitionState.getPipCandidateTaskInfo().getToken());
        mTransitions.startTransition(TRANSIT_CLOSE, wct, null);
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
                        "Unexpected bundle for " + mPipTransitionState);
                break;
            case PipTransitionState.ENTERED_PIP:
                mPipInteractionHandler.end();
                break;
            case PipTransitionState.EXITED_PIP:
                mPipTransitionState.setPinnedTaskLeash(null);
                mPipTransitionState.setPipTaskInfo(null);
                mPipTransitionState.setPipCandidateTaskInfo(null);
                break;
        }
    }

    @Override
    public boolean isPackageActiveInPip(@Nullable String packageName) {
        final TaskInfo inPipTask = mPipTransitionState.getPipTaskInfo();
        return packageName != null && inPipTask != null && mPipTransitionState.isInPip()
                && packageName.equals(ComponentUtils.getPackageName(inPipTask.baseIntent));
    }

    @Override
    public boolean isTaskActiveInPip(int taskId) {
        final TaskInfo inPipTask = mPipTransitionState.getPipTaskInfo();
        return inPipTask != null && mPipTransitionState.isInPip()
                && taskId == inPipTask.getTaskId();
    }
}