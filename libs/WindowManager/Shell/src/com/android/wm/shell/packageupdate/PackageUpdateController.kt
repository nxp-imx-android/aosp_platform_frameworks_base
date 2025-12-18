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

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.os.UserHandle
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PACKAGE_UPDATE
import com.android.wm.shell.shared.annotations.ShellMainThreadImmediate
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Controller for handling package updates. When Shell is notified of a package update for a given
 * list of tasks, this class will handle it. Current implementation will launch a
 * [PackageUpdateActivity] as a placeholder activity in the tasks. It will also mark the tasks as
 * ready to continue package update.
 */
class PackageUpdateController(
    private val transitions: Transitions,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    shellInit: ShellInit,
    private val userProfileContexts: UserProfileContexts,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    private val desktopModeWindowDecorViewModel: Optional<DesktopModeWindowDecorViewModel>,
    @ShellMainThreadImmediate private val mainImmediateScope: CoroutineScope,
) : ShellTaskOrganizer.PackageUpdateListener {
    init {
        shellInit.addInitCallback(::onInit, this)
    }

    fun onInit() {
        ProtoLog.d(WM_SHELL_PACKAGE_UPDATE, "PackageUpdateController: onInit")
        shellTaskOrganizer.addPackageUpdateListener(this)
    }

    override fun onPackageUpdateRequested(updatingTasks: List<ActivityManager.RunningTaskInfo>) {
        ProtoLog.d(
            WM_SHELL_PACKAGE_UPDATE,
            "PackageUpdateController: onPackageUpdateRequested for task: [%s]",
            updatingTasks.joinToString { "${it.taskId}" }
        )


        mainImmediateScope.launch {
            val wct = WindowContainerTransaction()
            updatingTasks.forEach { task ->
                if (task.isVisible || task.isVisibleRequested) {
                    ProtoLog.d(
                        WM_SHELL_PACKAGE_UPDATE,
                        "PackageUpdateController: task %d is visible, launching placeholder. ",
                        task.taskId
                    )
                    val icon = getIcon(task)
                    launchPlaceholderInTask(wct, task, icon)
                }
                ProtoLog.d(
                    WM_SHELL_PACKAGE_UPDATE,
                    "PackageUpdateController: Setting continue package update for task %d.",
                    task.taskId
                )
                wct.continuePackageUpdate(task.token)
            }

            ProtoLog.d(
                WM_SHELL_PACKAGE_UPDATE,
                "PackageUpdateController: Starting transition",
            )
            transitions.startTransition(TRANSIT_OPEN, wct, null)
            ProtoLog.d(
                WM_SHELL_PACKAGE_UPDATE,
                "PackageUpdateController: Started transition",
            )
        }
    }

    override fun onPackageUpdateFinished(updatedTasks: List<ActivityManager.RunningTaskInfo>) {
        ProtoLog.d(
            WM_SHELL_PACKAGE_UPDATE,
            "PackageUpdateController: onPackageUpdateFinished for task: [%s]",
            updatedTasks.joinToString { "${it.taskId}" }
        )

        val wct = WindowContainerTransaction()
        updatedTasks.forEach { task ->
            if (task.isVisible || task.isVisibleRequested) {
                ProtoLog.d(
                    WM_SHELL_PACKAGE_UPDATE,
                    "PackageUpdateController: task %d is visible, launching the base intent and removing placeholder. ",
                    task.taskId
                )
                launchBaseIntent(wct, task)
            } else {
                ProtoLog.d(
                    WM_SHELL_PACKAGE_UPDATE,
                    "PackageUpdateController: Task is not visible, removing task %d.",
                    task.taskId
                )
                wct.removeTask(task.token, /* removeFromRecents= */ false)
            }
        }
        transitions.startTransition(TRANSIT_CHANGE, wct, null)
    }

    private fun launchPlaceholderInTask(
        wct: WindowContainerTransaction,
        task: ActivityManager.RunningTaskInfo,
        icon: Bitmap?
    ) {
        val userId = task.userId
        val userHandle = UserHandle.of(userId)
        val userContext = userProfileContexts.getOrCreate(userId)
        val intent = PackageUpdateActivity.createIntent(userContext, userId, task.taskId, icon)

        val options =
            ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                launchTaskId = task.taskId
                setAvoidMoveToFront()
            }
        val pendingIntent =
            PendingIntent.getActivityAsUser(
                userContext,
                /* requestCode= */ 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                /* options= */ null,
                userHandle,
            )
        wct.sendPendingIntent(pendingIntent, intent, options.toBundle())
    }

    private fun launchBaseIntent(
        wct: WindowContainerTransaction,
        task: ActivityManager.RunningTaskInfo
    ) {
        val userId = task.userId
        val userHandle = UserHandle.of(userId)
        val userContext = userProfileContexts[userId]

        task.baseIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        val options =
            ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                launchTaskId = task.taskId
                setAvoidMoveToFront()
            }
        val pendingIntent =
            PendingIntent.getActivityAsUser(
                userContext,
                /* requestCode= */ 0,
                task.baseIntent,
                PendingIntent.FLAG_IMMUTABLE,
                /* options= */ null,
                userHandle,
            )
        wct.sendPendingIntent(pendingIntent, Intent(), options.toBundle())
    }

    suspend fun getIcon(task: ActivityManager.RunningTaskInfo): Bitmap? {
        // Handle when there is window decor
        if (desktopModeWindowDecorViewModel.isPresent) {
            val viewModel = desktopModeWindowDecorViewModel.get()
            return if (viewModel.hasWindowDecoration(task.taskId)) {
                taskResourceLoader.getVeilIcon(task)
            } else {
                null
            }
        }
        // Don't show any icon if there is no window decor
        // TODO(b/468276203) - Add icon when there is no window decor
        return null
    }
}