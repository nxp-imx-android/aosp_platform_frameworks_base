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

package com.android.systemui.topwindoweffects.ui.compose

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.compose.ComposeInitializer
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectViewModel
import com.android.wm.shell.appzoomout.AppZoomOut
import java.util.Optional

@SuppressLint("ViewConstructor")
class EffectsWindowRoot(
    context: Context,
    private val onEffectStarted: suspend () -> Unit,
    private val onEffectFinished: suspend () -> Unit,
    private val viewModelFactory: SqueezeEffectViewModel.Factory,
    @DrawableRes private val topRoundedCornerResourceId: Int,
    @DrawableRes private val bottomRoundedCornerResourceId: Int,
    private val physicalPixelDisplaySizeRatio: Float,
    private val appZoomOutOptional: Optional<AppZoomOut>,
    private val interactionJankMonitor: InteractionJankMonitor,
) : AbstractComposeView(context) {

    override fun onAttachedToWindow() {
        ComposeInitializer.onAttachedToWindow(this, true)
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ComposeInitializer.onDetachedFromWindow(this)
    }

    @Composable
    override fun Content() {
        SqueezeEffect(
            viewModelFactory = viewModelFactory,
            onEffectStarted = onEffectStarted,
            onEffectFinished = onEffectFinished,
            topRoundedCornerResourceId = topRoundedCornerResourceId,
            bottomRoundedCornerResourceId = bottomRoundedCornerResourceId,
            physicalPixelDisplaySizeRatio = physicalPixelDisplaySizeRatio,
            appZoomOutOptional = appZoomOutOptional,
            interactionJankMonitor = interactionJankMonitor,
        )
    }
}
