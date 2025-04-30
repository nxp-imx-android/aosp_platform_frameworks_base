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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel
import com.android.wm.shell.Flags
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Test entering bubble via clicking bubble menu.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:EnterBubbleViaBubbleMenuTest`
 *
 * Actions:
 * ```
 *     Long press [simpleApp] icon to show [AppIconMenu].
 *     Click the bubble menu to launch [simpleApp] into bubble.
 * ```
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RunWith(AndroidJUnit4::class)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class EnterBubbleViaBubbleMenuTest : BubbleFlickerTestBase() {

    companion object : FlickerPropertyInitializer() {

        @ClassRule
        @JvmField
        val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = { setUpBeforeTransition(instrumentation, wmHelper) },
            transition = { launchBubbleViaBubbleMenu(testApp, tapl, wmHelper) },
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
    fun bubbleWindowIsVisibleAtEnd() {
        wmStateSubjectAtEnd.isAboveAppWindowVisible(ComponentNameMatcher.BUBBLE)
    }

    /**
     * Verifies the bubble layer is visible at the end of transition.
     */
    @Test
    fun bubbleLayerIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(ComponentNameMatcher.BUBBLE)
    }

    /**
     * Verifies the bubble window becomes visible.
     */
    @Test
    fun bubbleWindowBecomesVisible() {
        wmTraceSubject
            .skipUntilFirstAssertion()
            .isAboveAppWindowInvisible(ComponentNameMatcher.BUBBLE)
            .then()
            .isAboveAppWindowVisible(ComponentNameMatcher.BUBBLE)
            .forAllEntries()
    }

    /**
     * Verifies the bubble layer becomes visible.
     */
    @Test
    fun bubbleLayerBecomesVisible() {
        layersTraceSubject
            // Bubble may not appear at the start of the transition.
            .isInvisible(ComponentNameMatcher.BUBBLE, mustExist = false)
            .then()
            .isVisible(ComponentNameMatcher.BUBBLE)
            .forAllEntries()
    }

// endregion

// region App Launch related tests

    /**
     * Verifies the focus changed from launcher to [testApp].
     */
    @Test
    fun focusChanges() {
        EventLogSubject(
            traceDataReader.readEventLogTrace() ?: error("Failed to read event log"),
            traceDataReader
        ).focusChanges(
            ComponentNameMatcher.LAUNCHER.toWindowName(),
            testApp.toWindowName()
        )
    }

    /**
     * Verifies the [testApp] replaces launcher to be the top window.
     */
    @Test
    fun appWindowReplacesLauncherAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(ComponentNameMatcher.LAUNCHER)
            .then()
            .isAppWindowOnTop(
                ComponentNameMatcher.SNAPSHOT
                    .or(ComponentNameMatcher.SPLASH_SCREEN),
                isOptional = true,
            )
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the [testApp] is the top window at the end of transition.
     */
    @Test
    fun appWindowAsTopWindowAtEnd() {
        wmStateSubjectAtEnd.isAppWindowOnTop(testApp)
    }

    /**
     * Verifies the [testApp] becomes the top window.
     */
    @Test
    fun appWindowBecomesTopWindow() {
        wmTraceSubject
            .skipUntilFirstAssertion()
            .isAppWindowNotOnTop(testApp)
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the [testApp] window becomes visible.
     */
    @Test
    fun appWindowBecomesVisible() {
        wmTraceSubject
            .skipUntilFirstAssertion()
            .isAppWindowInvisible(testApp)
            .then()
            .isAppWindowVisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the [testApp] layer becomes visible.
     */
    @Test
    fun appLayerBecomesVisible() {
        layersTraceSubject
            .isInvisible(testApp)
            .then()
            .isVisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the [testApp] window is visible at the end of transition.
     */
    @Test
    fun appWindowIsVisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowVisible(testApp)
    }

    /**
     * Verifies the [testApp] layer is visible at the end of transition.
     */
    @Test
    fun appLayerIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(testApp)
    }

    /**
     * Verifies the [testApp] layer has rounded corners
     */
    @Test
    fun appLayerHasRoundedCorner() {
        layerTraceEntrySubjectAtEnd.hasRoundedCorners(testApp)
    }

// endregion
}