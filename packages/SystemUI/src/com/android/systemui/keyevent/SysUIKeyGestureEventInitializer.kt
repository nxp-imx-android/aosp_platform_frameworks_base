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

package com.android.systemui.keyevent

import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL
import android.util.Slog
import com.android.hardware.input.Flags.enableQuickSettingsPanelShortcut
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shade.display.StatusBarTouchShadeDisplayPolicy
import com.android.systemui.statusbar.CommandQueue
import com.android.window.flags.Flags.enableKeyGestureHandlerForSysui
import javax.inject.Inject

/**
 * Registers system UI interested keyboard shortcut events and dispatches events to the correct
 * handlers.
 */
@SysUISingleton
class SysUIKeyGestureEventInitializer
@Inject
constructor(
    private val inputManager: InputManager,
    private val commandQueue: CommandQueue,
    private val shadeDisplayPolicy: StatusBarTouchShadeDisplayPolicy,
) : CoreStartable {
    override fun start() {
        val supportedGestures = mutableListOf<Int>()
        if (enableKeyGestureHandlerForSysui()) {
            supportedGestures.add(KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL)
        }
        if (enableQuickSettingsPanelShortcut()) {
            supportedGestures.add(KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL)
        }
        if (supportedGestures.isEmpty()) {
            return
        }
        inputManager.registerKeyGestureEventHandler(supportedGestures) { event, _ ->
            when (event.keyGestureType) {
                KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL -> {
                    shadeDisplayPolicy.onNotificationPanelKeyboardShortcut()
                    commandQueue.toggleNotificationsPanel()
                }
                KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL -> {
                    shadeDisplayPolicy.onQSPanelKeyboardShortcut()
                    commandQueue.toggleQuickSettingsPanel()
                }

                else -> Slog.w(TAG, "Unsupported key gesture event: ${event.keyGestureType}")
            }
        }
    }

    private companion object {
        const val TAG = "KeyGestureEventInitializer"
    }
}
