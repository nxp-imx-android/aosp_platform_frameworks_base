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

package com.android.systemui.statusbar.quickactions.assistant.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.assistant.domain.interactor.AssistantIconInteractor
import com.android.systemui.statusbar.quickactions.assistant.shared.model.AssistantIconSharedModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.quickactions.ui.viewmodel.ChipIcon
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipId
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class AssistantIconViewModel
@AssistedInject
constructor(assistantIconInteractor: AssistantIconInteractor) :
    StatusBarPopupChipViewModel, ExclusiveActivatable() {
    private val hydrator: Hydrator = Hydrator("AssistantIconViewModel.hydrator")

    override val chip: QuickActionChipUiState by
        hydrator.hydratedStateOf(
            traceName = "AssistantIcon",
            initialValue = QuickActionChipUiState.Hidden(QuickActionChipId.AssistantIcon),
            source =
                assistantIconInteractor.assistantIconSharedModel.map {
                    it.toPopupChipModel { context ->
                        assistantIconInteractor.startAssistant(context)
                    }
                },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(): AssistantIconViewModel
    }
}

fun AssistantIconSharedModel.toPopupChipModel(
    startAssistant: (Context) -> Unit
): QuickActionChipUiState {
    // Hide the icon if assistInfo is null or is not the configured package.
    return if (assistInfo == null || !isStatusBarAssistantPackage) {
        QuickActionChipUiState.Hidden(QuickActionChipId.AssistantIcon)
    } else {
        // TODO(b/440281094): update with a proper description.
        val iconDrawable =
            Icon.Resource(resId = R.drawable.ic_assistant_icon, contentDescription = null)
        QuickActionChipUiState.PopupChip(
            chipId = QuickActionChipId.AssistantIcon,
            icons = listOf(ChipIcon(icon = iconDrawable)),
            chipContent = null,
            showPopup = startAssistant,
            contentDescription =
                ContentDescription.Resource(R.string.accessibility_status_bar_assistant_icon),
            isPopupShown = isAssistShown,
        )
    }
}
