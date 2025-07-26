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
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAlwaysVisibleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAppBecomesNotExpandedTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Test collapse bubble app via clicking back key.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:CollapseBubbleViaBackTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble
 * ```
 *
 * Actions:
 * ```
 *     Collapse bubbled [testApp] via back key
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAlwaysVisibleTestCases]
 * - [BubbleAppBecomesNotExpandedTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class CollapseBubbleAppViaBackTest(navBar: NavBar) :
    BubbleFlickerTestBase(),
    BubbleAlwaysVisibleTestCases,
    BubbleAppBecomesNotExpandedTestCases
{
    companion object : FlickerPropertyInitializer() {
        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = { launchBubbleViaBubbleMenu(testApp, tapl, wmHelper) },
            transition = { collapseBubbleAppViaBackKey(testApp, tapl, wmHelper) },
            tearDownAfterTransition = { testApp.exit(wmHelper) }
        )
    }

    @get:Rule
    val setUpRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
        params = arrayOf(navBar)
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    /**
     * Verifies bubble app window is invisible at the end of the transition.
     */
    @Test
    override fun appWindowIsInvisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowInvisible(testApp, mustExist = true)
    }
}