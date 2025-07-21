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
import com.android.server.wm.flicker.helpers.ScrollToFinishHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.DismissExpandedBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.waitAndVerifyBubbleGone
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Verifies that the content within a bubble can be scrolled and that its UI elements can be clicked.
 *
 * This test validates user interaction by launching a bubble that displays an activity with a
 * [android.widget.ScrollView]. The scrollable content is intentionally long enough to not be
 * fully visible, with a "finish" button located at the very bottom.
 *
 * The test succeeds if it can programmatically scroll to the button and click it, which in
 * turn dismisses the bubble app. This confirms that both scrolling and touch events are
 * processed correctly within the bubble app.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:ScrollableBubbleAppTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble
 * ```
 *
 * Actions:
 * ```
 *     Scroll the [testApp] until find the finish button
 *     Click the finish button to finish the bubble app
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [DismissExpandedBubbleTestCases]
 */
@FlakyTest(bugId = 433241651)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class ScrollableBubbleAppTest(navBar: NavBar) : BubbleFlickerTestBase(),
    DismissExpandedBubbleTestCases {

    companion object : FlickerPropertyInitializer() {
        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = { launchBubbleViaBubbleMenu(testApp, tapl, wmHelper) },
            transition = {
                testApp.scrollToFinish()
                waitAndVerifyBubbleGone(wmHelper)
            },
            tearDownAfterTransition = { testApp.exit() }
        )

        override val testApp
            get() = ScrollToFinishHelper(instrumentation)
    }

    @get:Rule
    val setUpRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
        params = arrayOf(navBar)
    )

    // This is necessary or the test will use the testApp from BubbleFlickerTestBase.
    override val testApp
        get() = ScrollableBubbleAppTest.testApp

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader
}