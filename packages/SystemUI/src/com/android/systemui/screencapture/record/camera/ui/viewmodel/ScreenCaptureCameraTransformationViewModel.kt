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
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.core.graphics.toRegion
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenCaptureCameraTransformationInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraInteractor
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

class ScreenCaptureCameraTransformationViewModel
@AssistedInject
constructor(
    screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    cameraInteractor: ScreenRecordCameraInteractor,
    private val transformationInteractor: ScreenCaptureCameraTransformationInteractor,
) : HydratedActivatable() {

    val transformableByTouchAnywhere: Boolean by
        screenRecordingServiceInteractor.status
            .map { it.transformableByTouchAnywhere() }
            .hydratedStateOf(
                "ScreenCaptureCameraTransformationViewModel#transformableByTouchAnywhere",
                screenRecordingServiceInteractor.status.value.transformableByTouchAnywhere(),
            )

    private var cameraScreenBounds: Rect = Rect.Zero
    private val cameraSubjectBounds: Path by
        cameraInteractor.cameraSubjectBounds
            .mapNotNull { it?.boundaryPath?.asComposePath() ?: cameraScreenBounds.toPath() }
            .onEach { recalculateTransformation(it) }
            .hydratedStateOf(
                "ScreenCaptureCameraViewModel#cameraSubjectBounds",
                cameraScreenBounds.toPath(),
            )

    val offsetX: Float by transformationInteractor::offsetX
    val offsetY: Float by transformationInteractor::offsetY
    val scale: Float by transformationInteractor::scale
    val rotation: Float by transformationInteractor::rotation

    val state: TransformableState = TransformableState { _, zoomChange, panChange, rotationChange ->
        changeTransformation(
            offsetChange = panChange,
            zoomChange = zoomChange,
            rotationChange = rotationChange,
        )
    }

    private val transformation = Matrix()
    private var transformedCameraSubjectBounds: Path = Path()

    override suspend fun onActivated() {
        coroutineScope {
            snapshotFlow { state.isTransformInProgress }
                .onEach { transformationInteractor.isTransforming = it }
                .launchIn(this)
        }
    }

    private fun changeTransformation(
        offsetChange: Offset,
        zoomChange: Float,
        rotationChange: Float,
    ) {
        with(transformationInteractor) {
            scale *= zoomChange
            rotation += rotationChange
            offsetX += offsetChange.x
            offsetY += offsetChange.y
        }
        recalculateTransformation()
    }

    fun onCameraScreenBoundsUpdated(bounds: Rect) {
        cameraScreenBounds = bounds

        recalculateTransformation()
    }

    fun fillCameraInteractableRegion(outRegion: Region) {
        if (transformableByTouchAnywhere) {
            with(cameraScreenBounds) {
                outRegion.set(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            }
        } else {
            outRegion.setPath(
                transformedCameraSubjectBounds.asAndroidPath(),
                cameraScreenBounds.toAndroidRectF().toRegion(),
            )
        }
    }

    private fun recalculateTransformation(cameraTouchableBounds: Path = this.cameraSubjectBounds) {
        transformation.apply {
            reset()
            translate(
                x = cameraScreenBounds.center.x + offsetX,
                y = cameraScreenBounds.center.y + offsetY,
            )
            rotateZ(degrees = rotation)
            scale(x = scale, y = scale)
            translate(x = -cameraScreenBounds.center.x, y = -cameraScreenBounds.center.y)
        }
        transformedCameraSubjectBounds =
            cameraTouchableBounds.copy().apply { transform(transformation) }
    }

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureCameraTransformationViewModel
    }
}

private fun ScreenRecordingStatus.transformableByTouchAnywhere(): Boolean =
    this is ScreenRecordingStatus.Stopped

private fun Rect.toPath(): Path = Path().apply { addRect(this@toPath, Path.Direction.Clockwise) }
