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

package com.android.systemui.accessibility.shortcutchooser.ui.viewmodel

import android.content.Context
import android.util.Log
import android.view.Display.INVALID_DISPLAY
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.util.AccessibilityUtils
import com.android.systemui.accessibility.shortcutchooser.domain.interactor.ShortcutChooserDialogInteractor
import com.android.systemui.accessibility.shortcutchooser.shared.model.DialogRequestModel
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter

/** ViewModel for the set of Shortcut Chooser dialogs. */
class ShortcutChooserDialogViewModel
@AssistedInject
constructor(
    @param:Application private val applicationContext: Context,
    private val interactor: ShortcutChooserDialogInteractor,
    private val keyguardInteractor: KeyguardInteractor,
) : HydratedActivatable() {

    enum class DialogType {
        NONE,
        TUTORIAL,
        EDIT_TARGETS,
        TOGGLE_TARGETS,
    }

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced { interactor.dialogRequest.collect { onDialogRequest(it) } }
            launchTraced {
                dialogType
                    .filter { it == DialogType.TOGGLE_TARGETS }
                    .collect { updateEditButtonVisibility() }
            }
            awaitCancellation()
        }
    }

    /**
     * Latest snapshot of the dialog request that's kept up to date once the view model is
     * activated.
     */
    val dialogRequest by
        interactor.dialogRequest.hydratedStateOf(
            DialogRequestModel(UserShortcutType.DEFAULT, INVALID_DISPLAY)
        )

    private val _dialogType = MutableStateFlow(DialogType.NONE)
    /** The type of dialog that should be shown. */
    val dialogType = _dialogType.asStateFlow()

    fun getAllAccessibilityTargets(@UserShortcutType shortcutType: Int) =
        interactor.getAllAccessibilityTargets(shortcutType)

    fun getAssignedAccessibilityTargets(@UserShortcutType shortcutType: Int) =
        interactor.getAssignedAccessibilityTargets(shortcutType)

    fun enableShortcutForTarget(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = interactor.enableShortcutForTarget(enable, shortcutType, targetName)

    fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = interactor.performAccessibilityShortcut(displayId, shortcutType, targetName)

    fun dismissDialog() {
        _dialogType.value = DialogType.NONE
    }

    fun showEditDialog() {
        _dialogType.value = DialogType.EDIT_TARGETS
    }

    fun onEditTargetsDoneClick(@UserShortcutType shortcutType: Int) {
        val assignedTargetsCount = interactor.getAssignedAccessibilityTargetsCount(shortcutType)
        if (assignedTargetsCount < 2) {
            dismissDialog()
        } else {
            _dialogType.value = DialogType.TOGGLE_TARGETS
        }
    }

    private val _isEditButtonVisible = MutableStateFlow(false)
    /** Whether the edit button should be shown on the Toggle Targets dialog. */
    val isEditButtonVisible = _isEditButtonVisible.asStateFlow()

    private suspend fun updateEditButtonVisibility() {
        _isEditButtonVisible.value =
            !isOOBE() &&
                !interactor.isHeadlessSystemUser() &&
                !keyguardInteractor.isKeyguardCurrentlyShowing()
    }

    // TODO: https://issuetracker.google.com/461597843 - Use SecureSettings in interactor.
    private fun isOOBE() = !AccessibilityUtils.isUserSetupCompleted(applicationContext)

    private suspend fun onDialogRequest(requestModel: DialogRequestModel) {
        val shortcutType = requestModel.shortcutType
        val assignedTargetsCount = interactor.getAssignedAccessibilityTargetsCount(shortcutType)

        if (assignedTargetsCount == 1) {
            // The target should be directly performed without showing any dialog.
            Log.d(TAG, "No dialog for shortcut type=${shortcutType} with 1 assigned target")
            return
        }

        if (shortcutType == UserShortcutType.TOP_ROW_KEY) {
            if (interactor.isHeadlessSystemUser() || isOOBE()) {
                interactor.sendBroadcastToLaunchQuickAccessDialog(requestModel.displayId)
                return
            }
            if (assignedTargetsCount == 0) {
                if (!keyguardInteractor.isKeyguardCurrentlyShowing()) {
                    _dialogType.value = DialogType.TUTORIAL
                } else {
                    Log.d(
                        TAG,
                        "No dialog for shortcut type=${shortcutType} with no assigned targets on lock screen",
                    )
                }
                return
            }
        }

        if (assignedTargetsCount != 0) {
            _dialogType.value = DialogType.TOGGLE_TARGETS
        } else {
            Log.d(TAG, "No dialog for shortcut type=${shortcutType} with no assigned targets")
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): ShortcutChooserDialogViewModel
    }

    private companion object {
        private val TAG = ShortcutChooserDialogViewModel::class.simpleName
    }
}
