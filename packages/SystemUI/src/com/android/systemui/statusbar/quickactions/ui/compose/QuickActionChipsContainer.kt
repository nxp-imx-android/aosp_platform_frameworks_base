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

package com.android.systemui.statusbar.quickactions.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.quickactions.popups.ui.compose.StatusBarPopup
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState

/** Container view that holds all right hand side chips in the status bar. */
@Composable
fun QuickActionChipsContainer(
    chips: List<QuickActionChipUiState.PopupChip>,
    modifier: Modifier = Modifier,
) {

    //    TODO(b/385353140): Add padding and spacing for this container according to UX specs.
    Box {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            chips.forEach { chip ->
                QuickActionChip(
                    isSelected = chip.isPopupShown,
                    icons = chip.icons,
                    chipContent = chip.chipContent,
                    colors = chip.colors,
                    contentDescription = chip.contentDescription,
                    onClick = { chip.showPopup() },
                )
                if (chip.isPopupShown) {
                    if (chip.popupViewModelFactory != null) {
                        val popupViewModel =
                            rememberViewModel("StatusBarPopupViewModel-${chip.chipId}") {
                                chip.popupViewModelFactory.create()
                            }
                        StatusBarPopup(popupViewModel = popupViewModel, onDismiss = chip.hidePopup)
                    }
                }
            }
        }
    }
}
