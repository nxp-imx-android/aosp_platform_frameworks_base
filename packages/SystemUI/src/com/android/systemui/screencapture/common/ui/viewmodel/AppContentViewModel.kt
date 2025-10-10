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

package com.android.systemui.screencapture.common.ui.viewmodel

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation

/** Data for a UI to display app content. */
class AppContentViewModel
@AssistedInject
constructor(@Assisted override val model: ScreenCaptureAppContent) :
    TargetViewModel<ScreenCaptureAppContent> {

    override val icon: Result<Bitmap>? =
        Result.failure(IllegalArgumentException("App content does not yet support icons"))

    override val label: Result<CharSequence>? = Result.success(model.label)

    override val thumbnail: Result<Bitmap>? = Result.success(model.thumbnail)

    override val backgroundColorOpaque: Color = Color.Black

    override suspend fun activate(): Nothing = awaitCancellation()

    @AssistedFactory
    interface Factory {
        fun create(model: ScreenCaptureAppContent): AppContentViewModel
    }
}
