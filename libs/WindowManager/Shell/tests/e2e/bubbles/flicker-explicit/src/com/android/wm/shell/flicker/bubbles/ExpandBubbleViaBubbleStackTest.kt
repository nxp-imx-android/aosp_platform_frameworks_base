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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.collapseBubbleAppViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.expandBubbleAppViaTapOnBubbleStack
import com.android.wm.shell.flicker.bubbles.utils.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.setUpBeforeTransition
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Test clicking bubble to expand a bubble that was in collapsed state.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:ExpandBubbleViaBubbleStackTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble and collapse the bubble
 * ```
 *
 * Actions:
 * ```
 *     Expand the [testApp] bubble via clicking floating bubble icon
 * ```
 * Verified tests:
 * - [ExpandBubbleTestBase]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RunWith(AndroidJUnit4::class)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class ExpandBubbleViaBubbleStackTest : ExpandBubbleTestBase() {

    companion object : FlickerPropertyInitializer() {
        @ClassRule
        @JvmField
        val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                setUpBeforeTransition(instrumentation, wmHelper)
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                collapseBubbleAppViaBackKey(testApp, tapl, wmHelper)
            },
            transition = { expandBubbleAppViaTapOnBubbleStack(uiDevice, testApp, wmHelper) },
            tearDownAfterTransition = { testApp.exit(wmHelper) }
        )
    }

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    @Before
    fun setUp() {
        assumeFalse(tapl.isTablet)
    }
}