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

package com.android.systemui.screencapture.record.largescreen.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.PreCaptureViewModel
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.ScreenCaptureRegion

/** Main component for the pre-capture UI. */
@Composable
fun PreCaptureUI(viewModel: PreCaptureViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentSize(Alignment.TopCenter)
                    .padding(top = 36.dp)
                    .zIndex(1f)
        ) {
            PreCaptureToolbar(
                viewModel = viewModel,
                expanded = true,
                onCloseClick = { viewModel.closeUI() },
            )
        }

        when (viewModel.captureRegion) {
            ScreenCaptureRegion.FULLSCREEN -> {
                Box(
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center).zIndex(0f)
                ) {
                    PrimaryButton(
                        icon =
                            loadIcon(
                                viewModel = viewModel,
                                resId = R.drawable.ic_screen_capture_camera,
                                contentDescription = null,
                            ),
                        text = stringResource(R.string.screen_capture_fullscreen_screenshot_button),
                        onClick = { viewModel.takeFullscreenScreenshot() },
                    )
                }
            }
            ScreenCaptureRegion.PARTIAL -> {
                // TODO(b/427541309) Set the initial width and height of the RegionBox based on the
                // viewmodel state.
                RegionBox(
                    initialWidth = 100.dp,
                    initialHeight = 100.dp,
                    onDragEnd = viewModel::onPartialRegionDragEnd,
                    drawableLoaderViewModel = viewModel,
                )
            }
            ScreenCaptureRegion.APP_WINDOW -> {}
        }
    }
}
