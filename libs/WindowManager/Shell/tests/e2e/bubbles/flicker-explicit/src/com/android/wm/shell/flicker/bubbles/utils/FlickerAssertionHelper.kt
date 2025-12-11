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

/** A helper to assert the flicker traces. */
internal object FlickerAssertionHelper {

    /**
     * Asserts the [layerMatcher]'s layer alpha value only changes in one direction when visible.
     */
    fun assertLayerAlphaChange(
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

    /** Asserts the [layerMatcher]'s layer position only changes in one direction when visible. */
    fun assertLayerPositionChange(
        layersTraceSubject: LayersTraceSubject,
        layerMatcher: IComponentNameMatcher,
    ) {
        val layerList =
            layersTraceSubject.layers { layerMatcher.layerMatchesAnyOf(it) && it.isVisible }
        var prevXDirection = 0
        var prevYDirection = 0
        layerList.zipWithNext { previous, current ->
            val prevX = previous.layer.bounds.left
            val prevY = previous.layer.bounds.top
            val currX = current.layer.bounds.left
            val currY = current.layer.bounds.top
            val xDirection = currX.compareTo(prevX)
            val yDirection = currY.compareTo(prevY)
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
                "Position of $layerMatcher should only move in one direction at" +
                    " prevXDirection=$prevXDirection prevYDirection=$prevYDirection" +
                    " currXDirection=$xDirection currYDirection=$yDirection" +
                    " at ${current.timestamp}"
            }
        }
    }
}
