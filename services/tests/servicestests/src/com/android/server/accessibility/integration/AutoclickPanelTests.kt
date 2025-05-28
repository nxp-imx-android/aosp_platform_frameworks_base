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
package com.android.server.accessibility.integration

import android.app.Instrumentation
import android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
import android.content.Context
import android.graphics.Point
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.SettingsStateChangerRule
import com.android.server.accessibility.Flags
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import platform.test.desktop.DesktopMouseTestRule

@Ignore
@RunWith(JUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
class AutoclickPanelTests {
    @Rule(order = 0)
    @JvmField
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Rule(order = 1)
    @JvmField
    val autoclickEnabledSettingRule: SettingsStateChangerRule =
        SettingsStateChangerRule(
            InstrumentationRegistry.getInstrumentation().context,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_ENABLED,
            "1"
        )

    @Rule(order = 2)
    @JvmField
    val desktopMouseTestRule = DesktopMouseTestRule()

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.context
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)

    private lateinit var uiDevice: UiDevice

    @Before
    fun setUp() {
        Configurator.getInstance().setUiAutomationFlags(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiDevice = UiDevice.getInstance(instrumentation)

        // Move the cursor to the edge of the screen once to trigger the Autoclick panel creation.
        var bounds = windowManager.currentWindowMetrics.bounds
        desktopMouseTestRule.move(DEFAULT_DISPLAY, bounds.width() - 1, bounds.height() - 1)
    }

    private fun findObject(selector: BySelector): UiObject2 {
        return uiDevice.wait(Until.findObject(selector), FIND_OBJECT_TIMEOUT.inWholeMilliseconds)
    }

    private fun clickPauseButton() {
        findObject(
            By.res(PAUSE_BUTTON_LAYOUT_ID)
        ).click()
    }

    private fun clickClickTypeButton(resourceId: String) {
        findObject(By.res(resourceId)).click()
        // The delay is needed to let the animation of the panel opening/closing complete before
        // querying for the next element.
        uiDevice.waitForIdle(DELAY_FOR_ANIMATION.inWholeMilliseconds)
    }

    private fun clickLeftClickButton() {
        clickClickTypeButton(LEFT_CLICK_BUTTON_LAYOUT_ID)
    }

    private fun clickLongPressButton() {
        clickClickTypeButton(LONG_PRESS_BUTTON_LAYOUT_ID)
    }

    private fun clickPositionButton() {
        clickClickTypeButton(POSITION_BUTTON_LAYOUT_ID)
    }

    // The panel is considered open when every click type button is showing.
    private fun isAutoclickPanelOpen(): Boolean {
        val PANEL_OPEN_CLICK_TYPE_COUNT = 6
        val clickTypeButtonGroupContainer = findObject(
            By.res(CLICK_TYPE_BUTTON_GROUP_ID)
        )
        return clickTypeButtonGroupContainer.childCount == PANEL_OPEN_CLICK_TYPE_COUNT
    }

    private fun getAutoclickPanelPosition(): Point {
        return findObject(
            By.res(AUTOCLICK_PANEL_ID)
        ).visibleCenter
    }

    @Test
    fun togglePauseResumeButton_contentDescriptionReflectsTheState() {
        // Expect the panel to start with the pause button.
        assertNotNull(
            findObject(
                By.res(PAUSE_BUTTON_IMAGE_ID).desc("Pause")
            )
        )

        // After clicking, verify it's changed to the resume button.
        clickPauseButton()
        assertNotNull(
            findObject(
                By.res(PAUSE_BUTTON_IMAGE_ID).desc("Resume")
            )
        )

        // Click again and verify it's back to the pause button.
        clickPauseButton()
        assertNotNull(
            findObject(
                By.res(PAUSE_BUTTON_IMAGE_ID).desc("Pause")
            )
        )
    }

    @Test
    fun switchClickType_LongPressClickTypeIsSelected() {
        // Click the left click button to open the panel.
        clickLeftClickButton()

        // Click the long press button then verify only the long press button is visible with all
        // other click type buttons hidden.
        clickLongPressButton()
        assertNotNull(
            findObject(By.res(LONG_PRESS_BUTTON_LAYOUT_ID))
        )
        assertFalse(isAutoclickPanelOpen())
    }

    @Test
    fun clickPositionButton_autoclickPanelMovesAroundTheScreen() {
        // Capture position of the panel after each click.
        val startingPosition = getAutoclickPanelPosition()
        clickPositionButton()
        val secondPosition = getAutoclickPanelPosition()
        clickPositionButton()
        val thirdPosition = getAutoclickPanelPosition()
        clickPositionButton()
        val fourthPosition = getAutoclickPanelPosition()
        clickPositionButton()
        val fifthPosition = getAutoclickPanelPosition()

        // Confirm the panel moved around the screen and finished in the starting location.
        assertNotEquals(startingPosition, secondPosition)
        assertNotEquals(secondPosition, thirdPosition)
        assertNotEquals(thirdPosition, fourthPosition)
        assertEquals(startingPosition, fifthPosition)
    }

    private companion object {
        private val FIND_OBJECT_TIMEOUT = 30.seconds
        private val DELAY_FOR_ANIMATION = 2.seconds

        // Resource ids
        private val PAUSE_BUTTON_LAYOUT_ID = "android:id/accessibility_autoclick_pause_layout"
        private val PAUSE_BUTTON_IMAGE_ID = "android:id/accessibility_autoclick_pause_button"
        private val LEFT_CLICK_BUTTON_LAYOUT_ID =
            "android:id/accessibility_autoclick_left_click_layout"
        private val LONG_PRESS_BUTTON_LAYOUT_ID =
            "android:id/accessibility_autoclick_long_press_layout"
        private val POSITION_BUTTON_LAYOUT_ID = "android:id/accessibility_autoclick_position_layout"
        private val CLICK_TYPE_BUTTON_GROUP_ID =
            "android:id/accessibility_autoclick_click_type_button_group_container"
        private val AUTOCLICK_PANEL_ID = "android:id/accessibility_autoclick_type_panel"
    }
}
