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
import android.os.UserHandle
import android.view.Display.INVALID_DISPLAY
import android.view.accessibility.Flags as AccessibilityFlags
import androidx.annotation.VisibleForTesting
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.Flags as SystemUIFlags
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.DialogRequestModel
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.user.domain.interactor.HeadlessSystemUserMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge

@SysUISingleton
class ShortcutChooserDialogInteractor
@Inject
constructor(
    private val repository: AccessibilityShortcutsRepository,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val broadcastSender: BroadcastSender,
    private val userRepository: UserRepository,
    private val hsum: HeadlessSystemUserMode,
) {
    val dialogRequest: Flow<DialogRequestModel> =
        merge(
                broadcastDispatcher.broadcastFlow(
                    filter = IntentFilter().apply { addAction(SHORTCUT_CHOOSER_ACTION) },
                    user = UserHandle.SYSTEM,
                    flags = Context.RECEIVER_NOT_EXPORTED,
                ) { intent, _ ->
                    processShortcutChooserIntent(intent)
                },
                broadcastDispatcher.broadcastFlow(
                    filter = IntentFilter().apply { addAction(QUICK_ACCESS_ACTION) },
                    user = UserHandle.SYSTEM,
                    flags = Context.RECEIVER_EXPORTED,
                    permission = QUICK_ACCESS_PERMISSION,
                ) { intent, _ ->
                    processQuickAccessIntent(intent)
                },
            )
            .filterNotNull()

    fun getAllAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> = repository.getAllAccessibilityTargets(shortcutType)

    fun getAssignedAccessibilityTargets(
        @UserShortcutType shortcutType: Int
    ): Flow<List<AccessibilityTargetModel>> =
        repository.getSelectedAccessibilityTargets(shortcutType)

    fun getAssignedAccessibilityTargetsCount(@UserShortcutType shortcutType: Int): Int =
        repository.getSelectedAccessibilityTargetsInfo(shortcutType).size

    fun enableShortcutForTarget(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = repository.enableShortcutsForTargets(enable, shortcutType, setOf(targetName))

    fun enableShortcutForAllTargets(@UserShortcutType shortcutType: Int) =
        repository
            .getAllAccessibilityTargetsInfo(shortcutType)
            .filter { !it.isAssigned }
            .map { it.targetName }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { repository.enableShortcutsForTargets(true, shortcutType, it) }

    fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) {
        if (displayId != INVALID_DISPLAY) {
            repository.performAccessibilityShortcut(displayId, shortcutType, targetName)
        }
    }

    fun launchQuickAccessDialog(displayId: Int) =
        broadcastSender.sendBroadcastAsUser(
            Intent().apply {
                setPackage(SYSTEMUI_PACKAGE)
                setAction(QUICK_ACCESS_ACTION)
                putExtra(ShortcutChooserDialogConstants.DISPLAY_ID, displayId)
            },
            UserHandle.SYSTEM,
        )

    /** True if on login screen (HSU). */
    suspend fun isHeadlessSystemUser(): Boolean =
        hsum.isHeadlessSystemUser(userRepository.getSelectedUserInfo().id)

    private fun processShortcutChooserIntent(intent: Intent): DialogRequestModel? {
        if (!AccessibilityFlags.enableA11yTopRowShortcut()) {
            return null
        }
        val shortcutType =
            intent.getIntExtra(
                ShortcutChooserDialogConstants.SHORTCUT_TYPE,
                UserShortcutType.DEFAULT,
            )
        val displayId =
            intent.getIntExtra(ShortcutChooserDialogConstants.DISPLAY_ID, INVALID_DISPLAY)

        if (shortcutType != UserShortcutType.DEFAULT && displayId != INVALID_DISPLAY) {
            return DialogRequestModel(shortcutType, displayId)
        }
        return null
    }

    private fun processQuickAccessIntent(intent: Intent): DialogRequestModel? {
        if (!SystemUIFlags.launchAccessibilityQuickAccessDialogPermission()) {
            return null
        }
        val displayId =
            intent.getIntExtra(ShortcutChooserDialogConstants.DISPLAY_ID, INVALID_DISPLAY)
        if (displayId != INVALID_DISPLAY) {
            return DialogRequestModel(UserShortcutType.QUICK_ACCESS, displayId)
        }
        return null
    }

    companion object {
        @VisibleForTesting
        const val SHORTCUT_CHOOSER_ACTION =
            "com.android.systemui.action.LAUNCH_ACCESSIBILITY_SHORTCUT_CHOOSER_DIALOG"

        @VisibleForTesting
        const val QUICK_ACCESS_ACTION =
            "com.android.systemui.action.LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG"
        const val QUICK_ACCESS_PERMISSION =
            "com.android.systemui.permission.LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG"
        @VisibleForTesting const val SYSTEMUI_PACKAGE = "com.android.systemui"
    }
}
