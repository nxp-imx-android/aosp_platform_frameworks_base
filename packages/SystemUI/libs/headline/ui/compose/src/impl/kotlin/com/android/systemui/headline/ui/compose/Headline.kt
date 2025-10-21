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

package com.android.systemui.headline.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel.Companion.GoneScene

/** The top-level composable for the Headline UI. */
@Composable
fun Headline(viewModel: HeadlineViewModel, modifier: Modifier = Modifier) {
    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        LocalTextStyle provides MaterialTheme.typography.labelMedium,
    ) {
        val items = viewModel.items
        SceneTransitionLayout(viewModel.state, modifier) {
            scene(GoneScene) { GoneScene() }

            items.forEachIndexed { i, item ->
                val previousItem = i.takeIf { it > 0 }?.let { items[it - 1] }
                val nextItem = i.takeIf { it < items.lastIndex }?.let { items[it + 1] }

                scene(
                    key = item.key.toSceneKey(),
                    userActions = userActions(previousItem, nextItem),
                ) {
                    HeadlineItemScene(item, previousItem, nextItem)
                }
            }
        }
    }
}

@Composable
private fun GoneScene(modifier: Modifier = Modifier) {
    // Draw the largest possible circle that is centered and fits the max parent constraints.
    Box(modifier.fillMaxSize().drawBehind { drawCircle(Color.Black) })
}

@Composable
private fun HeadlineItemScene(
    item: HeadlineItem,
    previousItem: HeadlineItem?,
    nextItem: HeadlineItem?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.Center)) {
            // Dot indicator before the pill.
            previousItem?.let { previous ->
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset(-DotIndicatorOffset)
                        .size(DotIndicatorSize)
                        .background(Color.Black, CircleShape)
                )
            }

            // Dot indicator after the pill.
            nextItem?.let { next ->
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .offset(DotIndicatorOffset)
                        .size(DotIndicatorSize)
                        .background(Color.Black, CircleShape)
                )
            }

            // Pill.
            Box(
                // TODO(b/449675581): Use expandable instead.
                modifier = Modifier.clip(CircleShape).background(color = Color.Black)
            ) {
                HeadlinePill(
                    startContent = { HeadlineItemContents(item.startContents) },
                    endContent = { HeadlineItemContents(item.endContents, isReversed = true) },
                )
            }
        }
    }
}

private fun userActions(
    previousItem: HeadlineItem?,
    nextItem: HeadlineItem?,
): Map<UserAction, UserActionResult> {
    if (previousItem == null && nextItem == null) {
        return emptyMap()
    }

    return buildMap {
        previousItem?.let { put(Swipe.End, UserActionResult(it.key.toSceneKey())) }
        nextItem?.let { put(Swipe.Start, UserActionResult(it.key.toSceneKey())) }
    }
}

@Composable
private fun HeadlinePill(
    startContent: @Composable () -> Unit,
    endContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(modifier = modifier, contents = listOf(startContent, endContent)) {
        measurables,
        constraints ->
        check(measurables.size == 2) { "The list passed to Layout.contents should have 2 contents" }
        check(measurables[0].size == 1) { "HeadlinePill.startContent should emit exactly one node" }
        check(measurables[1].size == 1) { "HeadlinePill.endContent should emit exactly one node" }
        check(constraints.hasBoundedWidth) { "HeadlinePill should have a maxWidth" }
        check(constraints.hasBoundedHeight) { "HeadlinePill should have a maxHeight" }

        val horizontalPadding = 8.dp.roundToPx()
        val centerSize = constraints.maxHeight
        val maxChildWidth =
            ((constraints.maxWidth - centerSize) / 2 -
                    horizontalPadding -
                    DotIndicatorOffset.roundToPx())
                .coerceAtLeast(0)
        val minChildWidth =
            (centerSize - DotIndicatorSize.roundToPx() / 2).coerceIn(0, maxChildWidth)
        val childConstraints =
            Constraints(minWidth = minChildWidth, maxWidth = maxChildWidth, maxHeight = centerSize)
        val startPlaceable = measurables[0][0].measure(childConstraints)
        val endPlaceable = measurables[1][0].measure(childConstraints)

        val width =
            centerSize + horizontalPadding * 2 + maxOf(startPlaceable.width, endPlaceable.width) * 2

        layout(width, centerSize) {
            startPlaceable.placeRelative(
                horizontalPadding,
                (centerSize - startPlaceable.height) / 2,
            )
            endPlaceable.placeRelative(
                width - endPlaceable.width - horizontalPadding,
                (centerSize - endPlaceable.height) / 2,
            )
        }
    }
}

@Composable
private fun HeadlineItemContents(contents: List<HeadlineItemContent>, isReversed: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isReversed) Arrangement.End else Arrangement.Start,
    ) {
        contents.forEachIndexed { i, content ->
            when (content) {
                is HeadlineItemContent.Text -> {
                    // Ensure that there is a minimum spacing between a Text and its siblings.
                    val textModifier =
                        when {
                            isReversed && i < contents.lastIndex -> Modifier.padding(end = 4.dp)
                            !isReversed && i > 0 -> Modifier.padding(start = 4.dp)
                            else -> Modifier
                        }

                    Text(
                        modifier = textModifier,
                        text = content.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                is HeadlineItemContent.Icon -> Icon(content.icon, content.contentDescription)
            }
        }
    }
}

private val DotIndicatorSize = 8.dp
private val DotIndicatorOffset = DotIndicatorSize * 1.5f
