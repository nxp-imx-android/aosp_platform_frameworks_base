/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.communal.ui.compose.Communal
import com.android.systemui.communal.ui.compose.TransitionDuration
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.scene.shared.model.Scenes

fun TransitionBuilder.lockscreenToCommunalTransition() {
    spec = tween(durationMillis = TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS)

    // Elevate the status bar so that it doesn't scale down during the transition.
    sharedElement(LockscreenElementKeys.StatusBar, elevateInContent = Scenes.Lockscreen)

    timestampRange(endMillis = 166, easing = FastOutSlowInEasing) {
        // Lockscreen depth push
        scaleDraw(LockscreenElementKeys.Root, scaleX = 0.9f, scaleY = 0.9f)

        // Lockscreen fade out
        fade(LockscreenElementKeys.Root)

        // Status text position y translation
        translate(LockscreenElementKeys.IndicationArea, y = (-10).dp)
    }

    timestampRange(startMillis = 166, easing = LinearOutSlowInEasing) {
        // Widget entry x translation
        translate(Communal.Elements.Grid, Edge.End)

        // Hub background fade in
        fade(Communal.Elements.Scrim)
    }
}
