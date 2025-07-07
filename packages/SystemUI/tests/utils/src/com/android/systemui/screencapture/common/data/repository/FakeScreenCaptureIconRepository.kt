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

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeScreenCaptureIconRepository : ScreenCaptureIconRepository {

    var fakeIconFlow = MutableStateFlow<Result<Bitmap>?>(null)
    val iconForCalls = mutableListOf<Triple<ComponentName, Int, Boolean>>()

    override fun iconFor(
        component: ComponentName,
        userId: Int,
        badged: Boolean,
    ): StateFlow<Result<Bitmap>?> {
        iconForCalls.add(Triple(component, userId, badged))
        return fakeIconFlow
    }

    var fakeIcon: Bitmap? = createBitmap(100, 100)
    val loadIconCalls = mutableListOf<Triple<ComponentName, Int, Boolean>>()

    override suspend fun loadIcon(component: ComponentName, userId: Int, badged: Boolean): Bitmap? {
        loadIconCalls.add(Triple(component, userId, badged))
        return fakeIcon
    }
}
