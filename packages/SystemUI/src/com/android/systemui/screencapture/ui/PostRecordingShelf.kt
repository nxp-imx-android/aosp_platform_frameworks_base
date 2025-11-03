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
package com.android.systemui.screencapture.ui

import android.graphics.drawable.Icon
import android.net.Uri
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.ActionButtonGroupItem
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.PostCaptureToastBar
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

class PostRecordingShelf
@AssistedInject
constructor(
    @Assisted private val uri: Uri,
    @Assisted private val thumbnail: Icon?,
    dialogFactory: SystemUIDialogFactory,
    private val viewModelFactory: PostRecordingViewModel.Factory,
) {
    private val visibleState = MutableTransitionState(false)
    private val dialog: SystemUIDialog =
        dialogFactory
            .create(
                theme = R.style.Theme_SystemUI_Dialog,
                dialogDelegate = EdgeToEdgeDialogDelegate(),
            ) {
                DialogContent(uri = uri, thumbnail = thumbnail)
            }
            .apply {
                setupWindow(window!!)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                setOnDismissListener { visibleState.targetState = false }
            }

    private fun setupWindow(window: Window) {
        window.attributes =
            window.attributes.apply {
                title = "PostRecordingShelf"
                gravity = Gravity.BOTTOM or Gravity.START
            }
        with(window) {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setWindowAnimations(-1)
        }
    }

    @Composable
    private fun DialogContent(uri: Uri, thumbnail: Icon?) {
        if (!visibleState.targetState && visibleState.isIdle) {
            SideEffect {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }

        LaunchedEffect(visibleState.targetState) {
            if (visibleState.targetState) {
                delay(DEFAULT_TIMEOUT)
                hide()
            }
        }

        val postRecordingViewModel =
            rememberViewModel("PostRecordingShelf#viewModel") { viewModelFactory.create(uri) }
        val actionButtonItems =
            listOf(
                ActionButtonGroupItem(
                    icon =
                        loadIcon(
                                viewModel = postRecordingViewModel,
                                resId = R.drawable.ic_screenshot_share,
                                contentDescription = null,
                            )
                            .value,
                    onClick = {},
                ),
                ActionButtonGroupItem(
                    icon =
                        loadIcon(
                                viewModel = postRecordingViewModel,
                                resId = R.drawable.ic_screen_capture_folder,
                                contentDescription = null,
                            )
                            .value,
                    onClick = {},
                ),
                ActionButtonGroupItem(
                    icon =
                        loadIcon(
                                viewModel = postRecordingViewModel,
                                resId = R.drawable.ic_screenshot_delete,
                                contentDescription = null,
                            )
                            .value,
                    onClick = {},
                ),
            )
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .clickable(onClick = { hide() }, indication = null, interactionSource = null),
            contentAlignment = Alignment.BottomStart,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter =
                    fadeIn(animationSpec = spring<Float>()) +
                        slideInHorizontally(
                            animationSpec = spring<IntOffset>(),
                            initialOffsetX = { -it },
                        ),
                exit =
                    fadeOut(animationSpec = spring<Float>()) +
                        slideOutHorizontally(
                            animationSpec = spring<IntOffset>(),
                            targetOffsetX = { -it },
                        ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PostRecordingThumbnail(
                        viewmodel = postRecordingViewModel,
                        preview = thumbnail?.bitmap?.asImageBitmap(),
                        modifier =
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .border(3.dp, MaterialTheme.colorScheme.surfaceVariant)
                                .width(190.dp)
                                .height(107.dp),
                    )
                    PostCaptureToastBar(
                        actionButtonGroup = actionButtonItems,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }

    fun show() {
        if (!dialog.isShowing) {
            visibleState.targetState = true
            dialog.show()
        }
    }

    fun hide() {
        if (dialog.isShowing) {
            visibleState.targetState = false
        }
    }

    @Composable
    private fun PostRecordingThumbnail(
        viewmodel: DrawableLoaderViewModel,
        preview: ImageBitmap?,
        modifier: Modifier = Modifier,
    ) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                LoadingIcon(
                    loadIcon(
                            viewModel = viewmodel,
                            resId = R.drawable.ic_screen_capture_movie,
                            contentDescription = null,
                        )
                        .value,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(uri: Uri, thumbnail: Icon?): PostRecordingShelf
    }

    companion object {
        private val DEFAULT_TIMEOUT = 6.seconds
    }
}
