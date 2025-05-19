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
import com.android.internal.R
import com.android.server.accessibility.Flags
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import platform.test.desktop.DesktopMouseTestRule

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
            By.res(
                context.getResources()
                    .getResourceName(R.id.accessibility_autoclick_pause_layout)
            )
        ).click()
    }

    private fun clickClickTypeButton(id: Int) {
        findObject(
            By.res(context.getResources().getResourceName(id))
        ).click()
        // The delay is needed to let the animation of the panel opening/closing complete before
        // querying for the next element.
        uiDevice.waitForIdle(DELAY_FOR_ANIMATION.inWholeMilliseconds)
    }

    private fun clickLeftClickButton() {
        clickClickTypeButton(R.id.accessibility_autoclick_left_click_layout)
    }

    private fun clickLongPressButton() {
        clickClickTypeButton(R.id.accessibility_autoclick_long_press_layout)
    }

    // The panel is considered open when every click type button is showing.
    private fun isAutoclickPanelOpen(): Boolean {
        val PANEL_OPEN_CLICK_TYPE_COUNT = 6
        val clickTypeButtonGroupContainer = findObject(
            By.res(
                context.getResources()
                    .getResourceName(R.id.accessibility_autoclick_click_type_button_group_container)
            )
        )
        return clickTypeButtonGroupContainer.childCount == PANEL_OPEN_CLICK_TYPE_COUNT
    }

    @Test
    fun togglePauseResumeButton_contentDescriptionReflectsTheState() {
        val autoclickPauseButtonId = context.getResources()
            .getResourceName(R.id.accessibility_autoclick_pause_button)
        val resumeText = "Resume"
        val pauseText = "Pause"

        // Expect the panel to start with the pause button.
        assertNotNull(
            findObject(
                By.res(autoclickPauseButtonId)
                    .desc(pauseText)
            )
        )

        // After clicking, verify it's changed to the resume button.
        clickPauseButton()
        assertNotNull(
            findObject(
                By.res(autoclickPauseButtonId)
                    .desc(resumeText)
            )
        )

        // Click again and verify it's back to the pause button.
        clickPauseButton()
        assertNotNull(
            findObject(
                By.res(autoclickPauseButtonId)
                    .desc(pauseText)
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
            findObject(
                By.res(
                    context.getResources()
                        .getResourceName(R.id.accessibility_autoclick_long_press_layout)
                )
            )
        )
        assertFalse(isAutoclickPanelOpen())
    }

    private companion object {
        private val FIND_OBJECT_TIMEOUT = 30.seconds
        private val DELAY_FOR_ANIMATION = 2.seconds
    }
}
