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

package com.android.wm.shell.flicker.bubbles.testcase

import android.platform.test.annotations.RequiresFlagsEnabled
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerAlphaChangeConsistently
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerMoveInSingleDirection
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerResizeConsistently
import org.junit.Test

/**
 * Verifies that the [testApp] animates in from invisible.
 *
 * This verifies:
 * - The [testApp] window/layer becomes visible from invisible.
 * - The [testApp] layer fades in (optional if the spec wants to keep alpha unchanged).
 * - The [testApp] layer animates in only one direction (optional if the spec wants to keep bounds
 *   unchanged).
 */
interface AppAnimateInTestCases : BubbleFlickerSubjects {

    /** Verifies the [testApp] window becomes visible from invisible. */
    @Test
    fun appWindowBecomesVisible() {
        wmTraceSubject
            .isAppWindowInvisible(testApp)
            .then()
            .isAppWindowVisible(testApp)
            .forAllEntries()
    }

    /** Verifies the [testApp] layer becomes visible from invisible. */
    @Test
    fun appLayerBecomesVisible() {
        layersTraceSubject.isInvisible(testApp).then().isVisible(testApp).forAllEntries()
    }

    /** Verifies the [testApp] window is visible at the end of transition. */
    @Test
    fun appWindowIsVisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowVisible(testApp)
    }

    /** Verifies the [testApp] layer is visible at the end of transition. */
    @Test
    fun appLayerIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(testApp)
    }

    /**
     * Verifies the [testApp] layer's alpha value only increases (optional if the spec wants to keep
     * alpha unchanged).
     */
    @RequiresFlagsEnabled(
        com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK,
        com.android.window.flags.Flags.FLAG_VISIBILITY_MANAGEMENT_IN_BUBBLE_ROOT,
    )
    @Test
    fun appLayerFadeIn() {
        assertLayerAlphaChangeConsistently(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = testApp,
            isFadeIn = true,
        )
    }

    /**
     * Verifies the [testApp] layer's bounds don't jump around (optional if the spec wants to keep
     * bounds unchanged).
     */
    @RequiresFlagsEnabled(
        com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK,
        com.android.window.flags.Flags.FLAG_VISIBILITY_MANAGEMENT_IN_BUBBLE_ROOT,
    )
    @Test
    fun appLayerAnimateIn() {
        assertLayerMoveInSingleDirection(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = testApp,
        )

        assertLayerResizeConsistently(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = testApp,
        )
    }
}
