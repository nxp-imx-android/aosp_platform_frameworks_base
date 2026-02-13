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
package com.android.wm.shell.hierarchy.transitions

import android.app.ActivityManager
import android.platform.test.annotations.EnableFlags
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.modes.StubMode
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.hierarchy.updates.HierarchyUpdater
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.AnimationPlan
import com.android.wm.shell.transition.Transitions
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

/**
 * Tests for [HierarchyTransitionPlanner].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:HierarchyTransitionPlannerTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class HierarchyTransitionPlannerTest : ShellTestCase() {

    private val transitions = mock<Transitions>()
    private val updater = mock<HierarchyUpdater>()
    private val shellInit = mock<ShellInit>()

    private val child1 =
        Container(
            WindowContainerToken.createProxy("child1"),
            TaskContainerProperties(ActivityManager.RunningTaskInfo().apply {
                taskId = 1
            })
        )
    private val child2 =
        Container(
            WindowContainerToken.createProxy("child2"),
            TaskContainerProperties(ActivityManager.RunningTaskInfo().apply {
                taskId = 2
            })
        )
    private val hierarchy = ContainerHierarchy().apply {
        // Create a hierarchy with two nested containers
        root.mode = spy(StubMode("root"))
        child1.parent = root
        child1.mode = spy(StubMode("child1"))
        child2.parent = child1
        child2.mode = spy(StubMode("child2"))
    }

    private val planner = HierarchyTransitionPlanner(transitions, hierarchy, updater, shellInit)
    private val startTx = mock<SurfaceControl.Transaction>()
    private val leash = mock<SurfaceControl>()

    @Test
    fun testDetach_notifyModes() {
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        val plan = mock<AnimationPlan>()

        // Remove child2 from child1 in the hierarchy
        val info = TransitionInfo(TRANSIT_CLOSE, 0)
        info.addChange(Change(child1.token, leash).apply {
            mode = TRANSIT_CHANGE
        })
        info.addChange(Change(child2.token, leash).apply {
            mode = TRANSIT_CLOSE
        })
        child2.parent = null

        val child1Mode = child1.mode!! as StubMode
        val child2Mode = child2.mode!! as StubMode
        val rootMode = hierarchy.root.mode!! as StubMode

        planner.handleMixpatcherTransition(snapshot, plan, info, info, startTx)

        // Verify that we called the mode for associated containers to detach the container
        verify(child2Mode).prepareForAnimation(listOf(child2))
        verify(child1Mode).prepareForAnimation(listOf(child1))
        verify(rootMode, never()).prepareForAnimation(any())
    }

    @Test
    fun testCreate_notifyModes() {
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        val plan = mock<AnimationPlan>()

        // Remove child2 from child1 in the hierarchy
        val info = TransitionInfo(TRANSIT_CLOSE, 0)
        info.addChange(Change(child1.token, leash).apply {
            mode = TRANSIT_CHANGE
        })
        info.addChange(Change(child2.token, leash).apply {
            mode = TRANSIT_CLOSE
        })
        child2.parent = null

        val child1Mode = child1.mode!! as StubMode
        val child2Mode = child2.mode!! as StubMode
        val rootMode = hierarchy.root.mode!! as StubMode

        planner.handleMixpatcherTransition(snapshot, plan, info, info, startTx)

        // Verify that we called the mode for associated containers to create the animation
        verify(child2Mode).createAnimation(any(), eq(listOf(child2)), any())
        verify(child1Mode).createAnimation(any(), eq(listOf(child1)), any())
        verify(rootMode, never()).createAnimation(any(), any(), any())
    }
}