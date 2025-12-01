/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.testing.TestableLooper
import android.widget.CheckBox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.app.AlertController
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import javax.inject.Inject
import org.junit.Test
import org.junit.runner.RunWith

/** UsbPermissionActivityTest */
@RunWith(AndroidJUnit4::class)
@SmallTest
@TestableLooper.RunWithLooper
class UsbPermissionActivityTest :
    UsbDialogActivityTest<UsbPermissionActivityTest.UsbPermissionActivityTestable>() {

    class UsbPermissionActivityTestable
    @Inject
    constructor(val message: UsbAudioWarningDialogMessage) :
        UsbPermissionActivity(message), UsbDialogActivityTestable {
        override fun getAlertParams(): AlertController.AlertParams {
            return mAlertParams
        }
    }

    override fun createActivity(): UsbPermissionActivityTestable {
        return UsbPermissionActivityTestable(mMessage)
    }

    override fun getActivityClass(): Class<UsbPermissionActivityTestable> {
        return UsbPermissionActivityTestable::class.java
    }

    @Test
    fun testUsbAccessoryDialogTitleAndMessage() {
        deviceConfiguration(isUsbDevice = false)

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_permission_prompt_title,
                mAppName,
                USB_ACCESSORY_DESCRIPTION,
            )
        assertThat(mAlertParams.mTitle).isEqualTo(expectedTitle)

        val expectedMessage: String =
            context.getString(
                R.string.usb_accessory_permission_prompt,
                mAppName,
                USB_ACCESSORY_DESCRIPTION,
            )
        assertThat(mAlertParams.mMessage).isEqualTo(expectedMessage)

        // Dialog shouldn't have a checkbox, if it can't be default.
        assertThat(mAlertParams.mView).isNull()
    }

    @Test
    fun testUsbAccessoryDialog() {
        deviceConfiguration(isUsbDevice = false, canBeDefault = true)

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_permission_prompt_title,
                mAppName,
                USB_ACCESSORY_DESCRIPTION,
            )
        assertThat(mAlertParams.mTitle).isEqualTo(expectedTitle)

        val expectedMessage: String =
            context.getString(
                R.string.usb_accessory_permission_prompt,
                mAppName,
                USB_ACCESSORY_DESCRIPTION,
            )
        assertThat(mAlertParams.mMessage).isEqualTo(expectedMessage)

        val alwaysUseView: CheckBox? =
            mAlertParams.mView.findViewById(com.android.internal.R.id.alwaysUse)
        assertThat(alwaysUseView!!.text)
            .isEqualTo(
                context.getString(
                    R.string.always_use_accessory,
                    mAppName,
                    USB_ACCESSORY_DESCRIPTION,
                )
            )
    }

    @Test
    fun testUsbDeviceDialogTitle() {
        deviceConfiguration(isUsbDevice = true)

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_permission_prompt_title,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(mAlertParams.mTitle).isEqualTo(expectedTitle)

        // Dialog shouldn't have a message, if it is not an audio device.
        assertThat(mAlertParams.mMessage).isNull()
        // Dialog shouldn't have a checkbox, if it is not an audio device.
        assertThat(mAlertParams.mView).isNull()
    }

    @Test
    fun testUsbDeviceDialogTitleAndMessage() {
        deviceConfiguration(isUsbDevice = true, hasAudioPlayback = true)

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_permission_prompt_title,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(mAlertParams.mTitle).isEqualTo(expectedTitle)

        val expectedMessage: String =
            context.getString(R.string.usb_audio_device_prompt, mAppName, USB_DEVICE_PRODUCT_NAME)
        assertThat(mAlertParams.mMessage).isEqualTo(expectedMessage)

        // Dialog shouldn't have a checkbox, if it can't be default.
        assertThat(mAlertParams.mView).isNull()
    }

    @Test
    fun testUsbDeviceDialog() {
        deviceConfiguration(
            canBeDefault = true,
            isUsbDevice = true,
            hasAudioCapture = true,
            audioRecordingPermissionStatus = android.content.pm.PackageManager.PERMISSION_GRANTED,
        )

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_permission_prompt_title,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(mAlertParams.mTitle).isEqualTo(expectedTitle)

        val expectedMessage: String =
            context.getString(R.string.usb_audio_device_prompt, mAppName, USB_DEVICE_PRODUCT_NAME)
        assertThat(mAlertParams.mMessage).isEqualTo(expectedMessage)

        val alwaysUseView: CheckBox? =
            mAlertParams.mView.findViewById(com.android.internal.R.id.alwaysUse)
        assertThat(alwaysUseView!!.text)
            .isEqualTo(
                context.getString(R.string.always_use_device, mAppName, USB_DEVICE_PRODUCT_NAME)
            )
    }
}
