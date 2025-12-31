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

package com.android.server.wm;


import static android.app.ActivityManager.LOCK_TASK_MODE_LOCKED;
import static android.app.ActivityManager.LOCK_TASK_MODE_PINNED;

import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_INVISIBLE;
import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_VISIBLE;
import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Rect;
import android.util.ArraySet;
import android.window.DesktopExperienceFlags;

import com.android.window.flags.Flags;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Implementation of {@link WindowContainerVisibilityHelper}.
 *
 * @see ActivityTaskManagerService#mVisibilityHelper
 */
final class WindowContainerVisibilityHelperImpl implements WindowContainerVisibilityHelper {

    @NonNull
    private final ActivityTaskManagerService mService;
    @NonNull
    private final OpaqueContainerHelper mOpaqueContainerHelper = new OpaqueContainerHelper();

    WindowContainerVisibilityHelperImpl(@NonNull ActivityTaskManagerService service) {
        mService = service;
    }

    @Override
    @TaskFragment.TaskFragmentVisibility
    public int getTaskFragmentVisibility(@NonNull TaskFragment current,
            @Nullable ActivityRecord starting) {
        if (!current.isAttached() || current.isForceHidden()) {
            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
        }

        if (isTopActivityLaunchedBehind(current)) {
            return TASK_FRAGMENT_VISIBILITY_VISIBLE;
        }
        final WindowContainer<?> parent = current.getParent();
        final Task thisTask = current.asTask();
        if (thisTask != null && parent.asTask() == null
                && current.mTransitionController.isTransientVisible(thisTask)) {
            // Keep transient-hide root tasks visible. Non-root tasks still follow standard rule.
            return TASK_FRAGMENT_VISIBILITY_VISIBLE;
        }

        if (thisTask != null && !isPermittedInLockTask(thisTask)) {
            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
        }

        boolean gotTranslucentFullscreen = false;
        boolean gotTranslucentAdjacent = false;
        boolean shouldBeVisible = true;

        // This TaskFragment is only considered visible if all its parent TaskFragments are
        // considered visible, so check the visibility of all ancestor TaskFragment first.
        if (parent.asTaskFragment() != null) {
            final int parentVisibility = getTaskFragmentVisibility(
                    parent.asTaskFragment(), starting);
            if (parentVisibility == TASK_FRAGMENT_VISIBILITY_INVISIBLE) {
                // Can't be visible if parent isn't visible
                return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
            } else if (parentVisibility == TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT) {
                // Parent is behind a translucent container so the highest visibility this container
                // can get is that.
                gotTranslucentFullscreen = true;
            }
        }

        AdjacentVisibilityHelper adjacentVisibilityHelper = null;
        final Rect tmpRect = new Rect();
        final List<TaskFragment> adjacentTaskFragments = new ArrayList<>();
        for (int i = parent.getChildCount() - 1; i >= 0; --i) {
            final WindowContainer other = parent.getChildAt(i);
            if (other == null) continue;

            final boolean hasRunningActivities = hasRunningActivity(other);
            if (other == current) {
                if (Flags.fixTfAdjacentVisibility()) {
                    if (adjacentVisibilityHelper != null
                            && !adjacentVisibilityHelper.isUnprocessedAdjacentTaskFragment(
                                    current)) {
                        if (!adjacentVisibilityHelper.isBehindTranslucentTaskFragment(current)) {
                            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
                        } else {
                            gotTranslucentFullscreen = true;
                        }
                    }
                } else {
                    if (!adjacentTaskFragments.isEmpty() && !gotTranslucentAdjacent) {
                        // The z-order of this TaskFragment is in middle of two adjacent
                        // TaskFragments and it cannot be visible if the TaskFragment on top is
                        // not translucent and is occluding this one.
                        tmpRect.set(current.getBounds());
                        for (int j = adjacentTaskFragments.size() - 1; j >= 0; --j) {
                            final TaskFragment taskFragment = adjacentTaskFragments.get(j);
                            if (taskFragment.isAdjacentTo(current)) {
                                continue;
                            }
                            final boolean isOccluding = tmpRect.intersect(taskFragment.getBounds())
                                    || taskFragment.forOtherAdjacentTaskFragments(
                                            adjacentTf -> {
                                                return tmpRect.intersect(adjacentTf.getBounds());
                                            });
                            if (isOccluding) {
                                return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
                            }
                        }
                    }
                }
                // Should be visible if there is no other fragment occluding it, unless it doesn't
                // have any running activities, not starting one and not home stack.
                shouldBeVisible = hasRunningActivities
                        || (starting != null && starting.isDescendantOf(current))
                        || (current.isActivityTypeHome() && !current.isEmbedded());
                break;
            }

            if (!hasRunningActivities) {
                continue;
            }

            // Must fill the parent to affect visibility.
            boolean affectsSiblingVisibility = other.fillsParentBounds();
            if (DesktopExperienceFlags.ENABLE_SEE_THROUGH_TASK_FRAGMENTS.isTrue()) {
                // It also must have filling content itself, to prevent empty or only partially
                // occluding containers from affecting visibility.
                affectsSiblingVisibility &= other.hasFillingContent();
            }
            if (affectsSiblingVisibility) {
                // This task fragment is fully covered by |other|.
                if (isTranslucent(other, starting)) {
                    // Can be visible behind a translucent TaskFragment.
                    gotTranslucentFullscreen = true;
                    continue;
                }
                return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
            }

            final TaskFragment otherTaskFrag = other.asTaskFragment();
            if (otherTaskFrag != null) {
                // For adjacent TaskFragments, we have assumptions that:
                // 1. A set of adjacent TaskFragments always cover the entire Task window, so that
                // if this TaskFragment is behind a set of opaque TaskFragments, then this
                // TaskFragment is invisible.
                // 2. Adjacent TaskFragments do not overlap, so that if this TaskFragment is behind
                // any translucent TaskFragment in the adjacent set, then this TaskFragment is
                // visible behind translucent.
                if (Flags.fixTfAdjacentVisibility()) {
                    if (otherTaskFrag.hasAdjacentTaskFragment()
                            && (adjacentVisibilityHelper == null
                            || adjacentVisibilityHelper.isAllAdjacentTaskFragmentProcessed())) {
                        // Same as above. The TaskFragment must have filling content itself,
                        // otherwise it cannot affect the visibility.
                        adjacentVisibilityHelper = new AdjacentVisibilityHelper(
                                otherTaskFrag,
                                t -> t.hasFillingContent() && !isTranslucent(t, starting));
                    }

                    if (adjacentVisibilityHelper != null) {
                        adjacentVisibilityHelper.process(otherTaskFrag);
                        if (adjacentVisibilityHelper.isAllAdjacentTaskFragmentProcessed()) {
                            if (adjacentVisibilityHelper.occludesParent()) {
                                // Can not be visible behind adjacent TaskFragments.
                                return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
                            }
                            // Can be visible behind a translucent adjacent TaskFragments.
                            gotTranslucentFullscreen = true;
                        }
                    }
                } else if (otherTaskFrag.hasAdjacentTaskFragment()) {
                    final boolean hasTraversedAdj = otherTaskFrag.forOtherAdjacentTaskFragments(
                            adjacentTaskFragments::contains);
                    if (hasTraversedAdj) {
                        final boolean isTranslucent =
                                isBehindTransparentTaskFragment(current, otherTaskFrag, starting)
                                        || otherTaskFrag.forOtherAdjacentTaskFragments(
                                                (Predicate<TaskFragment>) tf ->
                                                        isBehindTransparentTaskFragment(
                                                                current, tf, starting));
                        if (isTranslucent) {
                            // Can be visible behind a translucent adjacent TaskFragments.
                            gotTranslucentFullscreen = true;
                            gotTranslucentAdjacent = true;
                            continue;
                        }
                        // Can not be visible behind adjacent TaskFragments.
                        return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
                    }
                    adjacentTaskFragments.add(otherTaskFrag);
                }
            }
        }

        if (!shouldBeVisible) {
            return TASK_FRAGMENT_VISIBILITY_INVISIBLE;
        }

        // Lastly - check if there is a translucent fullscreen TaskFragment on top.
        return gotTranslucentFullscreen
                ? TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT
                : TASK_FRAGMENT_VISIBILITY_VISIBLE;
    }

    @Override
    public boolean shouldActivityBeVisible(@NonNull ActivityRecord current,
            boolean ignoringKeyguard) {
        final Task task = current.getTask();
        if (task == null) {
            return false;
        }

        final boolean behindOccludedContainer = !task.shouldBeVisible(null /* starting */)
                || getOccludingActivityAbove(task, current) != null;
        return current.updateAndCheckVisibility(behindOccludedContainer, ignoringKeyguard);
    }

    @Override
    public boolean hasFillingContent(@NonNull WindowContainer current) {
        final int childCount = current.getChildCount();
        if (childCount == 0) {
            return false;
        }

        AdjacentVisibilityHelper adjacentVisibilityHelper = null;
        for (int i = childCount - 1; i >= 0; --i) {
            final WindowContainer<?> child = current.getChildAt(i);
            if (child.fillsParentBounds() && child.hasFillingContent()) {
                // At least one child fills this container and has content filling itself.
                return true;
            }

            if (Flags.fixTfAdjacentVisibility()) {
                final TaskFragment tf = child.asTaskFragment();
                if (tf != null) {
                    if (tf.hasAdjacentTaskFragment() && adjacentVisibilityHelper == null) {
                        adjacentVisibilityHelper = new AdjacentVisibilityHelper(
                                tf, TaskFragment::hasFillingContent);
                    }
                    if (adjacentVisibilityHelper != null) {
                        adjacentVisibilityHelper.process(tf);
                        if (adjacentVisibilityHelper.isAllAdjacentTaskFragmentProcessed()) {
                            if (adjacentVisibilityHelper.occludesParent()) {
                                return true;
                            } else {
                                adjacentVisibilityHelper = null;
                            }
                        }
                    }
                }
            } else {
                if (child.asTaskFragment() != null
                        && child.asTaskFragment().hasAdjacentTaskFragment()) {
                    // There's at least one child adjacent task fragment. Consider the parent
                    // filling as long as all of the adjacent task fragments have filling content.
                    // Whether or not they fill the parent in union is not important.
                    final boolean allFillingContent = child.hasFillingContent()
                            && !child.asTaskFragment().forOtherAdjacentTaskFragments(
                                    tf -> !tf.hasFillingContent());
                    if (allFillingContent) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isOpaque(@NonNull WindowContainer<?> current) {
        return mOpaqueContainerHelper.isOpaque(current);
    }

    @Override
    public boolean isOpaque(@NonNull WindowContainer<?> current,
            @Nullable ActivityRecord starting, boolean ignoringKeyguard,
            boolean ignoringInvisibleActivity) {
        return mOpaqueContainerHelper.isOpaque(current, starting, ignoringKeyguard,
                ignoringInvisibleActivity);
    }

    private static boolean isBehindTransparentTaskFragment(@NonNull TaskFragment currentTf,
            @NonNull TaskFragment otherTf, @Nullable ActivityRecord starting) {
        return otherTf.isTranslucent(starting)
                && currentTf.getBounds().intersect(otherTf.getBounds());
    }

    private static boolean hasRunningActivity(@NonNull WindowContainer wc) {
        if (wc.asTaskFragment() != null) {
            return wc.asTaskFragment().topRunningActivity() != null;
        }
        return wc.asActivityRecord() != null && !wc.asActivityRecord().finishing;
    }

    private static boolean isTranslucent(@NonNull WindowContainer wc,
            @Nullable ActivityRecord starting) {
        if (wc.asTaskFragment() != null) {
            return wc.asTaskFragment().isTranslucent(starting);
        } else if (wc.asActivityRecord() != null) {
            return !wc.asActivityRecord().occludesParent();
        }
        return false;
    }

    private static boolean isTopActivityLaunchedBehind(@NonNull TaskFragment current) {
        final ActivityRecord top = current.topRunningActivity();
        return top != null && top.mLaunchTaskBehind;
    }

    /**
     * Checks if a task is allowed to run in the lock task mode.
     *
     * <p>Returns {@code true} if the device is not currently in lock task.
     *
     * <p>A task is permitted if it's a leaf task that is allowed by the lock task admin policy, or
     * if any of its descendant leaf tasks are permitted by the policy.
     *
     * @param task the task to evaluate.
     * @return {@code true} if the task is allowed to run, {@code false} otherwise.
     */
    private boolean isPermittedInLockTask(@NonNull Task task) {
        final int lockTaskState = mService.getLockTaskController().getLockTaskModeState();
        final boolean isInLockTask =
                lockTaskState == LOCK_TASK_MODE_LOCKED || lockTaskState == LOCK_TASK_MODE_PINNED;
        if (!isInLockTask) {
            return true;
        }
        return task.forAllTasks(leafTask ->
                !mService.getLockTaskController().isLockTaskModeViolation(leafTask));
    }

    /**
     * Returns the top-most activity that occludes the given {@code activity}, or {@code null} if
     * none.
     */
    @Nullable
    private static ActivityRecord getOccludingActivityAbove(@NonNull Task current,
            @NonNull ActivityRecord activity) {
        final ActivityRecord top = current.getActivity(r -> {
            if (r == activity) {
                // Reached the given activity, return the activity to stop searching.
                return true;
            }

            if (!r.occludesParent()) {
                return false;
            }

            TaskFragment parent = r.getTaskFragment();
            if (parent == activity.getTaskFragment()) {
                // Found it. This activity on top of the given activity on the same TaskFragment.
                return true;
            }
            if (parent != null && parent.asTask() != null) {
                // Found it. This activity is the direct child of a leaf Task.
                return true;
            }
            // The candidate activity is being embedded. Checking if the bounds of the containing
            // TaskFragment equals to the outer TaskFragment.
            TaskFragment grandParent = parent.getParent().asTaskFragment();
            while (grandParent != null) {
                if (!parent.getBounds().equals(grandParent.getBounds())) {
                    // Not occluding the grandparent.
                    break;
                }
                if (grandParent.asTask() != null) {
                    // Found it. The activity occludes its parent TaskFragment and the parent
                    // TaskFragment also occludes its parent all the way up.
                    return true;
                }
                parent = grandParent;
                grandParent = parent.getParent().asTaskFragment();
            }
            return false;
        });
        return top != activity ? top : null;
    }

    /** The helper to calculate whether a container is opaque. */
    private static class OpaqueContainerHelper implements Predicate<ActivityRecord> {
        private final boolean mEnableMultipleDesktopsBackend =
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue();
        @Nullable
        private ActivityRecord mStarting;
        private boolean mIgnoringInvisibleActivity;
        private boolean mIgnoringKeyguard;

        /** Whether the container is opaque. */
        boolean isOpaque(@NonNull WindowContainer<?> container) {
            return isOpaque(container, null /* starting */, true /* ignoringKeyguard */,
                    false /* ignoringInvisibleActivity */);
        }

        /**
         * Whether the container is opaque, but only including visible activities in its
         * calculation.
         */
        boolean isOpaque(
                @NonNull WindowContainer<?> container, @Nullable ActivityRecord starting,
                boolean ignoringKeyguard,  boolean ignoringInvisibleActivity) {
            mStarting = starting;
            mIgnoringInvisibleActivity = ignoringInvisibleActivity;
            mIgnoringKeyguard = ignoringKeyguard;

            final boolean isOpaque;
            if (!mEnableMultipleDesktopsBackend) {
                isOpaque = container.getActivity(this,
                        true /* traverseTopToBottom */, null /* boundary */) != null;
            } else {
                isOpaque = isOpaqueInner(container);
            }
            mStarting = null;
            return isOpaque;
        }

        private boolean isOpaqueInner(@NonNull WindowContainer<?> container) {
            final boolean isActivity = container.asActivityRecord() != null;
            final boolean isLeafTaskFragment = container.asTaskFragment() != null
                    && ((TaskFragment) container).isLeafTaskFragment();
            final boolean isForceOpaque = container.asTask() != null
                    && container.asTask().isForceOpaque();
            if (isForceOpaque) {
                return true;
            }
            if (isActivity || isLeafTaskFragment) {
                // When it is an activity or leaf task fragment, then opacity is calculated based
                // on itself or its activities.
                return container.getActivity(this,
                        true /* traverseTopToBottom */, null /* boundary */) != null;
            }
            // Otherwise, it's considered opaque if any of its opaque children fill this
            // container, unless the children are adjacent fragments, in which case as long as they
            // are all opaque then |container| is also considered opaque, even if the adjacent
            // task fragment aren't filling.
            AdjacentVisibilityHelper adjacentVisibilityHelper = null;
            for (int i = container.getChildCount() - 1; i >= 0; --i) {
                final WindowContainer<?> child = container.getChildAt(i);
                if (child.fillsParent() && isOpaque(child)) {
                    return true;
                }

                if (Flags.fixTfAdjacentVisibility()) {
                    final TaskFragment tf = child.asTaskFragment();
                    if (tf != null) {
                        if (tf.hasAdjacentTaskFragment() && adjacentVisibilityHelper == null) {
                            adjacentVisibilityHelper = new AdjacentVisibilityHelper(
                                    tf, this::isOpaque);
                        }
                        if (adjacentVisibilityHelper != null) {
                            adjacentVisibilityHelper.process(tf);
                            if (adjacentVisibilityHelper.isAllAdjacentTaskFragmentProcessed()) {
                                if (adjacentVisibilityHelper.occludesParent()) {
                                    // return early if the adjacent TFs are opaque.
                                    return true;
                                } else {
                                    adjacentVisibilityHelper = null;
                                }
                            }
                        }
                    }
                } else {
                    if (child.asTaskFragment() != null
                            && child.asTaskFragment().hasAdjacentTaskFragment()) {
                        final boolean isAnyTranslucent = !isOpaque(child)
                                || child.asTaskFragment().forOtherAdjacentTaskFragments(
                                        tf -> !isOpaque(tf));
                        if (!isAnyTranslucent) {
                            // This task fragment and all its adjacent task fragments are opaque,
                            // consider it opaque even if it doesn't fill its parent.
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean test(ActivityRecord r) {
            if (mIgnoringInvisibleActivity && r != mStarting
                    && ((mIgnoringKeyguard && !r.visibleIgnoringKeyguard)
                    || (!mIgnoringKeyguard && !r.isVisible()))) {
                // Ignore invisible activities that are not the currently starting activity
                // (about to be visible).
                return false;
            }
            return r.occludesParent(!mIgnoringInvisibleActivity /* includingFinishing */);
        }
    }

    /**
     * The helper class to calculate the visibility of the adjacent TaskFragments.
     * </p>
     * For a complex case as below, the adjacent TaskFragments contain a translucent TaskFragment.
     * In that case, the TaskFragment B should be visible, but Task#1 should not be visible if
     * TaskFragment B occludes the whole area of the translucent TaskFragment C.
     * Task#2
     *    - TaskFragment C (adjacent to A, translucent)
     *    - TaskFragment B
     *    - TaskFragment A (adjacent to C)
     * Task#1
     * </p>
     * The visibility calculation should be done by processing the TaskFragments from top to
     * bottom, by calling {@link #process(TaskFragment)}.
     */
    private static class AdjacentVisibilityHelper {

        @NonNull
        private final ArraySet<TaskFragment> mUnprocessedAdjacentTaskFragments;
        @NonNull
        private final ArraySet<TaskFragment> mTranslucentTaskFragments = new ArraySet<>();
        @NonNull
        private final Predicate<TaskFragment> mOccludingCallback;

        AdjacentVisibilityHelper(@NonNull TaskFragment taskFragment,
                @NonNull Predicate<TaskFragment> occludingCallback) {
            mUnprocessedAdjacentTaskFragments =
                    taskFragment.getAdjacentTaskFragments().getTaskFragments();
            mOccludingCallback = occludingCallback;
        }

        /**
         * Process the given TaskFragment. The TaskFragment should be one of the adjacent
         * TaskFragments or the TaskFragments in between the adjacent TFs.
         */
        void process(@NonNull TaskFragment taskFragment) {
            if (mUnprocessedAdjacentTaskFragments.contains(taskFragment)) {
                mUnprocessedAdjacentTaskFragments.remove(taskFragment);
            }

            if (mOccludingCallback.test(taskFragment)) {
                // Remove the translucent TaskFragments if it can be fully occluded by this
                // TaskFragment.
                mTranslucentTaskFragments.removeIf(
                        t -> taskFragment.getBounds().contains(t.getBounds()));
            } else {
                mTranslucentTaskFragments.add(taskFragment);
            }
        }

        /**
         * Returns {@code true} if the process is done, i.e. the adjacent TaskFragments are all
         * processed.
         */
        boolean isAllAdjacentTaskFragmentProcessed() {
            return mUnprocessedAdjacentTaskFragments.isEmpty();
        }

        /**
         * Returns {@code true} if the given TaskFragment is one of the adjacent TaskFragment
         * that's not yet processed.
         */
        boolean isUnprocessedAdjacentTaskFragment(TaskFragment tf) {
            return mUnprocessedAdjacentTaskFragments.contains(tf);
        }

        /**
         * Returns {@code true} if the adjacent TaskFragments (and the TaskFragments in between)
         * can occlude parent container. Must be called after all adjacent TFs are processed.
         */
        boolean occludesParent() {
            return mTranslucentTaskFragments.isEmpty();
        }

        /**
         * Return {@code true} if the given TaskFragment is behind any of the translucent
         * TaskFragments
         */
        boolean isBehindTranslucentTaskFragment(@NonNull TaskFragment tf) {
            if (mUnprocessedAdjacentTaskFragments.contains(tf)) {
                return false;
            }

            final Rect bounds = tf.getBounds();
            for (int i = mTranslucentTaskFragments.size() - 1; i >= 0; i--) {
                final TaskFragment taskFragment = mTranslucentTaskFragments.valueAt(i);
                if (bounds.intersect(taskFragment.getBounds())) {
                    return true;
                }
            }
            return false;
        }
    }
}
