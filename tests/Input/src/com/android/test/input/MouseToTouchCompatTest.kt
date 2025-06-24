/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.test.input

import android.compat.testing.PlatformCompatChangeRule
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.hardware.input.VirtualMouseButtonEvent.BUTTON_SECONDARY
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.Display.DEFAULT_DISPLAY
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.inputeventmatchers.withActionButton
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
import com.android.hardware.input.Flags
import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.desktop.DesktopMouseTestRule

/** This is an end-to-end test for Mouse-to-touch compatibility feature. */
@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
class MouseToTouchCompatTest {

    @get:Rule(order = 0) val compatChangeRule = PlatformCompatChangeRule()

    @get:Rule(order = 0)
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule(order = 1) val desktopMouseRule = DesktopMouseTestRule()

    @get:Rule(order = 2)
    val activityScenarioRule = ActivityScenarioRule(CaptureEventActivity::class.java)

    private lateinit var eventVerifier: BlockingQueueEventVerifier

    @Before
    fun setUp() {
        val windowCenter = Point()
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED).onActivity { activity ->
            eventVerifier = activity.verifier
            eventVerifier.queue.clear()

            val windowManager = activity.getSystemService(WindowManager::class.java)!!
            val bounds = windowManager.currentWindowMetrics.bounds
            windowCenter.x = bounds.centerX()
            windowCenter.y = bounds.centerY()

            // Move twice to make sure at least one event is dispatched before the test starts.
            desktopMouseRule.move(DEFAULT_DISPLAY, windowCenter.x - 1, windowCenter.y - 1)
            desktopMouseRule.move(DEFAULT_DISPLAY, windowCenter.x, windowCenter.y)
        }

        // DesktopMouseRule splits move into multiple deltas. Consume events until it reaches the
        // center.
        eventVerifier.acceptOptionalMotion(withMotionAction(MotionEvent.ACTION_HOVER_ENTER))
        do {
            val event =
                eventVerifier.acceptOptionalMotion(
                    allOf(
                        withMotionAction(MotionEvent.ACTION_HOVER_MOVE),
                        withSource(InputDevice.SOURCE_MOUSE),
                        withToolType(MotionEvent.TOOL_TYPE_MOUSE),
                    )
                )
            val coordsNearCenter: Boolean =
                (Math.abs(event!!.x - windowCenter.x) < 1f) and
                    (Math.abs(event!!.y - windowCenter.y) < 1f)
        } while (!coordsNearCenter)
    }

    @Test
    @EnableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun testEnabled_clickToTouch() {
        desktopMouseRule.click()

        assertMotionEvents(
            allOf(
                withMotionAction(MotionEvent.ACTION_HOVER_EXIT),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_DOWN),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_UP),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
                withToolType(MotionEvent.TOOL_TYPE_FINGER),
            ),
        )
    }

    @Test
    @EnableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun testEnabled_rightClickAsIs() {
        desktopMouseRule.click(BUTTON_SECONDARY)

        assertMotionEvents(
            allOf(
                withMotionAction(MotionEvent.ACTION_HOVER_EXIT),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_DOWN),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
                withActionButton(MotionEvent.BUTTON_SECONDARY),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
                withActionButton(MotionEvent.BUTTON_SECONDARY),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_UP),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
        )
    }

    @Test
    @DisableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun testDisabled_click() {
        desktopMouseRule.click()

        assertMotionEvents(
            allOf(
                withMotionAction(MotionEvent.ACTION_HOVER_EXIT),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_DOWN),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
                withActionButton(MotionEvent.BUTTON_PRIMARY),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
                withActionButton(MotionEvent.BUTTON_PRIMARY),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
            allOf(
                withMotionAction(MotionEvent.ACTION_UP),
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            ),
        )
    }

    private fun assertMotionEvents(vararg itemMatchers: Matcher<MotionEvent>) {
        for (matcher in itemMatchers) {
            eventVerifier.assertReceivedMotion(matcher)
        }
    }
}
