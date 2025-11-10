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
import android.graphics.Rect
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.ui.viewmodel.ScreenCaptureCameraViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.sin
import kotlinx.coroutines.suspendCancellableCoroutine

@ScreenCaptureScope
class ScreenCaptureOverlayUi
@Inject
constructor(
    @Application context: Context,
    dialogFactory: SystemUIDialogFactory,
    private val cameraViewModelFactory: ScreenCaptureCameraViewModel.Factory,
) {

    private val dialog =
        dialogFactory
            .create(
                context = context,
                theme = R.style.Theme_SystemUI_Dialog_ScreenCapture,
                dialogDelegate = EdgeToEdgeDialogDelegate(),
                dismissOnDeviceLock = false,
            ) { dialog ->
                DialogContent(dialog.window!!)
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
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f))) {
            Camera(modifier = Modifier)
        }
    }

    @Composable
    private fun Camera(modifier: Modifier) {
        val viewModel =
            rememberViewModel("ScreenCaptureCameraViewModel") { cameraViewModelFactory.create() }
        var scale by remember { mutableFloatStateOf(1f) }
        var rotation by remember { mutableFloatStateOf(0f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
            scale *= zoomChange
            rotation += rotationChange
            offset += offsetChange
        }

        Box(modifier = modifier.fillMaxSize().transformable(state = state)) {
            // TODO(b/441486122): Wire the surface to the ViewModel for the actual output from the
            // service
            AndroidEmbeddedExternalSurface(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = offset.x
                            translationY = offset.y
                            scaleX = scale
                            scaleY = scale
                            rotationZ = rotation
                        }
                        .clickable { viewModel.onSurfaceClicked() }
                        .aspectRatio(2f)
            ) {
                onSurface { surface, width, height ->
                    viewModel.onSurfaceReady(surface)
                    var w = width
                    var h = height
                    // Initial draw to avoid a black frame
                    surface.lockCanvas(Rect(0, 0, w, h)).apply {
                        drawColor(Color.Yellow.toArgb())
                        surface.unlockCanvasAndPost(this)
                    } // React to surface dimension changes
                    surface.onChanged { newWidth, newHeight ->
                        w = newWidth
                        h = newHeight
                    } // Cleanup if needed
                    surface.onDestroyed { viewModel.onSurfaceDestroyed() }
                    // Render loop, automatically cancelled on surface destruction
                    while (true) {
                        withFrameNanos { time ->
                            surface.lockCanvas(Rect(0, 0, w, h)).apply {
                                val timeMs = time / 1_000_000L
                                val t = 0.5f + 0.5f * sin(timeMs / 1_000.0f)
                                drawColor(
                                    androidx.compose.ui.graphics
                                        .lerp(Color.Yellow, Color.Red, t)
                                        .toArgb()
                                )
                                surface.unlockCanvasAndPost(this)
                            }
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
