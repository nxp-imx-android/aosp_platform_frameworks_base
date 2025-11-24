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

package com.android.systemui.statusbar.quickactions.ime.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.quickactions.ime.domain.interactor.ImeIndicatorChipInteractor
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipId
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** View model for the IME indicator chip in the status bar. */
class ImeIndicatorChipViewModel
@AssistedInject
constructor(private val imeIndicatorChipInteractor: ImeIndicatorChipInteractor) :
    StatusBarPopupChipViewModel, HydratedActivatable() {

    override val chip: QuickActionChipUiState by
        imeIndicatorChipInteractor.isChipVisible
            .map { toPopupChipModel(it) }
            .hydratedStateOf(
                traceName = "imeIndicatorChip",
                initialValue = QuickActionChipUiState.Hidden(QuickActionChipId.ImeIndicator),
            )

    private fun toPopupChipModel(isVisible: Boolean): QuickActionChipUiState {
        return if (isVisible) {
            QuickActionChipUiState.Shown(
                chipId = QuickActionChipId.ImeIndicator,
                // TODO(b/458557858): Replace placeholder text with IME subtype short label or icon.
                icons = emptyList(),
                chipText = "IME",
                showPopup = { imeIndicatorChipInteractor.showInputMethodPicker() },
            )
        } else {
            QuickActionChipUiState.Hidden(QuickActionChipId.ImeIndicator)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): ImeIndicatorChipViewModel
    }
}
