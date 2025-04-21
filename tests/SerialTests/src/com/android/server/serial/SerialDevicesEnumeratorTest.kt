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
 * limitations under the License
 */

package com.android.server.serial

import android.content.Context
import android.hardware.serial.SerialPortInfo
import android.os.FileUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for serial devices enumeration functions for serial service.
 *
 * atest SerialTests:SerialDevicesEnumeratorTest
 */
@RunWith(AndroidJUnit4::class)
class SerialDevicesEnumeratorTest {

    private lateinit var context: Context
    private lateinit var tmpDir: Path
    private lateinit var drivers: List<SerialTtyDriverInfo>
    private lateinit var serialDevicesEnumerator: SerialDevicesEnumerator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tmpDir = context.filesDir.toPath().resolve("tmp")
        Files.createDirectory(tmpDir)
        drivers = listOf(
            SerialTtyDriverInfo("path1", 1, 100, 150),
            SerialTtyDriverInfo("path2", 2, 200, 250),
        )
        serialDevicesEnumerator = SerialDevicesEnumerator()
    }

    @After
    fun tearDown() {
        FileUtils.deleteContentsAndDir(tmpDir.toFile())
    }

    @Test
    fun testFilterDevicesHavingSerialDrivers() {
        val devices = listOf(
            DeviceFileInfo("dev10", mkdev(1, 50)),
            DeviceFileInfo("dev11", mkdev(1, 100)),
            DeviceFileInfo("dev12", mkdev(1, 200)),
            DeviceFileInfo("dev20", mkdev(2, 100)),
            DeviceFileInfo("dev21", mkdev(2, 200)),
            DeviceFileInfo("dev22", mkdev(2, 255)),
        )

        val result = serialDevicesEnumerator.filterDevicesHavingSerialDrivers(devices, drivers)

        assertEquals(listOf("dev11", "dev21"), result.map { it.mName })
    }

    @Test
    fun testReadSerialPortInfo_notUsb() {
        FileUtils.deleteContents(tmpDir.toFile())
        val sysLink = tmpDir.resolve("device")
        Files.createDirectory(sysLink)
        val device = DeviceFileInfo("dev", 0)
        device.setSubsystem("virtual")
        device.setSysfsLink(sysLink)

        val info: SerialPortInfo = SerialDevicesEnumerator.readSerialPortInfo(device)

        assertEquals("dev", info.name)
        assertEquals(-1, info.vendorId)
        assertEquals(-1, info.productId)
    }

    @Test
    fun testReadSerialPortInfo_usbWithoutInfoFiles() {
        FileUtils.deleteContents(tmpDir.toFile())
        val sysLink = tmpDir.resolve("device")
        Files.createDirectory(sysLink)
        val device = DeviceFileInfo("dev", 0)
        device.setSubsystem("usb")
        device.setSysfsLink(sysLink)

        val info: SerialPortInfo = SerialDevicesEnumerator.readSerialPortInfo(device)

        assertEquals("dev", info.name)
        assertEquals(-1, info.vendorId)
        assertEquals(-1, info.productId)
    }

    @Test
    fun testReadSerialPortInfo_usbWithInfoFiles() {
        FileUtils.deleteContents(tmpDir.toFile())
        val idVendor = tmpDir.resolve("idVendor")
        Files.write(idVendor, listOf("0D28\n"), UTF_8)
        val idProduct = tmpDir.resolve("idProduct")
        Files.write(idProduct, listOf("0204\n"), UTF_8)
        val sysLink = tmpDir.resolve("device")
        Files.createDirectory(sysLink)
        val device = DeviceFileInfo("dev", 0)
        device.setSubsystem("usb-serial")
        device.setSysfsLink(sysLink)

        val info: SerialPortInfo = SerialDevicesEnumerator.readSerialPortInfo(device)

        assertEquals("dev", info.name)
        assertEquals(0x0D28, info.vendorId)
        assertEquals(0x0204, info.productId)
    }

    // Based on MKDEV() from kdev_t.h
    fun mkdev(major: Int, minor: Int): Long {
        return (major.toLong() shl 8) or minor.toLong()
    }
}
