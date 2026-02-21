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

package com.android.systemui.statusbar.quickactions.popups.ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.statusbar.quickactions.assistant.StatusBarAssistantIcon
import com.android.systemui.statusbar.quickactions.assistant.ui.viewmodel.AssistantIconViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.AvControlsChipViewModel
import com.android.systemui.statusbar.quickactions.ime.ui.viewmodel.ImeIndicatorChipViewModel
import com.android.systemui.statusbar.quickactions.media.ui.viewmodel.MediaControlChipViewModel
import com.android.systemui.statusbar.quickactions.popups.StatusBarPopupChips
import com.android.systemui.statusbar.quickactions.sharescreen.ui.viewmodel.ShareScreenPrivacyIndicatorViewModel
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipId
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * View model deciding which system process chips to show in the status bar. Emits a list of
 * [QuickActionChipUiState]s.
 */
class StatusBarPopupChipsViewModel
@AssistedInject
constructor(
    @Assisted private val displayId: Int,
    mediaControlChipFactory: MediaControlChipViewModel.Factory,
    avControlsChipFactory: AvControlsChipViewModel.Factory,
    shareScreenPrivacyIndicatorFactory: ShareScreenPrivacyIndicatorViewModel.Factory,
    assistantIconFactory: AssistantIconViewModel.Factory,
    imeIndicatorChipFactory: ImeIndicatorChipViewModel.Factory,
) : ExclusiveActivatable() {

    private val mediaControlChip by lazy { mediaControlChipFactory.create() }
    private val avControlsChip by lazy { avControlsChipFactory.create() }
    private val shareScreenPrivacyIndicator by lazy { shareScreenPrivacyIndicatorFactory.create() }
    private val assistantIcon by lazy { assistantIconFactory.create() }
    private val imeIndicatorChip by lazy { imeIndicatorChipFactory.create(displayId) }

    /** The ID of the current chip that is currently active, or `null` if no chip is active. */
    private var currentActiveQuickActionId by mutableStateOf<QuickActionChipId?>(null)

    private val incomingQuickActionChipBundle: QuickActionChipBundle by derivedStateOf {
        QuickActionChipBundle(
            media = mediaControlChip.chip,
            privacy = avControlsChip.chip,
            shareScreen = shareScreenPrivacyIndicator.chip,
            assistant = assistantIcon.chip,
            ime = imeIndicatorChip.chip,
        )
    }

    val shownQuickActionChips: List<QuickActionChipUiState> by derivedStateOf {
        if (StatusBarPopupChips.isEnabled) {
            val bundle = incomingQuickActionChipBundle

            listOfNotNull(bundle.media, bundle.privacy, bundle.shareScreen)
                .filterIsInstance<QuickActionChipUiState.PopupChip>()
                .map { chip ->
                    chip.copy(
                        isPopupShown = chip.chipId == currentActiveQuickActionId,
                        showPopup = { currentActiveQuickActionId = chip.chipId },
                        hidePopup = { currentActiveQuickActionId = null },
                    )
                } +
                listOfNotNull(bundle.assistant, bundle.ime).filter {
                    it !is QuickActionChipUiState.Hidden
                }
        } else {
            emptyList()
        }
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { avControlsChip.activate() }
            launch { mediaControlChip.activate() }
            launch { shareScreenPrivacyIndicator.activate() }
            if (StatusBarAssistantIcon.isEnabled) {
                launch { assistantIcon.activate() }
            }
            launch { imeIndicatorChip.activate() }
            // TODO(b/452975516): Clean up this logic after the bundle is split into popup chips and
            // action chips.
            launch {
                snapshotFlow { incomingQuickActionChipBundle }
                    .distinctUntilChanged()
                    .collect { bundle ->
                        if (
                            listOfNotNull(bundle.media, bundle.privacy, bundle.shareScreen)
                                .filterIsInstance<QuickActionChipUiState.PopupChip>()
                                .none { it.chipId == currentActiveQuickActionId }
                        ) {
                            currentActiveQuickActionId = null
                        }
                    }
            }
        }
        awaitCancellation()
    }

    private data class QuickActionChipBundle(
        val media: QuickActionChipUiState =
            QuickActionChipUiState.Hidden(chipId = QuickActionChipId.MediaControl),
        val privacy: QuickActionChipUiState =
            QuickActionChipUiState.Hidden(chipId = QuickActionChipId.AvControlsIndicator),
        val shareScreen: QuickActionChipUiState =
            QuickActionChipUiState.Hidden(chipId = QuickActionChipId.ShareScreenPrivacyIndicator),
        val assistant: QuickActionChipUiState =
            QuickActionChipUiState.Hidden(chipId = QuickActionChipId.AssistantIcon),
        val ime: QuickActionChipUiState =
            QuickActionChipUiState.Hidden(chipId = QuickActionChipId.ImeIndicator),
    )

    @AssistedFactory
    interface Factory {
        fun create(@DisplayId displayId: Int): StatusBarPopupChipsViewModel
    }
}
