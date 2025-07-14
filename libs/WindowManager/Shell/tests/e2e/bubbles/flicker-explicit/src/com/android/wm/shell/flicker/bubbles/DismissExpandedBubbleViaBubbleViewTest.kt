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

package com.android.wm.shell.flicker.bubbles

import android.platform.test.annotations.Presubmit
import androidx.test.filters.RequiresDevice
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import android.tools.traces.component.ComponentNameMatcher.Companion.BUBBLE
import androidx.test.filters.FlakyTest
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAppBecomesNotExpandedTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.dismissBubbleAppViaBubbleView
import com.android.wm.shell.flicker.bubbles.utils.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.setUpBeforeTransition
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Test dismiss bubble app via dragging bubble to the dismiss view when the bubble is in expanded
 * state.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:DismissExpandedBubbleTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble
 * ```
 *
 * Actions:
 * ```
 *     Dismiss bubble app via dragging bubble icon to the dismiss view
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAppBecomesNotExpandedTestCases]
 * - [BUBBLE] is visible and then disappear
 */
@FlakyTest(bugId = 427850786)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class DismissExpandedBubbleViaBubbleViewTest(navBar: NavBar) :
    BubbleFlickerTestBase(),
    BubbleAppBecomesNotExpandedTestCases
{
    companion object : FlickerPropertyInitializer() {
        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                setUpBeforeTransition(instrumentation, wmHelper)
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
            },
            transition = { dismissBubbleAppViaBubbleView(uiDevice, wmHelper) },
            tearDownAfterTransition = { testApp.exit() }
        )
    }

    @get:Rule
    val setUpRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
        params = arrayOf(navBar)
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    // TODO(b/396020056): Verify expand bubble with bubble bar.
    @Before
    override fun setUp() {
        assumeFalse(tapl.isTablet)
        super.setUp()
    }

// region Bubble stack related tests

    /**
     * Verifies [BUBBLE] window is gone at the end of the transition.
     */
    @Test
    fun bubbleWindowIsGoneAtEnd() {
        wmStateSubjectAtEnd.notContains(BUBBLE)
    }

    /**
     * Verifies [BUBBLE] layer is gone at the end of the transition.
     */
    @Test
    fun bubbleLayerIsGoneAtEnd() {
        layerTraceEntrySubjectAtEnd.notContains(BUBBLE)
    }

    /**
     * Verifies [BUBBLE] window was visible then disappear.
     */
    @Test
    fun bubbleWindowWasVisibleThenDisappear() {
        wmTraceSubject
            .isAboveAppWindowVisible(BUBBLE)
            .then()
            // Use #isNonAppWindowInvisible here because the BUBBLE window may have been removed
            // from WM hierarchy.
            .isNonAppWindowInvisible(BUBBLE)
            .forAllEntries()
    }

    /**
     * Verifies [BUBBLE] layer was visible then disappear.
     */
    @Test
    fun bubbleLayerWasVisibleThenDisappear() {
        layersTraceSubject
            .isVisible(BUBBLE)
            .then()
            .isInvisible(BUBBLE)
            .forAllEntries()
    }

// endregion

// region bubble app related tests

    /**
     * Verifies bubble app window is gone at the end of the transition.
     */
    @Test
    fun appWindowIsGoneAtEnd() {
        wmStateSubjectAtEnd.notContains(testApp)
    }

    @FlakyTest(bugId = 396020056)
    @Test
    override fun appLayerBecomesInvisible() {
        super.appLayerBecomesInvisible()
    }

    @FlakyTest(bugId = 396020056)
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        super.visibleLayersShownMoreThanOneConsecutiveEntry()
    }

// endregion
}