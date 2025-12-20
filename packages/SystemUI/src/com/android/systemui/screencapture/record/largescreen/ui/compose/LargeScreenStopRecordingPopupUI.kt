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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.LargeScreenStopRecordingPopupViewModel
import com.android.systemui.statusbar.core.StatusBarForDesktop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LargeScreenStopRecordingPopupUI(viewModel: LargeScreenStopRecordingPopupViewModel) {

    // Controls the visibility of the popup, allowing for enter/exit animations.
    // Initial state is `false` with a target of `true` to ensure the enter animation runs on
    // first composition.
    val uiVisibilityState = remember { MutableTransitionState(false).apply { targetState = true } }

    // Wait for animation to finish before calling viewModel.dismiss()
    if (uiVisibilityState.isIdle && !uiVisibilityState.targetState) {
        SideEffect { viewModel.dismiss() }
    }

    AnimatedVisibility(
        visibleState = uiVisibilityState,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(50)),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment =
                if (StatusBarForDesktop.isEnabled) {
                    Alignment.End
                } else {
                    Alignment.Start
                },
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeContent.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 30.dp),
        ) {
            Surface(
                shape = FloatingToolbarDefaults.ContainerShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.height(64.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformIconButton(
                        onClick = { uiVisibilityState.targetState = false },
                        contentDescription =
                            stringResource(id = R.string.underlay_close_button_content_description),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        iconResource = R.drawable.ic_close,
                    )

                    Spacer(Modifier.width(12.dp))

                    val coroutineScope = rememberCoroutineScope()
                    var buttonJob: Job? by remember { mutableStateOf(null) }
                    StopRecordingButton(
                        onClick = {
                            if (buttonJob == null) {
                                buttonJob =
                                    coroutineScope.launch {
                                        viewModel.onStopButtonTapped()
                                        buttonJob = null
                                    }
                            }
                        },
                        viewModel = viewModel,
                        modifier = Modifier.height(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StopRecordingButton(
    viewModel: DrawableLoaderViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryButton(
        onClick = onClick,
        text = stringResource(R.string.screenrecord_stop_label),
        icon =
            loadIcon(viewModel = viewModel, resId = R.drawable.ic_stop, contentDescription = null)
                .value,
        contentPadding = PaddingValues(horizontal = 14.dp),
        iconPadding = 4.dp,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        modifier = modifier,
    )
}
