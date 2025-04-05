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

package com.android.wm.shell.flicker

import android.app.Instrumentation
import android.tools.Rotation
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.flicker.FlickerConfig
import android.tools.flicker.FlickerService
import android.tools.traces.monitors.withTracing
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import org.junit.Assume
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.flicker.subject.layers.LayersTraceSubject
import com.google.common.truth.Truth.assertThat
import com.android.wm.shell.Utils
import org.junit.Rule
import android.tools.NavBar
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.helpers.WindowUtils
import android.tools.io.Reader

// TODO(b/408170368): Refactor explicit tests if they prove to be reliable
class OpenAppFromAllAppsPortraitExplicitTest {
    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, rotation)

    companion object {
        val rotation = Rotation.ROTATION_0

        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val wmHelper: WindowManagerStateHelper =
            WindowManagerStateHelper(instrumentation, clearCacheAfterParsing = false)
        val tapl: LauncherInstrumentation = LauncherInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        val clockApp = ClockAppHelper()
        val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
        val config = FlickerConfig()
        val flickerService = FlickerService(config)
        lateinit var traceReader: Reader
        lateinit var finalState: LayerTraceEntrySubject

        @JvmStatic
        @BeforeClass
        fun setUp() {
            // Prerequisites
            Assume.assumeTrue(Flags.enableDesktopWindowingMode() && tapl.isTablet)
            tapl.setEnableRotation(true)
            tapl.setExpectedRotation(rotation.value)
            tapl.enableTransientTaskbar(false)
            ChangeDisplayOrientationRule.setRotation(rotation)
            tapl.showTaskbarIfHidden()

            // Start CUJ
            testApp.enterDesktopMode(wmHelper, device)
            wmHelper.StateSyncBuilder().withFreeformApp(testApp).waitForAndVerify()

            // Collect the trace we want to run assertions on
            traceReader = withTracing {
                // Launch Clock app from all apps menu
                tapl.launchedAppState.taskbar
                    .openAllApps()
                    .getAppIcon(clockApp.appName)
                    .launch(clockApp.packageName)

                wmHelper
                    .StateSyncBuilder()
                    .withFreeformApp(clockApp)
                    .waitForAndVerify()
            }

            val trace = traceReader.readLayersTrace() ?: error("Failed to read layers trace")
            val animationStates = LayersTraceSubject(trace, traceReader)
            finalState = animationStates.last()
        }

        @AfterClass
        @JvmStatic
        fun cleanUp() {
            clockApp.exit(wmHelper)
            testApp.exit(wmHelper)
        }
    }

    @Test
    fun assertScenarios() {
        val scenarios = flickerService.detectScenarios(traceReader)
        val assertions = scenarios.flatMap { it.generateAssertions() }
        assertions.forEach { it.execute() }
    }

    @Test
    fun assertClockIsVisibleAtEnd() {
        finalState.isVisible(clockApp)
    }

    @Test
    fun assertClockIsInsideDisplayBoundsAtEnd() {
        finalState.visibleRegion(clockApp)
            .coversAtMost(wmHelper.currentState.wmState.getDefaultDisplay()!!.displayRect)
    }

    @Test
    fun assertClockIsLaunchedWithCascading() {
        val displayAppBounds = WindowUtils.getInsetDisplayBounds(rotation)
        val windowBounds = finalState.visibleRegion(clockApp).region.bounds
        val onRightSide = windowBounds.right == displayAppBounds.right
        val onLeftSide = windowBounds.left == displayAppBounds.left
        val onTopSide = windowBounds.top == displayAppBounds.top
        val onBottomSide = windowBounds.bottom == displayAppBounds.bottom
        val alignedOnCorners = onRightSide.xor(onLeftSide) and onTopSide.xor(onBottomSide)

        assertThat(alignedOnCorners).isTrue()
    }

    @Test
    fun assertClockIsOnTopAtEnd() {
        val wmTrace = traceReader.readWmTrace() ?: error("Failed to get wm trace")
        val subject = WindowManagerTraceSubject(wmTrace)

        subject.isAppWindowOnTop(clockApp)
    }
}