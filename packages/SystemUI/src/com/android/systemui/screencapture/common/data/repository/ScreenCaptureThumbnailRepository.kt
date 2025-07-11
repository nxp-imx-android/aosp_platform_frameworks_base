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

package com.android.systemui.screencapture.common.data.repository

import android.graphics.Bitmap
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.shared.system.ActivityManagerWrapper
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Repository for fetching app thumbnails. */
interface ScreenCaptureThumbnailRepository {
    /** Returns a flow that fetches an app thumbnail. */
    fun thumbnailFor(taskId: Int): StateFlow<Result<Bitmap>?>

    /** Fetch app thumbnail on background dispatcher. */
    suspend fun loadThumbnail(taskId: Int): Bitmap?
}

/** Default implementation of [ScreenCaptureThumbnailRepository]. */
class ScreenCaptureThumbnailRepositoryImpl
@Inject
constructor(
    @ScreenCapture private val scope: CoroutineScope,
    @Background private val bgContext: CoroutineContext,
    private val activityManager: ActivityManagerWrapper,
) : ScreenCaptureThumbnailRepository {

    override fun thumbnailFor(taskId: Int): StateFlow<Result<Bitmap>?> =
        flow {
                val thumbnail = loadThumbnail(taskId)
                if (thumbnail == null) {
                    emit(Result.failure(Exception("Could not get thumbnail for task $taskId")))
                } else {
                    emit(Result.success(thumbnail))
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun loadThumbnail(taskId: Int): Bitmap? =
        withContext(bgContext) { activityManager.takeTaskThumbnail(taskId).thumbnail }
}
