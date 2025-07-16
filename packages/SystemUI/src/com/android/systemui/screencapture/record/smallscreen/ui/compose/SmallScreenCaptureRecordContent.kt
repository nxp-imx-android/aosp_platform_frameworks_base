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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.RecordDetailsPopupType
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.SmallScreenCaptureRecordViewModel
import javax.inject.Inject

private val elevation = 6.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeContent.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 30.dp),
        ) {
            // TODO(b/428686600) use Toolbar shared with the large screen
            Surface(
                shape = FloatingToolbarDefaults.ContainerShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = elevation,
            ) {
                Row(
                    modifier = Modifier.height(64.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformIconButton(
                        onClick = { viewModel.dismiss() },
                        contentDescription =
                            stringResource(id = R.string.underlay_close_button_content_description),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        iconResource = R.drawable.ic_close,
                    )
                    Spacer(Modifier.width(12.dp))
                    PrimaryButton(
                        onClick = { viewModel.startRecording() },
                        text = stringResource(R.string.screen_capture_toolbar_record_button),
                        icon =
                            loadIcon(
                                viewModel = viewModel,
                                resId = R.drawable.ic_screenrecord,
                                contentDescription = null,
                            ),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        iconPadding = 4.dp,
                        modifier = Modifier.height(40.dp),
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = elevation,
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
