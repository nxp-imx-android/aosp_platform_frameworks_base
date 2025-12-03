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

import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import org.junit.Test

/**
 * Verifies that the test app opens in fullscreen from invisible at the end of the transition.
 *
 * This verifies:
 * - The test app window is visible and fullscreen at the end of the trace.
 * - The test app layer is visible and covers the entire screen at the end of the trace.
 * - The test app window becomes visible and fullscreen during the transition.
 */
interface AppOpenInFullscreenTestCases : BubbleFlickerSubjects {

    /** Verifies the test app window is visible and fullscreen at the end of the transition. */
    @Test
    fun appWindowIsVisibleAndFullscreenAtEnd() {
        wmStateSubjectAtEnd.isAppWindowVisible(testApp)
        wmStateSubjectAtEnd.isFullscreen(testApp)
    }

    /** Verifies the test app layer is visible and covers the entire screen at the end. */
    @Test
    fun appLayerIsVisibleAndCoversFullScreenAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(testApp)
        val displayBounds =
            layerTraceEntrySubjectAtEnd.entry.physicalDisplayBounds
                ?: error("Missing physical display bounds")
        layerTraceEntrySubjectAtEnd.visibleRegion(testApp).coversExactly(displayBounds)
    }

    /** Verifies the test app window becomes visible and fullscreen. */
    @Test
    fun appWindowBecomesVisibleAndFullscreen() {
        wmTraceSubject
            .isAppWindowInvisible(testApp)
            .then()
            .isAppWindowVisible(testApp)
            .isFullscreen(testApp)
            .forAllEntries()
    }
}
