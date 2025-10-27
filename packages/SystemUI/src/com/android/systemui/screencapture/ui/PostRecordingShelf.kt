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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.ActionButtonGroupItem
import com.android.systemui.screencapture.common.ui.compose.PostCaptureToastBar
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class PostRecordingShelf
@AssistedInject
constructor(
    @Assisted private val uri: Uri,
    @Assisted private val thumbnail: Icon?,
    dialogFactory: SystemUIDialogFactory,
    private val viewModelFactory: PostRecordingViewModel.Factory,
) {
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
                setCancelable(true)
                setCanceledOnTouchOutside(true)
            }

    private fun setupWindow(window: Window) {
        window.attributes =
            window.attributes.apply {
                title = "PostRecordingShelf"
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM or Gravity.START
            }
        with(window) {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
    }

    @Composable
    private fun DialogContent(uri: Uri, thumbnail: Icon?) {
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
        Column(modifier = Modifier.padding(16.dp)) {
            PostRecordingThumbnail(
                preview = thumbnail?.bitmap?.asImageBitmap(),
                modifier =
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .padding(3.dp)
                        .width(190.dp)
                        .height(106.875.dp),
            )
            PostCaptureToastBar(
                actionButtonGroup = actionButtonItems,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }

    fun show() {
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    fun hide() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    @Composable
    private fun PostRecordingThumbnail(preview: ImageBitmap?, modifier: Modifier = Modifier) {
        Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(uri: Uri, thumbnail: Icon?): PostRecordingShelf
    }
}
