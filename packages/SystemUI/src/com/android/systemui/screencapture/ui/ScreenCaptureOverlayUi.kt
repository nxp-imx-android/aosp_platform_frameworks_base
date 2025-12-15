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
import android.graphics.Region
import android.view.ViewTreeObserver.InternalInsetsInfo
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAll
import androidx.core.graphics.toRegion
import com.android.internal.R as internalR
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.record.camera.ui.viewmodel.ScreenCaptureCameraTransformationViewModel
import com.android.systemui.screencapture.record.camera.ui.viewmodel.ScreenCaptureCameraViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import com.android.systemui.util.view.listenToComputeInternalInsets
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
    private val cameraTransformationViewModel: ScreenCaptureCameraTransformationViewModel.Factory,
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
            setWindowAnimations(internalR.style.Animation_Toast)
        }
    }

    @Composable
    private fun DialogContent(window: Window) {
        val drawingTouchableRegion = remember { Region.obtain() }
        val cameraTouchableRegion = remember { Region.obtain() }
        LaunchedEffect(window.decorView.viewTreeObserver) {
            window.decorView.viewTreeObserver.listenToComputeInternalInsets {
                setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
                touchableRegion.op(drawingTouchableRegion, Region.Op.UNION)
                touchableRegion.op(cameraTouchableRegion, Region.Op.UNION)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Camera(outTouchableRegion = cameraTouchableRegion, modifier = Modifier)
        }
    }

    @Composable
    private fun Camera(outTouchableRegion: Region, modifier: Modifier = Modifier) {
        val display = LocalContext.current.display
        val viewModel =
            rememberViewModel("ScreenCaptureCameraViewModel") { cameraViewModelFactory.create() }
        LaunchedEffect(display, viewModel) { viewModel.onDisplayReady(display) }
        val surfaceSize =
            viewModel.surfaceSize?.let { IntSize(width = it.width, height = it.height) }
        val shouldShowCamera = viewModel.shouldShowCamera
        ConditionalLaunchedEffect(shouldShowCamera != true || surfaceSize == null) {
            outTouchableRegion.setEmpty()
        }
        if (shouldShowCamera != null && surfaceSize != null) {
            val transformationViewModel =
                rememberViewModel("ScreenCaptureCameraTransformationViewModel") {
                    cameraTransformationViewModel.create()
                }
            val state: TransformableState =
                rememberTransformableState { zoomChange, offsetChange, rotationChange ->
                    with(transformationViewModel) {
                        changeTransformation(
                            offsetChange = offsetChange,
                            zoomChange = zoomChange,
                            rotationChange = rotationChange,
                        )
                        fillRegion(outTouchableRegion)
                    }
                }

            Box(contentAlignment = Alignment.BottomCenter, modifier = modifier.fillMaxSize()) {
                val surfaceAlpha by
                    animateFloatAsState(
                        targetValue = if (shouldShowCamera) 1f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    )
                AndroidEmbeddedExternalSurface(
                    surfaceSize = surfaceSize,
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(surfaceSize.height.toFloat() / surfaceSize.width)
                            .onGloballyPositioned { layoutCoordinates ->
                                transformationViewModel.bounds =
                                    layoutCoordinates.boundsInWindow(false)
                                transformationViewModel.fillRegion(outTouchableRegion)
                            }
                            .graphicsLayer {
                                alpha = surfaceAlpha
                                with(transformationViewModel) {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                    rotationZ = rotation
                                }
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        with(awaitPointerEvent()) {
                                            when {
                                                type == PointerEventType.Press ->
                                                    transformationViewModel
                                                        .onTransformationStarted()
                                                changes.fastAll { it.changedToUp() } ->
                                                    transformationViewModel.onTransformationEnded()
                                            }
                                        }
                                    }
                                }
                            }
                            .transformable(state)
                            .clickable { viewModel.onSurfaceClicked() },
                ) {
                    onSurface { surface, width, height ->
                        viewModel.onSurfaceReady(surface = surface, width = width, height = height)
                        surface.onDestroyed { viewModel.onSurfaceDestroyed() }
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

@Composable
private fun ConditionalLaunchedEffect(condition: Boolean, action: suspend () -> Unit) {
    LaunchedEffect(condition) {
        if (condition) {
            action()
        }
    }
}

private fun ScreenCaptureCameraTransformationViewModel.fillRegion(region: Region) {
    region.setPath(transformedBounds.asAndroidPath(), bounds.toAndroidRectF().toRegion())
}
