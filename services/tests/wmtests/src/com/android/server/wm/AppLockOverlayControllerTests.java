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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.WindowContainer.POSITION_BOTTOM;
import static com.android.server.wm.WindowContainer.POSITION_TOP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IApplicationThread;
import android.app.ProfilerInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.security.Flags;

import androidx.test.filters.MediumTest;

import com.android.internal.app.LockedAppActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/**
 * Build/Install/Run:
 * atest WmTests:AppLockOverlayControllerTests
 */
@EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppLockOverlayControllerTests extends WindowTestsBase {

    private AppLockOverlayController mAppLockOverlayController;

    @Before
    public void setUp() throws Exception {
        mAppLockOverlayController = mWm.mAppLockController.mAppLockOverlayController;
        spyOn(mAppLockOverlayController);
        spyOn(mRootWindowContainer);
    }

    @Test
    public void lockActivitiesTasksForAppLockLocked_emptyTask_doesNothing() {
        // GIVEN an empty task
        final Task task = createLockedTask(/* isVisible= */ false);
        task.mChildren.clear();

        // WHEN we try to lock tasks for that package
        mAppLockOverlayController.lockActivitiesTasksForAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN no overlay is added because the task doesn't have any children
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlayLocked(any(), any(),
                anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void lockActivitiesTasksForAppLockLocked_withNullRealActivity_doesNothing() {
        // GIVEN a task with a null realActivity
        final Task task = createLockedTask(/* isVisible= */ false);
        task.realActivity = null;

        // WHEN we try to lock tasks for that package
        mAppLockOverlayController.lockActivitiesTasksForAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN no overlay is added because the task has no realActivity to check
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlayLocked(any(),
                any(), anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void lockActivitiesTasksForAppLockLocked_withVisibleTask_doesNothing() {
        // GIVEN a task that is visible and belongs to a locked package
        createLockedTask(/* isVisible= */ true);

        // WHEN we try to lock tasks for that package
        mAppLockOverlayController.lockActivitiesTasksForAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN no overlay is added because the task is already visible
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlayLocked(any(),
                any(), anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void lockActivitiesTasksForAppLockLocked_withInvisibleTask_locksTask() {
        // GIVEN an invisible task that belongs to a locked package
        final Task task = createLockedTask(/* isVisible= */ false);
        doNothing().when(mAppLockOverlayController).addLockedByAppLockTaskOverlayLocked(any(),
                any(), anyInt());

        // WHEN we try to lock tasks for that package
        mAppLockOverlayController.lockActivitiesTasksForAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN a task-level overlay is added
        verify(mAppLockOverlayController).addLockedByAppLockTaskOverlayLocked(eq(task),
                eq(TEST_PACKAGE_1), eq(TEST_USER_ID_1));
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void lockActivitiesTasksForAppLockLocked_withInvisibleMixedTask_locksTopmostActivity() {
        // GIVEN an invisible task with multiple locked activities from the same package
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible=*/ false);
        final ActivityRecord topLockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(mixedStateTask.mTask)
                .build();
        mixedStateTask.mTask.positionChildAt(POSITION_TOP, topLockedActivity,
                /* includingParents= */ false);
        // Ensure the task is not visible since adding a new activity may make it visible.
        mixedStateTask.mTask.setVisibleRequested(false);
        clearInvocations(mAppLockOverlayController);
        doNothing().when(mAppLockOverlayController).addLockedByAppLockActivityOverlayLocked(any());

        // WHEN we try to lock tasks for the locked package
        mAppLockOverlayController.lockActivitiesTasksForAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN an activity-level overlay is added only for the topmost locked activity
        verify(mAppLockOverlayController).addLockedByAppLockActivityOverlayLocked(
                eq(topLockedActivity));
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(
                eq(mixedStateTask.mLockedActivity));
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(
                eq(mixedStateTask.mUnlockedActivity));
    }

    @Test
    public void lockActivitiesTasksForAppLockLocked_withVisibleMixedTask_locksTopmostActivity() {
        // GIVEN a visible task with multiple locked activities from the same package
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ true);
        final ActivityRecord topLockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(mixedStateTask.mTask)
                .build();
        mixedStateTask.mTask.positionChildAt(POSITION_TOP, topLockedActivity,
                /* includingParents= */ false);
        final ActivityRecord topUnlockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_2) // This activity's package is not locked
                .setTask(mixedStateTask.mTask)
                .build();
        mixedStateTask.mTask.positionChildAt(POSITION_TOP, topUnlockedActivity,
                /* includingParents= */ false);
        // Ensure the task is visible.
        mixedStateTask.mTask.setVisibleRequested(true);
        clearInvocations(mAppLockOverlayController);
        doNothing().when(mAppLockOverlayController).addLockedByAppLockActivityOverlayLocked(any());

        // WHEN we try to lock tasks for the locked package
        mAppLockOverlayController.lockActivitiesTasksForAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN an activity-level overlay is added only for the topmost locked activity
        verify(mAppLockOverlayController).addLockedByAppLockActivityOverlayLocked(
                eq(topLockedActivity));
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(
                eq(mixedStateTask.mLockedActivity));
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(
                eq(mixedStateTask.mUnlockedActivity));
    }

    @Test
    public void lockActivitiesTasksForAppLockLocked_withMixedTask_activityFinishing_doesNothing() {
        // GIVEN an invisible task with a mix of locked and unlocked activities
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ false);
        final ActivityRecord lockedActivity = mixedStateTask.mLockedActivity;
        lockedActivity.finishing = true;

        // WHEN we try to lock tasks for the locked package
        mAppLockOverlayController.lockActivitiesTasksForAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN no overlay is added
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlayLocked(any(), any(),
                anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void registerTask_registersListenerOnTask() {
        // GIVEN a mock task
        // Note: We use a mock Task here instead of getTestTask() because getTestTask() creates a
        // real Task, which (when the flag is enabled) automatically registers itself with the
        // controller in its constructor. This causes the controller's registerTask method to return
        // early (already registered), preventing the listener registration we want to verify.
        final Task task = mock(Task.class);

        // WHEN the task is registered with the controller
        mAppLockOverlayController.registerTask(task);

        // THEN a window container listener is registered on the task
        verify(task).registerWindowContainerListener(any(WindowContainerListener.class));
    }

    @Test
    public void registerTask_listenerSelfUnregistersOnTaskRemoved() {
        // GIVEN a task that was previously registered
        final Task task = getTestTask();
        spyOn(task);

        // WHEN the task is removed
        task.removeIfPossible();

        // THEN the window container listener is unregistered from the task
        verify(task).unregisterWindowContainerListener(any(WindowContainerListener.class));
    }

    @Test
    public void onTaskBecomesInvisibleAndEmpty_doesNothing() {
        // GIVEN a locked visible empty task
        final Task task = createLockedTask(/* isVisible= */ true);
        task.mChildren.clear();

        // WHEN the task becomes invisible
        task.setVisibleRequested(false);

        // THEN no overlay is added
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlayLocked(any(),
                anyString(), anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void onTaskBecomesInvisibleAndLocked_addsTaskOverlay() {
        // GIVEN a locked visible task with no other visible tasks for that package
        final Task task = createLockedTask(/* isVisible= */ true);
        doNothing().when(mAppLockOverlayController).addLockedByAppLockTaskOverlayLocked(any(),
                any(), anyInt());

        // WHEN the task becomes invisible
        task.setVisibleRequested(false);

        // THEN a task-level overlay is added
        verify(mAppLockOverlayController).addLockedByAppLockTaskOverlayLocked(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void onTaskBecomesInvisibleAndLocked_withOtherVisible_doesNothing() {
        // GIVEN a locked visible task and another visible task for the same package
        final Task task1 = createLockedTask(/* isVisible= */ true);
        createLockedTask(/* isVisible= */ true);
        doNothing().when(mAppLockOverlayController).addLockedByAppLockTaskOverlayLocked(any(),
                any(), anyInt());

        // WHEN the first task becomes invisible
        task1.setVisibleRequested(false);

        // THEN no overlay is added because another task is still visible
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlayLocked(any(),
                anyString(), anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void onTaskBecomesInvisibleWithMultipleLockedPackages_addsOneOverlayPerPackage() {
        // GIVEN a visible task with activities from two different locked packages
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(false).build();
        task.realActivity = TEST_COMPONENT_3; // Main package is not locked
        task.mUserId = TEST_USER_ID_1;

        // Bottom locked activity for package 1
        new ActivityBuilder(mAtm).setComponent(TEST_COMPONENT_1).setTask(task).build();
        // Topmost locked activity for package 2
        final ActivityRecord topLockedActivity2 = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_2).setTask(task).build();
        // Unlocked activity
        new ActivityBuilder(mAtm).setComponent(TEST_COMPONENT_3).setTask(task).build();
        // Topmost locked activity for package 1
        final ActivityRecord topLockedActivity1 = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1).setTask(task).build();

        doReturn(true).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_1, TEST_USER_ID_1);
        doReturn(true).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_2, TEST_USER_ID_1);
        doReturn(false).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_3, TEST_USER_ID_1);
        doNothing().when(mAppLockOverlayController).addLockedByAppLockActivityOverlayLocked(any());
        clearInvocations(mAppLockOverlayController);

        // WHEN the task becomes invisible
        task.setVisibleRequested(false);

        // THEN an activity-level overlay is added for the topmost activity of each locked package
        verify(mAppLockOverlayController, times(1))
                .addLockedByAppLockActivityOverlayLocked(topLockedActivity1);
        verify(mAppLockOverlayController, times(1))
                .addLockedByAppLockActivityOverlayLocked(topLockedActivity2);
        verify(mAppLockOverlayController, times(2))
                .addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void onTaskBecomesInvisibleWithLockedActivityFinishing_doesNothing() {
        // GIVEN a visible task with a mix of locked and unlocked activities
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ true);
        final ActivityRecord lockedActivity = mixedStateTask.mLockedActivity;
        lockedActivity.finishing = true;

        // WHEN the task becomes invisible
        mixedStateTask.mTask.setVisibleRequested(false);

        // THEN no overlay is added
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlayLocked(any(),
                anyString(), anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void onTaskBecomesVisibleAndLocked_doesNothing() {
        // GIVEN a locked invisible task
        final Task task = createLockedTask(/* isVisible= */ false);

        // WHEN the task becomes visible
        task.setVisibleRequested(true);

        // THEN no overlay logic is added
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlayLocked(any(),
                anyString(), anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlayLocked(any());
    }

    @Test
    public void addLockedByAppLockTaskOverlayLocked_emptyTask_doesNothing() {
        // GIVEN an empty task
        final Task task = getTestTask();
        task.mChildren.clear();
        spyOn(mSupervisor);

        // WHEN a task overlay is added
        mAppLockOverlayController.addLockedByAppLockTaskOverlayLocked(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        waitHandlerIdle(mAtm.mH);

        // THEN nothing happens
        verify(mAtm, never()).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), eq(TEST_USER_ID_1));
        verify(mSupervisor, never()).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockTaskOverlayLocked_startsLockedAppActivity() {
        // GIVEN a task and a successful activity start result
        final Task task = getTestTask();
        doReturn(ActivityManager.START_SUCCESS).when(mAtm).startActivityAsUser(any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());

        // WHEN a task overlay is added
        mAppLockOverlayController.addLockedByAppLockTaskOverlayLocked(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        waitHandlerIdle(mAtm.mH);

        // THEN the LockedAppActivity is started with the correct options
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<android.os.Bundle> optionsCaptor =
                ArgumentCaptor.forClass(android.os.Bundle.class);
        verify(mAtm).startActivityAsUser(any(), any(), any(), intentCaptor.capture(), any(), any(),
                any(), anyInt(), anyInt(), any(), optionsCaptor.capture(), eq(TEST_USER_ID_1));

        final Intent intent = intentCaptor.getValue();
        final Intent expectedIntent = LockedAppActivity.createLockedAppActivityIntent(
                TEST_PACKAGE_1, TEST_USER_ID_1, /* target= */ null);
        assertThat(intent.getComponent()).isEqualTo(expectedIntent.getComponent());
        // Verify that task overlay intent has the required flags
        final int expectedFlags =
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP;
        assertThat(intent.getFlags() & expectedFlags).isEqualTo(expectedFlags);

        final ActivityOptions options = new ActivityOptions(optionsCaptor.getValue());
        assertThat(options.getLaunchTaskId()).isEqualTo(task.mTaskId);
        assertThat(options.getTaskOverlay()).isTrue();
    }

    @Test
    public void addLockedByAppLockTaskOverlayLocked_removesTaskOnOverlayLaunchFailure() {
        // GIVEN a task and a failed activity start result
        final Task task = getTestTask();
        doReturn(ActivityManager.START_CANCELED).when(mAtm).startActivityAsUser(any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());
        spyOn(mSupervisor);

        // WHEN a task overlay is added
        mAppLockOverlayController.addLockedByAppLockTaskOverlayLocked(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        waitHandlerIdle(mAtm.mH);

        // THEN the task is removed as a fallback
        verify(mSupervisor).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockTaskOverlayLocked_withExistingOverlay_doesNothing() {
        // GIVEN a task with an existing overlay for the same package
        final Task task = getTestTask();
        createOverlayActivity(task, TEST_PACKAGE_1, TEST_USER_ID_1);
        spyOn(mSupervisor);

        // WHEN a task overlay is added
        mAppLockOverlayController.addLockedByAppLockTaskOverlayLocked(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        waitHandlerIdle(mAtm.mH);

        // THEN nothing happens because an overlay already exists
        verify(mAtm, never()).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), eq(TEST_USER_ID_1));
        verify(mSupervisor, never()).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_repositionsOverlayInSameParent() {
        // GIVEN a task with a target activity
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();
        new ActivityBuilder(mAtm).setTask(task).build();
        task.positionChildAt(POSITION_BOTTOM, targetActivity, false);

        // Mock startActivityAsUser to actually add the overlay activity to the task
        mockStartActivityToAddTask(task, TEST_PACKAGE_1, TEST_USER_ID_1, POSITION_TOP);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the overlay is positioned directly above the target activity
        verify(mAtm).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(), anyInt(),
                anyInt(), any(), any(), anyInt());
        final TaskFragment parent = targetActivity.getParent().asTaskFragment();
        assertThat(parent.mChildren.size()).isEqualTo(3);
        final int targetIndex = parent.mChildren.indexOf(targetActivity);
        final int overlayIndex = targetIndex + 1;
        final ActivityRecord overlay = parent.mChildren.get(overlayIndex).asActivityRecord();
        assertThat(overlay.mActivityComponent).isNotNull();
        assertThat(overlay.mActivityComponent.getClassName()).isEqualTo(
                LockedAppActivity.class.getName());
    }

    @Test
    public void
            addLockedByAppLockActivityOverlayLocked_repositionsBottomOverlayInDifferentParents() {
        // GIVEN a task with two TaskFragments, and the target activity in the bottom one.
        // The overlay is initially positioned at the bottom of the task.
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(false).build();
        final TaskFragment taskFragment = new TaskFragmentBuilder(mAtm).setParentTask(task).build();
        // Create the activity in the task, then move it to the fragment.
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm).setTask(task).build();
        targetActivity.reparent(taskFragment, POSITION_TOP);

        // Mock startActivityAsUser to add overlay to the Task at the bottom.
        mockStartActivityToAddTask(task, TEST_PACKAGE_1, TEST_USER_ID_1, POSITION_BOTTOM);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the overlay is positioned directly above the target's TaskFragment.
        final ActivityRecord overlay = task.getActivity(
                activity -> LockedAppActivity.class.getName().equals(
                        activity.mActivityComponent.getClassName()));
        final int overlayIndex = task.mChildren.indexOf(overlay);
        final int taskFragmentIndex = task.mChildren.indexOf(taskFragment);
        assertThat(overlayIndex).isEqualTo(taskFragmentIndex + 1);
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_repositionsTopOverlayInDifferentParents() {
        // GIVEN a task with two TaskFragments, and the target activity in the bottom one.
        // The overlay is initially positioned at the top of the task.
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(false).build();
        final TaskFragment taskFragment = new TaskFragmentBuilder(mAtm).setParentTask(task).build();
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm).setTask(task).build();
        targetActivity.reparent(taskFragment, POSITION_TOP);

        // Mock startActivityAsUser to add overlay to the Task at the top.
        mockStartActivityToAddTask(task, TEST_PACKAGE_1, TEST_USER_ID_1, POSITION_TOP);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the overlay is positioned directly above the target's TaskFragment.
        final ActivityRecord overlay = task.getActivity(
                activity -> LockedAppActivity.class.getName().equals(
                        activity.mActivityComponent.getClassName()));
        final int overlayIndex = task.mChildren.indexOf(overlay);
        final int taskFragmentIndex = task.mChildren.indexOf(taskFragment);
        assertThat(overlayIndex).isEqualTo(taskFragmentIndex + 1);
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_finishesTargetIfOverlayNotFound() {
        // GIVEN a task where the overlay activity cannot be found after launch
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();

        // Mock startActivityAsUser to success but DO NOT add activity
        doReturn(ActivityManager.START_SUCCESS).when(mAtm).startActivityAsUser(any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());

        spyOn(targetActivity);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the target activity is finished as a fallback
        verify(targetActivity).finishIfPossible(eq("could-not-find-overlay"), anyBoolean());
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_withNoTask_doesNothing() {
        // GIVEN an activity that is not attached to any task
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm).setTask(null).build();
        spyOn(targetActivity);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the method returns early and does not attempt to finish the activity
        verify(targetActivity, never()).finishIfPossible(anyString(), anyBoolean());
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_activityFinishing_doesNothing() {
        // GIVEN a target activity that is already finishing
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();
        targetActivity.finishing = true;
        spyOn(targetActivity);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the method returns early and does not attempt to finish the activity
        verify(targetActivity, never()).finishIfPossible(anyString(), anyBoolean());
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_removesTaskOnOverlayLaunchFailure() {
        // GIVEN a task with a target activity
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();

        // Mock startActivityAsUser to fail
        doReturn(ActivityManager.START_CANCELED).when(mAtm).startActivityAsUser(any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());
        spyOn(mSupervisor);

        // WHEN an activity overlay is added but fails to start
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the entire task is removed as a security fallback
        verify(mSupervisor).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_withExistingOverlay_doesNothing() {
        // GIVEN a task with a target activity and an existing overlay on top of it
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();
        final ActivityRecord existingOverlay = createOverlayActivity(task,
                targetActivity.packageName,
                targetActivity.mUserId);
        // Position existing overlay right on top of the target activity.
        task.positionChildAt(task.mChildren.indexOf(targetActivity) + 1, existingOverlay,
                /* includingParents= */ false);

        spyOn(targetActivity);
        spyOn(mSupervisor);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the method returns early and does not attempt to start a new activity or remove the
        // task
        verify(mAtm, never()).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), anyInt());
        verify(mSupervisor, never()).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_finishesTargetIfParentMissing() {
        // GIVEN a task with a target activity
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();
        // Add a dummy activity to keep the task alive when targetActivity is detached
        new ActivityBuilder(mAtm).setTask(task).build();

        // Mock startActivityAsUser to succeed, but detach target from parent before callback
        doAnswer(invocation -> {
            // Detach target from task to simulate race condition
            targetActivity.setParent(null);

            // Create the overlay so the lookup succeeds
            Intent intent = invocation.getArgument(3);
            String identifier = intent.getIdentifier();
            synchronized (mAtm.mGlobalLock) {
                ActivityRecord overlay = new ActivityBuilder(mAtm)
                        .setComponent(new ComponentName("android",
                                LockedAppActivity.class.getName()))
                        .setTask(task).build();
                overlay.intent.setIdentifier(identifier);
            }
            return ActivityManager.START_SUCCESS;
        }).when(mAtm).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(), anyInt(),
                anyInt(), any(), any(), anyInt());

        spyOn(targetActivity);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the target activity is finished because it has no parent to reposition in
        verify(targetActivity).finishIfPossible(eq("target-or-overlay-has-no-parent"),
                anyBoolean());
    }

    @Test
    public void addLockedByAppLockActivityOverlayLocked_finishesOverlayIfTargetFinishing() {
        // GIVEN a task with a target activity
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();

        // Capture the overlay identifier to verify it's finished later
        final StringBuilder overlayIdentifier = new StringBuilder();

        // Mock startActivityAsUser
        doAnswer(invocation -> {
            Intent intent = invocation.getArgument(3);
            overlayIdentifier.append(intent.getIdentifier());

            // Mark target as finishing *before* the overlay is created/processed
            targetActivity.finishing = true;

            synchronized (mAtm.mGlobalLock) {
                ActivityRecord overlay = new ActivityBuilder(mAtm)
                        .setComponent(new ComponentName("android",
                                LockedAppActivity.class.getName()))
                        .setTask(task).build();
                overlay.intent.setIdentifier(intent.getIdentifier());
                spyOn(overlay);
            }
            return ActivityManager.START_SUCCESS;
        }).when(mAtm).startActivityAsUser(any(), any(), any(), any(),
                any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlayLocked(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the overlay activity is finished
        final ActivityRecord overlay = task.getActivity(
                activity -> overlayIdentifier.toString().equals(activity.intent.getIdentifier()));
        verify(overlay).finishIfPossible(eq("target-finishing"), anyBoolean());
    }

    /** Helper to create a basic task for testing. */
    private Task getTestTask() {
        return new TaskBuilder(mSupervisor).setCreateActivity(true).build();
    }

    /** Helper to create a task associated with a locked package. */
    private Task createLockedTask(boolean isVisible) {
        final Task task = getTestTask();
        task.mUserId = TEST_USER_ID_1;
        task.realActivity = TEST_COMPONENT_1;
        if (!isVisible) {
            task.setVisibleRequested(false);
        }
        doReturn(true).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_1, TEST_USER_ID_1);
        clearInvocations(mAppLockOverlayController);
        return task;
    }

    /**
     * Helper to create a task whose owner package is not locked, but contains both a locked and an
     * unlocked activity.
     */
    private TestMixedStateTask createMixedStateTask(boolean isVisible) {
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(false).build();
        task.realActivity = TEST_COMPONENT_2; // Main package is not locked
        task.mUserId = TEST_USER_ID_1;

        final ActivityRecord unlockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_2)
                .setTask(task)
                .build();
        final ActivityRecord lockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(task)
                .build();

        doReturn(false).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_2, TEST_USER_ID_1);
        doReturn(true).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_1, TEST_USER_ID_1);

        if (!isVisible) {
            task.setVisibleRequested(false);
        }
        clearInvocations(mAppLockOverlayController);

        return new TestMixedStateTask(task, lockedActivity, unlockedActivity);
    }

    /** Helper to create a mock overlay activity and add it to a task. */
    private ActivityRecord createOverlayActivity(Task task, String packageName, int userId) {
        final Intent intent = LockedAppActivity.createLockedAppActivityIntent(packageName, userId,
                /* target= */ null);
        final ActivityRecord overlay = new ActivityBuilder(mAtm)
                .setComponent(intent.getComponent())
                .setTask(task)
                .setIntentExtras(intent.getExtras())
                .build();
        spyOn(overlay);
        doReturn(true).when(overlay).isTaskOverlay();
        return overlay;
    }

    /**
     * Mocks a call to
     * {@link ActivityTaskManagerService#startActivityAsUser(IApplicationThread, String, String,
     * Intent, String, IBinder, String, int, int, ProfilerInfo, Bundle, int)} to add a new
     * {@link LockedAppActivity} to the specified task and position within the task.
     */
    private void mockStartActivityToAddTask(Task task, String packageName, int userId,
            int position) {
        doAnswer(invocation -> {
            Intent intent = invocation.getArgument(3);
            String identifier = intent.getIdentifier();
            // Create overlay and ensure it has the correct identifier and component
            synchronized (mAtm.mGlobalLock) {
                final ActivityRecord overlay = createOverlayActivity(task, packageName, userId);
                overlay.intent.setIdentifier(identifier);
                if (position != POSITION_TOP) {
                    task.positionChildAt(position, overlay, /* includingParents= */ false);
                }
            }
            return ActivityManager.START_SUCCESS;
        }).when(mAtm).startActivityAsUser(any(), any(), any(), any(),
                any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());
    }

    /** Data class to hold the result of creating a mixed-lock-state task. */
    private static class TestMixedStateTask {
        final Task mTask;
        final ActivityRecord mLockedActivity;
        final ActivityRecord mUnlockedActivity;

        TestMixedStateTask(Task task, ActivityRecord lockedActivity,
                ActivityRecord unlockedActivity) {
            this.mTask = task;
            this.mLockedActivity = lockedActivity;
            this.mUnlockedActivity = unlockedActivity;
        }
    }
}
