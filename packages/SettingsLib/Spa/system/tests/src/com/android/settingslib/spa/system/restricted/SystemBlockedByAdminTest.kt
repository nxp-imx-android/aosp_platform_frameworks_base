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

package com.android.settingslib.spa.system.restricted

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SystemBlockedByAdminTest {
    private val context: Context =
        spy(ApplicationProvider.getApplicationContext()) {
            doNothing().whenever(mock).startActivity(any())
        }

    @Test
    fun canOverrideSwitchChecked_isTrue() {
        val systemBlockedByAdmin = SystemBlockedByAdmin(context, RESTRICTION_KEY)

        assertThat(systemBlockedByAdmin.canOverrideSwitchChecked).isTrue()
    }

    @Test
    fun showDetails() {
        val systemBlockedByAdmin = SystemBlockedByAdmin(context, RESTRICTION_KEY)

        systemBlockedByAdmin.showDetails()

        val intent = argumentCaptor { verify(context).startActivity(capture()) }.firstValue
        assertThat(intent.action).isEqualTo(Settings.ACTION_SHOW_ADMIN_SUPPORT_DETAILS)
        assertThat(intent.getStringExtra(DevicePolicyManager.EXTRA_RESTRICTION))
            .isEqualTo(RESTRICTION_KEY)
    }

    private companion object {
        const val RESTRICTION_KEY = UserManager.DISALLOW_ADJUST_VOLUME
    }
}
