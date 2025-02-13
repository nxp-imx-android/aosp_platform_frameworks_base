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
import android.window.WindowContainerTransaction.HierarchyOp
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.multidesks.RootTaskDesksOrganizer.DeskRoot
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
    fun testOnTaskVanished_removesRoot() {
        val desk = createDesk()

        organizer.onTaskVanished(desk.taskInfo)

        assertThat(organizer.roots.contains(desk.deskId)).isFalse()
    }

    @Test
    fun testDesktopWindowAppearsInDesk() {
        val desk = createDesk()
        val child = createFreeformTask().apply { parentTaskId = desk.deskId }

        organizer.onTaskAppeared(child, SurfaceControl())

        assertThat(desk.children).contains(child.taskId)
    }

    @Test
    fun testDesktopWindowDisappearsFromDesk() {
        val desk = createDesk()
        val child = createFreeformTask().apply { parentTaskId = desk.deskId }

        organizer.onTaskAppeared(child, SurfaceControl())
        organizer.onTaskVanished(child)

        assertThat(desk.children).doesNotContain(child.taskId)
    }

    @Test
    fun testRemoveDesk() {
        val desk = createDesk()

        val wct = WindowContainerTransaction()
        organizer.removeDesk(wct, desk.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_ROOT_TASK &&
                        hop.container == desk.taskInfo.token.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun testRemoveDesk_didNotExist_throws() {
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }

        val wct = WindowContainerTransaction()
        assertThrows(Exception::class.java) { organizer.removeDesk(wct, freeformRoot.taskId) }
    }

    @Test
    fun testActivateDesk() {
        val desk = createDesk()

        val wct = WindowContainerTransaction()
        organizer.activateDesk(wct, desk.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_REORDER &&
                        hop.toTop &&
                        hop.container == desk.taskInfo.token.asBinder()
                }
            )
            .isTrue()
        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT &&
                        hop.container == desk.taskInfo.token.asBinder()
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
        organizer.moveTaskToDesk(wct, desk.deskId, desktopTask)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.isReparent &&
                        hop.toTop &&
                        hop.container == desktopTask.token.asBinder() &&
                        hop.newParent == desk.taskInfo.token.asBinder()
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

        val task = createFreeformTask().apply { parentTaskId = desk.deskId }
        val endDesk =
            organizer.getDeskAtEnd(
                TransitionInfo.Change(task.token, SurfaceControl()).apply { taskInfo = task }
            )

        assertThat(endDesk).isEqualTo(desk.deskId)
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
        organizer.activateDesk(wct, desk.deskId)

        organizer.deactivateDesk(wct, desk.deskId)

        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.type == HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT &&
                        hop.container == desk.taskInfo.token.asBinder() &&
                        hop.windowingModes == null &&
                        hop.activityTypes == null
                }
            )
            .isTrue()
    }

    @Test
    fun isDeskChange() {
        val desk = createDesk()

        assertThat(
                organizer.isDeskChange(
                    TransitionInfo.Change(desk.taskInfo.token, desk.leash).apply {
                        taskInfo = desk.taskInfo
                    },
                    desk.deskId,
                )
            )
            .isTrue()
    }

    private fun createDesk(): DeskRoot {
        organizer.createDesk(Display.DEFAULT_DISPLAY, FakeOnCreateCallback())
        val freeformRoot = createFreeformTask().apply { parentTaskId = -1 }
        organizer.onTaskAppeared(freeformRoot, SurfaceControl())
        return organizer.roots[freeformRoot.taskId]
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
