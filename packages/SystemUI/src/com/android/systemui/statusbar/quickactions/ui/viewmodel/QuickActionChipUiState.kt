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

import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.statusbar.quickactions.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupViewModel

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

/** Model for an optionally clickable icon that is displayed on the chip. */
data class ChipIcon(val icon: Icon, val onClick: (() -> Unit)? = null)

/** Defines the behavior of the chip when hovered over. */
sealed interface HoverBehavior {
    /** No specific hover behavior. The default icon will be shown. */
    data object None : HoverBehavior

    /** Shows a list of buttons on hover with the given [icons] */
    data class Buttons(val icons: List<ChipIcon>) : HoverBehavior
}

/** Model for individual status bar quick action chips. */
sealed class QuickActionChipUiState {
    abstract val logName: String
    abstract val chipId: QuickActionChipId

    data class Hidden(override val chipId: QuickActionChipId, val shouldAnimate: Boolean = true) :
        QuickActionChipUiState() {
        override val logName = "Hidden(id=$chipId, anim=$shouldAnimate)"
    }

    data class Shown(
        override val chipId: QuickActionChipId,
        /** Icons shown on the chip when no specific hover behavior. */
        val icons: List<ChipIcon>,
        val chipText: String?,
        /** Determines the colors used for the chip. Defaults to system themed colors. */
        val colors: ColorsModel = ColorsModel.SystemTheme,
        val isPopupShown: Boolean = false,
        val showPopup: () -> Unit = {},
        val hidePopup: () -> Unit = {},
        val hoverBehavior: HoverBehavior = HoverBehavior.None,
        val contentDescription: ContentDescription? = null,
        val popupViewModelFactory: StatusBarPopupViewModel.Factory? = null,
    ) : QuickActionChipUiState() {
        override val logName = "Shown(id=$chipId, toggled=$isPopupShown)"
    }
}
