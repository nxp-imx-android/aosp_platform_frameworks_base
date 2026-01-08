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

package com.android.wm.shell.gamecontrols

import android.app.TaskInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.window.DesktopExperienceFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOW_DECORATION

/**
 * Helper class for the Game Controls entry point in the handle menu.
 *
 * This class provides utility methods for checking if Game Controls button should be shown, and for
 * handling the button click.
 */
object GameControlsHelper {

    /**
     * Returns true if the Game Controls button should be shown in the handle menu.
     *
     * The Game Controls button should be shown if:
     * - the task category is CATEGORY_GAME.
     * - the device has the game controls system feature.
     *
     * @param context The context of the handle menu.
     * @param taskInfo The task info of the app.
     * @return True if the Game Controls button should be shown, false otherwise.
     */
    @JvmStatic
    fun shouldShowGameControlsButton(context: Context, taskInfo: TaskInfo): Boolean {
        return gameControlsButtonFlagEnabled() &&
            isCategoryGame(taskInfo) &&
            hasGameControlsSystemFeature(context)
    }

    /**
     * Launches the Game Controls UI.
     *
     * This method sends a broadcast to the game controls intent receiver package with the game
     * controls intent action.
     *
     * @param context The context of the handle menu.
     * @param taskInfo The task info of the app.
     */
    @JvmStatic
    fun onLaunchGameControls(context: Context, taskInfo: TaskInfo) {
        val intentReceiverPackage =
            context.resources.getString(R.string.config_gameControlsIntentReceiverPackage)
        val intentAction = context.resources.getString(R.string.config_gameControlsIntentAction)
        if (intentReceiverPackage.isEmpty() || intentAction.isEmpty()) {
            ProtoLog.w(
                WM_SHELL_WINDOW_DECORATION,
                "GameControlsHelper: Intent receiver package or action is not defined.",
            )
            return
        }

        val intent = Intent(intentAction)
        intent.setPackage(intentReceiverPackage)
        ProtoLog.i(
            WM_SHELL_WINDOW_DECORATION,
            "GameControlsHelper: Sending the game controls intent %s to %s",
            intentAction, intentReceiverPackage
        )
        context.sendBroadcast(intent)
    }

    private fun gameControlsButtonFlagEnabled(): Boolean {
        return DesktopExperienceFlags.ENABLE_GAME_CONTROLS_ENTRY_IN_HANDLE_MENU.isTrue
    }

    private fun isCategoryGame(taskInfo: TaskInfo): Boolean {
        val appInfo = taskInfo.topActivityInfo?.applicationInfo
        if (appInfo == null) {
            val packageName = taskInfo.baseActivity?.packageName ?: "unknown"
            ProtoLog.w(
                WM_SHELL_WINDOW_DECORATION,
                "GameControlsHelper: Could not get ApplicationInfo for $packageName",
            )
            return false
        }
        return appInfo.category == ApplicationInfo.CATEGORY_GAME
    }

    private fun hasGameControlsSystemFeature(context: Context): Boolean {
        val gameControlsFeatureName =
            context.resources.getString(R.string.config_gameControlsSystemFeature)
        if (gameControlsFeatureName.isEmpty()) {
            return false
        }
        return context.packageManager.hasSystemFeature(gameControlsFeatureName)
    }
}
