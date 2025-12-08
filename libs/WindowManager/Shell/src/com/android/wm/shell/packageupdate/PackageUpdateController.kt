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
import android.os.UserHandle
import android.view.WindowManager.TRANSIT_OPEN
import android.window.WindowContainerTransaction
import androidx.core.net.toUri
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PACKAGE_UPDATE
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

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
            updatingTasks.joinToString { "${it?.taskId}" }
        )

        val wct = WindowContainerTransaction()
        updatingTasks.forEach { task ->
            if (task.isVisible || task.isVisibleRequested) {
                ProtoLog.d(
                    WM_SHELL_PACKAGE_UPDATE,
                    "PackageUpdateController: task %d is visible, launching placeholder. ",
                    task.taskId
                )
                launchPlaceholderInTask(wct, task)
            }
            ProtoLog.d(
                WM_SHELL_PACKAGE_UPDATE,
                "PackageUpdateController: Setting continue package update for task %d.",
                task.taskId
            )
            wct.continuePackageUpdate(task.token)
        }
        transitions.startTransition(TRANSIT_OPEN, wct, null)
    }

    private fun launchPlaceholderInTask(
        wct: WindowContainerTransaction,
        task: ActivityManager.RunningTaskInfo
    ) {
        val userId = task.userId
        val userHandle = UserHandle.of(userId)
        val userContext = userProfileContexts[userId]
        val intent = Intent(userContext, PackageUpdateActivity::class.java)

        task.baseIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // Add a unique data URI to distinguish PendingIntents for different tasks.
        intent.setData("packageupdate://task/${task.taskId}".toUri())
        intent.putExtra("ReplacingPackage", task.baseActivity?.packageName ?: "")
        intent.putExtra("baseIntent", task.baseIntent)
        intent.putExtra(Intent.EXTRA_USER_HANDLE, userId)

        val options =
            ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                launchTaskId = task.taskId
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
}