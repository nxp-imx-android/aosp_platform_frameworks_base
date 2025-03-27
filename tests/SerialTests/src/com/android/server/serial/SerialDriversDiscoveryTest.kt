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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SerialDriversDiscoveryTest {

    private lateinit var context: Context
    private lateinit var fakeDriversFile: File
    private lateinit var serialDriversDiscovery: SerialDriversDiscovery

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeDriversFile = File(context.getFilesDir(), "fakeDrivers")
        serialDriversDiscovery = SerialDriversDiscovery(fakeDriversFile.getAbsolutePath())
    }

    @After
    fun tearDown() {
        fakeDriversFile.delete()
    }

    @Test
    fun withValidDriversFile_discoversSerialDrivers() {
        val fileContent = """
            /dev/tty             /dev/tty        5       0 system:/dev/tty
            /dev/console         /dev/console    5       1 system:console
            /dev/ptmx            /dev/ptmx       5       2 system
            acm                  /dev/ttyACM   166 0-255 serial
            g_serial             /dev/ttyGS    235       7 serial
            ttynull              /dev/ttynull  240       0 console
            serial               /dev/ttyS       4 64-95 serial
            pty_slave            /dev/pts      136 0-1048575 pty:slave
            pty_master           /dev/ptm      128 0-1048575 pty:master
        """.trimIndent()
        setUpFakeDriversFile(fileContent)

        val drivers = serialDriversDiscovery.discover()

        assertEquals(3, drivers.size)
        assertContains(
            drivers, SerialTtyDriverInfo(
                /* devicePath */ "/dev/ttyACM",
                /* majorNumber */ 166,
                /* minorNumberFrom */ 0,
                /* minorNumberTo */ 255
            )
        )
        assertContains(
            drivers, SerialTtyDriverInfo(
                /* devicePath */ "/dev/ttyGS",
                /* majorNumber */ 235,
                /* minorNumberFrom */ 7,
                /* minorNumberTo */ 7
            )
        )
        assertContains(
            drivers, SerialTtyDriverInfo(
                /* devicePath */ "/dev/ttyS",
                /* majorNumber */ 4,
                /* minorNumberFrom */ 64,
                /* minorNumberTo */ 95
            )
        )
    }

    @Test
    fun withoutSerialDrivers_returnsEmptyList() {
        val fileContent = """
            /dev/tty             /dev/tty        5       0 system:/dev/tty
            /dev/console         /dev/console    5       1 system:console
        """.trimIndent()
        setUpFakeDriversFile(fileContent)

        val drivers = serialDriversDiscovery.discover()

        assertTrue(drivers.isEmpty())
    }

    @Test
    fun whenFileHasInvalidContent_ignoresInvalidLines() {
        val fileContent = """
            /dev/ptmx            /dev/ptmx       5       2 system
            acm                  serial
            g_serial             /dev/ttyGS    235       7 serial
        """.trimIndent()
        setUpFakeDriversFile(fileContent)

        val drivers = serialDriversDiscovery.discover()

        assertEquals(1, drivers.size)
        assertContains(
            drivers, SerialTtyDriverInfo(
                /* devicePath */ "/dev/ttyGS",
                /* majorNumber */ 235,
                /* minorNumberFrom */ 7,
                /* minorNumberTo */7
            )
        )
    }

    private fun setUpFakeDriversFile(content: String) {
        fakeDriversFile.writeText(content)
    }

    private fun assertContains(actual: List<SerialTtyDriverInfo>, expected: SerialTtyDriverInfo) {
        val found = actual.any { item ->
            // Compare all relevant fields since Object#equals() is not overridden
            item.mDevicePath == expected.mDevicePath &&
                    item.mMajorNumber == expected.mMajorNumber &&
                    item.mMinorNumberFrom == expected.mMinorNumberFrom &&
                    item.mMinorNumberTo == expected.mMinorNumberTo
        }

        if (!found) {
            fail("Assertion failed: Actual list of drivers does not contain matching driver for driver with device path `${expected.mDevicePath}`")
        }
    }
}