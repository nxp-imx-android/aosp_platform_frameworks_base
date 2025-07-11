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
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeScreenCaptureThumbnailRepository : ScreenCaptureThumbnailRepository {

    var fakeThumbnailFlow = MutableStateFlow<Result<Bitmap>?>(null)
    val thumbnailForCalls = mutableListOf<Int>()

    override fun thumbnailFor(taskId: Int): StateFlow<Result<Bitmap>?> {
        thumbnailForCalls.add(taskId)
        return fakeThumbnailFlow
    }

    var fakeThumbnail: Bitmap? = createBitmap(100, 100)
    val loadThumbnailCalls = mutableListOf<Int>()

    override suspend fun loadThumbnail(taskId: Int): Bitmap? {
        loadThumbnailCalls.add(taskId)
        return fakeThumbnail
    }
}
