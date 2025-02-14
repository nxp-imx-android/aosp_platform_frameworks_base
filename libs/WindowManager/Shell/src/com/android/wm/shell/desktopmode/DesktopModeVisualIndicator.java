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

package com.android.wm.shell.desktopmode;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.SurfaceControl;
import android.window.DesktopModeFlags;

import androidx.annotation.VisibleForTesting;

import com.android.internal.policy.SystemBarUtils;
import com.android.wm.shell.R;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.annotations.ShellDesktopThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.bubbles.BubbleDropTargetBoundsProvider;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

/**
 * Animated visual indicator for Desktop Mode windowing transitions.
 */
public class DesktopModeVisualIndicator {
    public enum IndicatorType {
        /** To be used when we don't want to indicate any transition */
        NO_INDICATOR,
        /** Indicates impending transition into desktop mode */
        TO_DESKTOP_INDICATOR,
        /** Indicates impending transition into fullscreen */
        TO_FULLSCREEN_INDICATOR,
        /** Indicates impending transition into split select on the left side */
        TO_SPLIT_LEFT_INDICATOR,
        /** Indicates impending transition into split select on the right side */
        TO_SPLIT_RIGHT_INDICATOR,
        /** Indicates impending transition into bubble on the left side */
        TO_BUBBLE_LEFT_INDICATOR,
        /** Indicates impending transition into bubble on the right side */
        TO_BUBBLE_RIGHT_INDICATOR
    }

    /**
     * The conditions surrounding the drag event that led to the indicator's creation.
     */
    public enum DragStartState {
        /** The indicator is resulting from a freeform task drag. */
        FROM_FREEFORM,
        /** The indicator is resulting from a split screen task drag */
        FROM_SPLIT,
        /** The indicator is resulting from a fullscreen task drag */
        FROM_FULLSCREEN,
        /** The indicator is resulting from an Intent generated during a drag-and-drop event */
        DRAGGED_INTENT;

        /**
         * Get the {@link DragStartState} of a drag event based on the windowing mode of the task.
         * Note that DRAGGED_INTENT will be specified by the caller if needed and not returned
         * here.
         */
        public static DesktopModeVisualIndicator.DragStartState getDragStartState(
                ActivityManager.RunningTaskInfo taskInfo
        ) {
            if (taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
                return FROM_FULLSCREEN;
            } else if (taskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
                return FROM_SPLIT;
            } else if (taskInfo.isFreeform()) {
                return FROM_FREEFORM;
            } else return null;
        }
    }

    private final VisualIndicatorViewContainer mVisualIndicatorViewContainer;

    private final Context mContext;
    private final DisplayController mDisplayController;
    private final ActivityManager.RunningTaskInfo mTaskInfo;

    private IndicatorType mCurrentType;
    private final DragStartState mDragStartState;

    public DesktopModeVisualIndicator(@ShellDesktopThread ShellExecutor desktopExecutor,
            @ShellMainThread ShellExecutor mainExecutor,
            SyncTransactionQueue syncQueue,
            ActivityManager.RunningTaskInfo taskInfo, DisplayController displayController,
            Context context, SurfaceControl taskSurface,
            RootTaskDisplayAreaOrganizer taskDisplayAreaOrganizer,
            DragStartState dragStartState,
            @Nullable BubbleDropTargetBoundsProvider bubbleBoundsProvider) {
        SurfaceControl.Builder builder = new SurfaceControl.Builder();
        taskDisplayAreaOrganizer.attachToDisplayArea(taskInfo.displayId, builder);
        mVisualIndicatorViewContainer = new VisualIndicatorViewContainer(
                DesktopModeFlags.ENABLE_DESKTOP_INDICATOR_IN_SEPARATE_THREAD_BUGFIX.isTrue()
                        ? desktopExecutor : mainExecutor,
                mainExecutor, builder, syncQueue, bubbleBoundsProvider);
        mTaskInfo = taskInfo;
        mDisplayController = displayController;
        mContext = context;
        mCurrentType = NO_INDICATOR;
        mDragStartState = dragStartState;
        mVisualIndicatorViewContainer.createView(
                mContext,
                mDisplayController.getDisplay(mTaskInfo.displayId),
                mDisplayController.getDisplayLayout(mTaskInfo.displayId),
                mTaskInfo,
                taskSurface
        );
    }

    /** Start the fade out animation, running the callback on the main thread once it is done. */
    public void fadeOutIndicator(
            @NonNull Runnable callback) {
        mVisualIndicatorViewContainer.fadeOutIndicator(
                mDisplayController.getDisplayLayout(mTaskInfo.displayId), mCurrentType, callback
        );
    }

    /** Release the visual indicator view and its viewhost. */
    public void releaseVisualIndicator() {
        mVisualIndicatorViewContainer.releaseVisualIndicator();
    }

    /**
     * Based on the coordinates of the current drag event, determine which indicator type we should
     * display, including no visible indicator.
     */
    @NonNull
    IndicatorType updateIndicatorType(PointF inputCoordinates) {
        final DisplayLayout layout = mDisplayController.getDisplayLayout(mTaskInfo.displayId);
        // Perform a quick check first: any input off the left edge of the display should be split
        // left, and split right for the right edge. This is universal across all drag event types.
        if (inputCoordinates.x < 0) return TO_SPLIT_LEFT_INDICATOR;
        if (inputCoordinates.x > layout.width()) return TO_SPLIT_RIGHT_INDICATOR;
        IndicatorType result;
        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()
                && !DesktopModeStatus.canEnterDesktopMode(mContext)) {
            // If desktop is not available, default to "no indicator"
            result = NO_INDICATOR;
        } else {
            // If we are in freeform, we don't want a visible indicator in the "freeform" drag zone.
            // In drags not originating on a freeform caption, we should default to a TO_DESKTOP
            // indicator.
            result = mDragStartState == DragStartState.FROM_FREEFORM
                    ? NO_INDICATOR
                    : TO_DESKTOP_INDICATOR;
        }
        final int transitionAreaWidth = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.desktop_mode_transition_region_thickness);
        // Because drags in freeform use task position for indicator calculation, we need to
        // account for the possibility of the task going off the top of the screen by captionHeight
        final int captionHeight = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.desktop_mode_freeform_decor_caption_height);
        final Region fullscreenRegion = calculateFullscreenRegion(layout, captionHeight);
        final Region splitLeftRegion = calculateSplitLeftRegion(layout, transitionAreaWidth,
                captionHeight);
        final Region splitRightRegion = calculateSplitRightRegion(layout, transitionAreaWidth,
                captionHeight);
        final int x = (int) inputCoordinates.x;
        final int y = (int) inputCoordinates.y;
        if (fullscreenRegion.contains(x, y)) {
            result = TO_FULLSCREEN_INDICATOR;
        }
        if (splitLeftRegion.contains(x, y)) {
            result = IndicatorType.TO_SPLIT_LEFT_INDICATOR;
        }
        if (splitRightRegion.contains(x, y)) {
            result = IndicatorType.TO_SPLIT_RIGHT_INDICATOR;
        }
        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
            if (calculateBubbleLeftRegion(layout).contains(x, y)) {
                result = IndicatorType.TO_BUBBLE_LEFT_INDICATOR;
            } else if (calculateBubbleRightRegion(layout).contains(x, y)) {
                result = IndicatorType.TO_BUBBLE_RIGHT_INDICATOR;
            }
        }
        if (mDragStartState != DragStartState.DRAGGED_INTENT) {
            mVisualIndicatorViewContainer.transitionIndicator(
                    mTaskInfo, mDisplayController, mCurrentType, result
            );
            mCurrentType = result;
        }
        return result;
    }

    /**
     * Returns the [DragStartState] of the visual indicator.
     */
    DragStartState getDragStartState() {
        return mDragStartState;
    }

    @VisibleForTesting
    Region calculateFullscreenRegion(DisplayLayout layout, int captionHeight) {
        final Region region = new Region();
        int transitionHeight = mDragStartState == DragStartState.FROM_FREEFORM
                || mDragStartState == DragStartState.DRAGGED_INTENT
                ? SystemBarUtils.getStatusBarHeight(mContext)
                : 2 * layout.stableInsets().top;
        // A Rect at the top of the screen that takes up the center 40%.
        if (mDragStartState == DragStartState.FROM_FREEFORM) {
            final float toFullscreenScale = mContext.getResources().getFloat(
                    R.dimen.desktop_mode_fullscreen_region_scale);
            final float toFullscreenWidth = (layout.width() * toFullscreenScale);
            region.union(new Rect((int) ((layout.width() / 2f) - (toFullscreenWidth / 2f)),
                    Short.MIN_VALUE,
                    (int) ((layout.width() / 2f) + (toFullscreenWidth / 2f)),
                    transitionHeight));
        }
        // A screen-wide Rect if the task is in fullscreen, split, or a dragged intent.
        if (mDragStartState == DragStartState.FROM_FULLSCREEN
                || mDragStartState == DragStartState.FROM_SPLIT
                || mDragStartState == DragStartState.DRAGGED_INTENT
        ) {
            region.union(new Rect(0,
                    Short.MIN_VALUE,
                    layout.width(),
                    transitionHeight));
        }
        return region;
    }

    @VisibleForTesting
    Region calculateSplitLeftRegion(DisplayLayout layout,
            int transitionEdgeWidth, int captionHeight) {
        final Region region = new Region();
        // In freeform, keep the top corners clear.
        int transitionHeight = mDragStartState == DragStartState.FROM_FREEFORM
                ? mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.desktop_mode_split_from_desktop_height) :
                -captionHeight;
        region.union(new Rect(0, transitionHeight, transitionEdgeWidth, layout.height()));
        return region;
    }

    @VisibleForTesting
    Region calculateSplitRightRegion(DisplayLayout layout,
            int transitionEdgeWidth, int captionHeight) {
        final Region region = new Region();
        // In freeform, keep the top corners clear.
        int transitionHeight = mDragStartState == DragStartState.FROM_FREEFORM
                ? mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.desktop_mode_split_from_desktop_height) :
                -captionHeight;
        region.union(new Rect(layout.width() - transitionEdgeWidth, transitionHeight,
                layout.width(), layout.height()));
        return region;
    }

    @VisibleForTesting
    Region calculateBubbleLeftRegion(DisplayLayout layout) {
        int regionWidth = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.bubble_transform_area_width);
        int regionHeight = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.bubble_transform_area_height);
        return new Region(0, layout.height() - regionHeight, regionWidth, layout.height());
    }

    @VisibleForTesting
    Region calculateBubbleRightRegion(DisplayLayout layout) {
        int regionWidth = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.bubble_transform_area_width);
        int regionHeight = mContext.getResources().getDimensionPixelSize(
                com.android.wm.shell.R.dimen.bubble_transform_area_height);
        return new Region(layout.width() - regionWidth, layout.height() - regionHeight,
                layout.width(), layout.height());
    }

    @VisibleForTesting
    Rect getIndicatorBounds() {
        return mVisualIndicatorViewContainer.getIndicatorBounds();
    }
}
