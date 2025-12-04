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

package com.android.systemui.usb

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.PermissionChecker
import android.hardware.usb.IUsbSerialReader
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.intercepting.SingleActivityFactory
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.internal.app.AlertController
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoSession
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

abstract class UsbDialogActivityTest<T> : SysuiTestCase()
    where T : Activity, T : UsbDialogActivityTest.UsbDialogActivityTestable {
    companion object {
        protected const val PACKAGE: String = "com.android.systemui.tests"
        protected const val EXTRA_PACKAGE: String = "com.android.systemui"
        protected const val EXTRA_UID: Int = 12
        protected const val REQUEST_CODE: Int = 334
        protected const val INTENT_ACTION: String = "NO_ACTION"

        protected const val NAME: String = "name"
        protected const val VENDOR_ID: Int = 3
        protected const val PRODUCT_ID: Int = 4
        protected const val CLASS: Int = 5
        protected const val SUB_CLASS: Int = 6
        protected const val PROTOCOL: Int = 7
        protected const val MANUFACTURER: String = "manufacturer"
        protected const val MODEL: String = "model"
        protected const val USB_ACCESSORY_DESCRIPTION: String = "description"
        protected const val USB_DEVICE_PRODUCT_NAME: String = "productName"
        protected const val VERSION: String = "version"
        protected const val URI: String = "uri"
        protected const val SERIAL_NUMBER: String = "serialNumber"
        protected const val HAS_MIDI: Boolean = false
        protected const val HAS_VIDEO_PLAYBACK: Boolean = false
        protected const val HAS_VIDEO_CAPTURE: Boolean = false
    }

    interface UsbDialogActivityTestable {
        fun getAlertParams(): AlertController.AlertParams
    }

    protected var mMessage: UsbAudioWarningDialogMessage = UsbAudioWarningDialogMessage()
    protected lateinit var mAppName: CharSequence
    protected lateinit var mAlertParams: AlertController.AlertParams

    private lateinit var mMockSession: MockitoSession
    private var mSerialReader: IUsbSerialReader =
        object : IUsbSerialReader.Stub() {
            override fun getSerial(packageName: String): String {
                return SERIAL_NUMBER
            }
        }

    protected abstract fun createActivity(): T

    protected abstract fun getActivityClass(): Class<T>

    @Rule
    @JvmField
    val activityRule =
        ActivityTestRule(
            // To avoid type erasure, getActivityClass() is explicitly passed to the factory's
            // constructor.
            object : SingleActivityFactory<T>(getActivityClass()) {
                override fun create(intent: Intent?): T {
                    return createActivity()
                }
            },
            false,
            false,
        )

    @Before
    fun setUp() {
        mMockSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .mockStatic(PermissionChecker::class.java)
                .startMocking()
    }

    @After
    fun tearDown() {
        activityRule.finishActivity()
        mMockSession.finishMocking()
    }

    private fun createIntent(canBeDefault: Boolean): Intent {
        return Intent(mContext, getActivityClass()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(UsbManager.EXTRA_PACKAGE, EXTRA_PACKAGE)
            putExtra(Intent.EXTRA_UID, EXTRA_UID)
            putExtra(
                Intent.EXTRA_INTENT,
                PendingIntent.getBroadcast(
                    mContext,
                    REQUEST_CODE,
                    Intent(INTENT_ACTION).apply { setPackage(PACKAGE) },
                    PendingIntent.FLAG_MUTABLE,
                ),
            )
            putExtra(UsbManager.EXTRA_CAN_BE_DEFAULT, canBeDefault)
        }
    }

    private fun createDeviceIntent(
        canBeDefault: Boolean,
        hasAudioPlayback: Boolean,
        hasAudioCapture: Boolean,
    ): Intent {
        return createIntent(canBeDefault)
            .putExtra(
                UsbManager.EXTRA_DEVICE,
                UsbDevice.Builder(
                        NAME,
                        VENDOR_ID,
                        PRODUCT_ID,
                        CLASS,
                        SUB_CLASS,
                        PROTOCOL,
                        MANUFACTURER,
                        USB_DEVICE_PRODUCT_NAME,
                        VERSION,
                        emptyArray<UsbConfiguration>(),
                        SERIAL_NUMBER,
                        hasAudioPlayback,
                        hasAudioCapture,
                        HAS_MIDI,
                        HAS_VIDEO_PLAYBACK,
                        HAS_VIDEO_CAPTURE,
                    )
                    .build(mSerialReader),
            )
    }

    private fun createAccessoryIntent(canBeDefault: Boolean): Intent {
        return createIntent(canBeDefault)
            .putExtra(
                UsbManager.EXTRA_ACCESSORY,
                UsbAccessory(
                    MANUFACTURER,
                    MODEL,
                    USB_ACCESSORY_DESCRIPTION,
                    VERSION,
                    URI,
                    mSerialReader,
                ),
            )
    }

    protected fun deviceConfiguration(
        isUsbDevice: Boolean,
        canBeDefault: Boolean = false,
        audioRecordingPermissionStatus: Int = android.content.pm.PackageManager.PERMISSION_DENIED,
        hasAudioPlayback: Boolean = false,
        hasAudioCapture: Boolean = false,
    ) {
        whenever(
                PermissionChecker.checkPermissionForPreflight(
                    any(),
                    eq(Manifest.permission.RECORD_AUDIO),
                    eq(PermissionChecker.PID_UNKNOWN),
                    eq(EXTRA_UID),
                    eq(EXTRA_PACKAGE),
                )
            )
            .thenReturn(audioRecordingPermissionStatus)

        if (isUsbDevice) {
            activityRule.launchActivity(
                createDeviceIntent(canBeDefault, hasAudioPlayback, hasAudioCapture)
            )
        } else {
            activityRule.launchActivity(createAccessoryIntent(canBeDefault))
        }

        mAppName =
            context.packageManager
                .getApplicationInfo(EXTRA_PACKAGE, 0)
                .loadLabel(context.packageManager)
        mAlertParams = activityRule.activity.getAlertParams()
    }

    @Test
    @DisableFlags(android.hardware.usb.flags.Flags.FLAG_ENABLE_PERSISTENT_DEVICE_PERMISSIONS)
    fun testHideNonSystemOverlay() {
        deviceConfiguration(isUsbDevice = false)
        assertThat(
                activityRule.activity.window.attributes.privateFlags and
                    WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
            )
            .isEqualTo(WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)
    }

    @Test
    @EnableFlags(android.hardware.usb.flags.Flags.FLAG_ENABLE_PERSISTENT_DEVICE_PERMISSIONS)
    fun testHideNonSystemOverlayNewDialog() {
        deviceConfiguration(isUsbDevice = false)
        assertThat(
                activityRule.activity.window.attributes.privateFlags and
                    WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
            )
            .isEqualTo(WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)
    }
}
