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

import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.view.Display
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.ActionButtonGroupItem
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.PostCaptureToastBar
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.PostRecordSnackbarDialogs
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
import kotlinx.coroutines.launch

class PostRecordingShelf
@AssistedInject
constructor(
    @Assisted private val uri: Uri,
    @Assisted private val thumbnail: Icon?,
    @Application private val context: Context,
    @Assisted private val display: Display,
    private val dialogFactory: SystemUIDialogFactory,
    private val viewModelFactory: PostRecordingViewModel.Factory,
    private val postRecordSnackbarDialogs: PostRecordSnackbarDialogs,
) {
    private val visibleState = MutableTransitionState(false)
    private val dialog: SystemUIDialog =
        dialogFactory
            .create(
                context.createWindowContext(display, DIALOG_WINDOW_TYPE, null),
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
            setType(DIALOG_WINDOW_TYPE)
            setWindowAnimations(-1)
        }
    }

    @Composable
    private fun DialogContent(uri: Uri, thumbnail: Icon?) {
        var isConfirmDeletionDialogShowing by remember { mutableStateOf(false) }
        if (!visibleState.targetState && visibleState.isIdle) {
            SideEffect {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }

        LaunchedEffect(visibleState.targetState, isConfirmDeletionDialogShowing) {
            if (visibleState.targetState && !isConfirmDeletionDialogShowing) {
                delay(DEFAULT_TIMEOUT)
                hide()
            }
        }

        val coroutineScope = rememberCoroutineScope()
        val postRecordingViewModel =
            rememberViewModel("PostRecordingShelf#viewModel") {
                viewModelFactory.create(uri, display.displayId)
            }
        val parentUri = postRecordingViewModel.parentUri
        val shareIcon =
            loadIcon(
                    viewModel = postRecordingViewModel,
                    resId = R.drawable.ic_screenshot_share,
                    contentDescription = null,
                )
                .value
        val folderIcon =
            loadIcon(
                    viewModel = postRecordingViewModel,
                    resId = R.drawable.ic_screen_capture_folder,
                    contentDescription = null,
                )
                .value
        val deleteIcon =
            loadIcon(
                    viewModel = postRecordingViewModel,
                    resId = R.drawable.ic_screenshot_delete,
                    contentDescription = null,
                )
                .value
        val actionButtonItems =
            remember(parentUri) {
                buildList {
                    add(
                        ActionButtonGroupItem(
                            icon = shareIcon,
                            onClick = {
                                postRecordingViewModel.share()
                                hide()
                            },
                        )
                    )

                    if (parentUri != null) {
                        add(
                            ActionButtonGroupItem(
                                icon = folderIcon,
                                onClick = {
                                    coroutineScope.launch {
                                        postRecordingViewModel.openInFolder()
                                        hide()
                                    }
                                },
                            )
                        )
                    }

                    add(
                        ActionButtonGroupItem(
                            icon = deleteIcon,
                            onClick = {
                                coroutineScope.launch {
                                    isConfirmDeletionDialogShowing = true
                                    if (
                                        postRecordingConfirmDeletion(
                                            dialogFactory,
                                            context,
                                            postRecordingViewModel,
                                        )
                                    ) {
                                        hide()
                                        postRecordSnackbarDialogs.showVideoDeleted(
                                            postRecordingViewModel.videoUri
                                        )
                                    }
                                    isConfirmDeletionDialogShowing = false
                                }
                            },
                        )
                    )
                }
            }

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .clickable(onClick = { hide() }, indication = null, interactionSource = null)
                    .safeDrawingPadding(),
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
                                .height(107.dp)
                                .clickable {
                                    postRecordingViewModel.view()
                                    hide()
                                },
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
        fun create(uri: Uri, thumbnail: Icon?, display: Display): PostRecordingShelf
    }

    companion object {
        private val DEFAULT_TIMEOUT = 6.seconds
        private val DIALOG_WINDOW_TYPE = WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL
    }
}
