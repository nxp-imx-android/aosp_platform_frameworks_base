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

package com.android.systemui.screencapture.record.camera.ui.viewmodel

import android.graphics.Region
import android.os.Build
import android.os.SystemProperties
import android.util.Size as AndroidSize
import android.view.Surface
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.core.graphics.toRegion
import com.android.internal.logging.UiEventLogger
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenCaptureCameraTransformationInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraInteractor
import com.android.systemui.screencapture.record.shared.model.ScreenRecordEvent
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.util.isEmpty
import com.android.systemui.util.kotlin.pairwiseBy
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Objects
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class ScreenCaptureCameraTransformationViewModel
@AssistedInject
constructor(
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    cameraInteractor: ScreenRecordCameraInteractor,
    private val transformationInteractor: ScreenCaptureCameraTransformationInteractor,
    private val uiEventLogger: UiEventLogger,
) :
    HydratedActivatable(),
    TransformableState by transformationInteractor.createTransformableState() {

    val shouldShowTouchBounds: Boolean =
        Build.IS_DEBUGGABLE && SystemProperties.getBoolean(SHOW_SELFIE_TOUCH_BOUNDS_PROPERTY, false)
    var debugTouchBounds: Region? by mutableStateOf(null)
        private set

    /**
     * Changes in this field indicate that the output of the [fillCameraInteractableRegion] will be
     * different from the last one
     */
    val fillCameraInteractableRegionIndicator: Any by derivedStateOf {
        if (transformableByTouchAnywhere) {
            uiBounds
        } else {
            Objects.hash(surfaceSubjectBoundsPath, uiBoundsRegion)
        }
    }

    val transformableByTouchAnywhere: Boolean by
        screenRecordingServiceInteractor.status.mapHydrate(
            traceName = "ScreenCaptureCameraTransformationViewModel#transformableByTouchAnywhere"
        ) {
            !it.isRecording
        }
    private val subjectSize by
        cameraInteractor.streamConfiguration.mapHydrate(
            traceName = "ScreenCaptureCameraTransformationViewModel#transformableByTouchAnywhere"
        ) {
            it?.outputStreamSize ?: AndroidSize(0, 0)
        }

    private var surfaceScreenBounds: Rect by mutableStateOf(Rect.Zero)

    // Transformation matrix to convert stuff from surface space (in our case surface space is just
    // slightly smaller than the screen one) to screen space
    private val surfaceToScreenMatrix: Matrix by derivedStateOf {
        Matrix().apply {
            val subjectSize = subjectSize
            val surfaceScreenBounds = surfaceScreenBounds
            if (!subjectSize.isEmpty() && !surfaceScreenBounds.isEmpty) {
                resetToPivotedTransform(
                    translationX = surfaceScreenBounds.left,
                    translationY = surfaceScreenBounds.top,
                    scaleX = surfaceScreenBounds.width / subjectSize.width,
                    scaleY = surfaceScreenBounds.height / subjectSize.height,
                )
            }
        }
    }
    private var uiBounds: Rect by mutableStateOf(Rect.Zero)
    private val uiBoundsRegion: Region by derivedStateOf { uiBounds.toAndroidRectF().toRegion() }
    private val cameraSubjectBounds: Path? by
        cameraInteractor.cameraSubjectBounds
            .map { it?.boundaryPath?.asComposePath() }
            .hydratedStateOf("ScreenCaptureCameraViewModel#cameraSubjectBounds", null)
    private val surfaceSubjectBoundsPath: Path by derivedStateOf {
        transformCameraSubjectBounds(cameraSubjectBounds)
    }

    /**
     * Transformation matrix calculated based on the [offsetX], [offsetY], [scale] and [rotation]
     * around the center of the [onSurfaceScreenBoundsUpdated] rect.
     */
    val surfaceTransformation: Matrix by derivedStateOf {
        // Pivot around the center of the bounds ignoring bounds position
        // (cameraScreenBounds.topLeft). This is because this transformation matrix should be
        // applied to the surface itself which is stretched inside this cameraScreenBounds. So it's
        // essentially always positioned at (0, 0)
        with(surfaceScreenBounds) {
            createTransformationMatrix(pivotX = width / 2, pivotY = height / 2)
        }
    }

    val offsetX: Float by transformationInteractor::offsetX
    val offsetY: Float by transformationInteractor::offsetY
    val scale: Float by transformationInteractor::scale
    val rotation: Float by transformationInteractor::rotation

    override suspend fun onActivated() {
        coroutineScope {
            snapshotFlow { isTransformInProgress }
                .onEach { transformationInteractor.isTransforming = it }
                .pairwiseBy { wasTransforming, isTransforming ->
                    if (wasTransforming && !isTransforming) {
                        if (screenRecordingServiceInteractor.status.value.isRecording) {
                            uiEventLogger.log(
                                ScreenRecordEvent.SCREEN_RECORD_SURFACE_ADJUSTED_MID_RECORDING
                            )
                        } else {
                            uiEventLogger.log(
                                ScreenRecordEvent.SCREEN_RECORD_SURFACE_ADJUSTED_PRE_RECORDING
                            )
                        }
                    }
                    false
                }
                .launchIn(this)
        }
    }

    /**
     * Notifies the ViewModel that the screen bounds of the [Surface] has changed.
     *
     * This is the size the [Surface] occupies on the screen (ie the layout size of the TextureView)
     */
    fun onSurfaceScreenBoundsUpdated(bounds: Rect) {
        surfaceScreenBounds = bounds
    }

    /**
     * Notifies the ViewModel that the bounds of the ui has changed. This is an active area for when
     * the [transformableByTouchAnywhere] is true.
     */
    fun onUiBoundsChanged(bounds: Rect) {
        uiBounds = bounds
    }

    /**
     * Fills the [outRegion] with the touchable bounds. Use [fillCameraInteractableRegionIndicator]
     * to get notified about the updates.
     */
    fun fillCameraInteractableRegion(outRegion: Region) {
        if (transformableByTouchAnywhere) {
            with(uiBounds) {
                outRegion.set(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            }
        } else {
            outRegion.setPath(surfaceSubjectBoundsPath.asAndroidPath(), uiBoundsRegion)
        }
        if (shouldShowTouchBounds) {
            debugTouchBounds = Region(outRegion)
        }
    }

    /**
     * Transforms [cameraSubjectBounds] received in the surface space (ie all points of the path are
     * positioned inside the (0, 0, outputStreamSize#width, outputStreamSize#height) rect) to a
     * transformed path in the screen space (ie in [surfaceScreenBounds])
     */
    private fun transformCameraSubjectBounds(cameraSubjectBounds: Path?): Path {
        // First we need translate the basis from surface to screen
        val cameraSubjectBoundsInScreenSpace =
            cameraSubjectBounds?.apply { transform(surfaceToScreenMatrix) }
        // Apply screen transformation to path
        return (cameraSubjectBoundsInScreenSpace ?: surfaceScreenBounds.toPath()).apply {
            // Pivot around the actual center of the bounds taking into account bounds position
            // because this applies to a figure that is positioned with an arbitrary offset
            transform(
                createTransformationMatrix(
                    pivotX = surfaceScreenBounds.center.x,
                    pivotY = surfaceScreenBounds.center.y,
                )
            )
        }
    }

    /** Creates a transformation matrix pivoting around ([pivotX], [pivotY]) */
    private fun createTransformationMatrix(pivotX: Float, pivotY: Float): Matrix =
        Matrix().apply {
            resetToPivotedTransform(
                translationX = offsetX,
                translationY = offsetY,
                pivotX = pivotX,
                pivotY = pivotY,
                rotationZ = rotation,
                scaleX = scale,
                scaleY = scale,
            )
        }

    private fun <T, R> StateFlow<T>.mapHydrate(traceName: String, mapping: (T) -> R): State<R> {
        return map(mapping).hydratedStateOf(traceName = traceName, initialValue = mapping(value))
    }

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureCameraTransformationViewModel
    }

    companion object {
        @VisibleForTesting
        val SHOW_SELFIE_TOUCH_BOUNDS_PROPERTY: String =
            "debug.sysui.screen_record_show_selfie_touch_bounds"
    }
}

private fun ScreenCaptureCameraTransformationInteractor.createTransformableState():
    TransformableState = TransformableState { _, zoomChange, panChange, rotationChange ->
    scale *= zoomChange
    rotation += rotationChange
    offsetX += panChange.x
    offsetY += panChange.y
}

private fun Rect.toPath(): Path = Path().apply { addRect(this@toPath, Path.Direction.Clockwise) }
