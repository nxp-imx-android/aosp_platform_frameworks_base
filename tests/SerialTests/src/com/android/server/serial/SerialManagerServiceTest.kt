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

import android.hardware.serial.SerialPort
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for serial manager service.
 *
 * atest SerialTests:SerialManagerServiceTest
 */
@RunWith(AndroidJUnit4::class)
class SerialManagerServiceTest {
    // TODO(b/428744191) Test flag validation by invoking requestOpen() instead of toOsConstants().
    @Test
    fun testToOsConstants_success() {
        val flags = SerialPort.OPEN_FLAG_READ_WRITE or SerialPort.OPEN_FLAG_NONBLOCK
        val expected = OsConstants.O_RDWR or OsConstants.O_NONBLOCK or OsConstants.O_NOCTTY

        val actual = SerialManagerService.toOsConstants(flags)

        assertEquals(expected, actual)
    }

    @Test
    fun testToOsConstants_twoOpenModes() {
        val flags = SerialPort.OPEN_FLAG_WRITE_ONLY or SerialPort.OPEN_FLAG_READ_WRITE

        assertThrows(IllegalArgumentException::class.java) {
            SerialManagerService.toOsConstants(flags)
        }
    }

    @Test
    fun testToOsConstants_wrongBitInFlags() {
        val flags = 1 shl 31

        assertThrows(IllegalArgumentException::class.java) {
            SerialManagerService.toOsConstants(flags)
        }
    }
}