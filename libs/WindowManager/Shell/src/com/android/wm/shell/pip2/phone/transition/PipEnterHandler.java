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

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PIP;

import static com.android.wm.shell.pip.PipTransitionController.ANIM_TYPE_ALPHA;
import static com.android.wm.shell.pip.PipTransitionController.ANIM_TYPE_BOUNDS;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.findFixedRotationChange;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getChangeByToken;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getFixedRotationDelta;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getLeash;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipChange;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipParams;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;

import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.desktopmode.DesktopPipTransitionController;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.pip2.animation.PipEnterAnimator;
import com.android.wm.shell.pip2.phone.PipAppIconOverlay;
import com.android.wm.shell.pip2.phone.PipInteractionHandler;
import com.android.wm.shell.pip2.phone.PipScheduler;
import com.android.wm.shell.pip2.phone.PipTaskListener;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

import java.util.Optional;

/**
 * Handles enter PiP transitions.
 */
public class PipEnterHandler implements Transitions.TransitionHandler {
    private static final String TAG = PipEnterHandler.class.getSimpleName();
    // Used when for ENTERING_PIP state update.
    private static final String PIP_TASK_LEASH = "pip_task_leash";
    private static final String PIP_TASK_INFO = "pip_task_info";

    private final Context mContext;
    private final PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private final PipBoundsState mPipBoundsState;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final PipTransitionState mPipTransitionState;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipDesktopState mPipDesktopState;
    private final PipTaskListener mPipTaskListener;
    private final PipScheduler mPipScheduler;
    private final Optional<DesktopPipTransitionController> mDesktopPipTransitionController;
    private final ContentPipHandler mContentPipHandler;
    private final PipInteractionHandler mPipInteractionHandler;
    private final DisplayController mDisplayController;

    @Nullable
    private Transitions.TransitionFinishCallback mFinishCallback;
    @Nullable
    private ValueAnimator mTransitionAnimator;

    private @PipTransitionController.AnimationType int mEnterAnimationType = ANIM_TYPE_BOUNDS;

    /**
     * @param context the application context.
     * @param pipSurfaceTransactionHelper a helper to manage surface transactions.
     * @param pipBoundsState the state of the PiP bounds.
     * @param pipBoundsAlgorithm the algorithm to calculate PiP bounds.
     * @param pipTransitionState the state of the PiP transition.
     * @param pipDisplayLayoutState the state of the display layout.
     * @param pipDesktopState the state of the desktop.
     * @param pipTaskListener a listener for PiP task events.
     * @param pipScheduler a scheduler for PiP transitions.
     * @param desktopPipTransitionController a controller for desktop PiP transitions.
     * @param contentPipHandler a handler for content PiP.
     * @param pipInteractionHandler a handler for PiP interactions.
     * @param displayController a controller for the display.
     */
    public PipEnterHandler(Context context,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipBoundsState pipBoundsState,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipTransitionState pipTransitionState,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipDesktopState pipDesktopState,
            PipTaskListener pipTaskListener,
            PipScheduler pipScheduler,
            Optional<DesktopPipTransitionController> desktopPipTransitionController,
            ContentPipHandler contentPipHandler,
            PipInteractionHandler pipInteractionHandler,
            DisplayController displayController) {
        mContext = context;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mPipBoundsState = pipBoundsState;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipTransitionState = pipTransitionState;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDesktopState = pipDesktopState;
        mPipTaskListener = pipTaskListener;
        mPipScheduler = pipScheduler;
        mDesktopPipTransitionController = desktopPipTransitionController;
        mContentPipHandler = contentPipHandler;
        mPipInteractionHandler = pipInteractionHandler;
        mDisplayController = displayController;
    }

    /**
     * Handles a transition request.
     *
     * @param transition the transition to handle.
     * @param request the request to handle.
     * @return a {@link WindowContainerTransaction} to apply, or {@code null} if the request is not
     * handled.
     */
    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (mPipTransitionState.getState() == PipTransitionState.SCHEDULED_ENTER_PIP) {
            // An enter PiP transition has already been scheduled and is waiting to be played.
            return null;
        }
        if (isAutoEnterInButtonNavigation(request) || isEnterPictureInPictureModeRequest(request)) {
            mPipTransitionState.setState(PipTransitionState.SCHEDULED_ENTER_PIP);
            final WindowContainerTransaction wct = getEnterPipTransaction(request.getPipChange());

            mDesktopPipTransitionController.ifPresent(
                    desktopPipTransitionController ->
                            desktopPipTransitionController.handlePipTransition(
                                    wct,
                                    transition,
                                    request.getPipChange().getTaskInfo()
                            )
            );
            return wct;
        }
        return null;
    }

    /**
     * This is not part of the TransitionHandler interface, but it is used by PipTransition to
     * handle augment requests.
     * @return true if the request was handled.
     */
    public boolean augmentRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWct) {
        if (isAutoEnterInButtonNavigation(request) || isEnterPictureInPictureModeRequest(request)) {
            outWct.merge(getEnterPipTransaction(request.getPipChange()),
                    true /* transfer */);
            mPipTransitionState.setState(PipTransitionState.SCHEDULED_ENTER_PIP);
            return true;
        }
        return false;
    }

    /**
     * Starts an animation for a transition.
     *
     * @param transition the transition to start an animation for.
     * @param info the transition info.
     * @param startTransaction the start transaction.
     * @param finishTransaction the finish transaction.
     * @param finishCallback a callback to call when the animation is finished.
     * @return {@code true} if the animation was started.
     */
    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // If we are in swipe PiP to Home transition we are ENTERING_PIP as a jumpcut transition
        // is being carried out.
        TransitionInfo.Change pipChange = getPipChange(info);

        // If there is no PiP change, exit this transition handler and potentially try others.
        if (pipChange == null) {
            Log.wtf(TAG, String.format("""
                    PipTransition did not find a PiP change despite waiting for a scheduled
                    enter PiP transition.
                    callers=%s""", Debug.getCallers(4)));
            finishCallback.onTransitionFinished(null);
            return true;
        }

        // Update the PipTransitionState while supplying the PiP leash and token to be cached.
        Bundle extra = new Bundle();
        extra.putParcelable(PIP_TASK_LEASH, pipChange.getLeash());
        extra.putParcelable(PIP_TASK_INFO, pipChange.getTaskInfo());
        mPipTransitionState.setState(PipTransitionState.ENTERING_PIP, extra);

        // TRUSTED_OVERLAY is granted iff Shell successfully receives the transition.
        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                "Set TRUSTED_OVERLAY for Task#%d", pipChange.getTaskInfo().taskId);
        startTransaction.setTrustedOverlay(pipChange.getLeash(), true);

        if (mPipTransitionState.isInSwipePipToHomeTransition()) {
            // If this is the second transition as a part of swipe PiP to home cuj,
            // handle this transition as a special case with no-op animation.
            return handleSwipePipToHomeTransition(info, startTransaction, finishTransaction,
                    finishCallback);
        }
        if (mContentPipHandler.startAnimation(transition, info,
                startTransaction, finishTransaction, finishCallback)) {
            return true;
        }
        if (isLegacyEnter(info)) {
            // If this is a legacy-enter-pip (auto-enter is off and PiP activity went to pause),
            // then we should run an ALPHA type (cross-fade) animation.
            return startAlphaTypeEnterAnimation(info, startTransaction, finishTransaction,
                    finishCallback);
        }

        TransitionInfo.Change pipActivityChange = PipTransitionUtils
                .getDeferConfigActivityChange(info, pipChange.getTaskInfo().getToken());
        if (pipActivityChange == null) {
            // Legacy-enter and swipe-pip-to-home filters did not resolve a scheduled PiP entry.
            // Bounds-type enter animation is the last resort, and it requires a config-at-end
            // activity amongst the list of changes. If no such change, something went wrong.
            Log.wtf(TAG, String.format("""
                    PipTransition.startAnimation didn't handle a scheduled PiP entry
                    transitionInfo=%s,
                    callers=%s""", info, Debug.getCallers(4)));
            return false;
        }

        return startBoundsTypeEnterAnimation(info, startTransaction, finishTransaction,
                finishCallback);
    }

    private boolean handleSwipePipToHomeTransition(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }

        // We expect the PiP activity as a separate change in a config-at-end transition.
        TransitionInfo.Change pipActivityChange = PipTransitionUtils.getDeferConfigActivityChange(
                info, pipChange.getTaskInfo().getToken());
        if (pipActivityChange == null) {
            Log.wtf(TAG, String.format("""
                        PipEnterHandler.handleSwipePipToHomeTransition() didn't detect
                        a config-at-end PiP activity, so activity leash manipulations are skipped.
                        transitionInfo=%s, callers=%s""", info, Debug.getCallers(4)));
        }
        mFinishCallback = finishCallback;

        final SurfaceControl pipLeash = getLeash(pipChange);
        final Rect destinationBounds = pipChange.getEndAbsBounds();
        final SurfaceControl swipePipToHomeOverlay = mPipTransitionState.getSwipePipToHomeOverlay();
        if (swipePipToHomeOverlay != null) {
            final int overlaySize = PipAppIconOverlay.getOverlaySize(
                    mPipTransitionState.getSwipePipToHomeAppBounds(), destinationBounds);
            // It is possible we reparent the PIP activity to a new PIP task (in multi-activity
            // apps), so we should also reparent the overlay to the final PIP task.
            startTransaction.reparent(swipePipToHomeOverlay, pipLeash)
                    .setLayer(swipePipToHomeOverlay, Integer.MAX_VALUE)
                    .setScale(swipePipToHomeOverlay, 1f, 1f)
                    .setPosition(swipePipToHomeOverlay,
                            (destinationBounds.width() - overlaySize) / 2f,
                            (destinationBounds.height() - overlaySize) / 2f);
        }

        final int delta = getFixedRotationDelta(info, pipChange, mPipDisplayLayoutState);
        if (delta != ROTATION_0) {
            // Update transition target changes in place to prepare for fixed rotation.
            updatePipChangesForFixedRotation(info, pipChange, pipActivityChange);
        }

        // Update the src-rect-hint in params in place, to set up initial animator transform.
        Rect sourceRectHint = getAdjustedSourceRectHint(info, pipChange, pipActivityChange);
        final PictureInPictureParams params = getPipParams(pipChange);
        params.copyOnlySet(
                new PictureInPictureParams.Builder().setSourceRectHint(sourceRectHint).build());

        if (pipActivityChange != null) {
            // Config-at-end transitions need to have their activities transformed before starting
            // the animation; this makes the buffer seem like it's been updated to final size.
            PipTransitionUtils.prepareConfigAtEndActivity(startTransaction, finishTransaction,
                    pipChange, pipActivityChange);
        }

        startTransaction.merge(finishTransaction);
        PipEnterAnimator animator = new PipEnterAnimator(mContext, mPipSurfaceTransactionHelper,
                pipLeash,
                startTransaction, finishTransaction, destinationBounds, delta);
        animator.setEnterStartState(pipChange);
        animator.onEnterAnimationUpdate(1.0f /* fraction */, startTransaction);
        startTransaction.apply();

        if (swipePipToHomeOverlay != null) {
            // fadeout the overlay if needed.
            mPipScheduler.startOverlayFadeoutAnimation(swipePipToHomeOverlay,
                    true /* withStartDelay */, () -> {
                        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
                        tx.remove(swipePipToHomeOverlay);
                        tx.apply();
                    });
        }
        finishTransition();
        return true;
    }

    private boolean startBoundsTypeEnterAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }

        // We expect the PiP activity as a separate change in a config-at-end transition.
        TransitionInfo.Change pipActivityChange = PipTransitionUtils.getDeferConfigActivityChange(
                info, pipChange.getTaskInfo().getToken());
        if (pipActivityChange == null) {
            return false;
        }

        // In multi-activity case, set the parent's leash to invisible while we're animating to PiP
        TransitionInfo.Change parentBeforePip = pipActivityChange.getLastParent() != null
                ? getChangeByToken(info, pipActivityChange.getLastParent()) : null;
        if (parentBeforePip != null && TransitionUtil.isClosingMode(parentBeforePip.getMode())) {
            startTransaction.setAlpha(parentBeforePip.getLeash(), 0);
        }

        mFinishCallback = finishCallback;

        final SurfaceControl pipLeash = getLeash(pipChange);
        final Rect startBounds = pipChange.getStartAbsBounds();
        final Rect endBounds = pipChange.getEndAbsBounds();
        final PictureInPictureParams params = getPipParams(pipChange);
        final Rect adjustedSourceRectHint = getAdjustedSourceRectHint(info, pipChange,
                pipActivityChange);

        final int delta = getFixedRotationDelta(info, pipChange, mPipDisplayLayoutState);
        if (delta != ROTATION_0) {
            // Update transition target changes in place to prepare for fixed rotation.
            updatePipChangesForFixedRotation(info, pipChange, pipActivityChange);
        }

        PipEnterAnimator animator = new PipEnterAnimator(mContext, mPipSurfaceTransactionHelper,
                pipLeash,
                startTransaction, finishTransaction, endBounds, delta);
        if (PipBoundsAlgorithm.getValidSourceHintRect(params, startBounds, endBounds) == null) {
            // If app provided src-rect-hint is invalid, use app icon overlay.
            animator.setAppIconContentOverlay(
                    mContext, startBounds, endBounds, pipChange.getTaskInfo().topActivityInfo,
                    mPipBoundsState.getLauncherState().getAppIconSizePx());
        }

        // Update the src-rect-hint in params in place, to set up initial animator transform.
        params.copyOnlySet(new PictureInPictureParams.Builder()
                .setSourceRectHint(adjustedSourceRectHint).build());

        // Config-at-end transitions need to have their activities transformed before starting
        // the animation; this makes the buffer seem like it's been updated to final size.
        PipTransitionUtils.prepareConfigAtEndActivity(startTransaction, finishTransaction,
                pipChange, pipActivityChange);

        animator.setAnimationStartCallback(() -> {
            animator.setEnterStartState(pipChange);
            mPipInteractionHandler.begin(pipLeash, PipInteractionHandler.INTERACTION_ENTER_PIP);
        });
        animator.setAnimationEndCallback(() -> {
            if (animator.getContentOverlayLeash() != null) {
                mPipScheduler.startOverlayFadeoutAnimation(animator.getContentOverlayLeash(),
                        true /* withStartDelay */, animator::clearAppIconOverlay);
            }
            finishTransition();
            mPipInteractionHandler.end();
        });
        cacheAndStartTransitionAnimator(animator);
        return true;
    }

    private void updatePipChangesForFixedRotation(TransitionInfo info,
            TransitionInfo.Change outPipTaskChange,
            @Nullable TransitionInfo.Change outPipActivityChange) {
        final TransitionInfo.Change fixedRotationChange = findFixedRotationChange(info);
        final Rect outEndBounds = outPipTaskChange.getEndAbsBounds();

        final Rect outEndActivityBounds = outPipActivityChange != null
                ? outPipActivityChange.getEndAbsBounds() : null;
        if (outEndActivityBounds == null) {
            // This can happen in legacy alpha animation path.
            return;
        }
        // Cache the task to activity offset to potentially restore later.
        final Point activityEndOffset = outEndActivityBounds != null ? new Point(
                outEndActivityBounds.left - outEndBounds.left,
                outEndActivityBounds.top - outEndBounds.top) : null;

        int startRotation = outPipTaskChange.getStartRotation();
        int endRotation = fixedRotationChange != null
                ? fixedRotationChange.getEndFixedRotation() : mPipDisplayLayoutState.getRotation();

        if (startRotation == endRotation) {
            return;
        }

        // This is used by display change listeners to respond properly to fixed rotation.
        mPipTransitionState.setInFixedRotation(true);

        // If we are running a fixed rotation bounds enter PiP animation,
        // then update the display layout rotation, and recalculate the end rotation bounds.
        // Update the endBounds in place, so that the PiP change is up-to-date.
        mPipDisplayLayoutState.rotateTo(endRotation);
        float snapFraction = mPipBoundsAlgorithm.getSnapFraction(
                mPipBoundsAlgorithm.getEntryDestinationBounds());
        mPipBoundsAlgorithm.applySnapFraction(outEndBounds, snapFraction);
        mPipBoundsState.setBounds(outEndBounds);

        // Display bounds were already updated to represent the final orientation,
        // so we just need to readjust the origin, and perform rotation about (0, 0).
        boolean isClockwise = (endRotation - startRotation) == -ROTATION_270;
        Rect displayBounds = mPipDisplayLayoutState.getDisplayBounds();
        int originTranslateX = isClockwise ? 0 : -displayBounds.width();
        int originTranslateY = isClockwise ? -displayBounds.height() : 0;
        outEndBounds.offset(originTranslateX, originTranslateY);

        if (activityEndOffset != null) {
            // Update the activity end bounds in place as well, as this is used for transform
            // calculation later.
            outEndActivityBounds.offsetTo(outEndBounds.left + activityEndOffset.x,
                    outEndBounds.top + activityEndOffset.y);
        }
    }

    private boolean startAlphaTypeEnterAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        mFinishCallback = finishCallback;

        final Rect destinationBounds = pipChange.getEndAbsBounds();
        if (pipChange.getEndRotation() != ROTATION_UNDEFINED
                && pipChange.getStartRotation() != pipChange.getEndRotation()) {
            // If we are playing an enter PiP animation with display change collected together
            // in the same transition, then PipController#onDisplayChange() must have already
            // updated the PiP bounds state to reflect the final desired destination bounds.
            // This might not be in the WM state yet as PiP task token might have been null then.
            // WM state will be updated via a follow-up bounds change transition after.
            destinationBounds.set(mPipBoundsState.getBounds());
        }

        final SurfaceControl pipLeash = mPipTransitionState.getPinnedTaskLeash();
        if (pipLeash == null) {
            Log.w(TAG, "Leash is null for alpha transition.");
            finishTransition();
            return true;
        }

        // Note that fixed rotation is different from the same transition display change rotation;
        // with fixed rotation, we expect a follow-up async rotation transition after this one.
        final int delta = getFixedRotationDelta(info, pipChange, mPipDisplayLayoutState);
        if (delta != ROTATION_0) {
            updatePipChangesForFixedRotation(info, pipChange,
                    // We don't have an activity change to animate in legacy enter,
                    // so just use a placeholder one as the outPipActivityChange.
                    new TransitionInfo.Change(null /* container */, new SurfaceControl()));
        }
        startTransaction.setWindowCrop(pipLeash,
                destinationBounds.width(), destinationBounds.height());
        if (delta != ROTATION_0) {
            // In a fixed rotation case, rotate PiP leash in the old orientation to its final
            // position, but keep the bounds visually invariant until async rotation changes
            // the display rotation after
            int normalizedRotation = delta;
            if (normalizedRotation == ROTATION_270) {
                normalizedRotation = -ROTATION_90;
            }
            Matrix transformTensor = new Matrix();
            final float[] matrixTmp = new float[9];
            transformTensor.setTranslate(destinationBounds.left, destinationBounds.top);
            transformTensor.postRotate(-normalizedRotation * 90f);

            startTransaction.setMatrix(pipLeash, transformTensor, matrixTmp);
            finishTransaction.setMatrix(pipLeash, transformTensor, matrixTmp);
        } else {
            startTransaction.setPosition(pipLeash, destinationBounds.left, destinationBounds.top);
            finishTransaction.setPosition(pipLeash, destinationBounds.left, destinationBounds.top);
        }

        PipAlphaAnimator animator = new PipAlphaAnimator(mContext, mPipSurfaceTransactionHelper,
                pipLeash, startTransaction,
                finishTransaction, PipAlphaAnimator.FADE_IN);
        animator.setAnimationStartCallback(() -> mPipInteractionHandler.begin(pipLeash,
                PipInteractionHandler.INTERACTION_ENTER_PIP));
        // This should update the pip transition state accordingly after we stop playing.
        animator.setAnimationEndCallback(() -> {
            finishTransition();
            mPipInteractionHandler.end();
        });
        cacheAndStartTransitionAnimator(animator);
        return true;
    }

    @NonNull
    private Rect getAdjustedSourceRectHint(@NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change pipTaskChange,
            @Nullable TransitionInfo.Change pipActivityChange) {
        final Rect startBounds = pipTaskChange.getStartAbsBounds();
        final Rect endBounds = pipTaskChange.getEndAbsBounds();
        final PictureInPictureParams params = pipTaskChange.getTaskInfo().pictureInPictureParams;

        // Get the source-rect-hint provided by the app and check its validity; null if invalid.
        final Rect sourceRectHint = PipBoundsAlgorithm.getValidSourceHintRect(params, startBounds,
                endBounds);

        final Rect adjustedSourceRectHint = new Rect();
        if (sourceRectHint != null && pipActivityChange != null) {
            adjustedSourceRectHint.set(sourceRectHint);
            // If multi-activity PiP, use the parent task before PiP to retrieve display cutouts;
            // then, offset the valid app provided source rect hint by the cutout insets.
            // For single-activity PiP, just use the pinned task to get the cutouts instead.
            TransitionInfo.Change parentBeforePip = pipActivityChange.getLastParent() != null
                    ? getChangeByToken(info, pipActivityChange.getLastParent()) : null;
            Rect cutoutInsets = parentBeforePip != null
                    ? parentBeforePip.getTaskInfo().displayCutoutInsets
                    : pipTaskChange.getTaskInfo().displayCutoutInsets;
            if (cutoutInsets != null && getFixedRotationDelta(info, pipTaskChange,
                    mPipDisplayLayoutState) == ROTATION_90) {
                adjustedSourceRectHint.offset(cutoutInsets.left, cutoutInsets.top);
            }
            if (mPipDesktopState.isDesktopWindowingPipEnabled()) {
                adjustedSourceRectHint.offset(-pipActivityChange.getStartAbsBounds().left,
                        -pipActivityChange.getStartAbsBounds().top);
            }
        } else {
            // For non-valid app provided src-rect-hint, calculate one to crop into during
            // app icon overlay animation.
            float aspectRatio = mPipBoundsAlgorithm.getAspectRatioOrDefault(params);
            adjustedSourceRectHint.set(
                    PipUtils.getPseudoSourceRectHint(startBounds, aspectRatio));
        }
        return adjustedSourceRectHint;
    }

    private WindowContainerTransaction getEnterPipTransaction(
            @NonNull TransitionRequestInfo.PipChange pipChange) {
        // cache the original task token to check for multi-activity case later
        final ActivityManager.RunningTaskInfo pipTask = pipChange.getTaskInfo();
        mPipTransitionState.setPipCandidateTaskInfo(pipTask);

        PictureInPictureParams pipParams = pipTask.pictureInPictureParams;
        mPipTaskListener.setPictureInPictureParams(pipParams);
        mPipBoundsState.setBoundsStateForEntry(pipTask.topActivity, pipTask.topActivityInfo,
                pipParams, mPipBoundsAlgorithm);

        // If PiP is enabled on Connected Displays, update PipDisplayLayoutState to have the correct
        // display info that PiP is entering in.
        if (mPipDesktopState.isConnectedDisplaysPipEnabled()
                && pipTask.displayId != mPipDisplayLayoutState.getDisplayId()) {
            final DisplayLayout displayLayout = mDisplayController.getDisplayLayout(
                    pipTask.displayId);
            if (displayLayout != null) {
                mPipDisplayLayoutState.setDisplayId(pipTask.displayId);
                mPipDisplayLayoutState.setDisplayLayout(displayLayout);
            }
        }

        if (!mPipTransitionState.isInSwipePipToHomeTransition()) {
            // Update the size spec in case aspect ratio is invariant, but display has changed
            // since the last PiP session, or this is the first PiP session altogether.
            // Skip the update if in swipe PiP to home, as this has already been done.
            mPipBoundsState.updateMinMaxSize(mPipBoundsState.getAspectRatio());
        }

        // calculate the entry bounds and notify core to move task to pinned with final bounds
        final Rect entryBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
        mPipBoundsState.setBounds(entryBounds);

        // Operate on the TF token in case we are dealing with AE case; this should avoid marking
        // activities in other TFs as config-at-end.
        WindowContainerToken token = pipChange.getTaskFragmentToken();
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.movePipActivityToPinnedRootTask(token, entryBounds);
        wct.deferConfigToTransitionEnd(token);
        return wct;
    }

    private boolean isAutoEnterInButtonNavigation(@NonNull TransitionRequestInfo requestInfo) {
        final ActivityManager.RunningTaskInfo pipTask = requestInfo.getPipChange() != null
                ? requestInfo.getPipChange().getTaskInfo() : null;
        if (pipTask == null) {
            return false;
        }
        if (pipTask.pictureInPictureParams == null) {
            return false;
        }

        // Since opening a new task while in Desktop Mode always first open in Fullscreen
        // until DesktopMode Shell code resolves it to Freeform, PipTransition will get a
        // possibility to handle it also. In this case return false to not have it enter PiP.
        if (mPipDesktopState.isPipInDesktopMode()) {
            return false;
        }

        // Assuming auto-enter is enabled and pipTask is non-null, the TRANSIT_OPEN request type
        // implies that we are entering PiP in button navigation mode. This is guaranteed by
        // TaskFragment#startPausing()` in Core which wouldn't get called in gesture nav.
        return requestInfo.getType() == TRANSIT_OPEN
                && pipTask.pictureInPictureParams.isAutoEnterEnabled();
    }

    private boolean isEnterPictureInPictureModeRequest(@NonNull TransitionRequestInfo requestInfo) {
        return requestInfo.getType() == TRANSIT_PIP;
    }

    private boolean isLegacyEnter(@NonNull TransitionInfo info) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange != null) {
            if (mEnterAnimationType == ANIM_TYPE_ALPHA) {
                // If enter animation type is force overridden to an alpha type,
                // treat this as legacy, and reset the animation type to default (i.e. bounds type).
                setEnterAnimationType(ANIM_TYPE_BOUNDS);
                return true;
            }

            // #getEnterPipTransaction() always attempts to mark PiP activity as config-at-end one.
            // However, the activity will only actually be marked config-at-end by Core if it is
            // both isVisible and isVisibleRequested, which is when we can run bounds animation.
            //
            // So we can use the absence of a config-at-end activity as a signal that we should run
            // a legacy-enter PiP animation instead.
            return (TransitionUtil.isOpeningMode(pipChange.getMode())
                    || pipChange.getMode() == TRANSIT_CHANGE)
                    && PipTransitionUtils.getDeferConfigActivityChange(
                    info, pipChange.getContainer()) == null;
        }
        return false;
    }

    /**
     * Sets the enter animation type.
     *
     * @param type the enter animation type.
     */
    public void setEnterAnimationType(@PipTransitionController.AnimationType int type) {
        mEnterAnimationType = type;
    }

    void cacheAndStartTransitionAnimator(@NonNull ValueAnimator animator) {
        mTransitionAnimator = animator;
        mTransitionAnimator.start();
    }

    /**
     * Finishes the transition.
     */
    public void finishTransition() {
        final int currentState = mPipTransitionState.getState();
        if (currentState != PipTransitionState.ENTERING_PIP) {
            Log.wtf(TAG, "finishTransition() called in a non-ENTERING_PIP state");
            return;
        }
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);

        if (mFinishCallback != null) {
            // Need to unset mFinishCallback first because onTransitionFinished can re-enter this
            // handler if there is a pending PiP animation.
            final Transitions.TransitionFinishCallback finishCallback = mFinishCallback;
            mFinishCallback = null;
            finishCallback.onTransitionFinished(null /* finishWct */);
        }
    }
}
