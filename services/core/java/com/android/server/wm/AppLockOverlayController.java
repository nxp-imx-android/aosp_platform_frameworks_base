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

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_APP_LOCK;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IApplicationThread;
import android.app.ProfilerInfo;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.LockedAppActivity;
import com.android.internal.protolog.ProtoLog;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manages the presentation of overlays for applications secured by the App Lock feature.
 *
 * <p>This controller is responsible for ensuring that when a user attempts to access a locked
 * application, an authentication screen is presented before the app's content is revealed. It
 * operates by placing a {@link LockedAppActivity} on top of any content that needs to be secured.
 *
 * <p>The controller centralizes App Lock logic around overlays. It is invoked under two primary
 * conditions:
 * <ul>
 *     <li>When a package's locked state changes (e.g., after a device unlock timeout).
 *     <li>When a task containing a locked app becomes invisible (e.g., the user navigates away).
 * </ul>
 *
 * <p>As a security fallback, if an overlay cannot be successfully launched, this controller will
 * either remove the associated task or finish the specific activity to prevent unauthorized
 * access.
 *
 * <p>This controller relies on the existing task and activity lifecycle management in the system.
 * For example, when an overlay activity is the last remaining activity in a task, the task is
 * automatically removed, and the overlay activity is finished. This is handled by the logic in
 * {@link Task#removeChild(WindowContainer, String)} and
 * {@link ActivityRecord#finishIfPossible(String, boolean)}, so no special cleanup is needed in this
 * controller.
 *
 * @hide
 */
final class AppLockOverlayController {

    @GuardedBy("mWmService.mGlobalLock")
    private final ArrayMap<Task, WindowContainerListener> mTaskListeners = new ArrayMap<>();

    private final ActivityTaskManagerService mAtmService;
    private final WindowManagerService mWmService;
    private ActivityTaskSupervisor mTaskSupervisor;
    private RootWindowContainer mRootWindowContainer;

    AppLockOverlayController(WindowManagerService wmService) {
        mWmService = wmService;
        mAtmService = wmService.mAtmService;
    }

    private RootWindowContainer getRootWindowContainer() {
        if (mRootWindowContainer == null) {
            mRootWindowContainer = mAtmService.mRootWindowContainer;
        }
        return mRootWindowContainer;
    }

    private ActivityTaskSupervisor getTaskSupervisor() {
        if (mTaskSupervisor == null) {
            mTaskSupervisor = mAtmService.mTaskSupervisor;
        }
        return mTaskSupervisor;
    }

    /**
     * Locks all activities and tasks associated with a given package.
     *
     * <p>This method is called when a package's locked state needs to be enforced, for example,
     * after a device unlock or a timeout. It iterates through all leaf tasks to identify content
     * belonging to the specified {@code packageName} and {@code userId}.
     *
     * <p>It applies one of two locking strategies:
     * <ul>
     *     <li><b>Task-level overlay:</b> If a task's primary identity ({@code realActivity})
     *         belongs to the locked package, a single overlay is placed on the entire task.</li>
     *     <li><b>Activity-level overlay:</b> If a task contains activities from the locked package
     *         but has a different primary identity (i.e., a shared task), a single overlay is
     *         placed above the topmost activity of the locked package. This is only done for
     *         non-visible tasks to avoid disrupting the foreground app.</li>
     * </ul>
     *
     * <p>The method skips locking entirely if the package already has at least one visible task,
     * under the assumption that a visible app is considered unlocked for the current session.
     *
     * @param packageName the name of the package whose tasks and activities are to be locked.
     * @param userId      the user ID for which the package should be locked.
     */
    @GuardedBy("mWmService.mGlobalLock")
    void lockActivitiesTasksForAppLockLocked(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        ProtoLog.d(WM_DEBUG_APP_LOCK, "lockActivitiesTasksForAppLockLocked: packageName=%s,"
                + " userId=%d", packageName, userId);
        // TODO(b/462423789): Remove hasVisibleTask check once AppLockLocalService listens to task
        //  visibility changes.
        // If the package owns at least one visible task, it is considered unlocked.
        final boolean hasVisibleTask = hasVisibleTaskForPackageLocked(packageName, userId);
        if (hasVisibleTask) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "lockActivitiesTasksForAppLockLocked: Skipping lock for"
                    + " %s, package owns a visible task", packageName);
            return;
        }

        // The package doesn't own any visible tasks, so activity and/or task overlays need to be
        // added.
        getRootWindowContainer().forAllLeafTasks(task -> {
            if (task.mUserId != userId) {
                return;
            }
            if (task.realActivity == null || task.getTopNonFinishingActivity() == null) {
                ProtoLog.d(WM_DEBUG_APP_LOCK, "lockActivitiesTasksForAppLockLocked: Skipping %s,"
                        + " no real or non-finishing activity", task);
                return;
            }
            if (packageName.equals(task.realActivity.getPackageName())) {
                // The entire task belongs to the locked package, so apply a task-level overlay.
                addLockedByAppLockTaskOverlayLocked(task, packageName, userId);
            } else {
                // The task belongs to a different package,so find the topmost activity belonging to
                // the locked package and place a single overlay above it. Note that this method
                // won't be called on the current visible activity since as part of
                // AppLockInternal#isPackageLocked logic the app is considered unlocked while it is
                // in the foreground.
                final ActivityRecord topmostLockedActivity = task.getActivity(activity ->
                        !activity.finishing
                                && packageName.equals(activity.packageName)
                                && activity.mUserId == userId, /* traverseTopToBottom= */ true);

                if (topmostLockedActivity != null) {
                    addLockedByAppLockActivityOverlayLocked(topmostLockedActivity);
                }
            }
        }, /* traverseTopToBottom= */ true);
    }

    /**
     * Registers a task to be monitored for App Lock visibility changes.
     *
     * <p>This method is called by the {@link Task} constructor to automatically register itself
     * with the controller. It attaches a {@link WindowContainerListener} to the given {@code task},
     * allowing the controller to respond to visibility changes by applying or managing App Lock
     * overlays as needed.
     *
     * <p>Locking Model: This method, and the listener callbacks it registers
     * (e.g., {@code onVisibleRequestedChanged}), are invoked by the system while the global
     * {@code WindowManagerGlobalLock} is already held. Therefore, no additional synchronization is
     * needed within this method or its callbacks.
     *
     * <p>To prevent memory leaks, the attached listener is responsible for its own cleanup. It
     * automatically unregisters itself from the task and removes its reference from the
     * controller's internal map when the task is removed (via the
     * {@link WindowContainerListener#onRemoved()} callback).
     *
     * @param task The task to be monitored.
     */
    @GuardedBy("mWmService.mGlobalLock")
    void registerTask(@NonNull Task task) {
        Objects.requireNonNull(task);

        if (mTaskListeners.containsKey(task)) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "registerTask: Already registered: %s", task);
            return;
        }
        final WindowContainerListener listener = new WindowContainerListener() {
            @Override
            public void onVisibleRequestedChanged(boolean isVisibleRequested) {
                onTaskVisibleRequestedChangedLocked(task, isVisibleRequested);
            }

            @Override
            public void onRemoved() {
                task.unregisterWindowContainerListener(this);
                mTaskListeners.remove(task);
            }
        };
        task.registerWindowContainerListener(listener);
        mTaskListeners.put(task, listener);
    }

    /**
     * Called when a task's visibility changes to apply App Lock overlays if necessary.
     *
     * <p>This method is the entry point for locking content when a user navigates away from a task.
     * It only acts when a task becomes invisible ({@code isVisibleRequested = false}).
     *
     * <p>The locking strategy depends on the task's content:
     * <ul>
     *     <li>If the task's primary package is locked and it is the last visible task for that
     *         package, a task-level overlay is applied to secure the entire task.
     *     <li>If the task itself is not locked but contains activities from multiple locked
     *         packages (i.e., a shared task), this method ensures that one overlay is placed above
     *         the topmost activity of each locked package.
     * </ul>
     *
     * @param task               the task whose visibility has changed.
     * @param isVisibleRequested {@code true} if the task is now visible, {@code false} otherwise.
     */
    @GuardedBy("mWmService.mGlobalLock")
    private void onTaskVisibleRequestedChangedLocked(@NonNull Task task,
            boolean isVisibleRequested) {
        Objects.requireNonNull(task);

        ProtoLog.d(WM_DEBUG_APP_LOCK, "onTaskVisibleRequestedChangedLocked: %s"
                + " isVisibleRequested=%b", task, isVisibleRequested);
        if (isVisibleRequested) {
            return;
        }
        if (task.getTopNonFinishingActivity() == null) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "onTaskVisibleRequestedChangedLocked: Skipping %s, no"
                    + " non-finishing activity", task);
            return;
        }
        if (task.realActivity == null) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "onTaskVisibleRequestedChangedLocked: Skipping %s, no"
                    + " real activity", task);
            return;
        }
        final String packageName = task.realActivity.getPackageName();
        final int userId = task.mUserId;
        if (mWmService.isPackageLockedByAppLock(packageName, userId)) {
            // TODO(b/462423789): Remove hasOtherVisibleTask check once AppLockLocalService listens
            //  to task visibility changes.
            // The entire task belongs to the locked package, so apply a task-level overlay if
            // the package doesn't own any other visible tasks. Note that, the current task is
            // already marked as invisible via task.isVisibleRequested.
            final boolean hasOtherVisibleTask = hasVisibleTaskForPackageLocked(packageName, userId);
            if (!hasOtherVisibleTask) {
                addLockedByAppLockTaskOverlayLocked(task, packageName, userId);
            }
        } else {
            // The task belongs to a different package and is no longer visible. For each locked
            // package present in the task, find its topmost activity and place a single overlay
            // above it.
            final ArrayMap<String, ActivityRecord> topmostActivitiesByPackage = new ArrayMap<>();
            task.forAllActivities(activity -> {
                if (activity.finishing) {
                    return;
                }
                if (mWmService.isPackageLockedByAppLock(activity.packageName, activity.mUserId)) {
                    // Since we traverse top-to-bottom, the first time we see an activity for a
                    // given package, it is the topmost one.
                    topmostActivitiesByPackage.putIfAbsent(activity.packageName, activity);
                }
            }, /* traverseTopToBottom= */ true);

            for (int i = 0; i < topmostActivitiesByPackage.size(); i++) {
                addLockedByAppLockActivityOverlayLocked(topmostActivitiesByPackage.valueAt(i));
            }
        }
    }

    /**
     * Checks if there is at least one visible task for a given package and user.
     *
     * @param packageName the package name to check for.
     * @param userId      the user ID to check for.
     * @return {@code true} if a visible task is found, {@code false} otherwise.
     */
    @GuardedBy("mWmService.mGlobalLock")
    private boolean hasVisibleTaskForPackageLocked(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        return getRootWindowContainer().getTask(task ->
                task.mUserId == userId
                        && task.isVisibleRequested()
                        && task.realActivity != null
                        && packageName.equals(task.realActivity.getPackageName())
                        && task.getTopNonFinishingActivity() != null)
                != null;
    }

    /**
     * Adds a task-level overlay for a locked app as part of the App Lock feature.
     *
     * <p>This method launches {@link LockedAppActivity} on top of the specified {@code task}. The
     * {@code LockedAppActivity} is configured as a task overlay, which blocks access to all
     * activities within the task until the user successfully authenticates. A new overlay is not
     * added if an existing one is found for the same package and user.
     *
     * <p>If launching the overlay fails, this method removes the task as a security fallback to
     * prevent unauthorized access to the locked application's content.
     *
     * @param overlayTask the task to be locked with an overlay.
     * @param packageName the package name of the locked app, used to create the overlay intent.
     * @param userId      the user ID for which the app is locked.
     */
    @VisibleForTesting
    @GuardedBy("mWmService.mGlobalLock")
    void addLockedByAppLockTaskOverlayLocked(@NonNull Task overlayTask, @NonNull String packageName,
            int userId) {
        Objects.requireNonNull(overlayTask);
        Objects.requireNonNull(packageName);

        ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockTaskOverlayLocked: %s, packageName=%s",
                overlayTask, packageName);

        final ActivityRecord topNonFinishingActivity = overlayTask.getTopNonFinishingActivity();
        if (topNonFinishingActivity == null) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockTaskOverlayLocked: Skipping %s, it"
                    + " has no activities", overlayTask);
            return;
        }
        if (isActivityAppLockOverlay(topNonFinishingActivity, packageName, userId)) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockTaskOverlayLocked: Skipping %s, an"
                    + " overlay already exists: %s", overlayTask, topNonFinishingActivity);
            return;
        }
        final Intent intent = createOverlayIntent(packageName, userId, /* isTaskOverlay= */ true);
        startLockedAppActivityAsync(overlayTask, intent, userId, /* onSuccessResultLocked= */ null);
    }

    /**
     * Adds an activity-level overlay for a specific locked activity within a shared task.
     *
     * <p>This method ensures that only a single activity is obscured, rather than the entire task.
     * It achieves this by first launching a {@link LockedAppActivity} into the target's task, which
     * initially places it at the top of the task stack. It then finds this newly created overlay
     * activity and repositions it in the Z-order to sit directly on top of the
     * {@code targetActivity}. A new overlay is not added if an existing one is found for the same
     * package and user.
     *
     * <p>If any step of this process fails (e.g., the overlay cannot be launched or found), the
     * {@code targetActivity} is finished as a security fallback to prevent unauthorized access.
     *
     * @param targetActivity the activity to be locked with an overlay.
     */
    // TODO(b/461861228): Do not add overlays for activities with ActivityRecord#showWhenLocked and
    //  ActivityInfo#showWhenLocked. This also requires reacting to Keyguard flag changes in
    //  DisplayContent#notifyKeyguardFlagsChanged, which indicates that activities' visibility needs
    //  to be reevaluated.
    // TODO(b/463749806): Handle activity overlays when the target activity is moved or removed.
    @VisibleForTesting
    @GuardedBy("mWmService.mGlobalLock")
    void addLockedByAppLockActivityOverlayLocked(@NonNull ActivityRecord targetActivity) {
        Objects.requireNonNull(targetActivity);

        ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlayLocked: targetActivity=%s",
                targetActivity);
        if (targetActivity.finishing) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlayLocked: Skipping %s,"
                    + " it is finishing", targetActivity);
            return;
        }
        final Task targetTask = targetActivity.getTask();
        if (targetTask == null) {
            ProtoLog.w(WM_DEBUG_APP_LOCK, "Cannot add activity overlay for an activity with no"
                    + " task: %s", targetActivity);
            return;
        }
        final String packageName = targetActivity.packageName;
        if (packageName == null) {
            ProtoLog.w(WM_DEBUG_APP_LOCK, "Cannot add activity overlay for an activity with no"
                    + " package: %s", targetActivity);
            return;
        }
        final int userId = targetActivity.mUserId;

        // An overlay should not be added if one already exists on top of the target activity.
        final TaskFragment targetParent = targetActivity.getTaskFragment();
        if (targetParent != null) {
            final int targetActivityIndex = targetParent.mChildren.indexOf(targetActivity);
            if (targetActivityIndex >= 0
                    && targetParent.mChildren.size() > targetActivityIndex + 1) {
                final ActivityRecord activityOnTop = targetParent.mChildren.get(
                        targetActivityIndex + 1).asActivityRecord();

                if (activityOnTop != null && isActivityAppLockOverlay(activityOnTop, packageName,
                        userId)) {
                    ProtoLog.d(WM_DEBUG_APP_LOCK, "addLockedByAppLockActivityOverlayLocked:"
                            + " Skipping %s, an overlay already exists on top of it: %s",
                            targetActivity, activityOnTop);
                    return;
                }
            }
        }

        final Intent intent = createOverlayIntent(packageName, userId, /* isTaskOverlay= */ false);
        // Create a unique identifier to find the overlay activity later.
        final String overlayIdentifier = "unique:" + UUID.randomUUID();
        intent.setIdentifier(overlayIdentifier);

        // Launch the overlay activity into the same task. It will initially appear
        // at the top of the task stack and be moved to the correct position later.
        startLockedAppActivityAsync(targetTask, intent, userId,
                /* onSuccessResultLocked= */ (task) -> findAndRepositionActivityOverlayLocked(task,
                        overlayIdentifier, targetActivity));
    }

    /**
     * Finds and repositions the App Lock overlay activity to securely cover its target.
     *
     * <p>This method is called after the overlay activity has been successfully launched. It finds
     * the overlay using a unique identifier and positions it correctly based on the target
     * activity's context.
     *
     * <p>It handles two primary scenarios:
     * <ol>
     *     <li><b>Same Container:</b> If the overlay and target activity share the same parent
     *         {@link TaskFragment}, the overlay is positioned directly above the target activity in
     *         the Z-order.
     *     <li><b>Activity Embedding:</b> If the target is in an inner {@link TaskFragment} and the
     *         overlay is at the {@link Task} level, this method identifies the entire set of
     *         adjacent {@code TaskFragment}s (e.g., in a split-pane view). It then positions the
     *         overlay above the topmost fragment in that set, ensuring the entire embedded surface
     *         is covered.
     * </ol>
     *
     * <p>If any step fails (e.g., the overlay or target cannot be found, or their parent
     * containers are inconsistent), this method falls back to finishing the target and/or overlay
     * activity to maintain security.
     *
     * @param task              the task containing the target activity and the overlay.
     * @param overlayIdentifier the unique identifier used to find the overlay activity.
     * @param targetActivity    the activity that is being locked.
     */
    @GuardedBy("mWmService.mGlobalLock")
    private void findAndRepositionActivityOverlayLocked(@NonNull Task task,
            @NonNull String overlayIdentifier, @NonNull ActivityRecord targetActivity) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(overlayIdentifier);
        Objects.requireNonNull(targetActivity);

        // Step 1: Find the newly launched overlay activity.
        final ActivityRecord overlayActivity = task.getActivity(
                activity -> overlayIdentifier.equals(activity.intent.getIdentifier()));
        if (overlayActivity == null) {
            ProtoLog.w(WM_DEBUG_APP_LOCK, "Could not find the launched overlay activity, finishing"
                    + " target: %s", targetActivity);
            finishActivitySafe(targetActivity, "could-not-find-overlay");
            return;
        }

        // Step 2: Validate that the target is still valid.
        if (targetActivity.finishing) {
            ProtoLog.d(WM_DEBUG_APP_LOCK, "Target activity is finishing, finishing overlay: %s",
                    overlayActivity);
            finishActivitySafe(overlayActivity, "target-finishing");
            return;
        }

        // Step 3: Check if the target and overlay have TaskFragment parents.
        final TaskFragment targetTaskFragment = targetActivity.getTaskFragment();
        final TaskFragment overlayTaskFragment = overlayActivity.getTaskFragment();

        if (targetTaskFragment == null || overlayTaskFragment == null) {
            ProtoLog.w(WM_DEBUG_APP_LOCK, "Target or overlay has no TaskFragment parent, finishing"
                            + " target and overlay. Target: %s, overlay: %s", targetActivity,
                    overlayActivity);
            finishActivitySafe(targetActivity, "target-or-overlay-has-no-parent");
            finishActivitySafe(overlayActivity, "target-or-overlay-has-no-parent");
            return;
        }

        // Step 4: Position the overlay above the target depending on their parents.
        if (targetTaskFragment == overlayTaskFragment) {
            // If parents are the same, position the overlay directly above the target.
            final int targetActivityIndex = targetTaskFragment.mChildren.indexOf(targetActivity);
            final int currentOverlayIndex = targetTaskFragment.mChildren.indexOf(overlayActivity);

            if (targetActivityIndex < 0 || currentOverlayIndex < 0) {
                ProtoLog.w(WM_DEBUG_APP_LOCK, "Could not find the target or overlay activity in its"
                                + " parent, finishing target and overlay. Target: %s, overlay: %s",
                        targetActivity, overlayActivity);
                finishActivitySafe(targetActivity, "target-or-overlay-not-in-parent");
                finishActivitySafe(overlayActivity, "target-or-overlay-not-in-parent");
                return;
            }

            // The overlay should be placed one position above the target.
            final int newOverlayIndex = targetActivityIndex + 1;
            if (currentOverlayIndex != newOverlayIndex) {
                targetTaskFragment.positionChildAt(newOverlayIndex, overlayActivity,
                        /* includingParents= */ false);
                ProtoLog.d(WM_DEBUG_APP_LOCK, "Moved %s to position %d above %s", overlayActivity,
                        newOverlayIndex, targetActivity);
            }
        } else {
            // If parents are different (Activity Embedding), position the overlay to cover the
            // targetTaskFragment and its topmost adjacent TaskFragment.

            // In this scenario, the overlay should be a direct child of a Task, so its containing
            // TaskFragment is supposed to be a Task.
            final Task parentTask = overlayTaskFragment.asTask();
            if (parentTask == null) {
                ProtoLog.w(WM_DEBUG_APP_LOCK, "Overlay is not directly in a Task, which is"
                        + " unexpected, finishing target and overlay. Target: %s,"
                        + " overlay: %s", targetActivity, overlayActivity);
                finishActivitySafe(targetActivity, "overlay-not-in-task");
                finishActivitySafe(overlayActivity, "overlay-not-in-task");
                return;
            }

            // The target's containing TaskFragment must belong to the same parent Task.
            if (targetTaskFragment.getTask() != parentTask) {
                ProtoLog.w(WM_DEBUG_APP_LOCK, "Target's Task does not match overlay's Task,"
                                + " finishing target and overlay. Target: %s, overlay: %s",
                        targetActivity, overlayActivity);
                finishActivitySafe(targetActivity, "overlay-parent-mismatch");
                finishActivitySafe(overlayActivity, "overlay-parent-mismatch");
                return;
            }

            // To find the topmost TaskFragment, start with the targetTaskFragment as topmost.
            final TaskFragment[] topmostFragmentInSet = {targetTaskFragment};
            final int[] topmostFragmentIndex = {parentTask.mChildren.indexOf(
                    topmostFragmentInSet[0])};

            // Iterate through the other adjacent fragments, comparing each one to the current
            // topmost fragment.
            targetTaskFragment.forOtherAdjacentTaskFragments(adjacentFragment -> {
                final int currentIndex = parentTask.mChildren.indexOf(adjacentFragment);
                if (currentIndex > topmostFragmentIndex[0]) {
                    // This fragment is higher in the Z-order, so it becomes the new topmost.
                    topmostFragmentIndex[0] = currentIndex;
                    topmostFragmentInSet[0] = adjacentFragment;
                }
            });

            // Position the overlay activity immediately above the topmost fragment.
            final int currentOverlayIndex = parentTask.mChildren.indexOf(overlayActivity);
            final int newOverlayIndex = (currentOverlayIndex < topmostFragmentIndex[0])
                    ? topmostFragmentIndex[0] : topmostFragmentIndex[0] + 1;

            if (currentOverlayIndex == newOverlayIndex) {
                ProtoLog.d(WM_DEBUG_APP_LOCK, "Overlay %s is already at position %d, which is on"
                                + " top of the TaskFragment set ending at %s, so no need to move",
                        overlayActivity, currentOverlayIndex, topmostFragmentInSet[0]);
            } else {
                parentTask.positionChildAt(newOverlayIndex, overlayActivity,
                        /* includingParents= */ false);
                ProtoLog.d(WM_DEBUG_APP_LOCK, "Moved overlay %s to position %d to be on top of"
                                + " TaskFragment set ending at %s", overlayActivity,
                        newOverlayIndex,
                        topmostFragmentInSet[0]);
            }
        }
    }

    private boolean isActivityAppLockOverlay(@NonNull ActivityRecord activity,
            @NonNull String packageName, int userId) {
        return !activity.finishing
                && activity.isTaskOverlay()
                && activity.mActivityComponent != null
                && activity.intent != null
                && LockedAppActivity.class.getName().equals(
                activity.mActivityComponent.getClassName())
                && packageName.equals(activity.intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
                && userId == activity.mUserId;
    }

    private void finishActivitySafe(@NonNull ActivityRecord activity, @NonNull String reason) {
        if (!activity.finishing) {
            activity.finishIfPossible(reason, /* oomAdj= */ true);
        }
    }

    /**
     * Creates an intent for launching the {@link LockedAppActivity} overlay.
     *
     * @param packageName   the package name of the app being locked.
     * @param userId        the user ID of the app being locked.
     * @param isTaskOverlay whether the overlay is for a task (true) or an activity (false).
     * @return an intent configured to launch the overlay activity.
     */
    private Intent createOverlayIntent(@NonNull String packageName, int userId,
            boolean isTaskOverlay) {
        Objects.requireNonNull(packageName);

        final Intent intent = LockedAppActivity.createLockedAppActivityIntent(packageName, userId,
                /* target= */ null);
        if (isTaskOverlay) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        return intent;
    }

    /**
     * Starts the {@link LockedAppActivity} asynchronously on the handler thread.
     *
     * <p>This method posts a runnable to the {@link ActivityTaskManagerService}'s handler to start
     * the activity. This is necessary to avoid deadlocks that can occur if {@code startActivity} is
     * called while holding the {@link WindowManagerGlobalLock}.
     *
     * <p>If the activity start is successful, the provided {@code onSuccessResultLocked} callback
     * is invoked on the handler thread with the {@link WindowManagerGlobalLock} held. If the start
     * fails, the task is removed as a security fallback.
     *
     * @param targetTask            the task where the overlay activity should be launched.
     * @param intent                the intent to launch the overlay activity.
     * @param userId                the user ID to launch the activity as.
     * @param onSuccessResultLocked a callback invoked if the overlay was successfully launched on
     *                              the re-validated task.
     */
    private void startLockedAppActivityAsync(@NonNull Task targetTask, @NonNull Intent intent,
            int userId, @Nullable Consumer<Task> onSuccessResultLocked) {
        Objects.requireNonNull(targetTask);
        Objects.requireNonNull(intent);

        final int taskId = targetTask.mTaskId;
        mAtmService.mH.post(() -> {
            final int result = startActivityAsUserUnchecked(intent, taskId, userId);
            synchronized (mWmService.mGlobalLock) {
                final Task task = getRootWindowContainer().anyTaskForId(taskId);
                if (task == null) {
                    ProtoLog.w(WM_DEBUG_APP_LOCK, "Task %d not found after launching overlay",
                            taskId);
                    return;
                }
                if (ActivityManager.isStartResultSuccessful(result)) {
                    if (onSuccessResultLocked != null) {
                        onSuccessResultLocked.accept(task);
                    }
                } else {
                    ProtoLog.e(WM_DEBUG_APP_LOCK, "Could not add App Lock task overlay for %s,"
                            + " removing task as fallback.", task);
                    getTaskSupervisor().removeTask(task, /* killProcess= */ false,
                            /* removeFromRecents= */ true, "Could not add App Lock task overlay");
                }
            }
        });
    }

    /**
     * Starts an activity as a given user without holding the global lock.
     *
     * <p>This method is a wrapper around
     * {@link ActivityTaskManagerService#startActivityAsUser(IApplicationThread, String, String,
     * Intent, String, IBinder, String, int, int, ProfilerInfo, Bundle, int)}
     * that handles exceptions and returns a standard result code. It must <b>NOT</b> be called
     * while holding the global lock to avoid deadlocks.
     *
     * @param intent the intent to start.
     * @param taskId the ID of the task to launch the activity into.
     * @param userId the user ID to launch the activity as.
     * @return the result of the start request, e.g., {@link ActivityManager#START_SUCCESS} or
     * {@link ActivityManager#START_CANCELED}.
     */
    private int startActivityAsUserUnchecked(@NonNull Intent intent, int taskId, int userId) {
        Objects.requireNonNull(intent);

        try {
            final ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchTaskId(taskId);
            options.setTaskOverlay(/* taskOverlay= */ true, /* canResume= */ false);
            return mAtmService.startActivityAsUser(
                    /* caller= */ mAtmService.mContext.getIApplicationThread(),
                    /* callingPackage= */ mAtmService.mContext.getBasePackageName(),
                    /* callingAttributionTag= */ mAtmService.mContext.getAttributionTag(), intent,
                    intent.resolveTypeIfNeeded(mAtmService.mContext.getContentResolver()),
                    /* resultTo= */ null, /* resultWho= */ null, /* requestCode= */ 0,
                    Intent.FLAG_ACTIVITY_NEW_TASK, /* profilerInfo= */ null, options.toBundle(),
                    userId);
        } catch (Exception e) {
            ProtoLog.e(WM_DEBUG_APP_LOCK, "startActivityAsUser failed: %s", e);
            return ActivityManager.START_CANCELED;
        }
    }
}
