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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.copy
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.properties.Delegates

class ScreenCaptureCameraTransformationViewModel @AssistedInject constructor() :
    HydratedActivatable() {

    var bounds: Rect by
        Delegates.observable(Rect.Zero) { _, _, new ->
            boundsAsPath.reset()
            boundsAsPath.addRect(new, Path.Direction.Clockwise)
            recalculateTransformation()
        }
    var offset by mutableStateOf(Offset.Zero)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    var rotation by mutableFloatStateOf(0f)
        private set

    private val transformation: Matrix by derivedStateOf { Matrix().apply {} }

    private val boundsAsPath = Path()
    var transformedBounds = Path()
        private set

    fun changeTransformation(offsetChange: Offset, zoomChange: Float, rotationChange: Float) {
        scale *= zoomChange
        rotation += rotationChange
        offset += offsetChange.rotateBy(rotation) * scale
        recalculateTransformation()
    }

    private fun recalculateTransformation() =
        with(transformation) {
            reset()
            translate(x = bounds.center.x + offset.x, y = bounds.center.y + offset.y)
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
