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
package com.android.systemui.accessibility.shortcutchooser.domain.interactor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.Display.INVALID_DISPLAY
import androidx.annotation.VisibleForTesting
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.QuickAccessDialogRequestModel
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class QuickAccessDialogInteractor
@Inject
constructor(
    private val repository: AccessibilityShortcutsRepository,
    private val broadcastDispatcher: BroadcastDispatcher,
) {
    val dialogRequest: Flow<QuickAccessDialogRequestModel?> =
        broadcastDispatcher.broadcastFlow(
            filter = IntentFilter().apply { addAction(ACTION) },
            flags = Context.RECEIVER_EXPORTED,
            permission = PERMISSION,
        ) { intent, _ ->
            processIntent(intent)
        }

    fun getAllAccessibilityTargets(): Flow<List<AccessibilityTargetModel>> =
        repository.getAllAccessibilityTargets(SHORTCUT_TYPE)

    fun enableShortcutForAllTargets() =
        repository
            .getAllAccessibilityTargetsInfo(SHORTCUT_TYPE)
            .filter { !it.isAssigned }
            .forEach { repository.enableShortcutsForTargets(true, SHORTCUT_TYPE, it.targetName) }

    fun performAccessibilityShortcut(displayId: Int, targetName: String) {
        if (displayId != INVALID_DISPLAY) {
            repository.performAccessibilityShortcut(displayId, SHORTCUT_TYPE, targetName)
        }
    }

    private fun processIntent(intent: Intent): QuickAccessDialogRequestModel? {
        val displayId = intent.getIntExtra(DISPLAY_ID, INVALID_DISPLAY)
        return if (displayId != INVALID_DISPLAY) QuickAccessDialogRequestModel(displayId) else null
    }

    @VisibleForTesting
    companion object {
        @UserShortcutType const val SHORTCUT_TYPE = UserShortcutType.QUICK_ACCESS

        const val ACTION = "com.android.systemui.action.LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG"
        const val PERMISSION =
            "com.android.systemui.permission.LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG"
        const val DISPLAY_ID = ShortcutChooserDialogConstants.DISPLAY_ID
    }
}
