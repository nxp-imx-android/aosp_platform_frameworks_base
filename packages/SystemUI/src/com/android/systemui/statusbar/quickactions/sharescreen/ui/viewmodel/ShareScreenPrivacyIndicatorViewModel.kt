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

package com.android.systemui.statusbar.quickactions.sharescreen.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.quickactions.sharescreen.domain.interactor.ShareScreenPrivacyIndicatorInteractor
import com.android.systemui.statusbar.quickactions.ui.viewmodel.ChipIcon
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipId
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/**
 * ViewModel for the privacy indicator of the screen sharing for large screen only. For others,
 * refer to [ShareToAppChipViewMode].
 */
class ShareScreenPrivacyIndicatorViewModel
@AssistedInject
constructor(
    shareScreenPrivacyIndicatorInteractor: ShareScreenPrivacyIndicatorInteractor,
    private val popupViewModelFactory: ShareScreenPrivacyIndicatorPopupViewModel.Factory,
) : StatusBarPopupChipViewModel, HydratedActivatable() {

    override val chip: QuickActionChipUiState by
        shareScreenPrivacyIndicatorInteractor.isChipVisible
            .map { toPopupChipModel(it) }
            .hydratedStateOf(
                traceName = "chip",
                initialValue =
                    QuickActionChipUiState.Hidden(QuickActionChipId.ShareScreenPrivacyIndicator),
            )

    private fun toPopupChipModel(isVisible: Boolean): QuickActionChipUiState {
        return if (isVisible) {
            QuickActionChipUiState.PopupChip(
                chipId = QuickActionChipId.ShareScreenPrivacyIndicator,
                icons = listOf(ChipIcon(Icon.Resource(R.drawable.ic_share_screen, null))),
                chipText = null,
                // TODO(b/444293568) Finalize and update the colors of this chip.
                colors = ColorsModel.AvControlsTheme,
                popupViewModelFactory = popupViewModelFactory,
            )
        } else {
            QuickActionChipUiState.Hidden(QuickActionChipId.ShareScreenPrivacyIndicator)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): ShareScreenPrivacyIndicatorViewModel
    }
}
