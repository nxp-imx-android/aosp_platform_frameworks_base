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
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.quickactions.ime.domain.interactor.ImeIndicatorChipInteractor
import com.android.systemui.statusbar.quickactions.ime.shared.model.ImeIndicatorChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipId
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** View model for the IME indicator chip in the status bar. */
class ImeIndicatorChipViewModel
@AssistedInject
constructor(
    @Assisted private val displayId: Int,
    private val imeIndicatorChipInteractor: ImeIndicatorChipInteractor,
) : StatusBarPopupChipViewModel, HydratedActivatable() {

    override val chip: QuickActionChipUiState by
        imeIndicatorChipInteractor.chipModel
            .map { toPopupChipModel(it) }
            .hydratedStateOf(
                traceName = "imeIndicatorChip",
                initialValue = QuickActionChipUiState.Hidden(QuickActionChipId.ImeIndicator),
            )

    private fun toPopupChipModel(model: ImeIndicatorChipModel): QuickActionChipUiState {
        if (!model.isVisible) {
            return QuickActionChipUiState.Hidden(QuickActionChipId.ImeIndicator)
        }

        // TODO(b/458557858): Use IME icon or subtype short label if available, update the fallback
        // to a keyboard icon, and remove the placeholder "IME" string.
        val chipText = model.selectedSubtype?.subtypeId?.toString() ?: "IME"

        return QuickActionChipUiState.PopupChip(
            chipId = QuickActionChipId.ImeIndicator,
            icons = emptyList(),
            chipText = chipText,
            showPopup = { imeIndicatorChipInteractor.showInputMethodPicker(displayId) },
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(@DisplayId displayId: Int): ImeIndicatorChipViewModel
    }
}
