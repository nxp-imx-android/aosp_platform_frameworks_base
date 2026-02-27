/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.dreams.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.dreams.domain.interactor.DreamInteractor
import com.android.systemui.dreams.shared.model.AccessibilityActionModel
import com.android.systemui.dreams.ui.DreamSwitcherDialogDelegate
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** View model for the [com.android.systemui.dreams.DreamOverlayContainerView] */
class DreamOverlayContainerViewModel
@AssistedInject
constructor(
    dreamInteractor: DreamInteractor,
    private val dialogDelegateFactory: DreamSwitcherDialogDelegate.Factory,
) : HydratedActivatable(enableEnqueuedActivations = true), DreamDialogController {

    private val dialogDelegate: DreamSwitcherDialogDelegate by lazy {
        dialogDelegateFactory.create(this)
    }

    private var currentDialog: SystemUIDialog? = null

    var dialogShowing: Boolean by mutableStateOf(false)
        private set

    val dreamSwitcherAction: AccessibilityActionModel? by
        dreamInteractor.canSwitchDreams
            .map { canSwitch ->
                AccessibilityActionModel(
                        labelResId = R.string.dreams_switcher_accessibility_action,
                        action = { showDialog() },
                    )
                    .takeIf { canSwitch }
            }
            .hydratedStateOf(initialValue = null)

    val canSwitchDreams: Boolean by
        dreamInteractor.canSwitchDreams.hydratedStateOf(initialValue = false)

    override fun showDialog(): Boolean {
        if (canSwitchDreams && currentDialog == null) {
            currentDialog =
                dialogDelegate.createDialog().apply {
                    setOnShowListener { dialogShowing = true }
                    setOnDismissListener {
                        dialogShowing = false
                        currentDialog = null
                    }
                    show()
                }
            return true
        }
        return false
    }

    override fun dismissDialog() {
        currentDialog?.dismiss()
    }

    override suspend fun onDeactivated() {
        dismissDialog()
    }

    @AssistedFactory
    fun interface Factory {
        fun create(): DreamOverlayContainerViewModel
    }
}
