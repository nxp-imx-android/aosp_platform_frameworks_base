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

package com.android.extensions.computercontrol.view;

import android.annotation.MainThread;
import android.companion.virtual.computercontrol.InteractiveMirror;
import android.content.Context;
import android.content.res.Configuration;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.extensions.computercontrol.ComputerControlSession;

import java.util.Objects;

/**
 * A view which allows interactive mirroring of a given {@link ComputerControlSession}.
 */
@MainThread
public class MirrorView extends FrameLayout {

    private static final String TAG = MirrorView.class.getSimpleName();

    // MirrorView should only be used on secure and trusted displays.
    private static final int REQUIRED_DISPLAY_FLAGS = Display.FLAG_SECURE | Display.FLAG_TRUSTED;

    private final HandlerThread mHandlerThread = new HandlerThread("mirrorHelper");

    private final MirrorSurface mMirrorSurface = new MirrorSurface(mContext);

    @Nullable
    private ComputerControlSession mComputerControlSession = null;
    private boolean mIsInteractive = false;
    private boolean mIsMirrorSurfaceCreated = false;

    // This member can only be read or written to from the auxiliary handler thread,
    // since its creation and interactions involve binder calls.
    @Nullable
    private InteractiveMirror mInteractiveMirror = null;

    private float mLastCompoundedAlpha = -1f;

    private final ViewTreeObserver.OnPreDrawListener mOnPreDrawListener = () -> {
        final float compoundedAlpha = getCompoundedAlpha();

        if (compoundedAlpha != mLastCompoundedAlpha) {
            mLastCompoundedAlpha = compoundedAlpha;
            mMirrorSurface.setAlpha(mLastCompoundedAlpha);
        }
        return true;
    };

    public MirrorView(Context context) {
        super(context);
        init();
    }

    public MirrorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MirrorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public MirrorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    /**
     * Sets the {@link ComputerControlSession} to be mirrored, or {@code null} if nothing needs to
     * be shown.
     */
    public void setComputerControlSession(@Nullable ComputerControlSession computerControlSession) {
        if (mComputerControlSession == computerControlSession) {
            return;
        }
        mComputerControlSession = computerControlSession;

        updateMirrorSurfaceVisibility();
        if (mIsMirrorSurfaceCreated) {
            updateInteractiveMirrorOnAuxThread(computerControlSession, mIsInteractive);
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        updateMirrorSurfaceVisibility();
    }

    private void updateMirrorSurfaceVisibility() {
        mMirrorSurface.setVisibility(
                mComputerControlSession != null && isAggregatedVisible() ? VISIBLE : GONE);
    }

    /**
     * Initializes or closes the {@link InteractiveMirror} from an auxiliary thread.
     *
     * <p>This will run on an auxiliary thread because creating an {@link InteractiveMirror}
     * involves binder calls. Passing a null {@code computerControlSession} will close the
     * existing mirror. All read-writes of {@link #mInteractiveMirror} must happen from this
     * auxiliary thread.
     */
    private void updateInteractiveMirrorOnAuxThread(
            @Nullable ComputerControlSession requestedSession, boolean isInteractive) {
        final var session = isMirrorViewAllowedOnDisplay(getDisplay())
                ? requestedSession : null;

        mHandlerThread.getThreadExecutor().execute(() -> {
            if (mInteractiveMirror != null) {
                mInteractiveMirror.close();
            }
            final var interactiveMirror = session != null
                    ? session.createInteractiveMirror() : null;
            mInteractiveMirror = interactiveMirror;

            final SurfaceControl mirrorSurface;
            final Size size;
            if (session != null && interactiveMirror != null) {
                mirrorSurface = interactiveMirror.getMirrorSurfaceControl();
                size = session.getDisplaySize();
                if (isInteractive != InteractiveMirror.DEFAULT_INTERACTIVE) {
                    interactiveMirror.setInteractive(isInteractive);
                }
            } else {
                mirrorSurface = null;
                size = null;
            }

            post(() -> mMirrorSurface.setMirrorSurfaceControl(mirrorSurface, size));
        });
    }

    private static boolean isMirrorViewAllowedOnDisplay(@Nullable Display display) {
        if (display == null) {
            return false;
        }
        final DisplayInfo displayInfo = new DisplayInfo();
        display.getDisplayInfo(displayInfo);
        if ((displayInfo.flags & REQUIRED_DISPLAY_FLAGS) != REQUIRED_DISPLAY_FLAGS) {
            Log.e(TAG, "MirrorView cannot be used on this display - displayInfo: " + displayInfo);
            return false;
        }
        return true;
    }

    /**
     * Sets whether user input can control the session being mirrored. By default, this is set to
     * {@code false}.
     */
    public void setInteractive(boolean interactive) {
        if (interactive == mIsInteractive) {
            return;
        }
        mIsInteractive = interactive;
        mHandlerThread.getThreadExecutor().execute(() -> {
            if (mInteractiveMirror != null) {
                mInteractiveMirror.setInteractive(interactive);
            }
        });
    }

    /**
     * Sets the corner radius for all corners of the mirror view.
     *
     * @param cornerRadius The new radius of the corners in pixels.
     */
    public void setCornerRadius(int cornerRadius) {
        mMirrorSurface.setCornerRadius(cornerRadius);
    }

    /** @hide */
    @Override
    public void onMovedToDisplay(int displayId, Configuration config) {
        if (mIsMirrorSurfaceCreated) {
            // Attempt to recreate the interactive mirror based on the new display.
            updateInteractiveMirrorOnAuxThread(mComputerControlSession, mIsInteractive);
        }
    }

    private void init() {
        mHandlerThread.start();

        // Add a placeholder view that's always visible to prevent the MirrorView from collapsing
        // to a zero-size view when the mirror surface is GONE. The visibility of the SurfaceView
        // is controlled carefully for performance reasons to avoid creating or maintaining a
        // surface when a computer control session is not set.
        // TODO: b/448896612 - Reimplement this as an optimization within the SurfaceView.
        addView(new View(mContext), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        updateMirrorSurfaceVisibility();
        mMirrorSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                // The InteractiveMirror's lifecycle is tied to the underlying Surface. This
                // ensures the remote mirroring session is only active when this view is visible.
                mIsMirrorSurfaceCreated = true;
                getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);
                updateInteractiveMirrorOnAuxThread(mComputerControlSession, mIsInteractive);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                    int height) {
                mHandlerThread.getThreadExecutor().execute(() -> {
                    if (mInteractiveMirror != null) {
                        mInteractiveMirror.resize(width, height);
                    }
                });
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                mIsMirrorSurfaceCreated = false;
                getViewTreeObserver().removeOnPreDrawListener(mOnPreDrawListener);
                updateInteractiveMirrorOnAuxThread(null, false);
            }
        });
        addView(mMirrorSurface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    private float getCompoundedAlpha() {
        float compoundedAlpha = getAlpha();
        ViewParent current = getParent();

        while (current instanceof View) {
            compoundedAlpha *= ((View) current).getAlpha();
            current = current.getParent();
        }

        return compoundedAlpha;
    }

    @MainThread
    private static final class MirrorSurface extends SurfaceView {

        // This SurfaceControl is owned by this MirrorSurface.
        @Nullable
        private SurfaceControl mRequestedMirrorSurface = null;
        @Nullable
        private Size mDisplaySize = null;
        // This SurfaceControl is owned by this MirrorSurface.
        @Nullable
        private SurfaceControl mCurrentMirrorSurface = null;
        @Nullable
        private Transformation mCurrentTransformation = null;
        // Un-owned SurfaceControl that the current mirror surface was last reparented to. This
        // is a reference to the super class SurfaceView's mSurfaceControl, and must only be used
        // for detecting when the parent surface changes.
        @Nullable
        private SurfaceControl mPreviousParentSurface = null;

        MirrorSurface(Context context) {
            super(context);
            setCompositionOrder(0);
            setSurfaceLifecycle(SURFACE_LIFECYCLE_FOLLOWS_VISIBILITY);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            if (mDisplaySize == null || mDisplaySize.getWidth() == 0
                    || mDisplaySize.getHeight() == 0) {
                // No display size, or invalid. Let SurfaceView do its default thing.
                return;
            }

            final int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
            final int parentHeight = MeasureSpec.getSize(heightMeasureSpec);

            if (parentWidth == 0 || parentHeight == 0) {
                return;
            }

            final int displayWidth = mDisplaySize.getWidth();
            final int displayHeight = mDisplaySize.getHeight();

            final float viewAspectRatio = (float) parentWidth / parentHeight;
            final float displayAspectRatio = (float) displayWidth / displayHeight;

            final int surfaceWidth;
            final int surfaceHeight;

            if (viewAspectRatio > displayAspectRatio) {
                // The parent is wider than the display, so there will be pillarboxing.
                // The surface should be scaled to fit the height of the parent.
                surfaceHeight = parentHeight;
                surfaceWidth = (int) (surfaceHeight * displayAspectRatio);
            } else {
                // The parent is taller than or has the same aspect ratio as the display, so there
                // will be letterboxing. The surface should be scaled to fit the width of the
                // parent.
                surfaceWidth = parentWidth;
                surfaceHeight = (int) (surfaceWidth / displayAspectRatio);
            }

            setMeasuredDimension(surfaceWidth, surfaceHeight);
        }

        @Override
        protected void updateSurface() {
            super.updateSurface();
            updateMirrorSurface();
        }

        /**
         * Set the SurfaceControl of the content to show inside this MirrorView. If a non-null
         * mirrorSurfaceControl is provided, it must be a valid SurfaceControl (not yet released),
         * and this MirrorSurface will take ownership over its lifecycle. If a null
         * mirrorSurfaceControl is provided, mirroring will be stopped.
         */
        void setMirrorSurfaceControl(@Nullable SurfaceControl mirrorSurfaceControl,
                @Nullable Size displaySize) {
            if (mRequestedMirrorSurface != null) {
                mRequestedMirrorSurface.release();
            }
            mRequestedMirrorSurface = mirrorSurfaceControl;
            if (!Objects.equals(mDisplaySize, displaySize)) {
                mDisplaySize = displaySize;
                // Request a layout pass to recalculate the new aspect ratio and size for the view.
                requestLayout();
            }
            invalidate();
        }

        private void updateMirrorSurface() {
            final var parentSurface = getSurfaceControl();
            final boolean parentChanged = !isSame(parentSurface, mPreviousParentSurface);

            final Transformation requestedTransformation = computeTransformation();

            final boolean mirrorChanged = !isSame(mCurrentMirrorSurface, mRequestedMirrorSurface);
            final boolean transformationChanged =
                    !Objects.equals(mCurrentTransformation, requestedTransformation);
            if (!(parentChanged || mirrorChanged || transformationChanged)) {
                return;
            }

            try (var transaction = new SurfaceControl.Transaction()) {
                if (mirrorChanged) {
                    if (mCurrentMirrorSurface != null) {
                        transaction
                                .hide(mCurrentMirrorSurface)
                                .remove(mCurrentMirrorSurface);
                    }
                    mCurrentMirrorSurface = mRequestedMirrorSurface != null
                            ? new SurfaceControl(mRequestedMirrorSurface,
                            "MirrorView#updateMirrorSurface")
                            : null;
                }

                if (mCurrentMirrorSurface != null && (mirrorChanged || parentChanged)) {
                    transaction
                            .reparent(mCurrentMirrorSurface, parentSurface)
                            .show(mCurrentMirrorSurface);
                    mPreviousParentSurface = parentSurface;
                }

                if (mCurrentMirrorSurface != null) {
                    transaction
                            .setScale(mCurrentMirrorSurface, requestedTransformation.scale,
                                    requestedTransformation.scale)
                            .setPosition(mCurrentMirrorSurface,
                                    requestedTransformation.translateX,
                                    requestedTransformation.translateY);
                    mCurrentTransformation = requestedTransformation;
                } else {
                    mCurrentTransformation = null;
                }

                applyTransactionOnVriDraw(transaction);
            }
        }

        private static boolean isSame(@Nullable SurfaceControl lhs,
                @Nullable SurfaceControl rhs) {
            return lhs == rhs
                    || (lhs != null && rhs != null && lhs.isSameSurface(rhs));
        }

        @NonNull
        private Transformation computeTransformation() {
            if (mDisplaySize == null) {
                return Transformation.IDENTITY;
            }
            final var result = computerCenterFillTransformation(mDisplaySize.getWidth(),
                    mDisplaySize.getHeight(), getWidth(), getHeight());
            return result != null ? result : Transformation.IDENTITY;
        }

        private void applyTransactionOnVriDraw(SurfaceControl.Transaction t) {
            final ViewRootImpl viewRoot = getViewRootImpl();
            if (viewRoot != null) {
                viewRoot.applyTransactionOnDraw(t);
            } else {
                t.apply();
            }
        }
    }

    /**
     * The transformation to be applied to the mirror contents.
     */
    private record Transformation(float scale, float translateX, float translateY) {
        static final Transformation IDENTITY = new Transformation(1f, 0f, 0f);
    }

    /**
     * Returns the transformation that should be applied to the mirror contents to center-fill the
     * input content into the output space. This will crop the content if the aspect ratio of the
     * input and output do not match.
     *
     * @param inputWidth   The width of the content to be transformed.
     * @param inputHeight  The height of the content to be transformed.
     * @param outputWidth  The width of the space the content should fit into.
     * @param outputHeight The height of the space the content should fit into.
     * @return a {@link Transformation} object, or null if the transformation is invalid.
     */
    @Nullable
    private static Transformation computerCenterFillTransformation(int inputWidth, int inputHeight,
            int outputWidth, int outputHeight) {
        if (outputWidth == 0 || outputHeight == 0 || inputWidth == 0 || inputHeight == 0) {
            return null;
        }

        final float scale = Math.max((float) outputHeight / inputHeight,
                (float) outputWidth / inputWidth);
        final float tx = (outputWidth - inputWidth * scale) / 2.0f;
        final float ty = (outputHeight - inputHeight * scale) / 2.0f;

        return new Transformation(scale, tx, ty);
    }
}
