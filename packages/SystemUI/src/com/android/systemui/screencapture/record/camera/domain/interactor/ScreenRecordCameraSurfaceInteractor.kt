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

import android.view.Surface
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureReleasable
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.util.kotlin.pairwiseBy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile

@ScreenCaptureScope
class ScreenRecordCameraSurfaceInteractor
@Inject
constructor(
    @ScreenCapture private val coroutineScope: CoroutineScope,
    private val cameraInteractor: ScreenRecordCameraInteractor,
) : ScreenCaptureReleasable {

    private val surfaceStatus = MutableStateFlow<Status>(Status.Inactive)
    private var consumingJob: Job? = null

    /** Releases the resources occupied by this interactor */
    override suspend fun release() {
        surfaceStatus.value = Status.Inactive
        consumingJob?.join()
        consumingJob = null
    }

    fun startStream(surface: Surface, width: Int, height: Int) {
        surfaceStatus.value = Status.Active(surface, width, height)
        if (consumingJob != null) {
            return
        }
        consumingJob =
            surfaceStatus
                .takeWhile { it is Status.Active }
                .map { it as Status.Active }
                .onStart { emit(Status.Active()) }
                .pairwiseBy { old: Status.Active, new: Status.Active ->
                    if (old.surface != null) {
                        cameraInteractor.stopStream()
                    }
                    if (new.surface != null) {
                        cameraInteractor.startStream(new.surface, new.width, new.height)
                    }
                }
                .onCompletion { cameraInteractor.stopStream() }
                .launchIn(coroutineScope)
    }

    fun stopStream() {
        surfaceStatus.value = Status.Active()
    }

    private sealed interface Status {
        data object Inactive : Status

        data class Active(val surface: Surface? = null, val width: Int = 0, val height: Int = 0) :
            Status
    }
}
