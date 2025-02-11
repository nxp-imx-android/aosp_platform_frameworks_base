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
package com.android.wm.shell.desktopmode.multidesks

import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.Change
import android.window.WindowContainerTransaction.HierarchyOp
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.multidesks.RootTaskDesksOrganizer.DeskMinimizationRoot
import com.android.wm.shell.desktopmode.multidesks.RootTaskDesksOrganizer.DeskRoot
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock

/**
 * Tests for [RootTaskDesksOrganizer].
 *
 * Usage: atest WMShellUnitTests:RootTaskDesksOrganizerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class RootTaskDesksOrganizerTest : ShellTestCase() {

    private val testExecutor = TestShellExecutor()
    private val testShellInit = ShellInit(testExecutor)
    private val mockShellCommandHandler = mock<ShellCommandHandler>()
    private val mockShellTaskOrganizer = mock<ShellTaskOrganizer>()

    private lateinit var organizer: RootTaskDesksOrganizer

    @Before
    fun setUp() {
        organizer =
            RootTaskDesksOrganizer(testShellInit, mockShellCommandHandler, mockShellTaskOrganizer)
    }

    @Test
    fun testCreateDesk_callsBack() {
        val callback = FakeOnCreateCallback()
        organizer.createDesk(Display.DEFAULT_DISPLAY, callback)

        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())

        assertThat(callback.created).isTrue()
        assertEquals(freeformRoot.taskId, callback.deskId)
    }

    @Test
    fun testCreateDesk_createsMinimizationRoot() {
        val callback = FakeOnCreateCallback()
        organizer.createDesk(Display.DEFAULT_DISPLAY, callback)
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())

        val minimizationRootTask = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(minimizationRootTask, SurfaceControl())

        val minimizationRoot = organizer.deskMinimizationRootsByDeskId[freeformRoot.taskId]
        assertNotNull(minimizationRoot)
        assertThat(minimizationRoot.deskId).isEqualTo(freeformRoot.taskId)
        assertThat(minimizationRoot.rootId).isEqualTo(minimizationRootTask.taskId)
    }

    @Test
    fun testCreateMinimizationRoot_marksHidden() {
        organizer.createDesk(Display.DEFAULT_DISPLAY, FakeOnCreateCallback())
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())

        val minimizationRootTask = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(minimizationRootTask, SurfaceControl())

        verify(mockShellTaskOrganizer)
            .applyTransaction(
                argThat { wct ->
                    wct.changes.any { change ->
                        change.key == minimizationRootTask.token.asBinder() &&
                            (change.value.changeMask and Change.CHANGE_HIDDEN != 0) &&
                            change.value.hidden
                    }
                }
            )
    }

    @Test
    fun testOnTaskAppeared_withoutRequest_throws() {
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }

        assertThrows(Exception::class.java) {
            organizer.onTaskAppeared(freeformRoot, SurfaceControl())
        }
    }

    @Test
    fun testOnTaskAppeared_withRequestOnlyInAnotherDisplay_throws() {
        organizer.createDesk(displayId = 2, FakeOnCreateCallback())
        val freeformRoot = createFreeformTask(Display.DEFAULT_DISPLAY).apply { parentTaskId = -1 }

        assertThrows(Exception::class.java) {
            organizer.onTaskAppeared(freeformRoot, SurfaceControl())
        }
    }

    @Test
    fun testOnTaskAppeared_duplicateRoot_throws() {
        organizer.createDesk(Display.DEFAULT_DISPLAY, FakeOnCreateCallback())
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())

        assertThrows(Exception::class.java) {
            organizer.onTaskAppeared(freeformRoot, SurfaceControl())
        }
    }

    @Test
    fun testOnTaskAppeared_duplicateMinimizedRoot_throws() {
        organizer.createDesk(Display.DEFAULT_DISPLAY, FakeOnCreateCallback())
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        val minimizationRootTask = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())
        organizer.onTaskAppeared(minimizationRootTask, SurfaceControl())

        assertThrows(Exception::class.java) {
            organizer.onTaskAppeared(minimizationRootTask, SurfaceControl())
        }
    }

    @Test
    fun testOnTaskVanished_removesRoot() {
        val desk = createDesk()

        organizer.onTaskVanished(desk.deskRoot.taskInfo)

        assertThat(organizer.deskRootsByDeskId.contains(desk.deskRoot.deskId)).isFalse()
    }

    @Test
    fun testOnTaskVanished_removesMinimizedRoot() {
        val desk = createDesk()

        organizer.onTaskVanished(desk.deskRoot.taskInfo)
        organizer.onTaskVanished(desk.minimizationRoot.taskInfo)

        assertThat(organizer.deskMinimizationRootsByDeskId.contains(desk.deskRoot.deskId)).isFalse()
    }

    @Test
    fun testDesktopWindowAppearsInDesk() {
        val desk = createDesk()
        val child = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }

        organizer.onTaskAppeared(child, SurfaceControl())

        assertThat(desk.deskRoot.children).contains(child.taskId)
    }

    @Test
    fun testDesktopWindowAppearsInDeskMinimizationRoot() {
        val desk = createDesk()
        val child = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }

        organizer.onTaskAppeared(child, SurfaceControl())

        assertThat(desk.minimizationRoot.children).contains(child.taskId)
    }

    @Test
    fun testDesktopWindowMovesToMinimizationRoot() {
        val desk = createDesk()
        val child = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        organizer.onTaskAppeared(child, SurfaceControl())

        child.parentTaskId = desk.minimizationRoot.rootId
        organizer.onTaskInfoChanged(child)

        assertThat(desk.deskRoot.children).doesNotContain(child.taskId)
        assertThat(desk.minimizationRoot.children).contains(child.taskId)
    }

    @Test
    fun testDesktopWindowDisappearsFromDesk() {
        val desk = createDesk()
        val child = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }

        organizer.onTaskAppeared(child, SurfaceControl())
        organizer.onTaskVanished(child)

        assertThat(desk.deskRoot.children).doesNotContain(child.taskId)
    }

    @Test
    fun testDesktopWindowDisappearsFromDeskMinimizationRoot() {
        val desk = createDesk()
        val child = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }

        organizer.onTaskAppeared(child, SurfaceControl())
        organizer.onTaskVanished(child)

        assertThat(desk.minimizationRoot.children).doesNotContain(child.taskId)
    }

    @Test
    fun testRemoveDesk_removesDeskRoot() {
        val desk = createDesk()

        val wct = WindowContainerTransaction()
        organizer.removeDesk(wct, desk.deskRoot.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_ROOT_TASK &&
                        hop.container == desk.deskRoot.token.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun testRemoveDesk_removesMinimizationRoot() {
        val desk = createDesk()

        val wct = WindowContainerTransaction()
        organizer.removeDesk(wct, desk.deskRoot.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_ROOT_TASK &&
                        hop.container == desk.minimizationRoot.token.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun testActivateDesk() {
        val desk = createDesk()

        val wct = WindowContainerTransaction()
        organizer.activateDesk(wct, desk.deskRoot.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REORDER &&
                        hop.toTop &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isTrue()
        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun testActivateDesk_didNotExist_throws() {
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }

        val wct = WindowContainerTransaction()
        assertThrows(Exception::class.java) { organizer.activateDesk(wct, freeformRoot.taskId) }
    }

    @Test
    fun testMoveTaskToDesk() {
        val desk = createDesk()

        val desktopTask = createFreeformTask().apply { parentTaskId = -1 }
        val wct = WindowContainerTransaction()
        organizer.moveTaskToDesk(wct, desk.deskRoot.deskId, desktopTask)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.isReparent &&
                        hop.toTop &&
                        hop.container == desktopTask.token.asBinder() &&
                        hop.newParent == desk.deskRoot.taskInfo.token.asBinder()
                }
            )
            .isTrue()
        assertThat(
                wct.changes.any { change ->
                    change.key == desktopTask.token.asBinder() &&
                        change.value.windowingMode == WINDOWING_MODE_UNDEFINED
                }
            )
            .isTrue()
    }

    @Test
    fun testMoveTaskToDesk_didNotExist_throws() {
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }

        val desktopTask = createFreeformTask().apply { parentTaskId = -1 }
        val wct = WindowContainerTransaction()
        assertThrows(Exception::class.java) {
            organizer.moveTaskToDesk(wct, freeformRoot.taskId, desktopTask)
        }
    }

    @Test
    fun testGetDeskAtEnd() {
        val desk = createDesk()

        val task = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val endDesk =
            organizer.getDeskAtEnd(
                TransitionInfo.Change(task.token, SurfaceControl()).apply { taskInfo = task }
            )

        assertThat(endDesk).isEqualTo(desk.deskRoot.deskId)
    }

    @Test
    fun testGetDeskAtEnd_inMinimizationRoot() {
        val desk = createDesk()

        val task = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }
        val endDesk =
            organizer.getDeskAtEnd(
                TransitionInfo.Change(task.token, SurfaceControl()).apply { taskInfo = task }
            )

        assertThat(endDesk).isEqualTo(desk.deskRoot.deskId)
    }

    @Test
    fun testIsDeskActiveAtEnd() {
        organizer.createDesk(Display.DEFAULT_DISPLAY, FakeOnCreateCallback())
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        freeformRoot.isVisibleRequested = true
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())

        val isActive =
            organizer.isDeskActiveAtEnd(
                change =
                    TransitionInfo.Change(freeformRoot.token, SurfaceControl()).apply {
                        taskInfo = freeformRoot
                        mode = TRANSIT_TO_FRONT
                    },
                deskId = freeformRoot.taskId,
            )

        assertThat(isActive).isTrue()
    }

    @Test
    fun deactivateDesk_clearsLaunchRoot() {
        val wct = WindowContainerTransaction()
        val desk = createDesk()
        organizer.activateDesk(wct, desk.deskRoot.deskId)

        organizer.deactivateDesk(wct, desk.deskRoot.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT &&
                        hop.container == desk.deskRoot.taskInfo.token.asBinder() &&
                        hop.windowingModes == null &&
                        hop.activityTypes == null
                }
            )
            .isTrue()
    }

    @Test
    fun isDeskChange_forDeskId() {
        val desk = createDesk()

        assertThat(
                organizer.isDeskChange(
                    TransitionInfo.Change(desk.deskRoot.taskInfo.token, desk.deskRoot.leash).apply {
                        taskInfo = desk.deskRoot.taskInfo
                    },
                    desk.deskRoot.deskId,
                )
            )
            .isTrue()
    }

    @Test
    fun isDeskChange_forDeskId_inMinimizationRoot() {
        val desk = createDesk()

        assertThat(
                organizer.isDeskChange(
                    change =
                        TransitionInfo.Change(
                                desk.minimizationRoot.token,
                                desk.minimizationRoot.leash,
                            )
                            .apply { taskInfo = desk.minimizationRoot.taskInfo },
                    deskId = desk.deskRoot.deskId,
                )
            )
            .isTrue()
    }

    @Test
    fun isDeskChange_anyDesk() {
        val desk = createDesk()

        assertThat(
                organizer.isDeskChange(
                    change =
                        TransitionInfo.Change(desk.deskRoot.taskInfo.token, desk.deskRoot.leash)
                            .apply { taskInfo = desk.deskRoot.taskInfo }
                )
            )
            .isTrue()
    }

    @Test
    fun isDeskChange_anyDesk_inMinimizationRoot() {
        val desk = createDesk()

        assertThat(
                organizer.isDeskChange(
                    change =
                        TransitionInfo.Change(
                                desk.minimizationRoot.taskInfo.token,
                                desk.minimizationRoot.leash,
                            )
                            .apply { taskInfo = desk.minimizationRoot.taskInfo }
                )
            )
            .isTrue()
    }

    @Test
    fun minimizeTask() {
        val desk = createDesk()
        val task = createFreeformTask().apply { parentTaskId = desk.deskRoot.deskId }
        val wct = WindowContainerTransaction()
        organizer.moveTaskToDesk(wct, desk.deskRoot.deskId, task)
        organizer.onTaskAppeared(task, SurfaceControl())

        organizer.minimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.isReparent &&
                        hop.container == task.token.asBinder() &&
                        hop.newParent == desk.minimizationRoot.token.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun minimizeTask_alreadyMinimized_noOp() {
        val desk = createDesk()
        val task = createFreeformTask().apply { parentTaskId = desk.minimizationRoot.rootId }
        val wct = WindowContainerTransaction()
        organizer.onTaskAppeared(task, SurfaceControl())

        organizer.minimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(wct.isEmpty).isTrue()
    }

    @Test
    fun minimizeTask_inDifferentDesk_noOp() {
        val desk = createDesk()
        val otherDesk = createDesk()
        val task = createFreeformTask().apply { parentTaskId = otherDesk.deskRoot.deskId }
        val wct = WindowContainerTransaction()
        organizer.onTaskAppeared(task, SurfaceControl())

        organizer.minimizeTask(wct, deskId = desk.deskRoot.deskId, task)

        assertThat(wct.isEmpty).isTrue()
    }

    private data class DeskRoots(
        val deskRoot: DeskRoot,
        val minimizationRoot: DeskMinimizationRoot,
    )

    private fun createDesk(): DeskRoots {
        organizer.createDesk(Display.DEFAULT_DISPLAY, FakeOnCreateCallback())
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())
        val minimizationRoot = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(minimizationRoot, SurfaceControl())
        return DeskRoots(
            organizer.deskRootsByDeskId[freeformRoot.taskId],
            checkNotNull(organizer.deskMinimizationRootsByDeskId[freeformRoot.taskId]),
        )
    }

    private class FakeOnCreateCallback : DesksOrganizer.OnCreateCallback {
        var deskId: Int? = null
        val created: Boolean
            get() = deskId != null

        override fun onCreated(deskId: Int) {
            this.deskId = deskId
        }
    }
}
