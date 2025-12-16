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

import android.util.Size
import android.view.Surface
import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.flow.collectTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureOverlayStateInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraSurfaceInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

class ScreenCaptureCameraViewModel
@AssistedInject
constructor(
    private val screenRecordCameraSurfaceInteractor: ScreenRecordCameraSurfaceInteractor,
    private val screenRecordCameraInteractor: ScreenRecordCameraInteractor,
    overlayStateInteractor: ScreenCaptureOverlayStateInteractor,
) : HydratedActivatable() {

    private val taps = Channel<Unit>()

    val shouldShowCamera: Boolean? by
        overlayStateInteractor.isCameraInUse.hydratedStateOf(
            "ScreenCaptureCameraViewModel#shouldShowCamera",
            null,
        )

    val surfaceSize: Size? by
        screenRecordCameraInteractor.optimalCameraStreamSize.hydratedStateOf(
            "ScreenCaptureCameraViewModel#surfaceSize",
            null,
        )

    override suspend fun onActivated() {
        super.onActivated()
        coroutineScope {
            launch {
                taps.consumeAsFlow().collectTraced("ScreenCaptureCameraViewModel#taps") {
                    screenRecordCameraInteractor.onTap()
                }
            }
        }
    }

    fun onSurfaceReady(surface: Surface, width: Int, height: Int) {
        screenRecordCameraSurfaceInteractor.startStream(surface, width, height)
    }

    fun onSurfaceDestroyed() {
        screenRecordCameraSurfaceInteractor.stopStream()
    }

    fun onSurfaceClicked() {
        taps.trySend(Unit)
    }

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureCameraViewModel
    }
}
