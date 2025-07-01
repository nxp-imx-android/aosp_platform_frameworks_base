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

import android.platform.systemui_tapl.ui.Bubble
import android.platform.systemui_tapl.ui.Root
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAlwaysVisibleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAppBecomesExpandedTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.dismissMultipleBubbles
import com.android.wm.shell.flicker.bubbles.utils.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.launchMultipleBubbleAppsViaBubbleMenuAndCollapse
import com.google.common.truth.Truth.assertWithMessage
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runners.MethodSorters

/**
 * Test launch multiple bubbles.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:LaunchMultipleBubbleTest`
 *
 * Pre-steps:
 * ```
 *   Launch five apps into bubble and collapse.
 * ```
 *
 * Actions:
 * ```
 *   Launch [testApp] into bubble
 *   The oldest bubble app will be removed from the bubble stack, or bubble bar.
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAlwaysVisibleTestCases]
 * - [BubbleAppBecomesExpandedTestCases]
 */
@FlakyTest(bugId = 430273288)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class LaunchMultipleBubbleTest(navBar: NavBar) : BubbleFlickerTestBase(),
    BubbleAlwaysVisibleTestCases, BubbleAppBecomesExpandedTestCases {

    companion object : FlickerPropertyInitializer() {

        private lateinit var bubbleIconsBeforeTransition: List<Bubble>

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                bubbleIconsBeforeTransition =
                    launchMultipleBubbleAppsViaBubbleMenuAndCollapse(
                        tapl,
                        wmHelper
                    )
            },
            transition = {
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                val bubbleIconsAfterTransition = Root.get().expandedBubbleStack.bubbles
                val oldestBubble = bubbleIconsBeforeTransition.first()
                assertWithMessage("The oldest bubble must be removed.")
                    .that(bubbleIconsAfterTransition)
                    .doesNotContain(oldestBubble)
            },
            tearDownAfterTransition = {
                testApp.exit()
                dismissMultipleBubbles()
            }
        )
    }

    @get:Rule
    val setUpRule: TestRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
        params = arrayOf(navBar)
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader
}