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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.accessibility.shortcutchooser.ui.composable.QuickAccessDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.viewmodel.QuickAccessDialogViewModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.phone.ComponentSystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect

@SysUISingleton
class QuickAccessDialogStartable
@Inject
constructor(
    private val viewModelFactory: QuickAccessDialogViewModel.Factory,
    private val dialogFactory: SystemUIDialogFactory,
    @Application private val applicationScope: CoroutineScope,
) : CoreStartable {

    private val viewModel = viewModelFactory.create()

    private var dialogInstance: ComponentSystemUIDialog? = null

    override fun start() {
        if (!Flags.launchAccessibilityQuickAccessDialogPermission()) {
            return
        }

        with(applicationScope) {
            launchTraced { observeDialogVisibility() }
            launchTraced { viewModel.activate() }
        }
    }

    private suspend fun observeDialogVisibility() {
        viewModel.isDialogVisible.collect { visible ->
            if (visible) {
                createDialog()
            } else {
                dialogInstance?.dismiss()
                dialogInstance = null
            }
        }
    }

    private fun createDialog() {
        if (dialogInstance != null) {
            return
        }

        dialogInstance =
            dialogFactory
                .create { dialog ->
                    QuickAccessDialogContent(
                        onDoneClick = { viewModel.dismissDialog() },
                        onTargetClick = { target ->
                            viewModel.performAccessibilityShortcut(target.targetName)
                        },
                        targets = viewModel.accessibilityTargets,
                    )
                }
                .apply {
                    setOnDismissListener { viewModel.dismissDialog() }
                    show()
                }
    }
}
