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
package com.android.wm.shell.hierarchy.updates

import android.app.ActivityManager
import android.content.ComponentName
import android.graphics.Rect
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.ActivityTransitionInfo
import android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
import android.window.TaskAppearedInfo
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_IS_DISPLAY
import android.window.TransitionInfo.FLAG_IS_WALLPAPER
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.containers.StubContainer
import com.android.wm.shell.hierarchy.modes.FormFactorModes
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.modes.StubMode
import com.android.wm.shell.hierarchy.properties.ActivityContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.properties.WallpaperContainerProperties
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy

/**
 * Tests for [HierarchyUpdater].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ContainerHierarchyUpdaterTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class HierarchyUpdaterTest : ShellTestCase() {

    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val transitions = mock<Transitions>()
    private val formFactorModes = mock<FormFactorModes>()
    private val shellInit = mock<ShellInit>()
    private val hierarchy = ContainerHierarchy().apply {
        // Create a hierarchy with two nested container
        val info1 = ActivityManager.RunningTaskInfo().apply {
            taskId = 1
        }
        val child1 =
            Container(WindowContainerToken.createProxy("test"), TaskContainerProperties(info1))
        child1.parent = root
        child1.mode = spy(StubMode())
        val info2 = ActivityManager.RunningTaskInfo().apply {
            taskId = 2
        }
        val child2 =
            Container(WindowContainerToken.createProxy("test"), TaskContainerProperties(info2))
        child2.parent = child1
        child2.mode = spy(StubMode())
    }
    private val updater =
        HierarchyUpdater(
            shellTaskOrganizer,
            transitions,
            hierarchy,
            formFactorModes,
            shellInit
        )

    @Test
    fun testOpenTransition() {
        // Create an open transition
        val wct = WindowContainerToken.createProxy("test")
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            flags = FLAG_IS_DISPLAY
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Ensure that a container was added
        assertThat(hierarchy.getContainer(wct)).isNotNull()
    }

    @Test
    fun testCloseTransition() {
        // Add existing container in hierarchy
        val wct = WindowContainerToken.createProxy("test")
        val container = StubContainer(wct)
        container.parent = hierarchy.root

        // Create a close transition
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_CLOSE
        }
        val info = TransitionInfo(TRANSIT_CLOSE, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Ensure that a container was removed
        assertThat(hierarchy.getContainer(wct)).isNull()
        assertThat(container.parent).isNull()
    }

    @Test
    fun testToFrontTransition() {
        // Add two containers in hierarchy in order (1, 2)
        val token1 = WindowContainerToken.createProxy("test")
        val container1 = StubContainer(token1)
        val token2 = WindowContainerToken.createProxy("test")
        val container2 = StubContainer(token2)
        container1.parent = hierarchy.root
        container2.parent = hierarchy.root

        // Create a to-front transition
        val change = TransitionInfo.Change(token1, mock<SurfaceControl>()).apply {
            mode = TRANSIT_TO_FRONT
            flags = FLAG_IS_DISPLAY
        }
        val info = TransitionInfo(TRANSIT_TO_FRONT, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Verify 1 moved to top
        assertThat(hierarchy.root.children.indexOf(container1)).isGreaterThan(
            hierarchy.root.children.indexOf(
                container2
            )
        )
    }

    @Test
    fun testToBackTransition() {
        // Add two containers in hierarchy in order (1, 2)
        val token1 = WindowContainerToken.createProxy("test")
        val container1 = StubContainer(token1)
        val token2 = WindowContainerToken.createProxy("test")
        val container2 = StubContainer(token2)
        container1.parent = hierarchy.root
        container2.parent = hierarchy.root

        // Create a to-back transition
        val change = TransitionInfo.Change(token2, mock<SurfaceControl>()).apply {
            mode = TRANSIT_TO_BACK
            flags = FLAG_IS_DISPLAY
        }
        val info = TransitionInfo(TRANSIT_TO_BACK, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Verify 2 moved to back
        assertThat(hierarchy.root.children.indexOf(container2)).isLessThan(
            hierarchy.root.children.indexOf(
                container1
            )
        )
    }

    @Test
    fun testNotifyAncestorModesInOrder_onAddContainer() {
        // Create an open transition
        val wct = WindowContainerToken.createProxy("test")
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            taskInfo = ActivityManager.RunningTaskInfo().apply {
                // Parented to the nested container
                parentTaskId = 2
            }
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Verify the container was attached to the associated ancestor modes in order
        val newChild = hierarchy.getContainer(wct)!!
        val child1Mode = hierarchy.root.children[0].mode!!
        val child2Mode = hierarchy.root.children[0].children[0].mode!!
        val inOrder = inOrder(child1Mode, child2Mode)
        inOrder.verify(child1Mode).attachToContainer(any(), eq(newChild), eq(false))
        inOrder.verify(child2Mode).attachToContainer(any(), eq(newChild), eq(false))
    }

    @Test
    fun testNotifyAncestorModesInOrder_onRemoveContainer() {
        // Create an close transition for a container that has some modes already applied
        val child1 = hierarchy.root.children[0]
        val child2 = hierarchy.root.children[0].children[0]
        val wct = WindowContainerToken.createProxy("test")
        val container = StubContainer(wct)
        container.parent = child2
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_CLOSE
        }
        val info = TransitionInfo(TRANSIT_CLOSE, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Verify the container was detached from the associated ancestor modes in order
        val child1Mode = child1.mode!!
        val child2Mode = child2.mode!!
        val inOrder = inOrder(child2Mode, child1Mode)
        inOrder.verify(child2Mode).detachFromContainer(any(), eq(container))
        inOrder.verify(child1Mode).detachFromContainer(any(), eq(container))
    }

    @Test
    fun testHandleCreateRootTask() {
        // Create a display container
        val display =
            Container(
                WindowContainerToken.createProxy("test"),
                DisplayContainerProperties(DEFAULT_DISPLAY)
            )
        display.parent = hierarchy.root
        display.leash = mock<SurfaceControl>()
        val displayArea =
            Container(
                WindowContainerToken.createProxy("test"),
                DisplayAreaContainerProperties(FEATURE_DEFAULT_TASK_CONTAINER)
            )
        displayArea.parent = display
        displayArea.leash = mock<SurfaceControl>()

        // Create a new root task
        val wct = WindowContainerToken.createProxy("test")
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            token = wct
        }
        val appearedInfo = TaskAppearedInfo(taskInfo, mock<SurfaceControl>())
        updater.handleCreateRootTask(appearedInfo, "test")

        // Ensure that a task container was added
        val container = hierarchy.getContainer(wct)
        assertThat(container).isNotNull()
        assertThat(container!!.props).isInstanceOf(TaskContainerProperties::class.java)
        assertThat(container.parent).isEqualTo(displayArea)
    }

    @Test
    fun testHandleRemoveRootTask() {
        // Add existing container in hierarchy
        val wct = WindowContainerToken.createProxy("test")
        val container = StubContainer(wct)
        container.parent = hierarchy.root

        updater.handleRemoveRootTask(wct)

        // Ensure that a container was removed
        assertThat(hierarchy.getContainer(wct)).isNull()
        assertThat(container.parent).isNull()
    }

    @Test
    fun testCreateTransientContainersInHierarchy() {
        // Create a display container
        val display =
            Container(
                WindowContainerToken.createProxy("test"),
                DisplayContainerProperties(DEFAULT_DISPLAY)
            )
        display.parent = hierarchy.root
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            taskId = 1234
        }
        val task =
            Container(
                WindowContainerToken.createProxy("test"),
                TaskContainerProperties(taskInfo),
            )
        task.parent = display

        // Create a transition with a wallpaper and activity change
        val displayId = display.props<DisplayContainerProperties>().displayId
        val wallpaperToken = WindowContainerToken.createProxy("test")
        val wallpaperChange = TransitionInfo.Change(wallpaperToken, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            flags = FLAG_IS_WALLPAPER
            setDisplayId(displayId, displayId)
        }
        val activityToken = WindowContainerToken.createProxy("test")
        val activityChange = TransitionInfo.Change(activityToken, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            activityTransitionInfo =
                ActivityTransitionInfo(
                    mock<ComponentName>(),
                    task.props<TaskContainerProperties>().taskId
                )
            parent = task.token
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(wallpaperChange)
            addChange(activityChange)
        }

        var hookCalled = false
        updater.updaterTestHook = object : HierarchyUpdater.UpdaterTestHook {
            override fun onHierarchyUpdated() {
                hookCalled = true
                // Verify transient containers exist
                val wallpaper = hierarchy.getContainer(wallpaperToken)
                assertThat(wallpaper).isNotNull()
                assertThat(wallpaper!!.props).isInstanceOf(WallpaperContainerProperties::class.java)
                val activity = hierarchy.getContainer(activityToken)
                assertThat(activity).isNotNull()
                assertThat(activity!!.props).isInstanceOf(ActivityContainerProperties::class.java)
            }
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Verify the hook above was called and the transitions ran
        assertThat(hookCalled).isTrue()

        // Verify transient containers are removed
        assertThat(hierarchy.getContainer(wallpaperToken)).isNull()
        assertThat(hierarchy.getContainer(activityToken)).isNull()
    }

    @Test
    fun testUpdateFocusedTask() {
        // Create a transition that updates focus
        val taskId = 10
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.isFocused = true
            this.displayId = DEFAULT_DISPLAY
        }
        val wct = WindowContainerToken.createProxy("test")
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            this.taskInfo = taskInfo
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Verify the root container's focus state is updated
        val rootProps = hierarchy.root.props<RootContainerProperties>()
        assertThat(rootProps.focusState.globallyFocusedTaskId).isEqualTo(taskId)
    }

    @Test
    fun testUpdateMode_onAncestorContainerChange() {
        // Create containers
        val wct1 = WindowContainerToken.createProxy("test")
        val child1 = StubContainer(wct1)
        child1.parent = hierarchy.root

        val wct2 = WindowContainerToken.createProxy("test")
        val child2 = StubContainer(wct2)
        val mode2 = StubMode()
        child2.parent = child1
        child2.mode = mode2

        // Create a transition that updates focus (by starting a new task)
        val taskId = 10
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.isFocused = true
            this.displayId = DEFAULT_DISPLAY
        }
        val wct = WindowContainerToken.createProxy("test")
        val change = TransitionInfo.Change(wct, mock<SurfaceControl>()).apply {
            mode = TRANSIT_OPEN
            this.taskInfo = taskInfo
        }
        val info = TransitionInfo(TRANSIT_OPEN, 0).apply {
            addChange(change)
        }

        // Notify the transition
        updater.handleTransition(
            mock<IBinder>(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>()
        )

        // Verify that child2 mode is updated (because ancestor changed)
        assertThat(mode2.updates).isNotEmpty()
    }

    @Test
    fun testUpdateMode_onModeContainerChange() {
        // Create containers
        val wct1 = WindowContainerToken.createProxy("test")
        val child1 = StubContainer(wct1)
        val mode1 = StubMode()
        child1.parent = hierarchy.root
        child1.mode = mode1

        // Trigger a change on child1
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        child1.setBounds(Rect(0, 0, 50, 50))
        updater.notifyModes(Mode.UpdateContext(), snapshot)

        // Verify that child1 mode is updated (because an ancestor changed)
        assertThat(mode1.updates).isNotEmpty()
    }

    @Test
    fun testUpdateMode_onDescendantContainerChange() {
        // Create containers
        val wct1 = WindowContainerToken.createProxy("test")
        val child1 = StubContainer(wct1)
        val mode1 = StubMode()
        child1.parent = hierarchy.root
        child1.mode = mode1

        val wct2 = WindowContainerToken.createProxy("test")
        val child2 = StubContainer(wct2)
        child2.parent = child1

        // Trigger a change on child2
        val snapshot = HierarchySnapshot(hierarchy.toContainerList())
        child2.setBounds(Rect(0, 0, 50, 50))
        updater.notifyModes(Mode.UpdateContext(), snapshot)

        // Verify that child1 mode is updated (because a descendant changed)
        assertThat(mode1.updates).isNotEmpty()
    }

    @Test
    fun testUpdateMode_manipulateHierarchyOnAttach() {
        // Create containers
        val wct1 = WindowContainerToken.createProxy("test")
        val child1 = StubContainer(wct1)
        val child2 = StubContainer(WindowContainerToken.createProxy("test2"))
        val mode1 = object : StubMode() {
            override fun attachToContainer(
                updateContext: Mode.UpdateContext,
                container: Container,
                isTopAncestor: Boolean
            ) {
                super.attachToContainer(updateContext, container, isTopAncestor)
                // If we are handling the "root" of the mode, then add a child
                if (isTopAncestor) {
                    child2.parent = container
                }
            }
        }

        // Trigger a change
        val snapshot = HierarchySnapshot(listOf(child1, child2))
        child1.parent = hierarchy.root
        child1.mode = mode1
        updater.notifyModes(Mode.UpdateContext(), snapshot)

        // Verify that the mode is notified of child2 even though it was added mid-update
        assertThat(mode1.attachedRoots).contains(child1)
        assertThat(mode1.attachedContainers).contains(child2)
    }
}