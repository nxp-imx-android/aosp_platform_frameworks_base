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

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
import android.os.Bundle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.view.Display.DEFAULT_DISPLAY
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SettingsStateChangerRule
import com.android.server.accessibility.Flags
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import platform.test.desktop.DesktopMouseTestRule

@RunWith(JUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_AUTOCLICK_INDICATOR)
class AutoclickClickTypeTests {
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

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<TestClickActivity> =
        ActivityScenarioRule(TestClickActivity::class.java)

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var uiDevice: UiDevice
    private lateinit var testClickButton: Button

    @Before
    fun setup() {
        Configurator.getInstance().setUiAutomationFlags(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiDevice = UiDevice.getInstance(instrumentation)

        activityScenarioRule.scenario.onActivity { activity ->
            testClickButton = activity.findViewById(TEST_BUTTON_ID)
        }
    }

    private fun moveMouseToView(view: View) {
        val xOnScreen = view.locationOnScreen[0]
        val yOnScreen = view.locationOnScreen[1]
        val centerX = xOnScreen + (view.width / 2)
        val centerY = yOnScreen + (view.height / 2)
        desktopMouseTestRule.move(DEFAULT_DISPLAY, centerX, centerY)
    }

    @Test
    fun performLeftClick_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, LEFT_CLICK_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        waitAndAssert {
            testClickButton.text == LEFT_CLICK_TEXT
        }
    }

    @Test
    fun performDoubleClick_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, DOUBLE_CLICK_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        waitAndAssert {
            testClickButton.text == DOUBLE_CLICK_TEXT
        }
    }

    @Test
    fun performRightClick_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, RIGHT_CLICK_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        waitAndAssert {
            testClickButton.text == RIGHT_CLICK_TEXT
        }
    }

    @Test
    fun performLongPress_buttonReflectsClickType() {
        changeClickType(uiDevice, desktopMouseTestRule, LONG_PRESS_BUTTON_LAYOUT_ID)
        moveMouseToView(testClickButton)

        waitAndAssert {
            testClickButton.text == LONG_PRESS_TEXT
        }
    }

    // Test activity responsible for receiving clicks and updating its UI depending on the click
    // type.
    class TestClickActivity : Activity() {
        private lateinit var gestureDetector: GestureDetector

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val contentLayout = LinearLayout(this)
            contentLayout.setOrientation(LinearLayout.VERTICAL)

            val testButton = Button(this)
            testButton.id = TEST_BUTTON_ID

            gestureDetector =
                GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        testButton.text = DOUBLE_CLICK_TEXT
                        return true
                    }
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        testButton.text = LEFT_CLICK_TEXT
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        testButton.text = LONG_PRESS_TEXT
                    }
                })
            testButton.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
            }

            // Right click listener.
            val genericMotionListener = View.OnGenericMotionListener { _, motionEvent ->
                if (motionEvent.isFromSource(InputDevice.SOURCE_MOUSE)
                    && motionEvent.action == MotionEvent.ACTION_BUTTON_PRESS
                    && motionEvent.actionButton == MotionEvent.BUTTON_SECONDARY
                ) {
                    testButton.text = RIGHT_CLICK_TEXT
                    true
                }
                false
            }
            testButton.setOnGenericMotionListener(genericMotionListener)

            contentLayout.addView(testButton)
            setContentView(contentLayout)
        }
    }

    private companion object {
        private val TEST_BUTTON_ID = View.generateViewId()

        // Button text.
        private val LEFT_CLICK_TEXT = "Left Clicked!"
        private val DOUBLE_CLICK_TEXT = "Double Clicked!"
        private val RIGHT_CLICK_TEXT = "Right Clicked!"
        private val LONG_PRESS_TEXT = "Long Press Clicked!"

        @BeforeClass
        @JvmStatic
        fun setupBeforeClass() {
            // Disables showing an SDK version dialog to prevent it from interfering with the test.
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("setprop debug.wm.disable_deprecated_target_sdk_dialog 1")
                .close()
        }

        @AfterClass
        @JvmStatic
        fun teardownAfterClass() {
            // Re-enable SDK version dialog.
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("setprop debug.wm.disable_deprecated_target_sdk_dialog 0")
                .close()
        }
    }
}
