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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.copy
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenCaptureCameraTransformationInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.properties.Delegates

class ScreenCaptureCameraTransformationViewModel
@AssistedInject
constructor(private val interactor: ScreenCaptureCameraTransformationInteractor) :
    HydratedActivatable() {

    var bounds: Rect by
        Delegates.observable(Rect.Zero) { _, _, new ->
            boundsAsPath.reset()
            boundsAsPath.addRect(new, Path.Direction.Clockwise)
            recalculateTransformation()
        }
    val offsetX: Float
        get() = interactor.offsetX

    val offsetY: Float
        get() = interactor.offsetY

    val scale: Float
        get() = interactor.scale

    val rotation: Float
        get() = interactor.rotation

    private val transformation: Matrix by derivedStateOf { Matrix().apply {} }

    private val boundsAsPath = Path()
    var transformedBounds = Path()
        private set

    fun changeTransformation(offsetChange: Offset, zoomChange: Float, rotationChange: Float) {
        with(interactor) {
            scale *= zoomChange
            rotation += rotationChange
            val compensatedOffsetChange = offsetChange.rotateBy(rotation) * scale
            offsetX += compensatedOffsetChange.x
            offsetY += compensatedOffsetChange.y
        }
        recalculateTransformation()
    }

    fun onTransformationStarted() {
        interactor.isTransforming = true
    }

    fun onTransformationEnded() {
        interactor.isTransforming = false
    }

    private fun recalculateTransformation() =
        with(transformation) {
            reset()
            translate(x = bounds.center.x + offsetX, y = bounds.center.y + offsetY)
            rotateZ(degrees = rotation)
            scale(x = scale, y = scale)
            translate(x = -bounds.center.x, y = -bounds.center.y)
            transformedBounds = boundsAsPath.copy().apply { transform(transformation) }
        }

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureCameraTransformationViewModel
    }
}

private fun Offset.rotateBy(angle: Float): Offset {
    val angleInRadians = angle * (PI / 180)
    val cos = cos(angleInRadians)
    val sin = sin(angleInRadians)
    return Offset((x * cos - y * sin).toFloat(), (x * sin + y * cos).toFloat())
}
