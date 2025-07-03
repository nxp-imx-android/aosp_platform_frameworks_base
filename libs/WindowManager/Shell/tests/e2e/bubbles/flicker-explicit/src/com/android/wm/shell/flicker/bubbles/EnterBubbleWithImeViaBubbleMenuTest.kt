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

import android.graphics.Bitmap
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import android.tools.Rotation
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.ImeShownOnAppStartHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.EnterBubbleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.ImeBecomesVisibleAndBubbleIsShrunkTestCase
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.setUpBeforeTransition
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Test entering bubble via clicking bubble menu and show IME.
 *
 * To run this test:
 *    `atest WMShellExplicitFlickerTestsBubbles:EnterBubbleWithImeViaBubbleMenuTest`
 * Pre-steps:
 * ```
 *     Launch [initializeApp] to ensure IME is ready to show.
 *     Finish [initializeApp].
 * ```
 *
 * Actions:
 * ```
 *     Long press [ImeActivityAutoFocus] icon to show [AppIconMenu].
 *     Click the bubble menu to launch [ImeActivityAutoFocus] into bubble.
 *     IME will show after [ImeActivityAutoFocus] is shown.
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [EnterBubbleViaBubbleMenuTest]
 * - [ImeBecomesVisibleAndBubbleIsShrunkTestCase]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class EnterBubbleWithImeViaBubbleMenuTest(navBar: NavBar) : BubbleFlickerTestBase(),
    EnterBubbleTestCases, ImeBecomesVisibleAndBubbleIsShrunkTestCase {

    companion object : FlickerPropertyInitializer() {
        /**
         * The screenshot took at the end of the transition.
         */
        private lateinit var bitmapAtEnd: Bitmap

        /**
         * The IME inset observed from [testApp]
         */
        private var imeInset: Int = -1

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                setUpBeforeTransition(instrumentation, wmHelper)
            },
            transition = {
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                testApp.waitIMEShown(wmHelper)
                bitmapAtEnd = instrumentation.uiAutomation.takeScreenshot()
                imeInset = testApp.retrieveImeBottomInset()
            },
            tearDownAfterTransition = { testApp.exit(wmHelper) }
        )

        override val testApp
            get() = ImeShownOnAppStartHelper(instrumentation, Rotation.ROTATION_0)
    }

    @get:Rule
    val setUpRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
        params = arrayOf(navBar)
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    // This is necessary or the test will use the testApp from BubbleFlickerTestBase.
    override val testApp
        get() = EnterBubbleWithImeViaBubbleMenuTest.testApp

    override val bitmapAtEnd: Bitmap
        get() = EnterBubbleWithImeViaBubbleMenuTest.bitmapAtEnd

    override val expectedImeInset: Int
        get() = imeInset

    @FlakyTest(bugId = 421000153)
    @Test
    override fun imeChangesNavBarColor() {
        super.imeChangesNavBarColor()
    }
}