/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.tools.Rotation
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.component.IComponentNameMatcher
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.ShowWhenLockedAppHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.RelaunchVisibleSplitAppToBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.AssumptionRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_TASK_BAR
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.android.wm.shell.flicker.utils.SplitScreenUtils.enterSplit
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Test entering bubble for an app that was previously open in split-screen and visible. Bubble is
 * launched via clicking bubble menu from the task bar.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:RelaunchVisibleSplitAppToBubbleTest`
 *
 * Pre-steps:
 * ```
 *     Start the app in split-screen.
 *     Click one of split apps to grant focus.
 * ```
 *
 * Actions:
 * ```
 *     Click the bubble menu in task bar to launch the app into a bubble.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [RelaunchVisibleSplitAppToBubbleTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class RelaunchVisibleSplitAppToBubbleTest(private val testScenario: TestScenario) :
    BubbleFlickerTestBase(), RelaunchVisibleSplitAppToBubbleTestCases {

    companion object {
        private val testApp2: StandardAppHelper = ShowWhenLockedAppHelper(instrumentation)

        private fun generateTransitionRule(
            primaryApp: StandardAppHelper,
            secondaryApp: StandardAppHelper,
            focusOnPrimary: Boolean,
        ) =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Pin to hotseat to ensure the app icon is on the taskbar, which is used
                    // to launch the app into a bubble.
                    SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, testApp.appName)
                    enterSplit(
                        wmHelper,
                        tapl,
                        uiDevice,
                        primaryApp,
                        secondaryApp,
                        Rotation.ROTATION_0,
                    )
                    // Update focus to either primary or secondary app.
                    val bounds =
                        wmHelper
                            .getWindowRegion(
                                if (focusOnPrimary) {
                                    primaryApp
                                } else {
                                    secondaryApp
                                }
                            )
                            .bounds
                    uiDevice.click(bounds.centerX(), bounds.centerY())
                },
                transition = {
                    launchBubbleViaBubbleMenu(testApp, tapl, wmHelper, fromSource = FROM_TASK_BAR)
                },
                tearDownAfterTransition = {
                    testApp.exit(wmHelper)
                    testApp2.exit(wmHelper)
                },
            )

        @Parameters(name = "scenario={0}")
        @JvmStatic
        fun data(): List<TestScenario> = TestScenario.entries
    }

    /**
     * Defines the different test scenarios for relaunching a visible split-screen app into a
     * bubble.
     */
    enum class TestScenario(val rule: RecordTraceWithTransitionRule) {
        /** Focus on the primary app, then bubble the primary app. */
        FOCUS_PRIMARY_TO_BUBBLE_PRIMARY(
            generateTransitionRule(
                primaryApp = testApp,
                secondaryApp = testApp2,
                focusOnPrimary = true,
            )
        ),
        /** Focus on the secondary app, then bubble the primary app. */
        FOCUS_SECONDARY_TO_BUBBLE_PRIMARY(
            generateTransitionRule(
                primaryApp = testApp,
                secondaryApp = testApp2,
                focusOnPrimary = false,
            )
        ),
        /** Focus on the primary app, then bubble the secondary app. */
        FOCUS_PRIMARY_TO_BUBBLE_SECONDARY(
            generateTransitionRule(
                primaryApp = testApp2,
                secondaryApp = testApp,
                focusOnPrimary = true,
            )
        ),
        /** Focus on the secondary app, then bubble the secondary app. */
        FOCUS_SECONDARY_TO_BUBBLE_SECONDARY(
            generateTransitionRule(
                primaryApp = testApp2,
                secondaryApp = testApp,
                focusOnPrimary = false,
            )
        ),
    }

    @get:Rule(order = 1)
    val assumptionRule =
        AssumptionRule(
            condition = { tapl.isTablet },
            message = "The bubble bar is only available on large screen devices",
        )

    @get:Rule(order = 2)
    val setUpRule =
        RunOncePerParameterRule(
            testClass = this::class,
            wrappedRule = testSetupRule(NavBar.MODE_GESTURAL).around(testScenario.rule),
            params = arrayOf(testScenario),
        )

    override val traceDataReader
        get() = testScenario.rule.reader

    override val toFullscreenApp: IComponentNameMatcher
        get() = testApp2
}
