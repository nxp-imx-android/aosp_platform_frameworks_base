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

@file:JvmName("DesktopModeCommonAssertions")

package com.android.wm.shell.flicker.utils

import android.graphics.Rect
import android.os.SystemProperties
import android.tools.PlatformConsts
import android.tools.flicker.FlickerTest
import android.tools.helpers.WindowUtils
import android.tools.traces.component.IComponentMatcher

// Common assertions for Desktop mode features.

fun FlickerTest.cascadingEffectAppliedAtEnd(component: IComponentMatcher) {
    assertWmEnd {
        val displayAppBounds = WindowUtils.getInsetDisplayBounds(scenario.startRotation)
        val windowBounds = visibleRegion(component).region.bounds

        val onRightSide = windowBounds.right == displayAppBounds.right
        val onLeftSide = windowBounds.left == displayAppBounds.left
        val onTopSide = windowBounds.top == displayAppBounds.top
        val onBottomSide = windowBounds.bottom == displayAppBounds.bottom
        val alignedOnCorners = onRightSide.xor(onLeftSide) and onTopSide.xor(onBottomSide)

        check { "window corner must meet display corner" }.that(alignedOnCorners).isEqual(true)
    }
}

fun FlickerTest.appLayerHasMaxDisplayHeightAtEnd(component: IComponentMatcher) {
    assertLayersEnd {
        val displayBounds = WindowUtils.getInsetDisplayBounds(scenario.startRotation)
        visibleRegion(component)
            .hasSameTopPosition(displayBounds)
            .hasSameBottomPosition(displayBounds)
    }
}

fun FlickerTest.appLayerHasMaxDisplayWidthAtEnd(component: IComponentMatcher) {
    assertLayersEnd {
        val displayBounds = WindowUtils.getInsetDisplayBounds(scenario.startRotation)
        visibleRegion(component)
            .hasSameLeftPosition(displayBounds)
            .hasSameRightPosition(displayBounds)
    }
}

fun FlickerTest.appLayerHasMaxBoundsInOnlyOneDimension(component: IComponentMatcher) {
    assertWmEnd {
        val maxDisplayBounds = WindowUtils.getInsetDisplayBounds(scenario.startRotation)
        val windowBounds = visibleRegion(component).region.bounds

        val hasMaxHeight =
            windowBounds.top == maxDisplayBounds.top &&
                windowBounds.bottom == maxDisplayBounds.bottom
        val hasMaxWidth =
            windowBounds.left == maxDisplayBounds.left &&
                windowBounds.right == maxDisplayBounds.right
        val isMaxInOneDimension = hasMaxHeight.xor(hasMaxWidth)

        check { "only one max bounds" }.that(isMaxInOneDimension).isEqual(true)
    }
}

fun FlickerTest.appLayerMaintainsAspectRatioAlways(component: IComponentMatcher) {
    assertLayers {
        val desktopWindowLayerList = layers { component.layerMatchesAnyOf(it) && it.isVisible }
        desktopWindowLayerList.zipWithNext { previous, current ->
            current.visibleRegion.isSameAspectRatio(previous.visibleRegion)
        }
    }
}

fun FlickerTest.resizeVeilKeepsIncreasingInSize(component: IComponentMatcher) {
    assertLayers {
        val layerList = layers {
            component.layerMatchesAnyOf(it) &&
                    it.isVisible &&
                    it.name.contains("Resize veil")
        }

        layerList.zipWithNext { previous, current ->
            current.visibleRegion.isStrictlyLargerThan(previous.visibleRegion.region)
        }
    }
}

fun FlickerTest.resizeVeilKeepsDecreasingInSize(component: IComponentMatcher) {
    assertLayers {
        val layerList = layers {
            component.layerMatchesAnyOf(it) &&
                    it.isVisible &&
                    it.name.contains("Resize veil")
        }

        layerList.zipWithNext { previous, current ->
            current.visibleRegion.isStrictlySmallerThan(previous.visibleRegion.region)
        }
    }
}

fun FlickerTest.appLayerHasSizeAtEnd(
    component: IComponentMatcher,
    width: Int,
    height: Int
) {
    assertLayersEnd {
        visibleRegion(component).hasSameSize(width, height, diffThreshold = 50)
    }
}

fun FlickerTest.leftTiledAppLargerThanRightAtEnd(
    leftComponent: IComponentMatcher,
    rightComponent: IComponentMatcher,
) {
    assertLayersEnd {
        val rightRegion = visibleRegion(rightComponent)
        visibleRegion(leftComponent).isStrictlyWiderThan(rightRegion.region)
    }
}
/**
 * Verify that app window fills > 95% of either half of the screen, accounting for the difference
 * due to the divider handle.
 */
fun FlickerTest.appWindowCoversHalfScreenAtEnd(
    component: IComponentMatcher,
    isLeftHalf: Boolean,
    coverageDifferenceThresholdRatio: Double = 0.05,
) {
    assertLayersEnd {
        // Build expected bounds of half the display (minus given threshold)
        val expectedBounds =
            WindowUtils.getInsetDisplayBounds(scenario.startRotation).apply {
                if (isLeftHalf) {
                    right = (centerX() * (1 - coverageDifferenceThresholdRatio)).toInt()
                } else {
                    left = (centerX() * (1 + coverageDifferenceThresholdRatio)).toInt()
                }
            }
        visibleRegion(component).coversAtLeast(expectedBounds)
    }
}

fun FlickerTest.appWindowHasDesktopModeInitialBoundsAtTheEnd(component: IComponentMatcher) {
    assertLayersEnd {
        val displayBounds =
            entry.physicalDisplayBounds ?: error("Missing physical display bounds")
        val stableBounds = WindowUtils.getInsetDisplayBounds(scenario.endRotation)
        val desktopModeInitialBoundsScale =
            SystemProperties.getInt("persist.wm.debug.desktop_mode_initial_bounds_scale", 72) /
                100f

        val desiredWidth = displayBounds.width().times(desktopModeInitialBoundsScale)
        val desiredHeight = displayBounds.height().times(desktopModeInitialBoundsScale)

        val outBounds = Rect(0, 0, desiredWidth.toInt(), desiredHeight.toInt())
        val xOffset = ((stableBounds.width() - desiredWidth) / 2).toInt()
        val yOffset =
            ((stableBounds.height() - desiredHeight) *
                 PlatformConsts.DESKTOP_MODE_INITIAL_WINDOW_HEIGHT_PROPORTION + stableBounds.top)
                 .toInt()
        // Position the task in screen bounds
        outBounds.offset(xOffset, yOffset)

        visibleRegion(component).coversExactly(outBounds)
    }
}

fun FlickerTest.appWindowBecomesPinned(component: IComponentMatcher) {
    assertWm {
        invoke("appWindowIsNotPinned") { it.isNotPinned(component) }
            .then()
            .invoke("appWindowIsPinned") { it.isPinned(component) }
    }
}

fun FlickerTest.tilingDividerBecomesVisibleThenInvisible() {
    assertLayers {
        this.isInvisible(TILING_SPLIT_DIVIDER)
            .then()
            .isVisible(TILING_SPLIT_DIVIDER)
            .then()
            .isInvisible(TILING_SPLIT_DIVIDER)
    }
}

fun FlickerTest.tilingDividerBecomesInvisibleThenVisible() {
    assertLayers {
        this.isVisible(TILING_SPLIT_DIVIDER)
            .then()
            .isInvisible(TILING_SPLIT_DIVIDER)
            .then()
            .isVisible(TILING_SPLIT_DIVIDER)
    }
}
