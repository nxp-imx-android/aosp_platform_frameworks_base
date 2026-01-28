/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.accessibility.hearingaid

import android.app.Notification
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.BatteryLevelsInfo
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.FakeBroadcastDispatcher
import com.android.systemui.broadcast.logging.BroadcastDispatcherLogger
import com.android.systemui.dump.DumpManager
import com.android.systemui.res.R
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.BluetoothController
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
class HearingDeviceNotificationTest : SysuiTestCase() {
    private val bluetoothController = mock<BluetoothController>()
    private val notificationManager = mock<NotificationManager>()
    private val cachedDevice = mock<CachedBluetoothDevice>()
    private val device = mock<BluetoothDevice>()

    private val mainExecutor = FakeExecutor(FakeSystemClock())
    private lateinit var broadcastDispatcher: FakeBroadcastDispatcher
    private lateinit var underTest: HearingDeviceNotification
    private val dismissController = mock<HearingDeviceNotificationDismissController>()

    @Before
    fun setUp() {
        cachedDevice.stub {
            on { device } doReturn device
            on { address } doReturn TEST_DEVICE_ADDRESS
            on { name } doReturn TEST_DEVICE_NAME
            on { isConnected } doReturn true
            on { isHearingDevice } doReturn true
            on { batteryLevel } doReturn 50
        }
        bluetoothController.stub { on { connectedDevices } doReturn listOf(cachedDevice) }
        dismissController.stub {
            on { updateAndCheckNotification(anyString(), anyInt()) } doReturn true
        }

        broadcastDispatcher =
            FakeBroadcastDispatcher(
                context,
                mainExecutor,
                TestableLooper.get(this).looper,
                mainExecutor,
                mock<DumpManager>(),
                mock<BroadcastDispatcherLogger>(),
                mock<UserTracker>(),
                false,
            )

        underTest =
            HearingDeviceNotification(
                context,
                bluetoothController,
                broadcastDispatcher,
                notificationManager,
                dismissController,
                mainExecutor,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_fastPairDevice_cancelNotification() {
        device.stub {
            on { getMetadata(BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET) } doReturn
                "true".toByteArray()
        }

        underTest.start()
        mainExecutor.runAllReady()

        verify(notificationManager).cancel(anyString(), anyInt())
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_batteryInfoAvailable_showLeftRightBattery() {
        val batteryInfo = BatteryLevelsInfo(50, 60, -1, -1)
        cachedDevice.stub { on { batteryLevelsInfo } doReturn batteryInfo }

        underTest.start()
        mainExecutor.runAllReady()

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())

        val notification = notificationCaptor.value
        val expectedMessage =
            context.getString(
                R.string.accessibility_hearing_device_left_right_battery_level,
                "50%",
                "60%",
            )
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT))
            .isEqualTo(expectedMessage)
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_leftBatteryInfoAvailable_showLeftBattery() {
        val batteryInfo = BatteryLevelsInfo(50, BluetoothDevice.BATTERY_LEVEL_UNKNOWN, -1, -1)
        cachedDevice.stub { on { batteryLevelsInfo } doReturn batteryInfo }

        underTest.start()
        mainExecutor.runAllReady()

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())

        val notification = notificationCaptor.value
        val expectedMessage =
            context.getString(R.string.accessibility_hearing_device_left_battery_level, "50%")
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT))
            .isEqualTo(expectedMessage)
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_rightBatteryInfoAvailable_showRightBattery() {
        val batteryInfo = BatteryLevelsInfo(BluetoothDevice.BATTERY_LEVEL_UNKNOWN, 60, -1, -1)
        cachedDevice.stub { on { batteryLevelsInfo } doReturn batteryInfo }

        underTest.start()
        mainExecutor.runAllReady()

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())

        val notification = notificationCaptor.value
        val expectedMessage =
            context.getString(R.string.accessibility_hearing_device_right_battery_level, "60%")
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT))
            .isEqualTo(expectedMessage)
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_shouldNotShowNotification_noNotification() {
        dismissController.stub {
            on { updateAndCheckNotification(anyString(), anyInt()) } doReturn false
        }

        underTest.start()
        mainExecutor.runAllReady()

        verify(notificationManager, never()).notify(anyString(), anyInt(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_deviceActive_showActiveSubtext() {
        cachedDevice.stub {
            on { isActiveDevice(BluetoothProfile.HEARING_AID) } doReturn true
            on { isActiveDevice(BluetoothProfile.LE_AUDIO) } doReturn false
        }

        underTest.start()
        mainExecutor.runAllReady()

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
        assertThat(notificationCaptor.value.extras.getString(Notification.EXTRA_SUB_TEXT))
            .isEqualTo(context.getString(R.string.quick_settings_hearing_devices_connected))
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_deviceConnectedNotActive_showConnectedSubtext() {
        cachedDevice.stub {
            on { isActiveDevice(BluetoothProfile.HEARING_AID) } doReturn false
            on { isActiveDevice(BluetoothProfile.LE_AUDIO) } doReturn false
        }

        underTest.start()
        mainExecutor.runAllReady()

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
        assertThat(notificationCaptor.value.extras.getString(Notification.EXTRA_SUB_TEXT))
            .isEqualTo(context.getString(R.string.quick_settings_bluetooth_device_connected))
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_verifyVisibilityAndLabel() {
        underTest.start()
        mainExecutor.runAllReady()

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())

        val notification = notificationCaptor.value
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        assertThat(notification.extras.getString(Notification.EXTRA_SUBSTITUTE_APP_NAME))
            .isEqualTo(context.getString(com.android.internal.R.string.android_system_label))
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_isActive_showActiveHearingDeviceStatus() {
        val activeDevice =
            cachedDevice.stub { on { isActiveDevice(BluetoothProfile.HEARING_AID) } doReturn true }
        val otherDevice = mock<CachedBluetoothDevice>()
        bluetoothController.stub {
            on { connectedDevices } doReturn listOf(otherDevice, activeDevice)
        }

        underTest.start()
        mainExecutor.runAllReady()

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
        assertThat(notificationCaptor.value.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo(activeDevice.name)
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_isNotActive_showConnectedHearingDeviceStatus() {
        val connectedDevice =
            cachedDevice.stub { on { isActiveDevice(BluetoothProfile.HEARING_AID) } doReturn false }
        val otherDevice = mock<CachedBluetoothDevice>()
        bluetoothController.stub {
            on { connectedDevices } doReturn listOf(otherDevice, connectedDevice)
        }

        underTest.start()
        mainExecutor.runAllReady()

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
        assertThat(notificationCaptor.value.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo(connectedDevice.name)
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_noHearingDevices_cancelNotification() {
        bluetoothController.stub { on { connectedDevices } doReturn emptyList() }

        underTest.start()
        mainExecutor.runAllReady()

        verify(notificationManager).cancel(anyString(), anyInt())
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_registerCallbackForMemberDevices() {
        val mockMemberDevice = getMemberDevice()
        val mockMainDevice =
            cachedDevice.stub { on { memberDevice } doReturn setOf(mockMemberDevice) }

        underTest.start()
        mainExecutor.runAllReady()

        verify(mockMainDevice).registerCallback(any(), any())
        verify(mockMemberDevice).registerCallback(any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_registerCallbackForSubDevice() {
        val mockSubDevice = getMemberDevice()
        val mockMainDevice = cachedDevice.stub { on { subDevice } doReturn mockSubDevice }

        underTest.start()
        mainExecutor.runAllReady()

        verify(mockMainDevice).registerCallback(any(), any())
        verify(mockSubDevice).registerCallback(any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun onDeviceAttributesChanged_updateNotification() {
        underTest.start()
        mainExecutor.runAllReady()
        reset(notificationManager)
        underTest.onDeviceAttributesChanged()
        mainExecutor.runAllReady()

        verify(notificationManager).notify(anyString(), anyInt(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun onBluetoothStateChange_disabled_cancelNotification() {
        underTest.start()
        mainExecutor.runAllReady()
        underTest.onBluetoothStateChange(false)
        mainExecutor.runAllReady()

        verify(notificationManager).cancel(anyString(), anyInt())
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun start_verifyChannelVisibility() {
        underTest.start()
        mainExecutor.runAllReady()

        val channelCaptor = ArgumentCaptor.forClass(android.app.NotificationChannel::class.java)
        verify(notificationManager).createNotificationChannel(channelCaptor.capture())

        val channel = channelCaptor.value
        assertThat(channel.lockscreenVisibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun dismissalBroadcast_callDismissOnController() {
        underTest.start()
        mainExecutor.runAllReady()
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, getDismissIntent())
        mainExecutor.runAllReady()

        verify(dismissController).dismissNotification(TEST_DEVICE_ADDRESS)
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun onBluetoothDevicesChanged_disconnect_resetController() {
        bluetoothController.stub { on { connectedDevices } doReturn listOf(cachedDevice) }

        underTest.start()
        mainExecutor.runAllReady()
        // Simulates cachedDevice disconnects
        bluetoothController.stub { on { connectedDevices } doReturn listOf() }
        underTest.onBluetoothDevicesChanged()
        mainExecutor.runAllReady()

        verify(dismissController).reset()
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun onBluetoothStateChange_disabled_resetController() {
        underTest.start()
        mainExecutor.runAllReady()
        underTest.onBluetoothStateChange(false)
        mainExecutor.runAllReady()

        verify(dismissController).reset()
    }

    private fun getMemberDevice(): CachedBluetoothDevice {
        return mock<CachedBluetoothDevice> {
            on { name } doReturn TEST_MEMBER_DEVICE_NAME
            on { address } doReturn TEST_MEMBER_DEVICE_ADDRESS
            on { isConnected } doReturn true
            on { isHearingDevice } doReturn true
            on { batteryLevel } doReturn 60
        }
    }

    private fun getDismissIntent(): Intent {
        return Intent("com.android.systemui.accessibility.hearingaid.DISMISS_NOTIFICATION").apply {
            putExtra("device_address", TEST_DEVICE_ADDRESS)
        }
    }

    companion object {
        private const val TEST_DEVICE_NAME = "test_device_name"
        private const val TEST_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        private const val TEST_MEMBER_DEVICE_NAME = "test_member_device_name"
        private const val TEST_MEMBER_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:GG"
    }
}
