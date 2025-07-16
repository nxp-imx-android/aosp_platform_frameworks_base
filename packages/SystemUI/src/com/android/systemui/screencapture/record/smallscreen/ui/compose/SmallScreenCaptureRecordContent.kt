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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.RecordDetailsPopupType
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.SmallScreenCaptureRecordViewModel
import javax.inject.Inject

class SmallScreenCaptureRecordContent
@Inject
constructor(private val viewModelFactory: SmallScreenCaptureRecordViewModel.Factory) :
    ScreenCaptureContent {

    @Composable
    override fun Content() {
        val viewModel =
            rememberViewModel("SmallScreenCaptureRecordContent#viewModel") {
                viewModelFactory.create()
            }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                // TODO: implement toolbar
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                modifier =
                    Modifier.animateContentSize()
                        .widthIn(max = 352.dp)
                        .padding(start = 30.dp, end = 30.dp),
            ) {
                AnimatedContent(
                    targetState = viewModel.detailsPopup,
                    contentAlignment = Alignment.Center,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { currentPopup ->
                    when (currentPopup) {
                        RecordDetailsPopupType.Empty -> {
                            /* show nothing */
                        }
                        RecordDetailsPopupType.Settings -> RecordDetailsSettings()
                        RecordDetailsPopupType.AppSelector ->
                            RecordDetailsAppSelector(
                                viewModel = viewModel.recordDetailsAppSelectorViewModel,
                                onBackPressed = { viewModel.showSettings() },
                            )
                        RecordDetailsPopupType.MarkupColorSelector ->
                            RecordDetailsMarkupColorSelector()
                    }
                }
            }
        }
    }
}
