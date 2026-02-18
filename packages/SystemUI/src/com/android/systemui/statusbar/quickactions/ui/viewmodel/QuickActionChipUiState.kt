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

package com.android.systemui.statusbar.quickactions.ui.viewmodel

import android.content.Context
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupViewModel
import com.android.systemui.statusbar.quickactions.ui.compose.ChipColors

/**
 * Ids used to track different types of popup chips. Will be used to ensure only one chip is
 * displaying its popup at a time.
 */
sealed class QuickActionChipId(val value: String) {
    data object MediaControl : QuickActionChipId("MediaControl")

    data object AvControlsIndicator : QuickActionChipId("AvControlsIndicator")

    data object ShareScreenPrivacyIndicator : QuickActionChipId("ShareScreenPrivacyIndicator")

    data object AssistantIcon : QuickActionChipId("AssistantIcon")

    data object ImeIndicator : QuickActionChipId("ImeIndicator")
}

/**
 * Model for an optionally clickable icon that is displayed on the chip.
 *
 * @param isHighlighted shows a highlighted background around this icon
 */
data class ChipIcon(
    val icon: Icon,
    val onClick: (() -> Unit)? = null,
    val isHighlighted: Boolean = false,
)

/** Content displayed to the right of the popup chip icons */
sealed class ChipContent {
    data class Text(val text: String) : ChipContent()

    data class SideIcon(val icon: Icon) : ChipContent()
}

/** Model for individual status bar quick action chips. */
sealed class QuickActionChipUiState {
    abstract val logName: String
    abstract val chipId: QuickActionChipId

    data class Hidden(override val chipId: QuickActionChipId, val shouldAnimate: Boolean = true) :
        QuickActionChipUiState() {
        override val logName = "Hidden(id=$chipId, anim=$shouldAnimate)"
    }

    data class PopupChip(
        override val chipId: QuickActionChipId,
        /** Icons shown on the chip when no specific hover behavior. */
        val icons: List<ChipIcon>,
        val chipContent: ChipContent? = null,
        /** Determines the colors used for the chip. Defaults to system themed colors. */
        val colors: ChipColors = ChipColors.SystemTheme,
        val isPopupShown: Boolean = false,
        val showPopup: (Context) -> Unit = {},
        val hidePopup: () -> Unit = {},
        val contentDescription: ContentDescription? = null,
        val popupViewModelFactory: StatusBarPopupViewModel.Factory? = null,
    ) : QuickActionChipUiState() {
        override val logName = "Shown(id=$chipId, toggled=$isPopupShown)"
    }
}
