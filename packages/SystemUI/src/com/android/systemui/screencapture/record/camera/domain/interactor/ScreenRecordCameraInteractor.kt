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

import android.util.Size
import android.view.Surface
import androidx.annotation.ColorInt
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.record.camera.data.repository.ScreenRecordCameraRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@ScreenCaptureScope
class ScreenRecordCameraInteractor
@Inject
constructor(
    private val repository: ScreenRecordCameraRepository,
    @ScreenCapture private val coroutineScope: CoroutineScope,
) {

    val cameraBackgroundColors =
        setOf(0x00000000, 0x8E3E92, 0x232D67, 0xF55E57, 0x4E8FF8, 0x1AA64A, 0xD37B00)
    val errors: Flow<Int> = repository.errors
    val state: Flow<Int> = repository.state
    val isConnected: Flow<Boolean> = repository.isConnected

    private val _cameraBackground = MutableStateFlow(cameraBackgroundColors.first())
    val cameraBackground: StateFlow<Int> = _cameraBackground

    init {
        cameraBackground
            .onEach { color -> repository.setBackgroundColor(color) }
            .launchIn(coroutineScope)
    }

    fun connect() {
        repository.connect()
    }

    fun disconnect() {
        repository.disconnect()
    }

    suspend fun startStream(surface: Surface, size: Size) {
        repository.startStream(surface, size)
    }

    suspend fun stopStream() {
        repository.stopStream()
    }

    suspend fun isCameraSupported(): Boolean = repository.isCameraSupported()

    suspend fun isOnTapSupported(): Boolean = repository.isOnTapSupported()

    suspend fun isBackgroundColorSupported(): Boolean = repository.isBackgroundColorSupported()

    suspend fun getOptimalCameraStreamSize(): Size? = repository.getOptimalCameraStreamSize()

    fun setBackgroundColor(@ColorInt color: Int) {
        require(color in cameraBackgroundColors) { "color should be one of cameraBackgroundColors" }
        _cameraBackground.value = color
    }

    suspend fun onTap() {
        repository.onTap()
    }
}
