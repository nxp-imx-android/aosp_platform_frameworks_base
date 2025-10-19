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

package com.android.systemui.statusbar.featurepods.assistant.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.assistant.domain.interactor.AssistantIconInteractor
import com.android.systemui.statusbar.featurepods.assistant.shared.model.AssistantIconSharedModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.HoverBehavior
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class AssistantIconViewModel
@AssistedInject
constructor(assistantIconInteractor: AssistantIconInteractor) :
    StatusBarPopupChipViewModel, ExclusiveActivatable() {
    private val hydrator: Hydrator = Hydrator("AssistantIconViewModel.hydrator")

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "AssistantIcon",
            initialValue = PopupChipModel.Hidden(PopupChipId.AssistantIcon),
            source =
                assistantIconInteractor.assistantIconSharedModel.map {
                    it.toPopupChipModel({ assistantIconInteractor.startAssistant() })
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

fun AssistantIconSharedModel.toPopupChipModel(startAssistant: () -> Unit): PopupChipModel {
    // Hide the icon if assistInfo is null or is not the configured package.
    return if (assistInfo == null || !isStatusBarAssistantPackage) {
        PopupChipModel.Hidden(PopupChipId.AssistantIcon)
    } else {
        // TODO(b/440281094): update with a proper description.
        val iconDrawable =
            Icon.Resource(resId = R.drawable.ic_assistant_icon, contentDescription = null)
        PopupChipModel.Shown(
            chipId = PopupChipId.AssistantIcon,
            icons = listOf(ChipIcon(icon = iconDrawable)),
            chipText = null,
            showPopup = startAssistant,
            hoverBehavior = HoverBehavior.None,
            contentDescription =
                ContentDescription.Resource(R.string.accessibility_status_bar_assistant_icon),
            isPopupShown = isAssistShown,
        )
    }
}
