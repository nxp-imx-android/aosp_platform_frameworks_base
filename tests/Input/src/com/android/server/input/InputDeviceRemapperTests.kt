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
package com.android.server.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Looper
import android.os.TestLooperManager
import android.platform.test.annotations.Presubmit
import android.testing.TestableContext
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.testutils.TestUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.reset

/**
 * Tests for {@link InputDeviceRemapper}.
 *
 * Build/Install/Run: atest InputTests:InputDeviceRemapperTests
 */
@Presubmit
@RunWith(MockitoJUnitRunner::class)
class InputDeviceRemapperTests {

    @JvmField
    @Rule
    val testableContext = TestableContext(ApplicationProvider.getApplicationContext())
    @Mock private lateinit var native: NativeInputManagerService
    @Mock private lateinit var inputManager: InputManager

    private lateinit var mInputDeviceRemapper: InputDeviceRemapper
    private lateinit var mainLooperManager: TestLooperManager
    private var inputDeviceIds = mutableListOf<Int>()

    @Before
    fun setup() {
        testableContext.addMockSystemService(Context.INPUT_SERVICE, inputManager)
        whenever(inputManager.inputDeviceIds).thenAnswer { inputDeviceIds.toIntArray() }
        mainLooperManager =
            InstrumentationRegistry.getInstrumentation()
                .acquireLooperManager(Looper.getMainLooper())
    }

    @After
    fun tearDown() {
        mainLooperManager.release()
    }

    private fun setupControllerRemapper() {
        mInputDeviceRemapper = InputDeviceRemapper(testableContext, native, Looper.getMainLooper())
        mInputDeviceRemapper.systemRunning()
        TestUtils.flushLoopers(mainLooperManager)
    }

    @Test
    fun testRemapKey_appliesToCorrectDevice() {
        setupControllerRemapper()
        val deviceId = 1
        val device = createDevice(deviceId, vendorId = 123, productId = 456)
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        mInputDeviceRemapper.remapKey(
            /* userId = */ 0,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )

        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_1)),
                eq(intArrayOf(KeyEvent.KEYCODE_2)),
            )
        assertEquals(
            mInputDeviceRemapper.getKeyRemapping(/* userId= */ 0, device.identifier),
            mapOf(KeyEvent.KEYCODE_1 to KeyEvent.KEYCODE_2),
        )
    }

    @Test
    fun testRemapKey_doesNotApplyToMouse() {
        setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_MOUSE,
            )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        mInputDeviceRemapper.remapKey(
            /* userId = */ 0,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )

        verify(native, never()).setKeyRemappingForDevice(eq(deviceId), any(), any())
    }

    @Test
    fun testRemoveKeyRemapping() {
        setupControllerRemapper()
        val deviceId = 1
        val device = createDevice(deviceId, vendorId = 123, productId = 456)
        mInputDeviceRemapper.remapKey(
            /* userId = */ 0,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        mInputDeviceRemapper.removeKeyRemapping(
            /* userId= */ 0,
            device.identifier,
            KeyEvent.KEYCODE_1,
        )

        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
        assertEquals(
            mInputDeviceRemapper.getKeyRemapping(/* userId= */ 0, device.identifier),
            mapOf<Int, Int>(),
        )
    }

    @Test
    fun testClearAllKeyRemapping() {
        setupControllerRemapper()
        val deviceId = 1
        val device = createDevice(deviceId, vendorId = 123, productId = 456)
        mInputDeviceRemapper.remapKey(
            /* userId = */ 0,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        mInputDeviceRemapper.clearAllKeyRemapping(/* userId= */ 0, device.identifier)

        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
        assertEquals(
            mInputDeviceRemapper.getKeyRemapping(/* userId= */ 0, device.identifier),
            mapOf<Int, Int>(),
        )
    }

    @Test
    fun testDeviceRemoved_removesKeyRemapping() {
        setupControllerRemapper()
        val deviceId = 1
        val device = createDevice(deviceId, vendorId = 123, productId = 456)
        mInputDeviceRemapper.remapKey(
            /* userId = */ 0,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        removeInputDevice(deviceId, device.descriptor)

        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
    }

    @Test
    fun testSetCurrentUser_appliesKeyRemapping() {
        setupControllerRemapper()
        val deviceId = 1
        val userId = 0
        val newUserId = 1
        val device = createDevice(deviceId, vendorId = 123, productId = 456)
        mInputDeviceRemapper.remapKey(
            /* userId = */ 0,
            device.identifier,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
        )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        // Switch to a different user with no remapping
        mInputDeviceRemapper.setCurrentUserId(newUserId)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native).setKeyRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))

        // Switch back to the user with remapping
        mInputDeviceRemapper.setCurrentUserId(userId)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native)
            .setKeyRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(KeyEvent.KEYCODE_1)),
                eq(intArrayOf(KeyEvent.KEYCODE_2)),
            )
    }

    @Test
    fun testRemapAxis_appliesToCorrectDevice() {
        setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        mInputDeviceRemapper.remapAxis(
            /* userId = */ 0,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )

        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_X)),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
            )
        assertEquals(
            mInputDeviceRemapper.getAxisRemappings(/* userId= */ 0, device.identifier),
            mapOf(MotionEvent.AXIS_X to MotionEvent.AXIS_Y),
        )
    }

    @Test
    fun testRemapAxis_doesNotApplyToKeyboard() {
        setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_KEYBOARD,
            )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        mInputDeviceRemapper.remapAxis(
            /* userId = */ 0,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )

        verify(native, never()).setAxisRemappingForDevice(eq(deviceId), any(), any())
    }

    @Test
    fun testRemoveAxisRemapping() {
        setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        mInputDeviceRemapper.remapAxis(
            /* userId = */ 0,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        mInputDeviceRemapper.remapAxis(
            /* userId = */ 0,
            device.identifier,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
        )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        mInputDeviceRemapper.removeAxisRemapping(
            /* userId= */ 0,
            device.identifier,
            MotionEvent.AXIS_X,
        )

        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
                eq(intArrayOf(MotionEvent.AXIS_Z)),
            )
        assertEquals(
            mInputDeviceRemapper.getAxisRemappings(/* userId= */ 0, device.identifier),
            mapOf(MotionEvent.AXIS_Y to MotionEvent.AXIS_Z),
        )
    }

    @Test
    fun testClearAllAxisRemappings() {
        setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        mInputDeviceRemapper.remapAxis(
            /* userId = */ 0,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        mInputDeviceRemapper.remapAxis(
            /* userId = */ 0,
            device.identifier,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
        )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        mInputDeviceRemapper.clearAllAxisRemappings(/* userId= */ 0, device.identifier)

        verify(native).setAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
        assertEquals(
            mInputDeviceRemapper.getAxisRemappings(/* userId= */ 0, device.identifier),
            mapOf<Int, Int>(),
        )
    }

    @Test
    fun testDeviceRemoved_removesAxisRemappings() {
        setupControllerRemapper()
        val deviceId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        mInputDeviceRemapper.remapAxis(
            /* userId = */ 0,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        removeInputDevice(deviceId, device.descriptor)

        verify(native).setAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))
    }

    @Test
    fun testSetCurrentUser_appliesAxisRemappings() {
        setupControllerRemapper()
        val deviceId = 1
        val userId = 0
        val newUserId = 1
        val device =
            createDevice(
                deviceId,
                vendorId = 123,
                productId = 456,
                sources = InputDevice.SOURCE_JOYSTICK,
            )
        mInputDeviceRemapper.remapAxis(
            /* userId = */ 0,
            device.identifier,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
        )
        addInputDevice(deviceId, device.descriptor, device)
        reset(native)

        // Switch to a different user with no remapping
        mInputDeviceRemapper.setCurrentUserId(newUserId)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native).setAxisRemappingForDevice(eq(deviceId), eq(intArrayOf()), eq(intArrayOf()))

        // Switch back to the user with remapping
        mInputDeviceRemapper.setCurrentUserId(userId)
        TestUtils.flushLoopers(mainLooperManager)
        verify(native)
            .setAxisRemappingForDevice(
                eq(deviceId),
                eq(intArrayOf(MotionEvent.AXIS_X)),
                eq(intArrayOf(MotionEvent.AXIS_Y)),
            )
    }

    private fun addInputDevice(deviceId: Int, descriptor: String, device: InputDevice) {
        inputDeviceIds.add(deviceId)
        whenever(inputManager.getInputDevice(deviceId)).thenReturn(device)
        whenever(inputManager.getInputDeviceByDescriptor(descriptor)).thenReturn(device)
        mInputDeviceRemapper.onInputDeviceAdded(deviceId)
    }

    private fun removeInputDevice(deviceId: Int, descriptor: String) {
        inputDeviceIds.remove(deviceId)
        whenever(inputManager.getInputDevice(deviceId)).thenReturn(null)
        whenever(inputManager.getInputDeviceByDescriptor(descriptor)).thenReturn(null)
        mInputDeviceRemapper.onInputDeviceRemoved(deviceId)
    }

    private fun createDevice(
        deviceId: Int,
        vendorId: Int,
        productId: Int,
        sources: Int = InputDevice.SOURCE_KEYBOARD,
    ): InputDevice =
        InputDevice.Builder()
            .setId(deviceId)
            .setVendorId(vendorId)
            .setProductId(productId)
            .setName("Device $deviceId")
            .setDescriptor("descriptor $deviceId")
            .setSources(sources)
            .build()
}
