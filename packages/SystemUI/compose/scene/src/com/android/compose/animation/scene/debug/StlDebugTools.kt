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
import android.util.Log
import androidx.compose.runtime.snapshots.Snapshot
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachReversed
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.Element
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayoutImpl
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.content.Content
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.debug.StlDebugConfig.DebugLabelPosition
import com.android.compose.animation.scene.getAllNestedTransitionStates
import java.util.Locale
import kotlin.collections.mutableMapOf
import kotlin.math.absoluteValue

/**
 * ONLY USED DURING DEBUGGING Stores the contents where this element was placed in for this frame.
 */
internal val placedInContents = mutableMapOf<ElementKey, MutableSet<ContentKey>>()

internal fun Modifier.debugElement(key: ElementKey): Modifier {
    return this.drawWithContent {
        // Draw Content first (so border is on top)
        drawContent()
        Snapshot.withoutReadObservation {
            if (filterOutElement(key)) return@drawWithContent
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
}

internal fun Modifier.debugScene(key: SceneKey): Modifier {
    return this.drawWithContent {
        val seed = key.hashCode()
        val color = generateDebugColor(seed)
        val strokeWidth = 2.dp.toPx()

        if (StlDebugConfig.showSceneBorders) {
            Snapshot.withoutReadObservation {
                // 1. Draw Border FIRST (so it ends up behind the content)
                drawDebugBorder(
                    color = color,
                    strokeWidth = strokeWidth,
                    pathEffect = null, // Solid
                    inset = 0f,
                )
            }
        }
        // 2. Draw Content (Scenes usually contain elements, which should render on top of this
        // border)
        drawContent()

        if (StlDebugConfig.showSceneLabels) {
            Snapshot.withoutReadObservation {
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
}

internal fun Modifier.debugStl(
    state: SceneTransitionLayoutState,
    debugName: String,
    nestingLevel: Int,
): Modifier {
    return this.drawWithContent {
        drawContent()
        Snapshot.withoutReadObservation {
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

internal fun Modifier.logElementsOnTransitionChange(
    layoutImpl: SceneTransitionLayoutImpl,
    logger: StateLogger,
): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)

        layout(placeable.width, placeable.height) {
            Snapshot.withoutReadObservation {
                // Clear placedInContents so we only observe this frame
                if (StlDebugConfig.logElements) {
                    layoutImpl.elements.keys.forEach { placedInContents[it]?.clear() }
                }
            }
            // place the root
            placeable.place(0, 0)

            Snapshot.withoutReadObservation {
                // Now placedInContents is up-to-date, we can log the results.
                if (StlDebugConfig.logElements) {
                    logger.logOnNewTransition(layoutImpl, layoutImpl.state.transitionState)
                }
            }
        }
    }

internal class StateLogger {
    private var lastState: TransitionState? = null

    internal fun logOnNewTransition(
        layoutImpl: SceneTransitionLayoutImpl,
        currentState: TransitionState,
    ) {
        if (currentState != lastState) {
            performLog(layoutImpl, currentState, lastState)
            lastState = currentState
        }
    }

    private fun performLog(
        layoutImpl: SceneTransitionLayoutImpl,
        current: TransitionState,
        previous: TransitionState?,
    ) {
        val sb = StringBuilder()
        if (previous == null) {
            sb.appendLine("\n========= STL(${layoutImpl.debugName}) ENTERED COMPOSITION =========")
        } else {
            sb.appendLine("\n========= STL(${layoutImpl.debugName}) STATE CHANGED =========")
            sb.appendLine("Before: $previous")
        }
        sb.appendLine("Now: $current")
        val allTransitionStates = getAllNestedTransitionStates(layoutImpl)
        if (allTransitionStates.any { it.any { it != current } }) {
            sb.appendLine("All current transition states: $allTransitionStates")
        }

        if (layoutImpl.ancestors.isNotEmpty()) {
            sb.append("STL is a NestedSTL nested within: ")
            layoutImpl.ancestors.fastForEach {
                sb.append("STL(${it.layoutImpl.debugName}) in Content: ${it.inContent.debugName}")
            }
            sb.appendLine()
        }
        sb.appendLine("==============================================================")

        sb.append("Total Elements: ${layoutImpl.elements.size}")
        if (StlDebugConfig.elementKeyFilter.isNotBlank()) {
            sb.appendLine(
                " - Filtered for Elements containing \"${StlDebugConfig.elementKeyFilter}\""
            )
        }

        layoutImpl.elements.forEach { (key, element) ->
            if (filterOutElement(key)) return@forEach
            sb.appendLine("Element: $key")
            val placedInContents = placedInContents[key] ?: emptyList()

            if (placedInContents.isEmpty()) {
                sb.appendLine("    STATUS: [NOT PLACED IN ANY CONTENT]")
            } else {
                sb.appendLine("    PLACED IN CONTENTS:")
            }
            placedInContents.forEach {
                val state = element.stateByContent[it]!!
                sb.appendLine("  > Placed in: ${getSceneInfo(layoutImpl, it)}")
                sb.appendIdleValues(state)
                sb.appendInterpolatedValues(state)
            }

            sb.appendLine("    STATE IN LOCAL CONTENTS OF STL(${layoutImpl.debugName}):")
            element.stateByContent.forEach { (contentKey, state) ->
                val isPlaced = placedInContents.contains(contentKey)
                if (isPlaced || !layoutImpl.isLocalContent(contentKey)) return@forEach

                sb.appendLine("  > NOT placed in: ${getSceneInfo(layoutImpl, contentKey)}")
                sb.appendIdleValues(state)
                sb.appendInterpolatedValues(state)
            }

            sb.appendLine("    STATE IN OTHER CONTENTS:")
            element.stateByContent.forEach { (contentKey, state) ->
                val isPlaced = placedInContents.contains(contentKey)
                if (isPlaced || layoutImpl.isLocalContent(contentKey)) return@forEach

                sb.appendLine("  > NOT placed in: ${getSceneInfo(layoutImpl, contentKey)}")
                sb.appendIdleValues(state)
            }
            sb.appendLine("--------------------------------------------------------------")
        }

        Log.i(TAG, sb.toString())
    }
}

private fun StringBuilder.appendInterpolatedValues(state: Element.State) {
    appendLine(
        String.format(
            Locale.ENGLISH,
            "      Interp: Size[%4d, %4d], Offset[%6.1f, %6.1f], Scale[%4s, %4s], Alpha[%4s]",
            state.lastSize.width,
            state.lastSize.height,
            state.lastOffset.x,
            state.lastOffset.y,
            formatFloat(state.lastScale.scaleX),
            formatFloat(state.lastScale.scaleY),
            formatFloat(state.lastAlpha),
        )
    )
}

private fun StringBuilder.appendIdleValues(state: Element.State) {
    appendLine(
        String.format(
            Locale.ENGLISH,
            "      Idle:   Size[%4d, %4d], Offset[%6.1f, %6.1f]",
            state.targetSize.width,
            state.targetSize.height,
            state.targetOffset.x,
            state.targetOffset.y,
        )
    )
}

private fun getSceneInfo(layoutImpl: SceneTransitionLayoutImpl, content: ContentKey): String {
    val sb = StringBuilder("$content")
    if (layoutImpl.isLocalContent(content)) {
        layoutImpl.ancestors.fastForEachReversed {
            sb.append(
                " > Nested in content ${it.inContent} of parent STL(${it.layoutImpl.debugName})"
            )
        }
    }
    return sb.toString()
}

internal fun Modifier.logElementState(
    layoutImpl: SceneTransitionLayoutImpl,
    key: ElementKey,
    content: Content,
): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)

        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
            if (filterOutElementExclusive(key)) return@layout

            Snapshot.withoutReadObservation {
                val element = layoutImpl.elements[key]
                val placedInContents = placedInContents[key] ?: emptyList()
                val state = element?.stateByContent?.get(content.key)
                if (state != null && placedInContents.contains(content.key)) {
                    val transition = element.lastTransition
                    val progress = transition?.progress ?: 0f
                    val offset = state.lastOffset

                    val logMsg =
                        String.format(
                            Locale.ENGLISH,
                            "Size: %4d x %4d | Off: %6.1f, %6.1f | A: %4s | S: %4s, %4s | %3.0f%% | During: %s ",
                            placeable.width,
                            placeable.height,
                            offset.x,
                            offset.y,
                            formatFloat(state.lastAlpha),
                            formatFloat(state.lastScale.scaleX),
                            formatFloat(state.lastScale.scaleY),
                            progress * 100,
                            transition.toString(),
                        )

                    Log.d(TAG, getContextString(content.key, element.key) + logMsg)
                }
            }
        }
    }

private fun formatFloat(value: Float): String {
    return if (value == Float.MAX_VALUE || value == Float.MIN_VALUE) "Unsp"
    else String.format(Locale.ENGLISH, "%.2f", value)
}

/**
 * performLog is separate from the inlined log function such that this logic is not copied
 * everywhere which bloats code and increases instruction cache pressure.
 */
internal fun performLog(elementKey: ElementKey, contentKey: ContentKey? = null, msg: String) {
    Snapshot.withoutReadObservation {
        val context = getContextString(contentKey, elementKey)
        Log.v(TAG, context + msg)
    }
}

private fun getContextString(contentKey: ContentKey?, elementKey: ElementKey): String {
    val maxNameLength = 18
    val contentStr =
        when (contentKey) {
            null -> ""
            is SceneKey -> "[S:${contentKey.debugName.takeLast(maxNameLength)}]"
            is OverlayKey -> "[O:${contentKey.debugName.takeLast(maxNameLength)}]"
        }
    return "[E:${elementKey.debugName.takeLast(maxNameLength)}]$contentStr "
}

/**
 * Returns true if the Element should be filtered out. It is filtered out when the key does not
 * contain the filter string. If an empty filter is set, filter out everything (always returns
 * true).
 */
internal fun filterOutElementExclusive(key: ElementKey): Boolean {
    if (StlDebugConfig.elementKeyFilter.isBlank()) return true
    return filterOutElement(key)
}

/**
 * Returns true if the Element should be filtered out. It is filtered out when the key does not
 * contain the filter string. If an empty filter is set, nothing is filtered out (always returns
 * false).
 */
internal fun filterOutElement(key: ElementKey): Boolean {
    return !key.debugName.contains(StlDebugConfig.elementKeyFilter, ignoreCase = true)
}

const val TAG = "StlDebug"
