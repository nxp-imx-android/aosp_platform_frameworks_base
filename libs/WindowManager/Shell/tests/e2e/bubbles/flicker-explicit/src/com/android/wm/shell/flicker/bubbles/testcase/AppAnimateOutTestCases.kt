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
 * Verifies that the [testApp] animates out from visible.
 *
 * This verifies:
 * - The [testApp] window/layer becomes invisible from visible.
 * - The [testApp] layer fades out (optional if the spec wants to keep alpha unchanged).
 * - The [testApp] layer animates out only one direction (optional if the spec wants to keep bounds
 *   unchanged).
 */
interface AppAnimateOutTestCases : BubbleFlickerSubjects {

    /** Verifies the [testApp] window becomes invisible from visible. */
    @Test
    fun appWindowBecomesInvisible() {
        wmTraceSubject
            .isAppWindowVisible(testApp)
            .then()
            .isAppWindowInvisible(testApp)
            .forAllEntries()
    }

    /** Verifies the [testApp] layer becomes invisible from visible. */
    @Test
    fun appLayerBecomesInvisible() {
        layersTraceSubject.isVisible(testApp).then().isInvisible(testApp).forAllEntries()
    }

    /** Verifies the [testApp] window is invisible at the end of the transition. */
    @Test
    fun appWindowIsInvisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowInvisible(testApp)
    }

    /** Verifies the [testApp] layer is invisible at the end of the transition. */
    @Test
    fun appLayerIsInvisibleAtEnd() {
        // TestApp may be gone if it's in dismissed state.
        layerTraceEntrySubjectAtEnd.isInvisible(testApp)
    }

    /**
     * Verifies the [testApp] layer's alpha value only decreases (optional if the spec wants to keep
     * alpha unchanged).
     */
    @RequiresFlagsEnabled(
        com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK,
        com.android.window.flags.Flags.FLAG_VISIBILITY_MANAGEMENT_IN_BUBBLE_ROOT,
    )
    @Test
    fun appLayerFadeOut() {
        assertLayerAlphaChangeConsistently(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = testApp,
            isFadeIn = false,
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
    fun appLayerAnimateOut() {
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
