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
import android.hardware.input.InputManager
import android.os.Handler
import android.provider.Settings
import android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.android.wm.shell.transition.Transitions

/** Controls the display windowing mode in desktop mode */
class DesktopDisplayModeController(
    private val context: Context,
    private val transitions: Transitions,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val windowManager: IWindowManager,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    private val inputManager: InputManager,
    private val displayController: DisplayController,
    @ShellMainThread private val mainHandler: Handler,
) {

    private val onTabletModeChangedListener =
        object : InputManager.OnTabletModeChangedListener {
            override fun onTabletModeChanged(whenNanos: Long, inTabletMode: Boolean) {
                refreshDisplayWindowingMode()
            }
        }

    init {
        if (DesktopExperienceFlags.FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH.isTrue) {
            inputManager.registerOnTabletModeChangedListener(
                onTabletModeChangedListener,
                mainHandler,
            )
        }
    }

    fun refreshDisplayWindowingMode() {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING.isTrue) return

        val targetDisplayWindowingMode = getTargetWindowingModeForDefaultDisplay()
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

    @VisibleForTesting
    fun getTargetWindowingModeForDefaultDisplay(): Int {
        if (isExtendedDisplayEnabled() && hasExternalDisplay()) {
            return WINDOWING_MODE_FREEFORM
        }
        if (DesktopExperienceFlags.FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH.isTrue) {
            if (isInClamshellMode()) {
                return WINDOWING_MODE_FREEFORM
            }
            return WINDOWING_MODE_FULLSCREEN
        }

        // If form factor-based desktop first switch is disabled, use the default display windowing
        // mode here to keep the freeform mode for some form factors (e.g., FEATURE_PC).
        return windowManager.getWindowingMode(DEFAULT_DISPLAY)
    }

    private fun isExtendedDisplayEnabled(): Boolean {
        if (DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue) {
            return rootTaskDisplayAreaOrganizer
                .getDisplayIds()
                .filter { it != DEFAULT_DISPLAY }
                .any { displayId ->
                    displayController.getDisplay(displayId)?.let { display ->
                        DesktopModeStatus.isDesktopModeSupportedOnDisplay(context, display)
                    } ?: false
                }
        }

        return 0 !=
            Settings.Global.getInt(
                context.contentResolver,
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS,
                0,
            )
    }

    private fun hasExternalDisplay() =
        rootTaskDisplayAreaOrganizer.getDisplayIds().any { it != DEFAULT_DISPLAY }

    private fun isInClamshellMode() = inputManager.isInTabletMode() == InputManager.SWITCH_STATE_OFF

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopDisplayModeController"
    }
}
