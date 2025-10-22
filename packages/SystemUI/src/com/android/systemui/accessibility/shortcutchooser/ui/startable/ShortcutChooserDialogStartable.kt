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

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.CoreStartable
import com.android.systemui.accessibility.shortcutchooser.domain.ShortcutChooserDialogInteractor
import com.android.systemui.accessibility.shortcutchooser.shared.model.DialogRequestModel
import com.android.systemui.accessibility.shortcutchooser.ui.composable.ShortcutEditorDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.ShortcutPickerDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.TopRowKeyTutorialDialogContent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.phone.ComponentSystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SysUISingleton
class ShortcutChooserDialogStartable
@Inject
constructor(
    private val interactor: ShortcutChooserDialogInteractor,
    private val dialogFactory: SystemUIDialogFactory,
    @param:Application private val mainScope: CoroutineScope,
) : CoreStartable {
    @VisibleForTesting var currentDialog: ComponentSystemUIDialog? = null

    @VisibleForTesting var currentScreenState: MutableState<DialogScreen>? = null

    override fun start() {
        if (!android.view.accessibility.Flags.enableA11yTopRowShortcut()) {
            return
        }

        mainScope.launch {
            interactor.dialogRequest.collectLatest { dialogRequestModel ->
                createDialog(dialogRequestModel)
            }
        }
    }

    private fun createDialog(dialogRequestModel: DialogRequestModel?) {
        // Only one dialog shown up.
        if (currentDialog != null) {
            return
        }

        if (dialogRequestModel == null) {
            return
        }

        val selectedTargetsList =
            interactor.getSelectedAccessibilityTargetsInfo(dialogRequestModel.shortcutType)

        // We shouldn't receive the request if only one target is selected, it will directly toggle
        // the feature for the target in [AccessibilityManagerService].
        if (selectedTargetsList.size == 1) {
            Log.d(
                TAG,
                "Dialog is not displayed because selected targets size is 1: $selectedTargetsList",
            )
            return
        }

        val shortcutType = dialogRequestModel.shortcutType
        if (
            selectedTargetsList.isEmpty() &&
                dialogRequestModel.shortcutType != UserShortcutType.TOP_ROW_KEY
        ) {
            Log.d(
                TAG,
                "Dialog is not displayed because selected targets is empty and shortcut type is $shortcutType",
            )
        }

        val startScreen =
            if (selectedTargetsList.isEmpty()) {
                DialogScreen.INITIAL
            } else {
                DialogScreen.TOGGLE_TARGETS
            }
        currentScreenState = mutableStateOf(startScreen)
        currentDialog =
            dialogFactory.create { dialogController ->
                currentScreenState?.let { state ->
                    var currentScreen by state

                    when (currentScreen) {
                        DialogScreen.INITIAL -> {
                            TopRowKeyTutorialDialogContent(
                                onAddFeaturesClick = { currentScreen = DialogScreen.EDIT_TARGETS },
                                onCancelClick = { dialogController.dismiss() },
                            )
                        }
                        DialogScreen.EDIT_TARGETS -> {
                            ShortcutEditorDialogContent(
                                shortcutType,
                                infoList = interactor.getAllAccessibilityTargetsInfo(shortcutType),
                                onDoneClick = {
                                    val newSelectedList =
                                        interactor.getSelectedAccessibilityTargetsInfo(shortcutType)
                                    if (newSelectedList.size < 2) {
                                        dialogController.dismiss()
                                    } else {
                                        currentScreen = DialogScreen.TOGGLE_TARGETS
                                    }
                                },
                                onTargetToggled = { targetName, isEnabled ->
                                    interactor.enableShortcutForTargets(
                                        isEnabled,
                                        shortcutType,
                                        targetName,
                                    )
                                },
                            )
                        }
                        DialogScreen.TOGGLE_TARGETS -> {
                            ShortcutPickerDialogContent(
                                infoList =
                                    interactor.getSelectedAccessibilityTargetsInfo(shortcutType),
                                onEditClick = { currentScreen = DialogScreen.EDIT_TARGETS },
                                onDoneClick = { dialogController.dismiss() },
                                onTargetClick = {
                                    interactor.performAccessibilityShortcut(
                                        dialogRequestModel.displayId,
                                        shortcutType,
                                        it.targetName,
                                    )
                                    dialogController.dismiss()
                                },
                            )
                        }
                    }
                }
            }

        currentDialog?.let { dialog ->
            dialog.show()
            dialog.setOnDismissListener {
                currentDialog = null
                currentScreenState = null
            }
        }
    }

    @VisibleForTesting
    enum class DialogScreen {
        INITIAL,
        EDIT_TARGETS,
        TOGGLE_TARGETS,
    }

    companion object {
        private val TAG = ShortcutChooserDialogStartable::class.simpleName
    }
}
