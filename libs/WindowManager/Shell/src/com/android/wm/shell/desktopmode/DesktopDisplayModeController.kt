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

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.app.WindowConfiguration.windowingModeToString
import android.content.Context
import android.provider.Settings
import android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.transition.Transitions

/** Controls the display windowing mode in desktop mode */
class DesktopDisplayModeController(
    private val context: Context,
    private val transitions: Transitions,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val windowManager: IWindowManager,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
) {

    fun refreshDisplayWindowingMode() {
        if (!Flags.enableDisplayWindowingModeSwitching()) return
        // TODO: b/375319538 - Replace the check with a DisplayManager API once it's available.
        val isExtendedDisplayEnabled =
            0 !=
                Settings.Global.getInt(
                    context.contentResolver,
                    DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS,
                    0,
                )
        if (!isExtendedDisplayEnabled) {
            // No action needed in mirror or projected mode.
            return
        }

        val hasNonDefaultDisplay =
            rootTaskDisplayAreaOrganizer.getDisplayIds().any { displayId ->
                displayId != DEFAULT_DISPLAY
            }
        val targetDisplayWindowingMode =
            if (hasNonDefaultDisplay) {
                WINDOWING_MODE_FREEFORM
            } else {
                // Use the default display windowing mode when no non-default display.
                windowManager.getWindowingMode(DEFAULT_DISPLAY)
            }
        val tdaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)
        requireNotNull(tdaInfo) { "DisplayAreaInfo of DEFAULT_DISPLAY must be non-null." }
        val currentDisplayWindowingMode = tdaInfo.configuration.windowConfiguration.windowingMode
        if (currentDisplayWindowingMode == targetDisplayWindowingMode) {
            // Already in the target mode.
            return
        }

        logV(
            "As an external display is connected, changing default display's windowing mode from" +
                " ${windowingModeToString(currentDisplayWindowingMode)}" +
                " to ${windowingModeToString(targetDisplayWindowingMode)}"
        )

        val wct = WindowContainerTransaction()
        wct.setWindowingMode(tdaInfo.token, targetDisplayWindowingMode)
        shellTaskOrganizer
            .getRunningTasks(DEFAULT_DISPLAY)
            .filter { it.activityType == ACTIVITY_TYPE_STANDARD }
            .forEach {
                // TODO: b/391965153 - Reconsider the logic under multi-desk window hierarchy
                when (it.windowingMode) {
                    currentDisplayWindowingMode -> {
                        wct.setWindowingMode(it.token, currentDisplayWindowingMode)
                    }
                    targetDisplayWindowingMode -> {
                        wct.setWindowingMode(it.token, WINDOWING_MODE_UNDEFINED)
                    }
                }
            }
        // The override windowing mode of DesktopWallpaper can be UNDEFINED on fullscreen-display
        // right after the first launch while its resolved windowing mode is FULLSCREEN. We here
        // it has the FULLSCREEN override windowing mode.
        desktopWallpaperActivityTokenProvider.getToken(DEFAULT_DISPLAY)?.let { token ->
            wct.setWindowingMode(token, WINDOWING_MODE_FULLSCREEN)
        }
        transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopDisplayModeController"
    }
}
