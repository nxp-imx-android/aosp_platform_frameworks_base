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

package com.android.systemui.ambientcue.ui.compose

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonColors
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.lerp
import com.android.compose.PlatformIconButton
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.ambientcue.ui.compose.modifier.animatedActionBorder
import com.android.systemui.ambientcue.ui.viewmodel.ActionViewModel
import com.android.systemui.res.R

@Composable
fun NavBarPill(
    actions: List<ActionViewModel>,
    navBarWidth: Dp,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val maxPillWidth = (configuration.screenWidthDp * 0.65f).dp
    val outlineColor = MaterialTheme.colorScheme.onBackground
    val backgroundColor = MaterialTheme.colorScheme.background

    val density = LocalDensity.current
    val collapsedWidthPx = with(density) { navBarWidth.toPx() }
    var expandedSize by remember { mutableStateOf(IntSize.Zero) }
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible

    val transition = rememberTransition(visibleState)
    val enterProgress by
        transition.animateFloat(
            transitionSpec = { tween(250, delayMillis = 200) },
            label = "enter",
        ) {
            if (it) 1f else 0f
        }
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val closeButtonSize = 28.dp
            Spacer(modifier = Modifier.size(closeButtonSize))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .widthIn(min = navBarWidth, max = maxPillWidth)
                        .background(backgroundColor)
                        .animatedActionBorder(
                            strokeWidth = 1.dp,
                            cornerRadius = 16.dp,
                            visible = visible,
                        )
                        .then(if (expanded) Modifier else Modifier.clickable { onClick() })
                        .padding(3.dp)
                        .onGloballyPositioned { expandedSize = it.size },
            ) {
                // Should have at most 1 expanded chip
                var expandedChip = false
                actions.fastForEachIndexed { index, action ->
                    val hasAttribution = action.attribution != null
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            if (hasAttribution) Modifier.weight(1f, false)
                            else Modifier.width(IntrinsicSize.Max),
                    ) {
                        if ((actions.size == 1 || hasAttribution) && !expandedChip) {
                            expandedChip = true
                            val hasBackground = actions.size > 1
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier =
                                    Modifier.padding(end = 3.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (hasBackground) {
                                                MaterialTheme.colorScheme.onSecondary
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .padding(6.dp),
                            ) {
                                Image(
                                    painter = rememberDrawablePainter(action.icon),
                                    contentDescription = action.label,
                                    modifier =
                                        Modifier.size(16.dp)
                                            .border(
                                                width = 0.75.dp,
                                                color = MaterialTheme.colorScheme.outline,
                                                shape = CircleShape,
                                            )
                                            .clip(CircleShape),
                                )
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = outlineColor,
                                    modifier = Modifier.widthIn(0.dp, maxPillWidth * 0.5f),
                                )
                                if (hasAttribution) {
                                    Text(
                                        text = action.attribution!!,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = outlineColor,
                                        modifier = Modifier.alpha(0.4f),
                                    )
                                }
                            }
                        } else {
                            Image(
                                painter = rememberDrawablePainter(action.icon),
                                contentDescription = action.label,
                                modifier =
                                    Modifier.then(
                                            if (index == 0) {
                                                Modifier.padding(start = 5.dp)
                                            } else if (index == actions.size - 1) {
                                                Modifier.padding(end = 5.dp)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(3.dp)
                                        .size(16.dp)
                                        .border(
                                            width = 0.75.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        )
                                        .clip(CircleShape),
                            )
                        }
                    }
                }
            }

            PlatformIconButton(
                modifier =
                    Modifier.size(closeButtonSize)
                        .clip(CircleShape)
                        .background(backgroundColor.copy(alpha = 0.7f))
                        .padding(6.dp),
                iconResource = R.drawable.ic_close_white_rounded,
                colors =
                    IconButtonColors(
                        containerColor = backgroundColor,
                        contentColor = outlineColor,
                        disabledContainerColor = backgroundColor,
                        disabledContentColor = outlineColor,
                    ),
                contentDescription =
                    stringResource(id = R.string.underlay_close_button_content_description),
                onClick = onCloseClick,
            )
        }
    }
}
