/*
 * Copyright 2025 The Android Open Source Project
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

package android.hardware.input

import android.Manifest
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputDevice.SOURCE_GAMEPAD
import android.view.InputDevice.SOURCE_KEYBOARD
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withSource
import com.google.common.truth.Truth.assertThat
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualGamepadTest {

    @get:Rule val activityScenarioRule = ActivityScenarioRule(CaptureEventActivity::class.java)

    @get:Rule
    val adoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY,
        )

    private lateinit var activity: CaptureEventActivity
    private lateinit var virtualGamepad: VirtualGamepad

    @Before
    fun setUp() {
        activityScenarioRule.scenario.onActivity { activity = it }

        val inputManager = activity.getSystemService(InputManager::class.java)!!
        val config = VirtualGamepadConfig()
        config.name = "TestVirtualGamepad"
        config.vendorId = VENDOR_ID
        config.productId = PRODUCT_ID
        config.associatedDisplayId = activity.display!!.displayId
        virtualGamepad = inputManager.createVirtualGamepad(config)

        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    @After
    fun tearDown() {
        if (this::virtualGamepad.isInitialized) {
            virtualGamepad.close()
        }
    }

    @Test
    fun sendMotionEvent_receivesCorrectEvent() {
        val motionEvent =
            VirtualGamepadMotionEventBuilder()
                .setX(0.5f)
                .setY(-0.5f)
                .setZ(0.1f)
                .setRz(-0.2f)
                .setHatX(1.0f)
                .setHatY(-1.0f)
                .setEventTimeNanos(SystemClock.uptimeNanos())
                .build()
        virtualGamepad.sendMotionEvent(motionEvent)

        val receivedEvent =
            activity.verifier.assertReceivedMotion(
                allOf(
                    withSource(InputDevice.SOURCE_JOYSTICK),
                    withMotionAction(MotionEvent.ACTION_MOVE),
                )
            )
        with(receivedEvent) {
            assertThat(getAxisValue(MotionEvent.AXIS_X)).isWithin(0.001f).of(0.5f)
            assertThat(getAxisValue(MotionEvent.AXIS_Y)).isWithin(0.001f).of(-0.5f)
            assertThat(getAxisValue(MotionEvent.AXIS_Z)).isWithin(0.001f).of(0.1f)
            assertThat(getAxisValue(MotionEvent.AXIS_RZ)).isWithin(0.001f).of(-0.2f)
            assertThat(getAxisValue(MotionEvent.AXIS_HAT_X)).isWithin(0.001f).of(1.0f)
            assertThat(getAxisValue(MotionEvent.AXIS_HAT_Y)).isWithin(0.001f).of(-1.0f)
            // Linux input interface only supports microsecond precision
            assertThat(eventTimeNanos / 1000).isEqualTo(motionEvent.eventTimeNanos / 1000)
        }
    }

    @Test
    fun sendKeyEvent_receivesCorrectEvents() {
        val withGamepadKeyboardSource = withSource(SOURCE_GAMEPAD or SOURCE_KEYBOARD)

        // Key down
        val keyDownEvent =
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_BUTTON_A)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        virtualGamepad.sendKeyEvent(keyDownEvent)
        activity.verifier.assertReceivedKey(
            allOf(
                withGamepadKeyboardSource,
                withKeyAction(KeyEvent.ACTION_DOWN),
                withKeyCode(KeyEvent.KEYCODE_BUTTON_A),
            )
        )

        // Key up
        val keyUpEvent =
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_BUTTON_A)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        virtualGamepad.sendKeyEvent(keyUpEvent)
        activity.verifier.assertReceivedKey(
            allOf(
                withGamepadKeyboardSource,
                withKeyAction(KeyEvent.ACTION_UP),
                withKeyCode(KeyEvent.KEYCODE_BUTTON_A),
            )
        )
    }

    /** Send a non-gamepad key, and check that this fails. */
    @Test
    fun sendKeyEvent_cantSendDisallowedKeys() {
        val keyEvent =
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()

        assertThrows(IllegalArgumentException::class.java) { virtualGamepad.sendKeyEvent(keyEvent) }

        activity.verifier.assertNoEvents()
    }

    companion object {
        private const val VENDOR_ID = 1234
        private const val PRODUCT_ID = 5678
    }
}
