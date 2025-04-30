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
import android.platform.test.annotations.RequiresDevice
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.flicker.subject.events.EventLogSubject
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.bubbles.EnterBubbleViaBubbleMenuTest.Companion.testApp
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Test collapse bubble via clicking back key.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:CollapseBubbleViaBackTest`
 *
 * Pre-steps:
 * ```
 *     Launch [simpleApp] into bubble
 * ```
 *
 * Actions:
 * ```
 *     Collapse bubble via back key
 * ```
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RunWith(AndroidJUnit4::class)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class CollapseBubbleViaBackTest : BubbleFlickerTestBase() {

    companion object : FlickerPropertyInitializer() {

        @ClassRule
        @JvmField
        val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                setUpBeforeTransition(instrumentation, wmHelper)
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
            },
            transition = { collapseBubbleViaBackKey(testApp, tapl, wmHelper) },
            tearDownAfterTransition = { testApp.exit(wmHelper) }
        )
    }

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    // TODO(b/396020056): Verify bubble scenarios in 3-button mode.
    override val isGesturalNavBar = tapl.navigationModel == NavigationModel.ZERO_BUTTON

// region Bubble related tests

    /**
     * Verifies the bubble window is visible at the end of transition.
     */
    @Test
    fun bubbleWindowIsAlwaysVisible() {
        wmTraceSubject.isAboveAppWindowVisible(ComponentNameMatcher.BUBBLE)
    }

    /**
     * Verifies the bubble layer is visible at the end of transition.
     */
    @Test
    fun bubbleLayerIsAlwaysVisible() {
        layersTraceSubject.isVisible(ComponentNameMatcher.BUBBLE)
    }

// endregion

// region launcher related tests

    /**
     * Verifies the focus changed from launcher to [testApp].
     */
    @Test
    fun focusChanges() {
        EventLogSubject(
            traceDataReader.readEventLogTrace() ?: error("Failed to read event log"),
            traceDataReader
        ).focusChanges(
            testApp.toWindowName(),
            LAUNCHER.toWindowName(),
        )
    }

    /**
     * Verifies the [testApp] replaces launcher to be the top window.
     */
    @Test
    fun launcherWindowReplacesTestAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(testApp)
            .then()
            .isAppWindowOnTop(LAUNCHER)
            .forAllEntries()
    }

    /**
     * Verifies [LAUNCHER] is the top window at the end of transition.
     */
    @Test
    fun appWindowAsTopWindowAtEnd() {
        wmStateSubjectAtEnd.isAppWindowOnTop(LAUNCHER)
    }

    /**
     * Verifies the [LAUNCHER] becomes the top window.
     */
    @Test
    fun launcherWindowBecomesTopWindow() {
        wmTraceSubject
            .skipUntilFirstAssertion()
            .isAppWindowNotOnTop(LAUNCHER)
            .then()
            .isAppWindowOnTop(LAUNCHER)
            .forAllEntries()
    }

// endregion
}