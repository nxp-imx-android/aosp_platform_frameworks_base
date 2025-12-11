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

package com.android.wm.shell.packageupdate

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_OPEN
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_CONTINUE_PACKAGE_UPDATE
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_PENDING_INTENT
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito.verify
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [PackageUpdateController]
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
class PackageUpdateControllerTest : ShellTestCase() {
    private val transitions = mock<Transitions>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val shellInit = mock<ShellInit>()
    private val userProfileContexts = mock<UserProfileContexts>()
    private lateinit var packageUpdateController: PackageUpdateController

    @Before
    fun setUp() {
        packageUpdateController = PackageUpdateController(
            transitions,
            shellTaskOrganizer,
            shellInit,
            userProfileContexts
        )

        whenever(userProfileContexts[anyInt()]).thenReturn(context)
        whenever(userProfileContexts.getOrCreate(anyInt())).thenReturn(context)
    }

    @Test
    fun onPackageUpdateRequested_visibleTask_launchesPlaceholder() {
        val task = createTaskInfo(1)

        packageUpdateController.onPackageUpdateRequested(listOf(task))

        val wct = getLatestWct(type = TRANSIT_OPEN)
        assertThat(wct.hierarchyOps.map { it.type }).containsExactly(
            HIERARCHY_OP_TYPE_PENDING_INTENT,
            HIERARCHY_OP_TYPE_CONTINUE_PACKAGE_UPDATE
        )
        wct.assertPendingIntent(createPuActivityIntent())
        wct.assertContinuePackageUpdate(task)
    }

    @Test
    fun onPackageUpdateRequested_invisibleTask_doesNotLaunchPlaceholder() {
        val task = createTaskInfo(id = 1, visible = false)
        packageUpdateController.onPackageUpdateRequested(listOf(task))

        val wct = getLatestWct(type = TRANSIT_OPEN)
        assertThat(wct.hierarchyOps.map { it.type }).containsExactly(
            HIERARCHY_OP_TYPE_CONTINUE_PACKAGE_UPDATE
        )
        wct.assertContinuePackageUpdate(task)
    }

    private fun createTaskInfo(
        id: Int,
        windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        visible: Boolean = true
    ) =
        RunningTaskInfo().apply {
            taskId = id
            displayId = DEFAULT_DISPLAY
            configuration.windowConfiguration.windowingMode = windowingMode
            token = MockToken().token()
            isVisible = visible
            baseIntent = Intent().apply { component = ComponentName("package", "component.name") }
        }

    private fun getLatestWct(
        @WindowManager.TransitionType type: Int = TRANSIT_OPEN,
        handlerClass: Class<out Transitions.TransitionHandler>? = null,
    ): WindowContainerTransaction {
        val arg = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        if (handlerClass == null) {
            verify(transitions).startTransition(eq(type), arg.capture(), isNull())
        } else {
            verify(transitions).startTransition(eq(type), arg.capture(), isA(handlerClass))
        }
        return arg.value
    }

    private fun WindowContainerTransaction.assertPendingIntent(intent: Intent) {
        assertHop("Type=PENDING_INTENT with component=${intent.component}") { hop ->
            hop.type == HIERARCHY_OP_TYPE_PENDING_INTENT &&
                    hop.pendingIntent?.intent?.component == intent.component
        }
    }

    private fun WindowContainerTransaction.assertContinuePackageUpdate(task: RunningTaskInfo) {
        assertHop("Type=CONTINUE_UPDATE for taskId=${task.taskId}") { hop ->
            hop.type == HIERARCHY_OP_TYPE_CONTINUE_PACKAGE_UPDATE &&
                    hop.container == task.token.asBinder()
        }
    }

    private fun WindowContainerTransaction.assertHop(
        description: String,
        predicate: (WindowContainerTransaction.HierarchyOp) -> Boolean
    ) {
        val match = hierarchyOps.find(predicate)
        val actualOps = hierarchyOps.joinToString(",") { op ->
            "$op"
        }
        assertWithMessage("Failed to find hop: $description.\nActual Hops:\n$actualOps")
            .that(match)
            .isNotNull()
    }

    private fun createPuActivityIntent(): Intent {
        return Intent(mContext, PackageUpdateActivity::class.java)
    }
}
