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
import android.graphics.PixelFormat
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.ink.authoring.compose.InProgressStrokes
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.record.camera.ui.viewmodel.ScreenCaptureCameraViewModel
import com.android.systemui.screencapture.record.markup.ui.viewmodel.ScreenCaptureMarkupOverlayViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@ScreenCaptureScope
class ScreenCaptureOverlayUi
@Inject
constructor(
    @Application context: Context,
    dialogFactory: SystemUIDialogFactory,
    private val cameraViewModelFactory: ScreenCaptureCameraViewModel.Factory,
    private val markupViewModelFactory: ScreenCaptureMarkupOverlayViewModel.Factory,
) {

    private val dialog =
        dialogFactory
            .create(
                context = context,
                theme = R.style.Theme_SystemUI_Dialog_ScreenCapture,
                dialogDelegate = EdgeToEdgeDialogDelegate(),
                dismissOnDeviceLock = false,
            ) {
                DialogContent(it.window!!)
            }
            .apply {
                setupWindow(window!!)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }

    private fun setupWindow(window: Window) {
        window.attributes =
            window.attributes.apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                title = "ScreenCaptureOverlayUi"
                format = PixelFormat.TRANSLUCENT
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            }
        with(window) {
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setWindowAnimations(-1)
        }
    }

    @Composable
    private fun DialogContent(window: Window) {
        Box(modifier = Modifier.fillMaxSize()) {
            DrawingView(modifier = Modifier)
            Camera(modifier = Modifier)
        }
    }

    @Composable
    private fun DrawingView(modifier: Modifier = Modifier) {
        val viewModel =
            rememberViewModel("ScreenCaptureMarkupOverlayViewModel") {
                markupViewModelFactory.create()
            }
        AnimatedVisibility(
            visible = viewModel.shouldShowMarkup,
            modifier = modifier.fillMaxSize(),
        ) {
            InProgressStrokes(
                defaultBrush = null,
                nextBrush = { viewModel.brush },
                onStrokesFinished = { /* do nothing */ },
            )
        }
    }

    @Composable
    private fun Camera(modifier: Modifier) {
        val viewModel =
            rememberViewModel("ScreenCaptureCameraViewModel") { cameraViewModelFactory.create() }
        val surfaceSize =
            viewModel.surfaceSize?.let { IntSize(width = it.width, height = it.height) }
        val shouldShowCamera = viewModel.shouldShowCamera
        if (shouldShowCamera != null && surfaceSize != null) {
            AnimatedVisibility(shouldShowCamera) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val state: TransformableState =
                    rememberTransformableState { zoomChange, offsetChange, rotationChange ->
                        scale *= zoomChange
                        offset += offsetChange
                    }
                Box(contentAlignment = Alignment.BottomCenter, modifier = modifier.fillMaxSize()) {
                    AndroidEmbeddedExternalSurface(
                        surfaceSize = surfaceSize,
                        modifier =
                            Modifier.fillMaxWidth()
                                .aspectRatio(surfaceSize.width.toFloat() / surfaceSize.height)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                                .transformable(state)
                                .clickable { viewModel.onSurfaceClicked() },
                    ) {
                        onSurface { surface, width, height ->
                            viewModel.onSurfaceReady(
                                surface = surface,
                                width = width,
                                height = height,
                            )
                            surface.onDestroyed { viewModel.onSurfaceDestroyed() }
                        }
                    }
                }
            }
        }
    }

    /**
     * Shows the UI and suspends until it's is dismissed. Cancelling the suspension dismisses the UI
     */
    suspend fun show(): Unit = suspendCancellableCoroutine { invocation ->
        dialog.setOnDismissListener {
            if (invocation.isActive) {
                invocation.resume(Unit)
            }
        }
        dialog.show()
        invocation.invokeOnCancellation { hide() }
    }

    private fun hide() {
        dialog.dismissWithoutAnimation()
    }
}
