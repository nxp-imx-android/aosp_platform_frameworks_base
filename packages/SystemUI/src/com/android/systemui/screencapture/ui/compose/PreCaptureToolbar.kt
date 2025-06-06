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

package com.android.systemui.screencapture.ui.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.res.R

/** TODO(b/422855266): Inject ViewModel */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun PreCaptureToolbar(expanded: Boolean, onCloseClick: () -> Unit, modifier: Modifier = Modifier) {
    // TODO(b/422855266): Preload icons in the view model to avoid loading icons in UI thread and
    // improve performance
    val screenshotIcon =
        IconModel.Resource(res = R.drawable.ic_screen_capture_camera, contentDescription = null)
    val screenRecordIcon =
        IconModel.Resource(res = R.drawable.ic_screenrecord, contentDescription = null)
    val screenshotFullscreenIcon =
        IconModel.Resource(res = R.drawable.ic_screen_capture_fullscreen, contentDescription = null)
    val screenshotRegionIcon =
        IconModel.Resource(res = R.drawable.ic_screen_capture_region, contentDescription = null)
    val screenshotWindowIcon =
        IconModel.Resource(res = R.drawable.ic_screen_capture_window, contentDescription = null)
    val moreOptionsIcon =
        IconModel.Resource(res = R.drawable.ic_more_vert, contentDescription = null)

    val captureRegionButtonItems =
        listOf(
            RadioButtonGroupItem(icon = screenshotWindowIcon),
            RadioButtonGroupItem(icon = screenshotRegionIcon),
            RadioButtonGroupItem(icon = screenshotFullscreenIcon),
        )

    val captureTypeButtonItems =
        listOf(
            RadioButtonGroupItem(
                icon = screenRecordIcon,
                label = stringResource(id = R.string.screen_capture_toolbar_record_button),
            ),
            RadioButtonGroupItem(
                icon = screenshotIcon,
                label = stringResource(id = R.string.screen_capture_toolbar_capture_button),
            ),
        )

    Toolbar(expanded = expanded, onCloseClick = onCloseClick, modifier = modifier) {
        Row {
            IconToggleButton(
                checked = false,
                onCheckedChange = {},
                shape = IconButtonDefaults.smallSquareShape,
            ) {
                Icon(icon = moreOptionsIcon)
            }

            Spacer(Modifier.size(8.dp))

            // TODO(b/422855266): Use state from ViewModel for selected index
            RadioButtonGroup(
                items = captureRegionButtonItems,
                selectedIndex = 0,
                onSelect = { _ -> },
            )

            Spacer(Modifier.size(16.dp))

            RadioButtonGroup(items = captureTypeButtonItems, selectedIndex = 0, onSelect = { _ -> })
        }
    }
}
