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
import androidx.annotation.ColorInt
import kotlinx.coroutines.flow.Flow

interface ScreenRecordCameraRepository {

    val errors: Flow<Int>
    val state: Flow<Int>
    val isConnected: Flow<Boolean>

    fun connect()

    fun disconnect()

    suspend fun startStream(surface: Surface, size: Size)

    suspend fun stopStream()

    suspend fun isCameraSupported(): Boolean

    suspend fun isOnTapSupported(): Boolean

    suspend fun isBackgroundColorSupported(): Boolean

    suspend fun prepareStream(
        displayId: Int,
        displayOrientation: Int,
    ): Size?

    suspend fun setBackgroundColor(@ColorInt color: Int)

    suspend fun onTap()
}
