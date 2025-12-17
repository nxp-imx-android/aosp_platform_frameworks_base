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
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

@SysUISingleton
class ScreenRecordCameraRepositoryImpl @Inject constructor() : ScreenRecordCameraRepository {
    override val errors: Flow<Int> = emptyFlow()
    override val state: Flow<Int> = emptyFlow()
    override val isConnected: Flow<Boolean> = flowOf(false)

    override fun connect() {}

    override fun disconnect() {}

    override suspend fun startStream(surface: Surface, size: Size) {}

    override suspend fun stopStream() {}

    override suspend fun prepareStream(
        displayId: Int,
        displayOrientation: Int,
    ): Size? = null

    override suspend fun setBackgroundColor(color: Int) {}

    override suspend fun onTap() {}

    override suspend fun isCameraSupported(): Boolean = false

    override suspend fun isOnTapSupported(): Boolean = false

    override suspend fun isBackgroundColorSupported(): Boolean = false
}
