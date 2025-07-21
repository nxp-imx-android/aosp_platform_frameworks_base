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

package com.android.systemui.screencapture.record.smallscreen.ui.compose

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.ui.viewmodel.ScreenCaptureRecordParametersViewModel

@Composable
fun RecordDetailsSettings(
    viewModel: ScreenCaptureRecordParametersViewModel,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()) {
            RichSwitch(
                icon =
                    loadIcon(
                        viewModel = drawableLoaderViewModel,
                        resId = R.drawable.ic_phone_expressive,
                        contentDescription = null,
                    ),
                label = stringResource(R.string.screen_record_record_device_audio_label),
                checked = viewModel.shouldRecordDevice,
                onCheckedChange = { viewModel.shouldRecordDevice = it },
                modifier = Modifier,
            )
            RichSwitch(
                icon =
                    loadIcon(
                        viewModel = drawableLoaderViewModel,
                        resId = R.drawable.ic_mic_expressive,
                        contentDescription = null,
                    ),
                label = stringResource(R.string.screen_record_record_microphone_label),
                checked = viewModel.shouldRecordMicrophone,
                onCheckedChange = { viewModel.shouldRecordMicrophone = it },
                modifier = Modifier,
            )
            RichSwitch(
                icon =
                    loadIcon(
                        viewModel = drawableLoaderViewModel,
                        resId = R.drawable.ic_touch_expressive,
                        contentDescription = null,
                    ),
                label = stringResource(R.string.screen_record_should_show_touches_label),
                checked = viewModel.shouldShowTaps == true,
                onCheckedChange = { viewModel.setShouldShowTaps(it) },
                modifier = Modifier,
            )
        }
    }
}

@Composable
private fun RichSwitch(
    icon: IconModel?,
    label: String,
    checked: Boolean,
    onCheckedChange: (isChecked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier.height(64.dp).padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
    ) {
        LoadingIcon(icon = icon, modifier = Modifier.size(40.dp).padding(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 8.dp).weight(1f).basicMarquee(),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
