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

import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.ImmersiveAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Base scenario test for toggling fullscreen status via the fullscreen key. */
@RequiresFlagsEnabled(Flags.FLAG_TOGGLE_FULLSCREEN_STATE_VIA_FULLSCREEN_KEY)
abstract class ToggleFullscreenViaFullscreenKey {
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val device = UiDevice.getInstance(getInstrumentation())
    private val immersiveAppHelper = ImmersiveAppHelper(getInstrumentation())
    private val immersiveApp = DesktopModeAppHelper(immersiveAppHelper)
    private val simpleApp = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))
    private val keyEventHelper = KeyEventHelper(getInstrumentation())

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1)
    val testSetupRule = Utils.testSetupRuleFunctional(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(getInstrumentation().context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )

        immersiveApp.enterDesktopMode(wmHelper, device, isImmersiveApp = true)
        simpleApp.enterDesktopMode(wmHelper, device, isImmersiveApp = false)

        tapl.showTaskbarIfHidden()
    }

    @Test
    open fun toggleBetweenDesktopAndFullscreen() {
        // Focus on the immersive app.
        immersiveApp.bringToFront(wmHelper, device)

        // Desktop -> Fullscreen.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFullscreen(wmHelper)

        // Fullscreen -> Desktop.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFreeform(wmHelper)

        // Desktop -> Fullscreen.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFullscreen(wmHelper)

        // Fullscreen -> Desktop.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFreeform(wmHelper)
    }

    @Test
    open fun moveSplitScreenToFullscreen() {
        // Enter split screen mode.
        simpleApp.exitDesktopModeToSplitScreenWithAppHeader(wmHelper)
        tapl.launchedAppState.taskbar
            .getAppIcon(immersiveAppHelper.appName)
            .launch(immersiveApp.packageName)
        SplitScreenUtils.waitForSplitComplete(wmHelper, simpleApp, immersiveApp)

        // Ensure that the immersive app is in focus.
        immersiveApp.bringToFront(wmHelper, device)

        // Split screen -> Fullscreen.
        keyEventHelper.press(KeyEvent.KEYCODE_FULLSCREEN)
        immersiveApp.waitForTransitionToFullscreen(wmHelper)
    }

    @After
    fun teardown() {
        immersiveApp.exit(wmHelper)
        simpleApp.exit(wmHelper)
    }
}
