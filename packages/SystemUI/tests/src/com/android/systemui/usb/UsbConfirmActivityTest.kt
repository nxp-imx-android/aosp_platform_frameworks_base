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

import android.hardware.usb.flags.Flags.FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.app.AlertController
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import javax.inject.Inject
import org.junit.Test
import org.junit.runner.RunWith

/** UsbConfirmActivityTest */
@RunWith(AndroidJUnit4::class)
@SmallTest
@TestableLooper.RunWithLooper
class UsbConfirmActivityTest :
    UsbDialogActivityTest<UsbConfirmActivityTest.UsbConfirmActivityTestable>() {
    class UsbConfirmActivityTestable
    @Inject
    constructor(val message: UsbAudioWarningDialogMessage) :
        UsbConfirmActivity(message), UsbDialogActivityTestable {
        override fun getAlertParams(): AlertController.AlertParams {
            return mAlertParams
        }
    }

    override fun createActivity(): UsbConfirmActivityTestable {
        return UsbConfirmActivityTestable(mMessage)
    }

    override fun getActivityClass(): Class<UsbConfirmActivityTestable> {
        return UsbConfirmActivityTestable::class.java
    }

    // Tests for old Usb Dialog UI

    @Test
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryDialog() {
        deviceConfiguration(isUsbDevice = false)

        checkNewUiIsNotInitialized()

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_confirm_prompt_title,
                mAppName,
                USB_ACCESSORY_DESCRIPTION,
            )
        assertThat(mAlertParams.mTitle).isEqualTo(expectedTitle)

        // Usb Accessory shouldn't have a message.
        assertThat(mAlertParams.mMessage).isNull()

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
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceDialogTitleAndMessage() {
        deviceConfiguration(isUsbDevice = true, hasAudioCapture = true)

        checkNewUiIsNotInitialized()

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_confirm_prompt_title,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(mAlertParams.mTitle).isEqualTo(expectedTitle)

        val expectedMessage: String =
            context.getString(
                R.string.usb_audio_device_prompt_warn,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(mAlertParams.mMessage).isEqualTo(expectedMessage)

        // Dialog shouldn't have a checkbox, if there is record warning.
        assertThat(mAlertParams.mView).isNull()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceDialogTitleAndCheckbox() {
        deviceConfiguration(isUsbDevice = true)

        checkNewUiIsNotInitialized()

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_confirm_prompt_title,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(mAlertParams.mTitle).isEqualTo(expectedTitle)

        // Dialog shouldn't have a message, if it is not an audio device.
        assertThat(mAlertParams.mMessage).isNull()

        val alwaysUseView: CheckBox? =
            mAlertParams.mView.findViewById(com.android.internal.R.id.alwaysUse)
        assertThat(alwaysUseView!!.text)
            .isEqualTo(
                context.getString(R.string.always_use_device, mAppName, USB_DEVICE_PRODUCT_NAME)
            )
    }

    @Test
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceDialog() {
        deviceConfiguration(
            isUsbDevice = true,
            hasAudioCapture = true,
            audioRecordingPermissionStatus = android.content.pm.PackageManager.PERMISSION_GRANTED,
        )

        checkNewUiIsNotInitialized()

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_confirm_prompt_title,
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

    // Tests for new Usb Dialog UI

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryNewDialog() {
        deviceConfiguration(isUsbDevice = false)

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_confirm_prompt_title,
                mAppName,
                USB_ACCESSORY_DESCRIPTION,
            )
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text)
            .isEqualTo(expectedTitle)

        // Usb Accessory shouldn't have a message.
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).visibility)
            .isEqualTo(View.GONE)

        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .findViewById<CheckBox>(com.android.internal.R.id.alwaysUse)
                    .text
            )
            .isEqualTo(
                context.getString(
                    R.string.always_use_accessory,
                    mAppName,
                    USB_ACCESSORY_DESCRIPTION,
                )
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialogTitleAndMessage() {
        deviceConfiguration(isUsbDevice = true, hasAudioCapture = true)

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_confirm_prompt_title,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text)
            .isEqualTo(expectedTitle)

        val expectedMessage: String =
            context.getString(
                R.string.usb_audio_device_prompt_warn,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).text)
            .isEqualTo(expectedMessage)

        // Dialog shouldn't have a checkbox, if there is record warning.
        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .visibility
            )
            .isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialogTitleAndCheckbox() {
        deviceConfiguration(isUsbDevice = true)

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_confirm_prompt_title,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text)
            .isEqualTo(expectedTitle)

        // Dialog shouldn't have a message, if it is not an audio device.
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).visibility)
            .isEqualTo(View.GONE)

        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .findViewById<CheckBox>(com.android.internal.R.id.alwaysUse)
                    .text
            )
            .isEqualTo(
                context.getString(R.string.always_use_device, mAppName, USB_DEVICE_PRODUCT_NAME)
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialog() {
        deviceConfiguration(
            isUsbDevice = true,
            hasAudioCapture = true,
            audioRecordingPermissionStatus = android.content.pm.PackageManager.PERMISSION_GRANTED,
        )

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        val expectedTitle: String =
            context.getString(
                R.string.usb_audio_device_confirm_prompt_title,
                mAppName,
                USB_DEVICE_PRODUCT_NAME,
            )
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text)
            .isEqualTo(expectedTitle)

        val expectedMessage: String =
            context.getString(R.string.usb_audio_device_prompt, mAppName, USB_DEVICE_PRODUCT_NAME)
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).text)
            .isEqualTo(expectedMessage)

        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .findViewById<CheckBox>(com.android.internal.R.id.alwaysUse)
                    .text
            )
            .isEqualTo(
                context.getString(R.string.always_use_device, mAppName, USB_DEVICE_PRODUCT_NAME)
            )
    }
}
