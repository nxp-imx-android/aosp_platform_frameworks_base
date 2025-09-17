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

package com.android.wm.shell.scenarios

import android.app.Instrumentation
import android.tools.Rotation
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Base scenario test to test launch a new instance from long pressing the app icon in the taskbar
 * and tapping the new window.
 */
@Ignore("Test Base Class")
abstract class LaunchNewInstanceFromTaskbarMenu(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    val browserApp = BrowserAppHelper(instrumentation)

    @Before
    fun setup() {
        testApp.enterDesktopMode(wmHelper, device)
        tapl.showTaskbarIfHidden()
        // Clear browser storage to avoid different taskbar menu UI.
        browserApp.clearStorage()
    }

    @Test
    open fun launchNewInstance() {
        openNewWindowFromTaskbarMenu()
        wmHelper
            .StateSyncBuilder()
            .withFreeformApp(browserApp.componentMatcher)
            .withAppTransitionIdle()
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        browserApp.exit(wmHelper)
    }

    private fun openNewWindowFromTaskbarMenu() {
        tapl.launchedAppState.taskbar.getAppIcon(browserApp.appName).openMenu()
        // Selects the new window by its index, which prevents regressions by future alterations
        // to the text or content description.
        val newWindowObject = device.wait(Until.findObjects(By.pkg(NEW_WINDOW_PACKAGE)), TIMEOUT)
        newWindowObject.get(NEW_WINDOW_INDEX).also { it.click() }
    }

    companion object {
        private const val NEW_WINDOW_PACKAGE = "com.google.android.apps.nexuslauncher"
        // The index of the "New window" button in the taskbar long-press menu.
        private const val NEW_WINDOW_INDEX = 3
        private const val TIMEOUT: Long = 5000L
    }
}
