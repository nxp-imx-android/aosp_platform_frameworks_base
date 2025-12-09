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

package com.android.systemui.accessibility.shortcutchooser.ui.startable

import android.view.accessibility.Flags as AccessibilityFlags
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.CoreStartable
import com.android.systemui.Flags as SystemUIFlags
import com.android.systemui.accessibility.shortcutchooser.ui.composable.QuickAccessDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.ShortcutEditorDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.ShortcutPickerDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.TopRowKeyTutorialDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.viewmodel.ShortcutChooserDialogViewModel
import com.android.systemui.accessibility.shortcutchooser.ui.viewmodel.ShortcutChooserDialogViewModel.DialogType
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.phone.ComponentSystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@SysUISingleton
class ShortcutChooserDialogStartable
@Inject
constructor(
    private val viewModelFactory: ShortcutChooserDialogViewModel.Factory,
    private val dialogFactory: SystemUIDialogFactory,
    @param:Application private val applicationScope: CoroutineScope,
) : CoreStartable {

    private val viewModel = viewModelFactory.create()

    private var dialogInstance: ComponentSystemUIDialog? = null

    override fun start() {
        if (
            !AccessibilityFlags.enableA11yTopRowShortcut() &&
                !SystemUIFlags.launchAccessibilityQuickAccessDialogPermission()
        ) {
            return
        }

        with(applicationScope) {
            launchTraced { observeDialogState() }
            launchTraced { viewModel.activate() }
        }
    }

    private suspend fun observeDialogState() {
        viewModel.dialogType.collect { dialogType ->
            if (dialogType == DialogType.NONE) {
                dialogInstance?.dismiss()
                dialogInstance = null
            } else if (dialogInstance == null) {
                createDialog()
            }
        }
    }

    private fun createDialog() {
        dialogInstance =
            dialogFactory
                .create { dialog ->
                    val dialogType by viewModel.dialogType.collectAsStateWithLifecycle()
                    val dialogRequest = viewModel.dialogRequest
                    val shortcutType = dialogRequest.shortcutType
                    val displayId = dialogRequest.displayId

                    when (dialogType) {
                        DialogType.TUTORIAL -> {
                            TopRowKeyTutorialDialogContent(
                                onAddFeaturesClick = viewModel::showEditDialog,
                                onCancelClick = viewModel::dismissDialog,
                            )
                        }
                        DialogType.EDIT_TARGETS -> {
                            val allTargets by
                                remember(shortcutType) {
                                        viewModel.getAllAccessibilityTargets(shortcutType)
                                    }
                                    .collectAsStateWithLifecycle(emptyList())
                            ShortcutEditorDialogContent(
                                shortcutType,
                                targets = allTargets,
                                onDoneClick = { viewModel.onEditTargetsDoneClick(shortcutType) },
                                onTargetToggled = { target ->
                                    viewModel.enableShortcutForTarget(
                                        !target.isAssigned,
                                        shortcutType,
                                        target.targetName,
                                    )
                                },
                            )
                        }
                        DialogType.TOGGLE_TARGETS -> {
                            val assignedTargets by
                                remember(shortcutType) {
                                        viewModel.getAssignedAccessibilityTargets(shortcutType)
                                    }
                                    .collectAsStateWithLifecycle(emptyList())
                            val isEditButtonVisible by
                                viewModel.isEditButtonVisible.collectAsStateWithLifecycle(false)
                            ShortcutPickerDialogContent(
                                targets = assignedTargets,
                                showEditButton = isEditButtonVisible,
                                onEditClick = viewModel::showEditDialog,
                                onDoneClick = viewModel::dismissDialog,
                                onTargetClick = {
                                    viewModel.performAccessibilityShortcut(
                                        displayId,
                                        shortcutType,
                                        it.targetName,
                                    )
                                    viewModel.dismissDialog()
                                },
                            )
                        }
                        DialogType.QUICK_ACCESS -> {
                            val allTargets by
                                remember(shortcutType) {
                                        viewModel.getAllAccessibilityTargets(shortcutType)
                                    }
                                    .collectAsStateWithLifecycle(emptyList())
                            QuickAccessDialogContent(
                                onDoneClick = { viewModel.dismissDialog() },
                                onTargetClick = {
                                    viewModel.performAccessibilityShortcut(
                                        displayId,
                                        shortcutType,
                                        it.targetName,
                                    )
                                    if (!it.isToggleable) {
                                        viewModel.dismissDialog()
                                    }
                                },
                                targets = allTargets,
                            )
                        }
                        else -> {}
                    }
                }
                .apply {
                    setOnDismissListener { viewModel.dismissDialog() }
                    show()
                }
    }
}
