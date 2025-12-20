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

package com.android.wm.shell.flicker.bubbles.utils

import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.traces.component.IComponentNameMatcher
import kotlin.math.roundToInt

/** A helper to assert the flicker traces. */
internal object FlickerAssertionHelper {

    /**
     * Asserts the [layerMatcher]'s layer alpha value only increases or decreases when visible.
     *
     * Note: It is optional if the spec wants to keep alpha unchanged.
     *
     * @param isFadeIn if `true`, it can only increase or stay unchanged; otherwise, it can only
     *   decrease or stay unchanged.
     */
    fun assertLayerAlphaChangeConsistently(
        layersTraceSubject: LayersTraceSubject,
        layerMatcher: IComponentNameMatcher,
        isFadeIn: Boolean,
    ) {
        val layerList =
            layersTraceSubject.layers { layerMatcher.layerMatchesAnyOf(it) && it.isVisible }
        layerList.zipWithNext { previous, current ->
            val prevAlpha = previous.layer.color.alpha()
            val currAlpha = current.layer.color.alpha()
            if (isFadeIn) {
                check(currAlpha >= prevAlpha) {
                    "Alpha of $layerMatcher should only fade in, but get prevAlpha=$prevAlpha" +
                        " currAlpha=$currAlpha at ${current.timestamp}"
                }
            } else {
                check(currAlpha <= prevAlpha) {
                    "Alpha of $layerMatcher should only fade out, but get prevAlpha=$prevAlpha" +
                        " currAlpha=$currAlpha at ${current.timestamp}"
                }
            }
        }
    }

    /**
     * Asserts the [layerMatcher]'s layer screen bounds only expand or shrink when visible.
     *
     * Note: It is optional if the spec wants to keep bounds unchanged.
     */
    fun assertLayerResizeConsistently(
        layersTraceSubject: LayersTraceSubject,
        layerMatcher: IComponentNameMatcher,
    ) {
        val layerList =
            layersTraceSubject.layers { layerMatcher.layerMatchesAnyOf(it) && it.isVisible }
        var prevWidthChange = 0
        var prevHeightChange = 0
        layerList.zipWithNext { previous, current ->
            // Use screenBounds to take scaling into account.
            val prevBounds = previous.layer.screenBounds
            val currBounds = current.layer.screenBounds
            val widthChange =
                currBounds.width().roundToInt().compareTo(prevBounds.width().roundToInt())
            val heightChange =
                currBounds.height().roundToInt().compareTo(prevBounds.height().roundToInt())
            if (prevWidthChange == 0) {
                prevWidthChange = widthChange
            }
            if (prevHeightChange == 0) {
                prevHeightChange = heightChange
            }

            check(
                (widthChange == 0 || prevWidthChange == widthChange) &&
                    (heightChange == 0 || prevHeightChange == heightChange)
            ) {
                "Screen Bounds of $layerMatcher should only expand or shrink, but it has" +
                    " prevWidthChange=$prevWidthChange currWidthChange=$widthChange" +
                    " prevHeightChange=$prevHeightChange currHeightChange=$heightChange" +
                    " at ${current.timestamp}"
            }
        }
    }

    /**
     * Asserts the [layerMatcher]'s layer position only moves in one direction (no jumping around)
     * when visible.
     *
     * Note: It is optional if the spec wants to keep bounds unchanged.
     */
    fun assertLayerMoveInSingleDirection(
        layersTraceSubject: LayersTraceSubject,
        layerMatcher: IComponentNameMatcher,
    ) {
        val layerList =
            layersTraceSubject.layers { layerMatcher.layerMatchesAnyOf(it) && it.isVisible }
        var prevXDirection = 0
        var prevYDirection = 0
        layerList.zipWithNext { previous, current ->
            // Use bounds' (left, top) for position.
            val prevBounds = previous.layer.bounds
            val currBounds = current.layer.bounds
            val xDirection = currBounds.left.roundToInt().compareTo(prevBounds.left.roundToInt())
            val yDirection = currBounds.top.roundToInt().compareTo(prevBounds.top.roundToInt())
            if (prevXDirection == 0) {
                prevXDirection = xDirection
            }
            if (prevYDirection == 0) {
                prevYDirection = yDirection
            }
            check(
                (xDirection == 0 || prevXDirection == xDirection) &&
                    (yDirection == 0 || prevYDirection == yDirection)
            ) {
                "Position of $layerMatcher should only move in one direction, but it has" +
                    " prevXDirection=$prevXDirection currXDirection=$xDirection" +
                    " prevYDirection=$prevYDirection currYDirection=$yDirection" +
                    " at ${current.timestamp}"
            }
        }
    }
}
