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
import androidx.compose.animation.core.TweenSpec
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.android.compose.PlatformIconButton
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.ambientcue.ui.compose.modifier.animatedActionBorder
import com.android.systemui.ambientcue.ui.viewmodel.ActionViewModel
import com.android.systemui.res.R

@Composable
fun ShortPill(
    actions: List<ActionViewModel>,
    modifier: Modifier = Modifier,
    horizontal: Boolean = true,
    visible: Boolean = true,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
) {
    val outlineColor = MaterialTheme.colorScheme.onBackground
    val backgroundColor = MaterialTheme.colorScheme.background
    val minSize = 48.dp
    val closeButtonSize = 28.dp
    val transitionTween: TweenSpec<Float> = tween(250, delayMillis = 200)

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible

    val transition = rememberTransition(visibleState)
    val enterProgress by
        transition.animateFloat(transitionSpec = { transitionTween }, label = "enter") {
            if (it) 1f else 0f
        }
    val expansionAlpha by
        animateFloatAsState(
            if (expanded) 0f else 1f,
            animationSpec = transitionTween,
            label = "expansion",
        )

    Box(
        modifier =
            modifier.graphicsLayer {
                scaleY = enterProgress
                scaleX = enterProgress
            }
    ) {
        val pillModifier =
            Modifier.graphicsLayer { alpha = enterProgress * expansionAlpha }
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .animatedActionBorder(strokeWidth = 1.dp, cornerRadius = 16.dp, visible = visible)
                .widthIn(0.dp, minSize * 2)
                .clickable { onClick() }
                .padding(4.dp)

        if (horizontal) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.size(closeButtonSize))

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(-4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = pillModifier.defaultMinSize(minWidth = minSize),
                ) {
                    actions.take(3).fastForEach { action ->
                        Icon(action, backgroundColor)
                        if (actions.size == 1) {
                            Text(
                                text = action.label,
                                color = outlineColor,
                                style = MaterialTheme.typography.labelMedium,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }

                CloseButton(
                    backgroundColor,
                    outlineColor,
                    onCloseClick,
                    Modifier.size(closeButtonSize),
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.size(closeButtonSize))

                Column(
                    verticalArrangement = Arrangement.spacedBy(-4.dp, Alignment.CenterVertically),
                    modifier = pillModifier.defaultMinSize(minHeight = minSize),
                ) {
                    actions.take(3).fastForEach { action -> Icon(action, backgroundColor) }
                }

                CloseButton(
                    backgroundColor,
                    outlineColor,
                    onCloseClick,
                    Modifier.size(closeButtonSize),
                )
            }
        }
    }
}

@Composable
private fun CloseButton(
    backgroundColor: Color,
    outlineColor: Color,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformIconButton(
        modifier = modifier.clip(CircleShape).background(backgroundColor).padding(8.dp),
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

@Composable
private fun Icon(action: ActionViewModel, backgroundColor: Color, modifier: Modifier = Modifier) {
    Image(
        painter = rememberDrawablePainter(action.icon),
        contentDescription = action.label,
        modifier =
            modifier
                .size(18.dp)
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                )
                .padding(1.dp)
                .clip(CircleShape)
                .background(backgroundColor),
    )
}
