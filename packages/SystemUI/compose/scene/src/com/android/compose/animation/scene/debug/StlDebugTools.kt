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

package com.android.compose.animation.scene.debug

import android.graphics.Paint as NativePaint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.debug.StlDebugConfig.DebugLabelPosition
import kotlin.math.absoluteValue

internal fun Modifier.debugElement(key: ElementKey): Modifier {
    return this.drawWithContent {
        // Draw Content first (so border is on top)
        drawContent()

        if (filterElement(key)) return@drawWithContent

        val seed = key.hashCode()
        val color = generateDebugColor(seed)
        val strokeWidth = 1.dp

        // We offset the track of the border randomly to one of 3 tracks to reduce overlapping
        // borders
        val trackIndex = (seed % 3).absoluteValue
        val strokePx = strokeWidth.toPx()
        val inset = trackIndex * strokePx

        // Random dash pattern to further distinguish overlapping borders
        val dashIndex = (seed % 8).absoluteValue
        val dashSize = (dashIndex + 1) * 5f
        val pathEffect =
            PathEffect.dashPathEffect(
                intervals = floatArrayOf(dashSize, dashSize),
                phase = (dashSize * 2).toFloat(),
            )

        if (StlDebugConfig.showElementBorders) {
            drawDebugBorder(
                color = color,
                strokeWidth = strokePx,
                pathEffect = pathEffect,
                inset = inset,
            )
        }

        if (StlDebugConfig.showElementLabels) {
            drawDebugLabel(
                text = listOf(key.debugName),
                color = color,
                position = StlDebugConfig.elementLabelPosition,
                verticalOffset = 0f,
                textSize = 12.sp,
            )
        }
    }
}

internal fun Modifier.debugScene(key: SceneKey): Modifier {
    return this.drawWithContent {
        val seed = key.hashCode()
        val color = generateDebugColor(seed)
        val strokeWidth = 2.dp.toPx()

        if (StlDebugConfig.showSceneBorders) {
            // 1. Draw Border FIRST (so it ends up behind the content)
            drawDebugBorder(
                color = color,
                strokeWidth = strokeWidth,
                pathEffect = null, // Solid
                inset = 0f,
            )
        }
        // 2. Draw Content (Scenes usually contain elements, which should render on top of this
        // border)
        drawContent()

        if (StlDebugConfig.showSceneLabels) {
            // 3. Draw Label (Always on top)
            drawDebugLabel(
                text = listOf("Scene: ${key.debugName}"),
                color = color,
                position = StlDebugConfig.sceneLabelPosition,
                textSize = 12.sp,
            )
        }
    }
}

internal fun Modifier.debugStl(
    state: SceneTransitionLayoutState,
    debugName: String,
    nestingLevel: Int,
): Modifier {
    return this.drawWithContent {
        drawContent()

        val textSize = 12.sp
        val isNested = nestingLevel > 0
        val color = generateDebugColor(nestingLevel)
        val strokeWidth = 2.dp.toPx()
        val labelPadding = 8f
        val inset = nestingLevel * (textSize.toPx() + labelPadding) * 2

        if (StlDebugConfig.showStlBorders) {
            // 1. Draw Border
            drawDebugBorder(
                color = color,
                strokeWidth = strokeWidth,
                pathEffect = null,
                inset = inset,
            )
        }

        // 2. Construct Status Text
        val typeLabel = if (isNested) "[Nested($nestingLevel)]" else ""
        val currentState = state.transitionState
        val stateText =
            when (currentState) {
                is TransitionState.Idle -> "Idle(${currentState.currentScene.debugName})"
                is TransitionState.Transition ->
                    "[${currentState.fromContent.debugName} -> ${currentState.toContent.debugName}]"
            }

        if (StlDebugConfig.showStlLabels) {
            // 3. Draw Label
            drawDebugLabel(
                text = listOf("$debugName$typeLabel:", stateText),
                color = color,
                position = StlDebugConfig.stlLabelPosition,
                textSize = textSize,
                verticalOffset = inset,
            )
        }
    }
}

/** Generates a random, stable, high-contrast color based on a seed. */
private fun generateDebugColor(seed: Int): Color {
    val rnd = kotlin.random.Random(seed)
    val hue = rnd.nextInt(360).toFloat()
    val saturation = 0.6f + (rnd.nextFloat() * 0.4f)
    val value = 0.8f + (rnd.nextFloat() * 0.2f)

    return Color.hsv(hue, saturation, value)
}

private fun DrawScope.drawDebugBorder(
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect?,
    inset: Float,
) {
    val topLeft = Offset(inset, inset)
    val size = Size(width = size.width - (inset * 2), height = size.height - (inset * 2))

    drawRect(
        color = color,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}

private fun DrawScope.drawDebugLabel(
    text: List<String>,
    color: Color,
    position: DebugLabelPosition,
    verticalOffset: Float = 0f,
    textSize: TextUnit = 12.sp,
) {
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas

        // 1. Configure Paint
        val textPaint =
            NativePaint().apply {
                this.color = android.graphics.Color.BLACK
                this.textSize = textSize.toPx()
                isFakeBoldText = true
                isAntiAlias = true
            }

        val bgPaint =
            NativePaint().apply {
                this.color = color.toArgb() // Background matches the border color
                style = NativePaint.Style.FILL
            }

        // 2. Measure Text
        val fontMetrics = textPaint.fontMetrics
        val lineHeight = fontMetrics.bottom - fontMetrics.top
        var maxLineWidth = 0f
        text.forEach { line ->
            val bounds = android.graphics.Rect()
            textPaint.getTextBounds(line, 0, line.length, bounds)
            if (bounds.width() > maxLineWidth) {
                maxLineWidth = bounds.width().toFloat()
            }
        }
        val totalTextHeight = lineHeight * text.size

        val padding = 8f
        val boxWidth = maxLineWidth + (padding * 2)
        val boxHeight = totalTextHeight + (padding * 2)

        // 3. Calculate Position
        val x: Float
        val y: Float

        when (position) {
            DebugLabelPosition.TopLeft -> {
                x = 0f
                y = 0f + verticalOffset
            }
            DebugLabelPosition.TopRight -> {
                x = size.width - boxWidth
                y = 0f + verticalOffset
            }
            DebugLabelPosition.BottomLeft -> {
                x = 0f
                y = size.height - boxHeight - verticalOffset
            }
            DebugLabelPosition.BottomRight -> {
                x = size.width - boxWidth
                y = size.height - boxHeight - verticalOffset
            }
            DebugLabelPosition.Center -> {
                x = (size.width - boxWidth) / 2f
                y = (size.height - boxHeight) / 2f + verticalOffset
            }
            DebugLabelPosition.CenterTop -> {
                x = (size.width - boxWidth) / 2f
                y = 0f + verticalOffset
            }
            DebugLabelPosition.CenterBottom -> {
                x = (size.width - boxWidth) / 2f
                y = size.height - boxHeight - verticalOffset
            }
            DebugLabelPosition.CenterHigh -> {
                x = (size.width - boxWidth) / 2f
                y = (size.height / 3f) - (boxHeight / 2f) + verticalOffset
            }
            DebugLabelPosition.CenterLow -> {
                x = (size.width - boxWidth) / 2f
                y = (size.height * 2f / 3f) - (boxHeight / 2f) + verticalOffset
            }
        }

        val bgRect = android.graphics.RectF(x, y, x + boxWidth, y + boxHeight)

        // 4. Draw
        nativeCanvas.drawRect(bgRect, bgPaint)
        text.forEachIndexed { index, line ->
            val lineY = y + padding - fontMetrics.top + (index * lineHeight)
            nativeCanvas.drawText(line, x + padding, lineY, textPaint)
        }
    }
}

internal fun filterElement(key: ElementKey): Boolean {
    return !key.debugName.contains(StlDebugConfig.elementKeyFilter ?: "", ignoreCase = true)
}

const val TAG = "StlDebugTools"
