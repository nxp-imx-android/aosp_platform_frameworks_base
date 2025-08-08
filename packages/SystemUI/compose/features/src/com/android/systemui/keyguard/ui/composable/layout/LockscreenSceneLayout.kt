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

package com.android.systemui.keyguard.ui.composable.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.android.compose.modifiers.padding
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementFactory
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementFactory.Companion.lockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import kotlin.math.max
import kotlin.math.min

/**
 * Models the UI state of the [LockscreenSceneLayout]. Properties should be backed by snapshot
 * state.
 */
@Immutable
interface LockscreenLayoutViewModel {
    /** Whether the ambient indication UI should currently be showing. */
    val isAmbientIndicationVisible: Boolean
    /** Amount of horizontal translation that should be applied to elements in the scene. */
    val unfoldTranslations: UnfoldTranslations
}

@Immutable
interface UnfoldTranslations {

    /**
     * Amount of horizontal translation to apply to elements that are aligned to the start side
     * (left in left-to-right layouts). Can also be used as horizontal padding for elements that
     * need horizontal padding on both side. In pixels.
     */
    val start: Float

    /**
     * Amount of horizontal translation to apply to elements that are aligned to the end side (right
     * in left-to-right layouts). In pixels.
     */
    val end: Float
}

/**
 * Encapsulates alignment lines produced by the lock icon element.
 *
 * Because the lock icon is also the same element as the under-display fingerprint sensor (UDFPS),
 * [LockscreenSceneLayout] uses the lock icon provided alignment lines to make sure that other
 * elements on screen do not overlap with the lock icon.
 */
object LockIconAlignmentLines {

    /** The left edge of the lock icon. */
    val Left =
        VerticalAlignmentLine(
            merger = { old, new ->
                // When two left alignment line values are provided, choose the leftmost one:
                min(old, new)
            }
        )

    /** The top edge of the lock icon. */
    val Top =
        HorizontalAlignmentLine(
            merger = { old, new ->
                // When two top alignment line values are provided, choose the topmost one:
                min(old, new)
            }
        )

    /** The right edge of the lock icon. */
    val Right =
        VerticalAlignmentLine(
            merger = { old, new ->
                // When two right alignment line values are provided, choose the rightmost one:
                max(old, new)
            }
        )

    /** The bottom edge of the lock icon. */
    val Bottom =
        HorizontalAlignmentLine(
            merger = { old, new ->
                // When two bottom alignment line values are provided, choose the bottommost
                // one:
                max(old, new)
            }
        )
}

/**
 * Arranges the layout for the lockscreen scene.
 *
 * Takes care of figuring out the correct layout configuration based on the device form factor,
 * orientation, and the current UI state.
 *
 * Notes about some non-obvious parameters:
 * - [lockIcon] is drawn according to the [LockIconAlignmentLines] that it must supply. The layout
 *   logic uses those alignment lines to make sure other elements don't overlap with the lock icon
 *   as it may be drawn on top of the UDFPS (under display fingerprint sensor)
 * - the [ambientIndication] is drawn between the start-side and end-side shortcuts, showing ambient
 *   information like the song that's currently being detected
 * - the [bottomIndication] is drawn between the start-side and end-side shortcuts, along the bottom
 */
@Composable
fun LockscreenSceneLayout(
    viewModel: LockscreenLayoutViewModel,
    elementFactory: LockscreenElementFactory,
    elementContext: LockscreenElementContext,
    statusBar: @Composable () -> Unit,
    lockIcon: @Composable () -> Unit,
    startShortcut: @Composable () -> Unit,
    ambientIndication: @Composable () -> Unit,
    bottomIndication: @Composable () -> Unit,
    endShortcut: @Composable () -> Unit,
    settingsMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val spacingAboveLockIconPx = with(density) { 64.dp.roundToPx() }
    val spacingBetweenColumnsPx = with(density) { 32.dp.roundToPx() }

    Layout(
        content = {
            Box(
                Modifier.padding(
                    horizontal = { viewModel.unfoldTranslations.start.fastRoundToInt() }
                )
            ) {
                statusBar()
            }

            elementFactory.lockscreenElement(LockscreenElementKeys.UpperRegion, elementContext)

            BottomArea(
                startShortcut = startShortcut,
                isAmbientIndicationVisible = viewModel.isAmbientIndicationVisible,
                ambientIndication = ambientIndication,
                bottomIndication = bottomIndication,
                endShortcut = endShortcut,
                unfoldTranslations = viewModel.unfoldTranslations,
                modifier = Modifier.navigationBarsPadding(),
            )

            lockIcon()
            settingsMenu()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        check(measurables.size == 5)
        val statusBarMeasurable = measurables[0]
        val contentMeasurable = measurables[1]
        val bottomAreaMeasurable = measurables[2]
        val lockIconMeasurable = measurables[3]
        val settingsMenuMeasurable = measurables[4]

        val statusBarPlaceable =
            statusBarMeasurable.measure(constraints = Constraints.fixedWidth(constraints.maxWidth))

        val lockIconPlaceable =
            lockIconMeasurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

        // Height available between the bottom of the status bar and either the top of the UDFPS
        // icon (if one is showing) or the bottom of the screen, if no UDFPS icon is showing.
        val lockIconBounds =
            IntRect(
                left = lockIconPlaceable[LockIconAlignmentLines.Left],
                top = lockIconPlaceable[LockIconAlignmentLines.Top],
                right = lockIconPlaceable[LockIconAlignmentLines.Right],
                bottom = lockIconPlaceable[LockIconAlignmentLines.Bottom],
            )

        val lockIconConstrainedMaxHeight =
            lockIconBounds.top - spacingAboveLockIconPx - statusBarPlaceable.measuredHeight

        val contentPlaceableOrNull =
            contentMeasurable.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth.coerceAtLeast(0),
                    minHeight = 0,
                    maxHeight = lockIconConstrainedMaxHeight.coerceAtLeast(0),
                )
            )

        val bottomAreaPlaceable =
            bottomAreaMeasurable.measure(constraints = Constraints.fixedWidth(constraints.maxWidth))

        val settingsMenuPleaceable = settingsMenuMeasurable.measure(constraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            statusBarPlaceable.place(0, 0)
            contentPlaceableOrNull?.placeRelative(0, statusBarPlaceable.measuredHeight)
            bottomAreaPlaceable.place(0, constraints.maxHeight - bottomAreaPlaceable.measuredHeight)
            lockIconPlaceable.place(x = lockIconBounds.left, y = lockIconBounds.top)
            settingsMenuPleaceable.placeRelative(
                x = (constraints.maxWidth - settingsMenuPleaceable.measuredWidth) / 2,
                y = constraints.maxHeight - settingsMenuPleaceable.measuredHeight,
            )
        }
    }
}

/** Draws the bottom area of the layout. */
@Composable
private fun BottomArea(
    startShortcut: @Composable () -> Unit,
    isAmbientIndicationVisible: Boolean,
    ambientIndication: @Composable () -> Unit,
    bottomIndication: @Composable () -> Unit,
    endShortcut: @Composable () -> Unit,
    unfoldTranslations: UnfoldTranslations,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        if (isAmbientIndicationVisible) {
            ambientIndication()
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.graphicsLayer { translationX = unfoldTranslations.start }) {
                startShortcut()
            }
            Box(Modifier.weight(1f)) { bottomIndication() }
            Box(Modifier.graphicsLayer { translationX = unfoldTranslations.end }) { endShortcut() }
        }
    }
}
