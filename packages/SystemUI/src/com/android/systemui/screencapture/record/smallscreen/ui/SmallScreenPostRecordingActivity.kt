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

package com.android.systemui.screencapture.record.smallscreen.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformOutlinedButton
import com.android.compose.theme.PlatformTheme
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingViewModel
import javax.inject.Inject

class SmallScreenPostRecordingActivity
@Inject
constructor(private val viewModelFactory: PostRecordingViewModel.Factory) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlatformTheme { Content() } }
    }

    @Composable
    private fun Content() {
        val viewModel =
            rememberViewModel("SmallScreenPostRecordingActivity#viewModel") {
                viewModelFactory.create(intent.data ?: error("Data URI is missing"))
            }

        var shouldShowSavedToast by rememberSaveable { mutableStateOf(true) }
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(shouldShowSavedToast) {
            if (!shouldShowSavedToast) return@LaunchedEffect
            shouldShowSavedToast = true
            snackbarHostState.showSnackbar(
                SnackbarVisualsWithIcon(
                    iconRes = R.drawable.ic_sync_saved_locally,
                    message = getString(R.string.screen_record_video_saved),
                )
            )
        }

        val shouldUseFlatBottomBar =
            booleanResource(R.bool.screen_record_post_recording_flat_bottom_bar)
        Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!shouldUseFlatBottomBar) {
                    Spacer(modifier = Modifier.size(50.dp))
                }
                Box(modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally)) {
                    // TODO(b/430553811): Add video player
                }
                Spacer(modifier = Modifier.size(32.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 24.dp).height(40.dp),
                ) {
                    val rowModifier = Modifier.weight(1f).fillMaxHeight()
                    PostRecordButton(
                        onClick = { viewModel.retake() },
                        drawableLoaderViewModel = viewModel,
                        iconRes = R.drawable.ic_arrow_back,
                        labelRes = R.string.screen_record_retake,
                        modifier = rowModifier,
                    )
                    PostRecordButton(
                        onClick = { viewModel.edit() },
                        drawableLoaderViewModel = viewModel,
                        iconRes = R.drawable.ic_edit_square,
                        labelRes = R.string.screen_record_edit,
                        modifier = rowModifier,
                    )
                    PostRecordButton(
                        onClick = { viewModel.delete() },
                        drawableLoaderViewModel = viewModel,
                        iconRes = R.drawable.ic_screenshot_delete,
                        labelRes = R.string.screen_record_delete,
                        modifier = rowModifier,
                    )
                    if (shouldUseFlatBottomBar) {
                        PrimaryButton(
                            text = stringResource(R.string.screenrecord_share_label),
                            onClick = { viewModel.share() },
                            modifier = rowModifier,
                        )
                    }
                }
                if (!shouldUseFlatBottomBar) {
                    PrimaryButton(
                        text = stringResource(R.string.screenrecord_share_label),
                        icon =
                            loadIcon(
                                viewModel,
                                R.drawable.ic_screenshot_share,
                                contentDescription = null,
                            ),
                        onClick = { viewModel.share() },
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .height(56.dp),
                    )
                }
            }
            TextButton(
                onClick = { finish() },
                modifier = Modifier.padding(horizontal = 12.dp).size(48.dp).align(Alignment.TopEnd),
            ) {
                LoadingIcon(
                    icon = loadIcon(viewModel, R.drawable.ic_close, null),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            ) { data ->
                PostRecordSnackbar(viewModel = viewModel, data = data, modifier = Modifier)
            }
        }
    }
}

@Composable
private fun PostRecordSnackbar(
    viewModel: DrawableLoaderViewModel,
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val visuals = data.visuals as? SnackbarVisualsWithIcon ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(percent = 50),
                )
                .padding(start = 12.dp, end = 20.dp)
                .height(48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.background(
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = CircleShape,
                    )
                    .size(24.dp),
        ) {
            LoadingIcon(
                icon = loadIcon(viewModel, visuals.iconRes, null),
                tint = MaterialTheme.colorScheme.inverseSurface,
                modifier = modifier.size(16.dp),
            )
        }
        Text(
            text = visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        if (visuals.actionLabel != null) {
            TextButton(onClick = data::performAction) {
                Text(
                    text = visuals.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

@Composable
private fun PostRecordButton(
    onClick: () -> Unit,
    @DrawableRes iconRes: Int,
    labelRes: Int,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    modifier: Modifier = Modifier,
) {
    PlatformOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
    ) {
        LoadingIcon(
            icon = loadIcon(drawableLoaderViewModel, iconRes, contentDescription = null),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
    }
}

private data class SnackbarVisualsWithIcon(
    override val message: String,
    @DrawableRes val iconRes: Int,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals
