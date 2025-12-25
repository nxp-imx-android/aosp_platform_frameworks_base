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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Rect;
import android.os.Binder;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test class for {@link WindowContainerVisibilityHelperImpl}.
 *
 * Build/Install/Run:
 * atest WmTests:WindowContainerVisibilityHelperTest
 */
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class WindowContainerVisibilityHelperTest extends WindowTestsBase {

    private WindowContainerVisibilityHelper mHelper;

    @Before
    public void setUp() {
        mHelper = mAtm.mVisibilityHelper;
    }

    @Test
    public void testOpaque_leafTask_occludingActivity_isOpaque() {
        final ActivityRecord activity = new ActivityBuilder(mAtm).setCreateTask(true).build();
        activity.setOccludesParent(true);
        final TaskFragment tf = activity.getTaskFragment();

        assertThat(mHelper.isOpaque(tf)).isTrue();
    }

    @Test
    public void testOpaque_leafTask_nonOccludingActivity_isTranslucent() {
        final ActivityRecord activity = new ActivityBuilder(mAtm).setCreateTask(true).build();
        activity.setOccludesParent(false);
        final TaskFragment tf = activity.getTaskFragment();

        assertThat(mHelper.isOpaque(tf)).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void testOpaque_rootTask_translucentFillingChild_isTranslucent() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_FREEFORM, /* opaque */ false, /* filling */ true);

        assertThat(mHelper.isOpaque(rootTask)).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void testOpaque_rootTask_opaqueAndNotFillingChild_isTranslucent() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_FREEFORM, /* opaque */ true, /* filling */ false);

        assertThat(mHelper.isOpaque(rootTask)).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void testOpaque_rootTask_opaqueAndFillingChild_isOpaque() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_FREEFORM, /* opaque */ true, /* filling */ true);

        assertThat(mHelper.isOpaque(rootTask)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void testOpaque_rootTask_nonFillingOpaqueAdjacentChildren_isOpaque() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        final TaskFragment tf1 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        final TaskFragment tf2 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        tf1.setAdjacentTaskFragments(new TaskFragment.AdjacentSet(tf1, tf2));

        assertThat(mHelper.isOpaque(rootTask)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void testOpaque_rootTask_nonFillingOpaqueAdjacentChildren_multipleAdjacent_isOpaque() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        final TaskFragment tf1 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        final TaskFragment tf2 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        final TaskFragment tf3 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        tf1.setAdjacentTaskFragments(new TaskFragment.AdjacentSet(tf1, tf2, tf3));

        assertThat(mHelper.isOpaque(rootTask)).isTrue();
    }

    @Test
    public void testOpaque_nonLeafTaskFragmentWithDirectActivity_opaque() {
        final ActivityRecord directChildActivity = new ActivityBuilder(mAtm).setCreateTask(true)
                .build();
        directChildActivity.setOccludesParent(true);
        final Task nonLeafTask = directChildActivity.getTask();
        final TaskFragment directChildFragment = new TaskFragment(mAtm, new Binder(),
                true /* createdByOrganizer */, false /* isEmbedded */);
        nonLeafTask.addChild(directChildFragment, 0);

        assertThat(mHelper.isOpaque(nonLeafTask)).isTrue();
    }

    @Test
    public void testOpaque_nonLeafTaskFragmentWithDirectActivity_transparent() {
        final ActivityRecord directChildActivity = new ActivityBuilder(mAtm).setCreateTask(true)
                .build();
        directChildActivity.setOccludesParent(false);
        final Task nonLeafTask = directChildActivity.getTask();
        final TaskFragment directChildFragment = new TaskFragment(mAtm, new Binder(),
                true /* createdByOrganizer */, false /* isEmbedded */);
        nonLeafTask.addChild(directChildFragment, 0);

        assertThat(mHelper.isOpaque(nonLeafTask)).isFalse();
    }

    @Test
    public void testOpaque_leafTaskUpdated() {
        final Task rootTask = new TaskBuilder(mSupervisor).setCreatedByOrganizer(true).build();
        final TaskFragment opaqueTask = createLeafTaskWithActivity(rootTask,
                WINDOWING_MODE_FREEFORM, /* opaque */ true, /* filling */ true);
        final Task childTask = new TaskBuilder(mSupervisor).setParentTask(rootTask).build();
        final ActivityRecord directChildActivity = new ActivityBuilder(mAtm).setTask(childTask)
                .build();

        directChildActivity.setOccludesParent(false);

        assertThat(mHelper.isOpaque(childTask)).isFalse();
        assertThat(mHelper.isOpaque(opaqueTask)).isTrue();

        directChildActivity.setOccludesParent(true);

        assertThat(mHelper.isOpaque(childTask)).isTrue();
    }

    @Test
    public void testOpaque_forceOpaque() {
        final Task forceOpaqueRootTask = new TaskBuilder(mSupervisor)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .setCreatedByOrganizer(true)
                .setForceOpaque(true)
                .build();
        final Rect leftBounds = new Rect();
        final Rect rightBounds = new Rect();
        forceOpaqueRootTask.getBounds().splitVertically(leftBounds, rightBounds);
        final Task adjacentTask = new TaskBuilder(mSupervisor)
                .setParentTask(forceOpaqueRootTask)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setCreateActivity(true)
                .build();
        final Task adjacentEmptyTask = new TaskBuilder(mSupervisor)
                .setParentTask(forceOpaqueRootTask)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setCreateActivity(false)
                .build();
        adjacentTask.setBounds(leftBounds);
        adjacentEmptyTask.setBounds(rightBounds);
        adjacentTask.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentTask, adjacentEmptyTask)
        );

        assertThat(mHelper.isOpaque(forceOpaqueRootTask)).isTrue();

        final Task forceOpaqueTaskWithTranslucentActivity = new TaskBuilder(mSupervisor)
                .setCreatedByOrganizer(true)
                .setForceOpaque(true)
                .setCreateActivity(true)
                .build();
        final ActivityRecord activity = forceOpaqueTaskWithTranslucentActivity.getTopMostActivity();
        activity.setOccludesParent(false);

        assertThat(mHelper.isOpaque(forceOpaqueTaskWithTranslucentActivity)).isTrue();
    }
}
