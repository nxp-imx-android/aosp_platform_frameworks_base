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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.os.IBinder
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.TransitionInfo
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.back.BackAnimationController
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.isExitDesktopModeTransition
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit

/**
 * Class responsible for updating [DesktopRepository] with back navigation related changes. Also
 * adds the back navigation transitions as [DesktopMixedTransitionHandler.PendingMixedTransition] to
 * the [DesktopMixedTransitionHandler].
 */
class DesktopBackNavTransitionObserver(
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopMixedTransitionHandler: DesktopMixedTransitionHandler,
    private val backAnimationController: BackAnimationController,
    private val desksOrganizer: DesksOrganizer,
    desktopState: DesktopState,
    shellInit: ShellInit,
) {
    init {
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback(::onInit, this)
        }
    }

    fun onInit() {
        logD("onInit")
    }

    fun onTransitionReady(transition: IBinder, info: TransitionInfo) {
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()) {
            handleBackNavigation(transition, info)
            removeTaskIfNeeded(info)
        }
    }

    private fun removeTaskIfNeeded(info: TransitionInfo) {
        // Since we are no longer removing all the tasks [onTaskVanished], we need to remove them by
        // checking the transitions.
        if (!(TransitionUtil.isOpeningType(info.type) || info.type.isExitDesktopModeTransition())) {
            return
        }
        // Remove a task from the repository if the app is launched outside of desktop.
        for (change in info.changes) {
            val taskInfo = change.taskInfo
            if (taskInfo == null || taskInfo.taskId == -1) continue

            val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
            if (desktopRepository.isExitingDesktopTask(change)) {
                desktopRepository.removeTask(taskInfo.displayId, taskInfo.taskId)
            }
        }
    }

    private fun handleBackNavigation(transition: IBinder, info: TransitionInfo) {
        // When default back navigation happens, transition type is TO_BACK and the change is
        // TO_BACK. Mark the task going to back as minimized.
        if (info.type == TRANSIT_TO_BACK) {
            for (change in info.changes) {
                val taskInfo = change.taskInfo
                if (taskInfo == null || taskInfo.taskId == -1) {
                    continue
                }
                val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
                val isInDesktop = desktopRepository.isAnyDeskActive(taskInfo.displayId)
                if (
                    isInDesktop &&
                        change.mode == TRANSIT_TO_BACK &&
                        desktopRepository.isDesktopTask(taskInfo)
                ) {
                    val isLastTask =
                        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
                            desktopRepository.hasOnlyOneVisibleTask(taskInfo.displayId)
                        } else {
                            desktopRepository.isOnlyVisibleTask(taskInfo.taskId, taskInfo.displayId)
                        }
                    logD(
                        "handleBackNavigation marking to-back taskId=%d as minimized",
                        taskInfo.taskId,
                    )
                    desktopRepository.minimizeTask(taskInfo.displayId, taskInfo.taskId)
                    desktopMixedTransitionHandler.addPendingMixedTransition(
                        DesktopMixedTransitionHandler.PendingMixedTransition.Minimize(
                            transition,
                            taskInfo.taskId,
                            isLastTask,
                        )
                    )
                }
            }
        } else if (info.type == TRANSIT_CLOSE) {
            // In some cases app will be closing as a result of back navigation but we would like
            // to minimize. Mark the task closing as minimized.
            var hasWallpaperClosing = false
            var minimizingTask: Int? = null
            for (change in info.changes) {
                val taskInfo = change.taskInfo
                if (taskInfo == null || taskInfo.taskId == -1) continue

                if (
                    TransitionUtil.isClosingMode(change.mode) &&
                        DesktopWallpaperActivity.isWallpaperTask(taskInfo)
                ) {
                    hasWallpaperClosing = true
                }

                if (change.mode == TRANSIT_CLOSE && minimizingTask == null) {
                    minimizingTask = getMinimizingTaskForClosingTransition(taskInfo)
                }
            }

            if (minimizingTask == null) return
            // If the transition has wallpaper closing, it means we are moving out of desktop.
            logD("handleBackNavigation marking close taskId=%d as minimized", minimizingTask)
            desktopMixedTransitionHandler.addPendingMixedTransition(
                DesktopMixedTransitionHandler.PendingMixedTransition.Minimize(
                    transition,
                    minimizingTask,
                    isLastTask = hasWallpaperClosing,
                )
            )
        }
    }

    /**
     * Given this a closing task in a closing transition, a task is assumed to be closed by back
     * navigation if:
     * 1) Desktop mode is visible.
     * 2) It is a desktop task.
     * 3) Task is the latest task that the back gesture is triggered on.
     * 4) It's not marked as a closing task as a result of closing it by the app header.
     *
     * This doesn't necessarily mean all the cases are because of back navigation but those cases
     * will be rare. E.g. triggering back navigation on an app that pops up a close dialog, and
     * closing it will minimize it here.
     */
    private fun getMinimizingTaskForClosingTransition(taskInfo: RunningTaskInfo): Int? {
        val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
        val isInDesktop = desktopRepository.isAnyDeskActive(taskInfo.displayId)
        if (
            isInDesktop &&
                desktopRepository.isDesktopTask(taskInfo) &&
                backAnimationController.latestTriggerBackTask == taskInfo.taskId &&
                !desktopRepository.isClosingTask(taskInfo.taskId)
        ) {
            desktopRepository.minimizeTask(taskInfo.displayId, taskInfo.taskId)
            return taskInfo.taskId
        }
        return null
    }

    private fun DesktopRepository.isDesktopTask(task: RunningTaskInfo): Boolean =
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            isActiveTask(task.taskId)
        } else {
            task.isFreeform
        }

    private fun DesktopRepository.isExitingDesktopTask(change: TransitionInfo.Change): Boolean {
        val task = change.taskInfo ?: return false
        return if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            isActiveTask(task.taskId) && desksOrganizer.getDeskAtEnd(change) == null
        } else {
            isActiveTask(task.taskId) && !task.isFreeform
        }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopBackNavTransitionObserver"
    }
}
