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

package com.android.systemui.screencapture.record.camera.data.repository

import android.util.Size
import android.view.Surface
import com.android.systemui.screencapture.record.camera.shared.model.CameraState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow

class FakeScreenRecordCameraRepository : ScreenRecordCameraRepository {

    override val errors: Flow<Int>
        get() = emptyFlow()

    private val _state = MutableStateFlow(CameraState.Unavailable)
    override val state: StateFlow<CameraState>
        get() = _state.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: Flow<Boolean>
        get() = _isConnected.asStateFlow()

    private val _taps: Channel<Unit> = Channel()
    val taps: Flow<Unit> = _taps.consumeAsFlow()

    var isOnTapSupported: Boolean = true
    var isCameraSupported: Boolean = true
    var isBackgroundColorSupported: Boolean = true
    var optimalCameraStreamSize: Size? = null
    var backgroundColor: Int = 0

    override fun connect() {
        _isConnected.value = true
    }

    override fun disconnect() {
        _isConnected.value = false
    }

    override suspend fun startStream(surface: Surface, size: Size) {
        _state.value = CameraState.Starting
        _state.value = CameraState.Started
    }

    override suspend fun stopStream() {
        _state.value = CameraState.Stopping
        _state.value = CameraState.Stopped
    }

    override suspend fun isCameraSupported(): Boolean = isCameraSupported

    override suspend fun isOnTapSupported(): Boolean = isOnTapSupported

    override suspend fun isBackgroundColorSupported(): Boolean = isBackgroundColorSupported

    override suspend fun prepareStream(
        displayUniqueId: String?,
        @Surface.Rotation displayRotation: Int,
    ): Size? = optimalCameraStreamSize

    override suspend fun setBackgroundColor(color: Int) {
        backgroundColor = color
    }

    override suspend fun onTap() {
        _taps.trySend(Unit)
    }
}
