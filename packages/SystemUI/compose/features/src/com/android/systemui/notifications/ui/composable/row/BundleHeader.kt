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

package com.android.systemui.notifications.ui.composable.row

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrDefault
import androidx.compose.ui.util.fastSumBy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.compose.theme.PlatformTheme
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.initOnBackPressedDispatcherOwner
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.statusbar.notification.row.ui.viewmodel.BundleHeaderViewModel

object BundleHeader {
    object Scenes {
        val Collapsed = SceneKey("Collapsed")
        val Expanded = SceneKey("Expanded")
    }

    object Elements {
        val PreviewIcon1 = ElementKey("PreviewIcon1")
        val PreviewIcon2 = ElementKey("PreviewIcon2")
        val PreviewIcon3 = ElementKey("PreviewIcon3")
        val TitleText = ElementKey("TitleText")
    }
}

fun createComposeView(viewModel: BundleHeaderViewModel, context: Context): ComposeView {
    return ComposeView(context).apply {
        repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                initOnBackPressedDispatcherOwner(this@repeatWhenAttached.lifecycle)
                setContent {
                    // TODO(b/399588047): Check if we can init PlatformTheme once instead of once
                    //  per ComposeView
                    PlatformTheme { BundleHeader(viewModel) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BundleHeader(viewModel: BundleHeaderViewModel, modifier: Modifier = Modifier) {
    val state =
        rememberMutableSceneTransitionLayoutState(
            initialScene = BundleHeader.Scenes.Collapsed,
            transitions =
                transitions {
                    from(BundleHeader.Scenes.Collapsed, to = BundleHeader.Scenes.Expanded) {
                        spec = tween(500)
                        translate(BundleHeader.Elements.PreviewIcon3, x = 32.dp)
                        translate(BundleHeader.Elements.PreviewIcon2, x = 16.dp)
                        fade(BundleHeader.Elements.PreviewIcon1)
                        fade(BundleHeader.Elements.PreviewIcon2)
                        fade(BundleHeader.Elements.PreviewIcon3)
                    }
                },
        )

    DisposableEffect(viewModel, state) {
        viewModel.state = state
        onDispose { viewModel.state = null }
    }

    val scope = rememberCoroutineScope()
    DisposableEffect(viewModel, state) {
        viewModel.composeScope = scope
        onDispose { viewModel.composeScope = null }
    }

    Box(modifier) {
        Background(background = viewModel.backgroundDrawable, modifier = Modifier.matchParentSize())
        SceneTransitionLayout(
            state = state,
            modifier =
                Modifier.clickable(
                    onClick = { viewModel.onHeaderClicked() },
                    interactionSource = null,
                    indication = null,
                ),
        ) {
            scene(BundleHeader.Scenes.Collapsed) {
                BundleHeaderContent(viewModel, collapsed = true)
            }
            scene(BundleHeader.Scenes.Expanded) {
                BundleHeaderContent(viewModel, collapsed = false)
            }
        }
    }
}

@Composable
private fun Background(background: Drawable?, modifier: Modifier = Modifier) {
    if (background != null) {
        val painter = rememberDrawablePainter(drawable = background)
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContentScope.BundleHeaderContent(
    viewModel: BundleHeaderViewModel,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 16.dp),
    ) {
        BundleIcon(
            viewModel.bundleIcon,
            modifier =
                Modifier.padding(horizontal = 16.dp)
                    // Has to be a shared element because we may have a semi-transparent background
                    .element(NotificationRowPrimitives.Elements.NotificationIconBackground),
        )
        Text(
            text = stringResource(viewModel.titleText),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.element(BundleHeader.Elements.TitleText).weight(1f),
        )

        if (collapsed) {
            val currentPreviewIcons: List<Drawable> by
                viewModel.previewIcons.collectAsStateWithLifecycle(initialValue = emptyList())
            if (currentPreviewIcons.isNotEmpty()) {
                BundlePreviewIcons(
                    previewDrawables = currentPreviewIcons,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        ExpansionControl(
            collapsed = collapsed,
            numberToShow = viewModel.numberOfChildren,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp),
        )
    }
}

@Composable
private fun ContentScope.BundlePreviewIcons(
    previewDrawables: List<Drawable>,
    modifier: Modifier = Modifier,
) {
    check(previewDrawables.isNotEmpty())
    val iconSize = 32.dp

    val borderWidth = 2.5.dp
    HalfOverlappingReversedRow(
        modifier =
            modifier.graphicsLayer {
                // This is needed for rendering transparent PreviewIcon border
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        PreviewIcon(
            drawable = previewDrawables[0],
            modifier = Modifier.element(BundleHeader.Elements.PreviewIcon1).size(iconSize),
            borderWidth = borderWidth,
        )
        if (previewDrawables.size < 2) return@HalfOverlappingReversedRow
        PreviewIcon(
            drawable = previewDrawables[1],
            modifier = Modifier.element(BundleHeader.Elements.PreviewIcon2).size(iconSize),
            borderWidth = borderWidth,
        )
        if (previewDrawables.size < 3) return@HalfOverlappingReversedRow
        PreviewIcon(
            drawable = previewDrawables[2],
            modifier = Modifier.element(BundleHeader.Elements.PreviewIcon3).size(iconSize),
            borderWidth = borderWidth,
        )
    }
}

/** The Icon used to display a preview of contained child notifications in a Bundle. */
@Composable
private fun PreviewIcon(drawable: Drawable, modifier: Modifier = Modifier, borderWidth: Dp) {
    val strokeWidthPx = with(LocalDensity.current) { borderWidth.toPx() }
    val stroke = remember(borderWidth) { Stroke(width = strokeWidthPx) }

    Box(
        modifier =
            modifier.drawWithContent {
                // Draw the original content of the inner Box
                drawContent()

                // Draw a circle with BlendMode.Clear to 'erase' pixels for the stroke.
                // This will punch a hole in *this* icon's local offscreen buffer, allowing the
                // background of the containing Composable (which needs to have a global
                // offscreen layer) to show through.
                drawCircle(
                    color = Color.Black, // Color doesn't matter for BlendMode.Clear
                    // Calculate the radius for the clearing circle.
                    // It should be the full size.minDimension / 2 PLUS half the stroke width.
                    // This pushes the *center* of the stroke outward, so the *inner* edge of the
                    // stroke aligns with the existing content boundary.
                    radius = (size.minDimension / 2f) + (strokeWidthPx / 2f),
                    center = center,
                    style = stroke,
                    blendMode = BlendMode.Clear,
                )
            }
    ) {
        val surfaceColor = notificationElementSurfaceColor()
        Image(
            painter = rememberDrawablePainter(drawable),
            contentDescription = null,
            modifier =
                Modifier.fillMaxSize()
                    .clip(CircleShape)
                    .background(color = surfaceColor, shape = CircleShape),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun HalfOverlappingReversedRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val placeables = measurables.fastMap { measurable -> measurable.measure(constraints) }

        if (placeables.isEmpty())
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val width = placeables.fastSumBy { it.width / 2 } + placeables.first().width / 2
        val childHeight = placeables.fastMaxOfOrDefault(0) { it.height }

        layout(constraints.constrainWidth(width), constraints.constrainHeight(childHeight)) {
            // Start in the middle of the right-most placeable
            var currentXPosition = placeables.fastSumBy { it.width / 2 }
            placeables.fastForEach { placeable ->
                currentXPosition -= placeable.width / 2
                placeable.placeRelative(x = currentXPosition, y = 0)
            }
        }
    }
}
