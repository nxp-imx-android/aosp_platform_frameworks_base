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

package com.android.systemui.screencapture.sharescreen.largescreen.ui.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.RadioButtonGroup
import com.android.systemui.screencapture.common.ui.compose.RadioButtonGroupItem
import com.android.systemui.screencapture.common.ui.compose.Toolbar
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel.PreShareViewModel
import com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel.ScreenShareTarget

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun PreShareToolbar(
    viewModel: PreShareViewModel,
    expanded: Boolean,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shareTargetButtonItems =
        listOf(
            RadioButtonGroupItem(
                icon =
                    loadIcon(
                        viewModel = viewModel,
                        resId = R.drawable.ic_screen_capture_tab,
                        contentDescription = null,
                    ),
                isSelected = viewModel.selectedScreenShareTarget == ScreenShareTarget.TAB,
                onClick = { viewModel.onTargetSelected(ScreenShareTarget.TAB) },
            ),
            RadioButtonGroupItem(
                icon =
                    loadIcon(
                        viewModel = viewModel,
                        resId = R.drawable.ic_screen_capture_window,
                        contentDescription = null,
                    ),
                isSelected = viewModel.selectedScreenShareTarget == ScreenShareTarget.APP_WINDOW,
                onClick = { viewModel.onTargetSelected(ScreenShareTarget.APP_WINDOW) },
            ),
        )

    Toolbar(expanded = expanded, onCloseClick = onCloseClick, modifier = modifier) {
        Row {
            RadioButtonGroup(items = shareTargetButtonItems)

            Spacer(Modifier.size(16.dp))

            PrimaryButton(
                icon =
                    loadIcon(
                        viewModel = viewModel,
                        resId = R.drawable.ic_present_to_all,
                        ContentDescription.Resource(R.string.screen_share_toolbar_share_button),
                    ),
                text = stringResource(R.string.screen_share_toolbar_share_button),
                onClick = {},
            )
        }
    }
}
