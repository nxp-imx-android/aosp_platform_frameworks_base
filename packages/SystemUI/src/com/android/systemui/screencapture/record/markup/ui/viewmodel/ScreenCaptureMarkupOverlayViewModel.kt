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

package com.android.systemui.screencapture.record.markup.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.storage.decode
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureMarkupInteractor
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureOverlayStateInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalInkCustomBrushApi::class)
class ScreenCaptureMarkupOverlayViewModel
@AssistedInject
constructor(
    @Application context: Context,
    markupInteractor: ScreenCaptureMarkupInteractor,
    overlayInteractor: ScreenCaptureOverlayStateInteractor,
) : HydratedActivatable() {

    private val brushFamily =
        BrushFamily.decode(context.resources.openRawResource(R.raw.screenrecording_markup))
    val brush: Brush? by
        markupInteractor.color
            .map {
                Brush.builder()
                    .setFamily(brushFamily)
                    .setColorIntArgb(it)
                    .setSize(15f)
                    .setEpsilon(0.1f)
                    .build()
            }
            .hydratedStateOf("ScreenCaptureMarkupViewModel#brush", null)
    val shouldShowMarkup: Boolean by
        overlayInteractor.isMarkupInUse.hydratedStateOf(
            "ScreenCaptureMarkupViewModel#isMarkupEnabled",
            false,
        )

    @AssistedFactory
    interface Factory {

        fun create(): ScreenCaptureMarkupOverlayViewModel
    }
}
