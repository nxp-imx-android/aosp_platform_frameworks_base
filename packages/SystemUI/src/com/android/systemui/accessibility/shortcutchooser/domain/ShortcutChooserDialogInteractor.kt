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

package com.android.systemui.accessibility.shortcutchooser.domain

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.view.Display.INVALID_DISPLAY
import androidx.annotation.VisibleForTesting
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.DialogRequestModel
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class ShortcutChooserDialogInteractor
@Inject
constructor(
    private val repository: AccessibilityShortcutsRepository,
    private val broadcastDispatcher: BroadcastDispatcher,
) {
    val dialogRequest: Flow<DialogRequestModel?> =
        broadcastDispatcher.broadcastFlow(
            filter = IntentFilter().apply { addAction(ACTION) },
            user = UserHandle.SYSTEM,
            flags = Context.RECEIVER_NOT_EXPORTED,
        ) { intent, _ ->
            processRequest(intent)
        }

    fun getAllAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> = repository.getAllAccessibilityTargetsInfo(shortcutType)

    fun getSelectedAccessibilityTargetsInfo(
        @UserShortcutType shortcutType: Int
    ): List<AccessibilityTargetModel> = repository.getSelectedAccessibilityTargetsInfo(shortcutType)

    fun enableShortcutForTargets(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = repository.enableShortcutsForTargets(enable, shortcutType, targetName)

    fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        if (displayId == INVALID_DISPLAY) {
            return
        }

        repository.performAccessibilityShortcut(displayId, shortcutType, targetName)
    }

    private fun processRequest(intent: Intent): DialogRequestModel? {
        val shortcutType = intent.getIntExtra(ShortcutChooserDialogConstants.SHORTCUT_TYPE, 0)
        val displayId =
            intent.getIntExtra(ShortcutChooserDialogConstants.DISPLAY_ID, INVALID_DISPLAY)

        return if (shortcutType == UserShortcutType.DEFAULT || displayId == INVALID_DISPLAY) {
            null
        } else {
            DialogRequestModel(shortcutType, displayId)
        }
    }

    companion object {
        @VisibleForTesting
        const val ACTION =
            "com.android.systemui.action.LAUNCH_ACCESSIBILITY_SHORTCUT_CHOOSER_DIALOG"
    }
}
