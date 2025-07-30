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
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.DismissExpandedBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.dismissBubbleAppViaBubbleView
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
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
 * - [DismissExpandedBubbleTestCases]
 */
@FlakyTest(bugId = 427850786)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class DismissExpandedBubbleViaBubbleViewTest(navBar: NavBar) : BubbleFlickerTestBase(),
    DismissExpandedBubbleTestCases {
    companion object : FlickerPropertyInitializer() {
        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = { launchBubbleViaBubbleMenu(testApp, tapl, wmHelper) },
            transition = { dismissBubbleAppViaBubbleView(wmHelper) },
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