/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.transition;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemProperties;
import android.view.animation.Transformation;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.Locale;

/**
 * Keeps track of the animation of a single window/container.
 */
class WindowAnimation {
    private static final boolean DEBUG_WINDOW_ANIMATION_STATE = Build.IS_DEBUGGABLE
            && SystemProperties.getBoolean("persist.wm.debug.window_animation_state", false);

    @NonNull
    final TransitionInfo.Change mChange;
    final float mCornerRadius;
    private final WindowAnimationState mWindowAnimationState = new WindowAnimationState();

    @Nullable
    private Animator mAnimator;
    @Nullable
    Transformation mTransformation;

    WindowAnimation(@NonNull TransitionInfo.Change change, float cornerRadius) {
        mChange = change;
        mCornerRadius = cornerRadius;
    }

    void setAnimator(@Nullable Animator animator) {
        mAnimator = animator;
    }

    @Nullable
    Animator getAnimator() {
        return mAnimator;
    }

    void start() {
        if (mAnimator != null) {
            mAnimator.start();
        }
    }

    void end() {
        if (mAnimator != null) {
            mAnimator.end();
        }
    }

    void cancelRemoveListeners() {
        if (mAnimator != null) {
            if (mAnimator instanceof ValueAnimator) {
                // Remove all update listeners so we don't trigger a jump to end state
                ((ValueAnimator) mAnimator).removeAllUpdateListeners();
            }
            mAnimator.cancel();
        }
    }

    void setTransformation(@Nullable Transformation transformation) {
        mTransformation = transformation;
    }

    /**
     * Builds a {@link WindowAnimationState} based on the current transformation and
     * animation properties.
     *
     * @return A {@link WindowAnimationState} representing the current state of the window
     * animation.
     */
    WindowAnimationState getWindowAnimationState() {
        PointF position = new PointF();
        PointF scale = new PointF();
        RectF currentBoundsF = getTransformComponents(position, scale);

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "getWindowAnimationState: capture bounds=%s scale=%s pos=%s corner=%f",
                currentBoundsF, scale, position, mCornerRadius);

        mWindowAnimationState.bounds = currentBoundsF;
        // We assume that the scaling is uniform eg. scale.x == scale.y
        mWindowAnimationState.scale = scale.x;
        // We assume that the corners are the same everywhere
        mWindowAnimationState.bottomLeftRadius = mCornerRadius;
        mWindowAnimationState.topLeftRadius = mCornerRadius;
        mWindowAnimationState.bottomRightRadius = mCornerRadius;
        mWindowAnimationState.topRightRadius = mCornerRadius;
        mWindowAnimationState.timestamp = System.currentTimeMillis();
        // TODO: Calculate velocity from previous state
        mWindowAnimationState.velocityPxPerMs = new PointF(0, 0);
        if (DEBUG_WINDOW_ANIMATION_STATE) {
            logWindowAnimationState(currentBoundsF, position, scale);
        }
        return mWindowAnimationState;
    }

    private void logWindowAnimationState(RectF currentBoundsF, PointF position, PointF scale) {
        final float[] matrix = new float[9];
        mTransformation.getMatrix().getValues(matrix);

        final String animationInfo;
        if (mAnimator != null) {
            animationInfo = String.format(Locale.ROOT, "%s { duration=%d, startDelay=%d, "
                            + "isRunning=%b, "
                            + "isStarted=%b }",
                    mAnimator.getClass().getSimpleName(),
                    mAnimator.getDuration(),
                    mAnimator.getStartDelay(),
                    mAnimator.isRunning(),
                    mAnimator.isStarted());
        } else {
            animationInfo = "null";
        }

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                """
                        getWindowAnimationState() called:
                          mAnimation: %s
                          bounds: %s
                          matrix: [%f, %f, %f, %f, %f, %f, %f, %f, %f]
                        """,
                animationInfo,
                currentBoundsF,
                matrix[0], matrix[1], matrix[2],
                matrix[3], matrix[4], matrix[5],
                matrix[6], matrix[7], matrix[8]);

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                """
                          Matrix Components: { positionX=%f, positionY=%f, scaleX=%f, scaleY=%f }
                        WindowAnimationState: bounds=%s, scale=%f, cornerRadius=%f, timestamp=%d,
                         velocityPxPerMs=[%f, %f],
                        """,
                position.x, position.y, scale.x, scale.y,
                mWindowAnimationState.bounds,
                mWindowAnimationState.scale,
                mCornerRadius,
                mWindowAnimationState.timestamp,
                mWindowAnimationState.velocityPxPerMs.x,
                mWindowAnimationState.velocityPxPerMs.y);
    }

    private RectF getTransformComponents(PointF position, PointF scale) {
        if (mTransformation == null) {
            position.set(-1, -1);
            scale.set(-1, -1);
            return new RectF();
        }

        /* Apply the current transformation based on the starting bounds of the change */
        float w = mChange.getStartAbsBounds().width();
        float h = mChange.getStartAbsBounds().height();
        RectF currentBoundsF = new RectF(0, 0, w, h);
        mTransformation.getMatrix().mapRect(currentBoundsF);

        final float[] matrix = new float[9];
        mTransformation.getMatrix().getValues(matrix);

        position.x = matrix[Matrix.MTRANS_X];
        position.y = matrix[Matrix.MTRANS_Y];

        // Calculate scale magnitudes, robust to rotation/skew.
        scale.x = (float) Math.hypot(matrix[Matrix.MSCALE_X],
                matrix[Matrix.MSKEW_X]);
        scale.y = (float) Math.hypot(matrix[Matrix.MSKEW_Y],
                matrix[Matrix.MSCALE_Y]);

        return currentBoundsF;
    }
}
