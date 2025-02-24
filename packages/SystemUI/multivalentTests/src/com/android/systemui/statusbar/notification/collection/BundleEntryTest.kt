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

package com.android.systemui.statusbar.notification.collection

import android.app.Notification
import android.graphics.Color
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class BundleEntryTest : SysuiTestCase() {
    private lateinit var underTest: BundleEntry

    @get:Rule
    val setFlagsRule = SetFlagsRule()

    @Before
    fun setUp() {
        underTest = BundleEntry("key")
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getParent_adapter() {
        assertThat(underTest.entryAdapter.parent).isEqualTo(GroupEntry.ROOT_ENTRY)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isTopLevelEntry_adapter() {
        assertThat(underTest.entryAdapter.isTopLevelEntry).isTrue()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getRow_adapter() {
        assertThat(underTest.entryAdapter.row).isNull()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getGroupRoot_adapter() {
        assertThat(underTest.entryAdapter.groupRoot).isEqualTo(underTest.entryAdapter)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getKey_adapter() {
        assertThat(underTest.entryAdapter.key).isEqualTo("key")
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun isClearable_adapter() {
        assertThat(underTest.entryAdapter.isClearable).isTrue()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getSummarization_adapter() {
        assertThat(underTest.entryAdapter.summarization).isNull()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getContrastedColor_adapter() {
        assertThat(underTest.entryAdapter.getContrastedColor(context, false, Color.WHITE))
            .isEqualTo(Notification.COLOR_DEFAULT)
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun canPeek_adapter() {
        assertThat(underTest.entryAdapter.canPeek()).isFalse()
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun getWhen_adapter() {
        assertThat(underTest.entryAdapter.`when`).isEqualTo(0)
    }
}