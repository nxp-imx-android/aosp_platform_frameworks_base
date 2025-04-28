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

package com.android.systemui.underlay.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.lerp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.underlay.ui.viewmodel.ActionViewModel

@Composable
fun NavBarPill(
    actions: List<ActionViewModel>,
    navBarWidth: Dp,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
) {
    val outlineColor = Color.White
    val backgroundColor = Color.Black

    val density = LocalDensity.current
    val collapsedWidthPx = with(density) { navBarWidth.toPx() }
    var expandedSize by remember { mutableStateOf(IntSize.Zero) }
    val enterProgress by
        animateFloatAsState(
            if (visible) 1f else 0f,
            animationSpec = tween(250, delayMillis = 200),
            label = "enter",
        )
    val expansionAlpha by
        animateFloatAsState(
            if (expanded) 0f else 1f,
            animationSpec = tween(250, delayMillis = 200),
            label = "expansion",
        )

    Box(
        modifier =
            modifier.graphicsLayer {
                alpha = enterProgress * expansionAlpha
                scaleY = enterProgress
                scaleX =
                    if (expandedSize.width != 0) {
                        val initialScale = collapsedWidthPx / expandedSize.width
                        lerp(initialScale, 1f, enterProgress)
                    } else {
                        1f
                    }
            }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.clip(RoundedCornerShape(16.dp))
                    .border(2.dp, outlineColor, RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .clickable { onClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .onGloballyPositioned { expandedSize = it.size },
        ) {
            actions.fastForEachIndexed { _, action ->
                val painter = rememberDrawablePainter(action.icon)
                Image(
                    painter = painter,
                    colorFilter =
                        if (action.attribution != null) ColorFilter.tint(outlineColor) else null,
                    contentDescription = action.label,
                    modifier = Modifier.size(16.dp).clip(CircleShape),
                )
            }

            if (actions.size == 1 || (actions.isNotEmpty() && actions.last().attribution != null)) {
                val action = actions.last()
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = outlineColor,
                )
                if (action.attribution != null) {
                    Text(
                        action.attribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = outlineColor,
                        modifier = Modifier.padding(start = 4.dp).alpha(0.4f),
                    )
                }
            }
        }
    }
}
