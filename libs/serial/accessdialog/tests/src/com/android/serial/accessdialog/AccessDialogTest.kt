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

package com.android.serial.accessdialog

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.serial.ISerialManager
import android.hardware.serial.SerialManager
import android.hardware.serial.SerialPort
import android.hardware.serial.SerialPortInfo
import android.hardware.serial.SerialPortListener
import android.os.Binder
import android.os.Bundle
import androidx.lifecycle.Lifecycle.State
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for access request dialog for serial ports.
 *
 * atest SerialPortAccessDialogTests
 */
@RunWith(AndroidJUnit4::class)
class AccessDialogTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock
    val mApplicationInfo: ApplicationInfo? = null

    val mToken = Binder()

    class InstrumentedAccessDialogActivity : AccessDialogActivity(sSerialAccessManager) {
        override fun getPackageManager(): PackageManager {
            return sPackageManager!!
        }

        override fun getCallingPackage(): String {
            return APP_PACKAGE_NAME
        }
    }

    @Before
    fun setUp() {
        doReturn(null).whenever(sPackageManager)!!.queryIntentServices(any(), any<Int>())
        doReturn(mApplicationInfo).whenever(sPackageManager)!!
            .getApplicationInfo(any<String>(), any<Int>())
    }

    @Test
    fun testTitle() {
        doReturn(APP_NAME).whenever(mApplicationInfo)!!.loadLabel(sPackageManager!!)

        launchActivity().use { scenario ->
            scenario.onActivity({ activity ->
                assertEquals("Allow SerialApp to access ttyS0?", activity!!.getAlertTitle())
            })
        }
    }

    @Test
    fun testClickAllow() {
        launchActivity().use {
            onView(withText("Allow")).perform(click())

            getInstrumentation().waitForIdle {
                verify(sSerialAccessManager!!).grantSerialPortAccess(PORT_NAME, UID, mToken)
            }
        }
    }

    @Test
    fun testClickDontAllow() {
        launchActivity().use {
            onView(withText("Don't allow")).perform(click())

            getInstrumentation().waitForIdle {
                verify(sSerialAccessManager!!).revokeSerialPortAccess(PORT_NAME, UID, mToken)
            }
        }
    }

    @Test
    fun testDismissDialog() {
        launchActivity().use { scenario ->
            scenario.moveToState(State.DESTROYED)

            getInstrumentation().waitForIdle {
                verify(sSerialAccessManager!!).revokeSerialPortAccess(PORT_NAME, UID, mToken)
            }
        }
    }

    @Test
    fun testDisconnectPort() {
        val context = getInstrumentation().getContext()
        val service: ISerialManager = mock()
        val port = SerialPort(context, SerialPortInfo(PORT_NAME, -1, -1), service)
        var listener: SerialPortListener? = null
        whenever(sSerialAccessManager!!.registerSerialPortListener(any(), any())).thenAnswer {
            listener = it.getArgument<SerialPortListener>(1)
        }

        launchActivity().use {
            listener!!.onSerialPortDisconnected(port)

            getInstrumentation().waitForIdle {
                verify(sSerialAccessManager!!).revokeSerialPortAccess(PORT_NAME, UID, mToken)
            }
        }
    }

    private fun launchActivity(): ActivityScenario<AccessDialogActivity> {
        val context = getInstrumentation().getContext()
        val intent = Intent(context, InstrumentedAccessDialogActivity::class.java)
        val binderExtras = Bundle()
        binderExtras.putBinder(SerialManager.EXTRA_REQUEST_TOKEN, mToken)
        with (intent) {
            putExtras(binderExtras)
            putExtra(SerialManager.EXTRA_PORT, PORT_NAME)
            putExtra(SerialManager.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
            putExtra(SerialManager.EXTRA_UID, UID)
        }
        return ActivityScenario.launch(intent)
    }

    companion object {
        @Mock
        val sPackageManager: PackageManager? = null

        @Mock
        val sSerialAccessManager: SerialAccessManager? = null

        val APP_NAME = "SerialApp"
        val APP_PACKAGE_NAME = "com.android.serial.accessdialog.AccessDialogTest"
        val PORT_NAME = "ttyS0"
        val UID = 10001
    }
}
