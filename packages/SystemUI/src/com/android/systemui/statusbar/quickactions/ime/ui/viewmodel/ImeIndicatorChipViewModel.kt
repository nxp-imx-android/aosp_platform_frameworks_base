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

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.ime.domain.interactor.ImeIndicatorChipInteractor
import com.android.systemui.statusbar.quickactions.ime.shared.model.ImeIndicatorChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.quickactions.ui.viewmodel.ChipIcon
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
    @param:Application private val context: Context,
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

        val subtypeIcon =
            model.selectedSubtype?.icon?.let { subtypeIcon ->
                android.graphics.drawable.Icon.createWithResource(
                        subtypeIcon.packageName,
                        subtypeIcon.resId,
                    )
                    .loadDrawable(context)
                    ?.asIcon(resId = subtypeIcon.resId, resPackage = subtypeIcon.packageName)
            }
        val subtypeShortLabel = model.selectedSubtype?.shortLabel

        val (icons: List<ChipIcon>, chipText: String?) =
            when {
                subtypeIcon != null -> {
                    listOf(ChipIcon(subtypeIcon)) to null
                }
                !subtypeShortLabel.isNullOrBlank() -> {
                    emptyList<ChipIcon>() to subtypeShortLabel
                }
                else -> {
                    val defaultIcon =
                        Icon.Resource(
                            R.drawable.ic_keyboard,
                            ContentDescription.Resource(
                                R.string.accessibility_status_bar_input_method_indicator
                            ),
                        )
                    listOf(ChipIcon(defaultIcon)) to null
                }
            }

        return QuickActionChipUiState.PopupChip(
            chipId = QuickActionChipId.ImeIndicator,
            icons = icons,
            chipText = chipText,
            showPopup = { imeIndicatorChipInteractor.showInputMethodPicker(displayId) },
            contentDescription =
                ContentDescription.Resource(
                    R.string.accessibility_status_bar_input_method_indicator
                ),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(@DisplayId displayId: Int): ImeIndicatorChipViewModel
    }
}
