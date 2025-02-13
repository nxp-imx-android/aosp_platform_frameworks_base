/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.featurepods.popups.ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.featurepods.media.ui.viewmodel.MediaControlChipViewModel
import com.android.systemui.statusbar.featurepods.popups.StatusBarPopupChips
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.shared.model.PopupChipModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/**
 * View model deciding which system process chips to show in the status bar. Emits a list of
 * PopupChipModels.
 */
class StatusBarPopupChipsViewModel
@AssistedInject
constructor(mediaControlChip: MediaControlChipViewModel) : ExclusiveActivatable() {
    private val hydrator: Hydrator = Hydrator("StatusBarPopupChipsViewModel.hydrator")

    /** The ID of the current chip that is showing its popup, or `null` if no chip is shown. */
    private var currentShownPopupChipId by mutableStateOf<PopupChipId?>(null)

    private val incomingPopupChipBundle: PopupChipBundle by
        hydrator.hydratedStateOf(
            traceName = "incomingPopupChipBundle",
            initialValue = PopupChipBundle(),
            source = mediaControlChip.chip.map { chip -> PopupChipBundle(media = chip) },
        )

    val shownPopupChips: List<PopupChipModel.Shown> by derivedStateOf {
        if (StatusBarPopupChips.isEnabled) {
            val bundle = incomingPopupChipBundle

            listOfNotNull(bundle.media).filterIsInstance<PopupChipModel.Shown>().map { chip ->
                chip.copy(
                    isPopupShown = chip.chipId == currentShownPopupChipId,
                    showPopup = { currentShownPopupChipId = chip.chipId },
                    hidePopup = { currentShownPopupChipId = null },
                )
            }
        } else {
            emptyList()
        }
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private data class PopupChipBundle(
        val media: PopupChipModel = PopupChipModel.Hidden(chipId = PopupChipId.MediaControl)
    )

    @AssistedFactory
    interface Factory {
        fun create(): StatusBarPopupChipsViewModel
    }
}
