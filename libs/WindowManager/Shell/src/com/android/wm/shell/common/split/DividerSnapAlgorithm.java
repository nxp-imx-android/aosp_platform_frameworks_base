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

package com.android.wm.shell.common.split;

import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;

import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_MINIMIZE;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_NONE;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SnapPosition;

import android.content.res.Resources;
import android.graphics.Rect;

import androidx.annotation.Nullable;

import com.android.mechanics.spec.MotionSpec;
import com.android.wm.shell.Flags;
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Calculates the snap targets and the snap position given a position and a velocity. All positions
 * here are to be interpreted as the left/top edge of the divider rectangle.
 *
 * @hide
 */
public class DividerSnapAlgorithm {

    private static final int MIN_FLING_VELOCITY_DP_PER_SECOND = 400;
    private static final int MIN_DISMISS_VELOCITY_DP_PER_SECOND = 600;

    /**
     * 3 snap targets: left/top has 16:9 ratio (for videos), 1:1, and right/bottom has 16:9 ratio
     */
    static final int SNAP_MODE_16_9 = 0;

    /**
     * 3 snap targets: fixed ratio, 1:1, (1 - fixed ratio)
     */
    static final int SNAP_FIXED_RATIO = 1;

    /**
     * 1 snap target: 1:1
     */
    static final int SNAP_ONLY_1_1 = 2;

    /**
     * 1 snap target: minimized height, (1 - minimized height)
     */
    static final int SNAP_MODE_MINIMIZED = 3;

    /**
     * A mode where apps can be "flexibly offscreen" on smaller displays.
     */
    static final int SNAP_FLEXIBLE_SPLIT = 4;
    /**
     * A mode combining {@link #SNAP_FIXED_RATIO} with {@link #SNAP_FLEXIBLE_SPLIT}. Has 5
     * split screen snap points on smaller devices.
     */
    static final int SNAP_FLEXIBLE_HYBRID = 5;

    private final float mMinFlingVelocityPxPerSecond;
    private final float mMinDismissVelocityPxPerSecond;
    private final int mDisplayWidth;
    private final int mDisplayHeight;
    private final int mDividerSize;
    private final ArrayList<SnapTarget> mTargets = new ArrayList<>();
    private final Rect mInsets = new Rect();
    private final Rect mPinnedTaskbarInsets = new Rect();
    private final int mSnapMode;
    private final boolean mFreeSnapMode;
    private final int mMinimalSizeResizableTask;
    private final int mTaskHeightInMinimizedMode;
    private final float mFixedRatio;
    /** Allows split ratios to calculated dynamically instead of using {@link #mFixedRatio}. */
    private final boolean mCalculateRatiosBasedOnAvailableSpace;
    /** Allows split ratios that go offscreen (a.k.a. "flexible split") */
    private final boolean mAllowOffscreenRatios;
    private final boolean mIsLeftRightSplit;
    /** In SNAP_MODE_MINIMIZED, the side of the screen on which an app will "dock" when minimized */
    private final int mDockSide;
    private final SplitTargetProvider mSplitTargetProvider;

    /**
     * The first, and usually only, snap target between the left/top screen edge and center.
     */
    private final SnapTarget mFirstSplitTarget;
    /**
     * Another snap target on the top/left side (closer to center than the "first").
     */
    private final SnapTarget mSecondSplitTarget;
    /**
     * The last, and usually only, snap target between the center and the right/bottom screen edge.
     */
    private final SnapTarget mLastSplitTarget;
    /**
     * Another snap target on the right/bottom side (closer to center than the "last").
     */
    private final SnapTarget mSecondLastSplitTarget;

    private final SnapTarget mDismissStartTarget;
    private final SnapTarget mDismissEndTarget;
    private final SnapTarget mMiddleTarget;

    /** A spec used for "magnetic snap" user-controlled movement. */
    private final MotionSpec mMotionSpec;

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize,
            boolean isLeftRightSplit, Rect insets, Rect pinnedTaskbarInsets, int dockSide,
            SplitTargetProvider splitTargetProvider) {
        this(res, displayWidth, displayHeight, dividerSize, isLeftRightSplit, insets,
                pinnedTaskbarInsets, dockSide, true /* resizable */,
                splitTargetProvider);
    }

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize,
            boolean isLeftRightSplit, Rect insets, Rect pinnedTaskbarInsets, int dockSide,
            boolean isHomeResizable, SplitTargetProvider splitTargetProvider) {
        mMinFlingVelocityPxPerSecond =
                MIN_FLING_VELOCITY_DP_PER_SECOND * res.getDisplayMetrics().density;
        mMinDismissVelocityPxPerSecond =
                MIN_DISMISS_VELOCITY_DP_PER_SECOND * res.getDisplayMetrics().density;
        mDividerSize = dividerSize;
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        mIsLeftRightSplit = isLeftRightSplit;
        mDockSide = dockSide;
        mSplitTargetProvider = splitTargetProvider;
        mInsets.set(insets);
        mPinnedTaskbarInsets.set(pinnedTaskbarInsets);
        mSnapMode = mSplitTargetProvider.getSnapMode();
        mFreeSnapMode = res.getBoolean(
                com.android.internal.R.bool.config_dockedStackDividerFreeSnapMode);
        mFixedRatio = res.getFraction(
                com.android.internal.R.fraction.docked_stack_divider_fixed_ratio, 1, 1);
        mMinimalSizeResizableTask = res.getDimensionPixelSize(
                com.android.internal.R.dimen.default_minimal_size_resizable_task);
        mCalculateRatiosBasedOnAvailableSpace = res.getBoolean(
                com.android.internal.R.bool.config_flexibleSplitRatios);
        // If this is a small screen or a foldable, use offscreen ratios
        mAllowOffscreenRatios = SplitScreenUtils.allowOffscreenRatios(res);
        mTaskHeightInMinimizedMode = isHomeResizable ? res.getDimensionPixelSize(
                com.android.internal.R.dimen.task_height_of_minimized_mode) : 0;
        calculateTargets();
        mFirstSplitTarget = mTargets.get(1);
        mSecondSplitTarget = mSnapMode == SNAP_FLEXIBLE_HYBRID && areOffscreenRatiosSupported()
                        ? mTargets.get(2) : null;
        mSecondLastSplitTarget = mSnapMode == SNAP_FLEXIBLE_HYBRID && areOffscreenRatiosSupported()
                        ? mTargets.get(mTargets.size() - 3) : null;
        mLastSplitTarget = mTargets.get(mTargets.size() - 2);
        mDismissStartTarget = mTargets.get(0);
        mDismissEndTarget = mTargets.get(mTargets.size() - 1);
        mMiddleTarget = mTargets.get(mTargets.size() / 2);
        mMiddleTarget.isMiddleTarget = true;
        mMotionSpec = Flags.enableMagneticSplitDivider()
                ? MagneticDividerUtils.generateMotionSpec(mTargets, res) : null;
    }

    /**
     * @param position the top/left position of the divider
     * @param velocity current dragging velocity
     * @param hardToDismiss if set, make it a bit harder to get reach the dismiss targets
     */
    public SnapTarget calculateSnapTarget(int position, float velocity, boolean hardToDismiss) {
        if (position < mFirstSplitTarget.position && velocity < -mMinDismissVelocityPxPerSecond) {
            return mDismissStartTarget;
        }
        if (position > mLastSplitTarget.position && velocity > mMinDismissVelocityPxPerSecond) {
            return mDismissEndTarget;
        }
        if (Math.abs(velocity) < mMinFlingVelocityPxPerSecond) {
            return snap(position, hardToDismiss);
        }
        if (velocity < 0) {
            return mFirstSplitTarget;
        } else {
            return mLastSplitTarget;
        }
    }

    public SnapTarget calculateNonDismissingSnapTarget(int position) {
        SnapTarget target = snap(position, false /* hardDismiss */);
        if (target == mDismissStartTarget) {
            return mFirstSplitTarget;
        } else if (target == mDismissEndTarget) {
            return mLastSplitTarget;
        } else {
            return target;
        }
    }

    /**
     * Gets the SnapTarget corresponding to the given {@link SnapPosition}, or null if no such
     * SnapTarget exists.
     */
    @Nullable
    public SnapTarget findSnapTarget(@SnapPosition int snapPosition) {
        for (SnapTarget t : mTargets) {
            if (t.snapPosition == snapPosition) {
                return t;
            }
        }

        return null;
    }

    public float calculateDismissingFraction(int position) {
        if (position < mFirstSplitTarget.position) {
            return 1f - (float) (position - getStartInset())
                    / (mFirstSplitTarget.position - getStartInset());
        } else if (position > mLastSplitTarget.position) {
            return (float) (position - mLastSplitTarget.position)
                    / (mDismissEndTarget.position - mLastSplitTarget.position - mDividerSize);
        }
        return 0f;
    }

    public SnapTarget getFirstSplitTarget() {
        return mFirstSplitTarget;
    }

    public SnapTarget getSecondSplitTarget() {
        return mSecondSplitTarget;
    }

    public SnapTarget getSecondLastSplitTarget() {
        return mSecondLastSplitTarget;
    }

    public SnapTarget getLastSplitTarget() {
        return mLastSplitTarget;
    }

    public SnapTarget getDismissStartTarget() {
        return mDismissStartTarget;
    }

    public SnapTarget getDismissEndTarget() {
        return mDismissEndTarget;
    }

    private int getStartInset() {
        if (mIsLeftRightSplit) {
            return mInsets.left;
        } else {
            return mInsets.top;
        }
    }

    private int getEndInset() {
        if (mIsLeftRightSplit) {
            return mInsets.right;
        } else {
            return mInsets.bottom;
        }
    }

    private boolean shouldApplyFreeSnapMode(int position) {
        if (!mFreeSnapMode) {
            return false;
        }
        if (!isFirstSplitTargetAvailable() || !isLastSplitTargetAvailable()) {
            return false;
        }
        return mFirstSplitTarget.position < position && position < mLastSplitTarget.position;
    }

    /** Returns if we are currently on a device/screen that supports split apps going offscreen. */
    public boolean areOffscreenRatiosSupported() {
        return mAllowOffscreenRatios;
    }

    private SnapTarget snap(int position, boolean hardDismiss) {
        if (shouldApplyFreeSnapMode(position)) {
            return new SnapTarget(position, SNAP_TO_NONE);
        }
        int minIndex = -1;
        float minDistance = Float.MAX_VALUE;
        int size = mTargets.size();
        for (int i = 0; i < size; i++) {
            SnapTarget target = mTargets.get(i);
            float distance = Math.abs(position - target.position);
            if (hardDismiss) {
                distance /= target.distanceMultiplier;
            }
            if (distance < minDistance) {
                minIndex = i;
                minDistance = distance;
            }
        }
        return mTargets.get(minIndex);
    }

    private void calculateTargets() {
        mTargets.clear();
        int dividerMax = mIsLeftRightSplit
                ? mDisplayWidth
                : mDisplayHeight;
        int startPos = -mDividerSize;
        if (mDockSide == DOCKED_RIGHT) {
            startPos += mInsets.left;
        }
        mTargets.add(new SnapTarget(startPos, SNAP_TO_START_AND_DISMISS, 0.35f));
        switch (mSnapMode) {
            case SNAP_MODE_16_9:
                addRatio16_9Targets(dividerMax);
                break;
            case SNAP_FIXED_RATIO:
                addFixedDivisionTargets(dividerMax);
                break;
            case SNAP_ONLY_1_1:
                mTargets.add(new SnapTarget(getMiddleTargetPos(), SNAP_TO_2_50_50));
                break;
            case SNAP_MODE_MINIMIZED:
                addMinimizedTarget(mDockSide);
                break;
            case SNAP_FLEXIBLE_SPLIT:
                addFlexSplitTargets(dividerMax);
                break;
            case SNAP_FLEXIBLE_HYBRID:
                addFlexHybridSplitTargets(dividerMax);
                break;
        }
        mTargets.add(new SnapTarget(dividerMax, SNAP_TO_END_AND_DISMISS, 0.35f));
    }

    /**
     * Adds the non-dismissing snap targets (i.e. not the dismiss targets on the screen edges).
     *
     * @param positions The int positions of each non-dismissing snap target. (i.e. has size 3 for a
     *                  3-target layout, and size 5 for a 5-target layout.) Should always be in
     *                  ascending order.
     */
    private void addNonDismissingTargets(List<Integer> positions, int dividerMax) {
        // Get the desired layout for our snap mode.
        List<Integer> targetSpec = mSplitTargetProvider
                .getTargets(false /*includeDismissal*/)
                .stream().map(SplitTargetProvider.SplitTarget::getSnapPosition)
                .toList();

        if (positions.size() != targetSpec.size()) {
            throw new IllegalStateException("unexpected number of snap positions");
        }

        // Iterate through the spec, adding a target for each.
        boolean midpointPassed = false;
        for (int i = 0; i < targetSpec.size(); i++) {
            @PersistentSnapPosition int target = targetSpec.get(i);
            int position = positions.get(i);

            if (!midpointPassed) {
                if (target == SNAP_TO_2_50_50) {
                    // midpoint
                    mTargets.add(new SnapTarget(position, target));
                    midpointPassed = true;
                } else {
                    // before midpoint
                    maybeAddTarget(position, position - getStartInset(), target);
                }
            } else {
                // after midpoint
                maybeAddTarget(position,
                        dividerMax - getEndInset() - (position + mDividerSize),
                        target);
            }
        }
    }

    private void addFixedDivisionTargets(int dividerMax) {
        int start = mIsLeftRightSplit ? mInsets.left : mInsets.top;
        int end = mIsLeftRightSplit
                ? mDisplayWidth - mInsets.right
                : mDisplayHeight - mInsets.bottom;

        int size = (int) (mFixedRatio * (end - start)) - mDividerSize / 2;
        if (mCalculateRatiosBasedOnAvailableSpace) {
            size = Math.max(size, mMinimalSizeResizableTask);
        }

        int topPosition = start + size;
        int bottomPosition = end - size - mDividerSize;
        addNonDismissingTargets(List.of(topPosition, getMiddleTargetPos(), bottomPosition),
                dividerMax);
    }

    private void addFlexSplitTargets(int dividerMax) {
        int start = 0;
        int end = mIsLeftRightSplit ? mDisplayWidth : mDisplayHeight;
        int pinnedTaskbarShiftStart = mIsLeftRightSplit
                ? mPinnedTaskbarInsets.left : mPinnedTaskbarInsets.top;
        int pinnedTaskbarShiftEnd = mIsLeftRightSplit
                ? mPinnedTaskbarInsets.right : mPinnedTaskbarInsets.bottom;

        float ratio = areOffscreenRatiosSupported()
                ? SplitSpec.OFFSCREEN_ASYMMETRIC_RATIO
                : SplitSpec.ONSCREEN_ONLY_ASYMMETRIC_RATIO;

        // The intended size of the smaller app, in pixels
        int size = (int) (ratio * (end - start)) - mDividerSize / 2;

        // If there are insets that interfere with the smaller app (visually or blocking touch
        // targets), make the smaller app bigger by that amount to compensate. This applies to
        // pinned taskbar, 3-button nav (both create an opaque bar at bottom) and status bar (blocks
        // touch targets at top).
        int extraSpace = IntStream.of(
                getStartInset(), getEndInset(), pinnedTaskbarShiftStart, pinnedTaskbarShiftEnd
        ).max().getAsInt();

        int leftTopPosition = start + extraSpace + size;
        int rightBottomPosition = end - extraSpace - size - mDividerSize;
        addNonDismissingTargets(List.of(leftTopPosition, getMiddleTargetPos(), rightBottomPosition),
                dividerMax);
    }

    private void addFlexHybridSplitTargets(int dividerMax) {
        int start = 0;
        int end = mIsLeftRightSplit ? mDisplayWidth : mDisplayHeight;
        int pinnedTaskbarShiftStart = mIsLeftRightSplit
                ? mPinnedTaskbarInsets.left : mPinnedTaskbarInsets.top;
        int pinnedTaskbarShiftEnd = mIsLeftRightSplit
                ? mPinnedTaskbarInsets.right : mPinnedTaskbarInsets.bottom;

        // If offscreen apps are supported, add 5 targets instead of 3.
        if (areOffscreenRatiosSupported()) {
            // Find the desired sizes for a 10% app and a 33% app.
            float ratio10 = SplitSpec.OFFSCREEN_ASYMMETRIC_RATIO;
            float ratio33 = SplitSpec.ONSCREEN_ONLY_ASYMMETRIC_RATIO;
            int size10 = (int) (ratio10 * (end - start)) - mDividerSize / 2;
            int size33 = (int) (ratio33 * (end - start)) - mDividerSize / 2;

            // If there are insets that interfere with the smaller app (visually or blocking touch
            // targets), make the 10% app ratio bigger by that amount to compensate. This applies to
            // pinned taskbar, 3-button nav (both create an opaque bar at bottom) and status bar
            // (blocks touch targets at top).
            int extraSpaceFor10 = IntStream.of(
                    getStartInset(), getEndInset(), pinnedTaskbarShiftStart, pinnedTaskbarShiftEnd
            ).max().getAsInt();

            int leftTop10Position = start + extraSpaceFor10 + size10;
            int rightBottom10Position = end - extraSpaceFor10 - size10 - mDividerSize;
            int leftTop33Position = start + size33;
            int rightBottom33Position = end - size33 - mDividerSize;
            addNonDismissingTargets(List.of(leftTop10Position, leftTop33Position,
                            getMiddleTargetPos(), rightBottom33Position, rightBottom10Position),
                    dividerMax);
        } else {
            // If offscreen apps are not supported, just add the regular 3 targets.
            float ratio = SplitSpec.ONSCREEN_ONLY_ASYMMETRIC_RATIO;

            // The intended size of the smaller app, in pixels
            int size = (int) (ratio * (end - start)) - mDividerSize / 2;

            int leftTopPosition = start + size;
            int rightBottomPosition = end - size - mDividerSize;
            addNonDismissingTargets(List.of(leftTopPosition, getMiddleTargetPos(),
                    rightBottomPosition), dividerMax);
        }
    }

    private void addRatio16_9Targets(int dividerMax) {
        int start = mIsLeftRightSplit ? mInsets.left : mInsets.top;
        int end = mIsLeftRightSplit
                ? mDisplayWidth - mInsets.right
                : mDisplayHeight - mInsets.bottom;
        int startOther = mIsLeftRightSplit ? mInsets.top : mInsets.left;
        int endOther = mIsLeftRightSplit
                ? mDisplayHeight - mInsets.bottom
                : mDisplayWidth - mInsets.right;
        float size = 9.0f / 16.0f * (endOther - startOther);
        int sizeInt = (int) Math.floor(size);
        int topPosition = start + sizeInt;
        int bottomPosition = end - sizeInt - mDividerSize;
        addNonDismissingTargets(List.of(topPosition, getMiddleTargetPos(), bottomPosition),
                dividerMax);
    }

    /**
     * Adds a target at {@param position} but only if the area with size of {@param smallerSize}
     * meets the minimal size requirement.
     */
    private void maybeAddTarget(int position, int smallerSize, @SnapPosition int snapPosition) {
        if (smallerSize >= mMinimalSizeResizableTask || areOffscreenRatiosSupported()) {
            mTargets.add(new SnapTarget(position, snapPosition));
        }
    }

    /** Calculates the screen position of the middle snap target. */
    private int getMiddleTargetPos() {
        return DockedDividerUtils.calculateMiddlePosition(mIsLeftRightSplit, mInsets, mDisplayWidth,
                mDisplayHeight, mDividerSize);
    }

    private void addMinimizedTarget(int dockedSide) {
        // In portrait offset the position by the statusbar height, in landscape add the statusbar
        // height as well to match portrait offset
        int position = mTaskHeightInMinimizedMode + mInsets.top;
        if (mIsLeftRightSplit) {
            if (dockedSide == DOCKED_LEFT) {
                position += mInsets.left;
            } else if (dockedSide == DOCKED_RIGHT) {
                position = mDisplayWidth - position - mInsets.right - mDividerSize;
            }
        }
        mTargets.add(new SnapTarget(position, SNAP_TO_MINIMIZE));
    }

    public SnapTarget getMiddleTarget() {
        return mMiddleTarget;
    }

    /**
     * @return whether or not there are more than 1 split targets that do not include the two
     * dismiss targets, used in deciding to display the middle target for accessibility
     */
    public boolean showMiddleSplitTargetForAccessibility() {
        return (mTargets.size() - 2) > 1;
    }

    public boolean isFirstSplitTargetAvailable() {
        return mFirstSplitTarget != mMiddleTarget;
    }

    public boolean isLastSplitTargetAvailable() {
        return mLastSplitTarget != mMiddleTarget;
    }

    /**
     * Finds the {@link SnapPosition} nearest to the given position.
     */
    public int calculateNearestSnapPosition(int currentPosition) {
        return snap(currentPosition, /* hardDismiss */ true).snapPosition;
    }

    public MotionSpec getMotionSpec() {
        return mMotionSpec;
    }

    /**
     * An object, calculated at boot time, representing a legal position for the split screen
     * divider (i.e. the divider can be dragged to this spot).
     */
    public static class SnapTarget {
        /** Position of this snap target. The right/bottom edge of the top/left task snaps here. */
        public final int position;

        /**
         * An int (enum) describing the placement of the divider in this snap target.
         */
        public final @SnapPosition int snapPosition;

        public boolean isMiddleTarget;

        /**
         * Multiplier used to calculate distance to snap position. The lower this value, the harder
         * it's to snap on this target
         */
        private final float distanceMultiplier;

        public SnapTarget(int position, @SnapPosition int snapPosition) {
            this(position, snapPosition, 1f);
        }

        public SnapTarget(int position, @SnapPosition int snapPosition,
                float distanceMultiplier) {
            this.position = position;
            this.snapPosition = snapPosition;
            this.distanceMultiplier = distanceMultiplier;
        }

        public int getPosition() {
            return position;
        }
    }
}
