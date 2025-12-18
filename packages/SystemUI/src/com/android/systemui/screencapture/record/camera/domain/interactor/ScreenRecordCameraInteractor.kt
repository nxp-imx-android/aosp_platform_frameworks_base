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

package com.android.systemui.screencapture.record.camera.domain.interactor

import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.ColorInt
import androidx.core.content.res.use
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.systemui.content.res.map
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.record.camera.data.repository.ScreenRecordCameraRepository
import com.android.systemui.settings.DisplayTracker
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "ScreenRecordCameraInteractor"

@ScreenCaptureScope
class ScreenRecordCameraInteractor
@Inject
constructor(
    @Main resources: Resources,
    @ScreenCapture private val coroutineScope: CoroutineScope,
    private val repository: ScreenRecordCameraRepository,
    private val displayTracker: DisplayTracker,
) {

    val cameraBackgroundColors: List<Int> =
        resources.obtainTypedArray(R.array.screen_record_color_palette).use { array ->
            array.map { index -> getColor(index, Color.TRANSPARENT) }
        }
    val errors: Flow<Int> = repository.errors
    val state: Flow<Int> = repository.state
    val isConnected: Flow<Boolean> = repository.isConnected

    private val _cameraBackground = MutableStateFlow(cameraBackgroundColors.first())
    val cameraBackground: StateFlow<Int> = _cameraBackground

    val isCameraSupported: Flow<Boolean> =
        repository.isConnected
            .map { repository.isCameraSupported() }
            .stateInTraced(
                "ScreenRecordCameraInteractor#isCameraSupported",
                coroutineScope,
                SharingStarted.Eagerly,
                null,
            )
            .filterNotNull()
    val isOnTapSupported: Flow<Boolean> =
        repository.isConnected
            .map { repository.isOnTapSupported() }
            .stateInTraced(
                "ScreenRecordCameraInteractor#isOnTapSupported",
                coroutineScope,
                SharingStarted.Eagerly,
                null,
            )
            .filterNotNull()
    val isBackgroundColorSupported: Flow<Boolean> =
        repository.isConnected
            .map { repository.isBackgroundColorSupported() }
            .stateInTraced(
                "ScreenRecordCameraInteractor#isBackgroundColorSupported",
                coroutineScope,
                SharingStarted.Eagerly,
                null,
            )
            .filterNotNull()
    val optimalCameraStreamSize: Flow<Size> =
        repository.isConnected
            .map {
                repository.prepareStream(
                    displayId = displayTracker.defaultDisplayId,
                    displayOrientation =
                        displayTracker.getDisplay(displayTracker.defaultDisplayId).rotation,
                )
            }
            .stateInTraced(
                "ScreenRecordCameraInteractor#optimalCameraStreamSize",
                coroutineScope,
                SharingStarted.Eagerly,
                null,
            )
            .filterNotNull()

    init {
        // Keep the service connected throughout the recording for faster camera on/off
        coroutineScope.launch { setup() }

        cameraBackground
            .onEach { color -> repository.setBackgroundColor(color) }
            .launchIn(coroutineScope)
    }

    private suspend fun setup(): Nothing = suspendCancellableCoroutine { continuation ->
        repository.connect()

        continuation.invokeOnCancellation { repository.disconnect() }
    }

    suspend fun startStream(surface: Surface, width: Int, height: Int) {
        val optimalSize =
            repository.prepareStream(
                displayId = displayTracker.defaultDisplayId,
                displayOrientation =
                    displayTracker.getDisplay(displayTracker.defaultDisplayId).rotation,
            )
        require(optimalSize != null) { "Couldn't get optimal size. Skipping stream start" }
        require(width == optimalSize.width && height == optimalSize.height) {
            "Surface dimensions aren't optimal: optimal=$optimalSize, width=$width, height=$height"
        }
        repository.startStream(surface, optimalSize)
        Log.i(TAG, "Started a stream with size=$optimalSize")
    }

    suspend fun stopStream() {
        repository.stopStream()
        Log.i(TAG, "Stopped the stream")
    }

    fun setBackgroundColor(@ColorInt color: Int) {
        require(color in cameraBackgroundColors) { "color should be one of cameraBackgroundColors" }
        _cameraBackground.value = color
    }

    suspend fun onTap() {
        repository.onTap()
    }
}
