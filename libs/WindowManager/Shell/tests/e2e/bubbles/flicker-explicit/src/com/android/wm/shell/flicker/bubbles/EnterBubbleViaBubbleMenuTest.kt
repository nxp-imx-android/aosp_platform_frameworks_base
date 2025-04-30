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
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.tools.Tag
import android.tools.flicker.assertions.SubjectsParser
import android.tools.flicker.subject.FlickerSubject
import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.wm.WindowManagerStateSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.io.Reader
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.wm.WindowManagerState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel
import com.android.server.wm.flicker.assertNavBarPosition
import com.android.server.wm.flicker.assertStatusBarLayerPosition
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.Flags
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Rule
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
class EnterBubbleViaBubbleMenuTest {

    companion object {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()

        /**
         * Helper class to wait on [WindowManagerState] or [LayerTraceEntry] conditions.
         *
         * This is also used to wait for transition completes.
         */
        private val wmHelper = WindowManagerStateHelper(
            instrumentation,
            clearCacheAfterParsing = false,
        )

        /**
         * Used for building the scenario.
         */
        private val tapl: LauncherInstrumentation = LauncherInstrumentation()

        /**
         * The app used in scenario.
         */
        private val testApp = SimpleAppHelper(instrumentation)

        // TODO(b/396020056): Verify bubble scenarios in 3-button mode.
        /**
         * Indicates whether the device uses gesture navigation bar or not.
         */
        private val isGesturalNavBar = tapl.navigationModel == NavigationModel.ZERO_BUTTON

        /**
         * The reader to read trace from.
         */
        private lateinit var traceDataReader: Reader

        @ClassRule
        @JvmField
        val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = { setUpBeforeTransition(instrumentation, wmHelper) },
            transition = { launchBubbleViaBubbleMenu(testApp, tapl, wmHelper) },
            tearDownAfterTransition = { testApp.exit(wmHelper) }
        )
    }

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    /**
     * The reader to read trace from.
     */
    private val traceDataReader = recordTraceWithTransitionRule.reader

    /**
     * The WindowManager trace subject, which is equivalent to the data shown in
     * `Window Manager` tab in go/winscope.
     */
    private val wmTraceSubject = WindowManagerTraceSubject(
        traceDataReader.readWmTrace() ?: error("Failed to read WM trace")
    )

    /**
     * The Layer trace subject, which is equivalent to the data shown in
     * `Surface Flinger` tab in go/winscope.
     */
    private val layersTraceSubject = LayersTraceSubject(
        traceDataReader.readLayersTrace() ?: error("Failed to read layer trace")
    )

    /**
     * The first [WindowManagerState] of the WindowManager trace.
     */
    private val wmStateSubjectAtStart: WindowManagerStateSubject

    /**
     * The last [WindowManagerState] of the WindowManager trace.
     */
    private val wmStateSubjectAtEnd: WindowManagerStateSubject

    /**
     * The first [LayerTraceEntry] of the Layers trace.
     */
    private val layerTraceEntrySubjectAtStart: LayerTraceEntrySubject

    /**
     * The last [LayerTraceEntry] of the Layers trace.
     */
    private val layerTraceEntrySubjectAtEnd: LayerTraceEntrySubject

    // TODO(b/396020056): Verify bubble scenarios in 3-button mode.
    /**
     * Indicates whether the device uses gesture navigation bar or not.
     */
    private val isGesturalNavBar = tapl.navigationModel == NavigationModel.ZERO_BUTTON

    /**
     * Initialize subjects inherited from [FlickerSubject].
     */
    init {
        val parser = SubjectsParser(traceDataReader)
        wmStateSubjectAtStart = parser.getSubjectOfType(Tag.START)
        wmStateSubjectAtEnd = parser.getSubjectOfType(Tag.END)
        layerTraceEntrySubjectAtStart = parser.getSubjectOfType(Tag.START)
        layerTraceEntrySubjectAtEnd = parser.getSubjectOfType(Tag.END)
    }

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

// region Generic tests

    /**
     * Verifies there's no flickers among all visible windows.
     *
     * In other words, all visible windows shouldn't be visible -> invisible -> visible in
     * consecutive entries
     */
    @Test
    fun visibleWindowsShownMoreThanOneConsecutiveEntry() {
        wmTraceSubject
            .visibleWindowsShownMoreThanOneConsecutiveEntry()
            .forAllEntries()
    }

    /**
     * Verifies there's no flickers among all visible layers.
     *
     * In other words, all visible layers shouldn't be visible -> invisible -> visible in
     * consecutive entries
     */
    @Test
    fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        layersTraceSubject
            .visibleLayersShownMoreThanOneConsecutiveEntry()
            .forAllEntries()
    }

// endregion

// region System UI related tests

    /**
     * Verifies the launcher window is always visible.
     */
    @Test
    fun launcherWindowIsAlwaysVisible() {
        wmTraceSubject.isAppWindowVisible(ComponentNameMatcher.LAUNCHER).forAllEntries()
    }

    /**
     * Verifies the launcher layer is always visible.
     */
    @Test
    fun launcherLayerIsAlwaysVisible() {
        layersTraceSubject.isVisible(ComponentNameMatcher.LAUNCHER).forAllEntries()
    }

    /**
     * Verifies navigation bar layer is visible at the start and end of transition.
     */
    @Test
    fun navBarLayerIsVisibleAtStartAndEnd() {
        layerTraceEntrySubjectAtStart.isVisible(ComponentNameMatcher.NAV_BAR)
        layerTraceEntrySubjectAtEnd.isVisible(ComponentNameMatcher.NAV_BAR)
    }

    /**
     * Verifies navigation bar position at the start and end of transition.
     */
    @Test
    fun navBarLayerPositionAtStartAndEnd() {
        assertNavBarPosition(layerTraceEntrySubjectAtStart, isGesturalNavBar)
        assertNavBarPosition(layerTraceEntrySubjectAtEnd, isGesturalNavBar)
    }

    /**
     * Verifies navigation bar window is visible.
     */
    @Test
    fun navBarWindowIsAlwaysVisible() {
        wmTraceSubject
            .isAboveAppWindowVisible(ComponentNameMatcher.NAV_BAR)
            .forAllEntries()
    }

    /**
     * Verifies status bar layer is visible at the start and end of transition.
     */
    @Test
    fun statusBarLayerIsVisibleAtStartAndEnd() {
        layerTraceEntrySubjectAtStart.isVisible(ComponentNameMatcher.STATUS_BAR)
        layerTraceEntrySubjectAtEnd.isVisible(ComponentNameMatcher.STATUS_BAR)
    }

    /**
     * Verifies status bar position at the start and end of transition.
     */
    @Test
    fun statusBarLayerPositionAtStartAndEnd() {
        assertStatusBarLayerPosition(layerTraceEntrySubjectAtStart, wmStateSubjectAtStart.wmState)
        assertStatusBarLayerPosition(layerTraceEntrySubjectAtEnd, wmStateSubjectAtEnd.wmState)
    }

    /**
     * Verifies status bar window is visible.
     */
    @Test
    fun statusBarWindowIsAlwaysVisible() {
        wmTraceSubject
            .isAboveAppWindowVisible(ComponentNameMatcher.STATUS_BAR)
            .forAllEntries()
    }

// endregion
}