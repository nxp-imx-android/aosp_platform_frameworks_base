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

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.row.data.repository.TEST_BUNDLE_SPEC
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class BundleEntryTest : SysuiTestCase() {
    private lateinit var bundleEntry: BundleEntry
    @get:Rule val setFlagsRule = SetFlagsRule()

    @Before
    fun setUp() {
        bundleEntry = BundleEntry(TEST_BUNDLE_SPEC)
    }

    @Test
    fun testTotalCount_initial_isZero() {
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(0)
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testTotalCount_addNotif() {
        bundleEntry.addChild(NotificationEntryBuilder().build())
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(1)
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testTotalCount_addGroup() {
        val groupEntry1 = GroupEntry("key", 0)
        groupEntry1.addChild(NotificationEntryBuilder().build())
        groupEntry1.addChild(NotificationEntryBuilder().build())
        bundleEntry.addChild(groupEntry1)
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(2)
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testTotalCount_addMultipleGroups() {
        val groupEntry1 = GroupEntry("key", 0)
        groupEntry1.addChild(NotificationEntryBuilder().build())
        bundleEntry.addChild(groupEntry1)

        val groupEntry2 = GroupEntry("key", 0)
        groupEntry2.addChild(NotificationEntryBuilder().build())
        groupEntry2.addChild(NotificationEntryBuilder().build())
        bundleEntry.addChild(groupEntry2)

        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(3)
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testTotalCount_addNotifAndGroup() {
        val groupEntry1 = GroupEntry("key", 0)
        groupEntry1.addChild(NotificationEntryBuilder().build())
        bundleEntry.addChild(groupEntry1)
        bundleEntry.addChild(NotificationEntryBuilder().build())
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(2)
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testTotalCount_removeNotif() {
        val groupEntry1 = GroupEntry("key", 0)
        groupEntry1.addChild(NotificationEntryBuilder().build())
        bundleEntry.addChild(groupEntry1)

        val bundleNotifChild = NotificationEntryBuilder().build()
        bundleEntry.addChild(bundleNotifChild)
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(2)

        bundleEntry.removeChild(bundleNotifChild)
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(1)
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testTotalCount_removeGroupChild() {
        val groupEntry1 = GroupEntry("key", 0)
        groupEntry1.addChild(NotificationEntryBuilder().build())
        bundleEntry.addChild(groupEntry1)
        bundleEntry.addChild(NotificationEntryBuilder().build())
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(2)

        groupEntry1.clearChildren()

        // Explicitly call updateTotalCount, which is what ShadeListBuilder does via
        // BundleCoordinator's OnBeforeRenderListListener before rendering.
        bundleEntry.updateTotalCount()
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(1)
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testTotalCount_clearChildren() {
        val groupEntry1 = GroupEntry("key", 0)
        groupEntry1.addChild(NotificationEntryBuilder().build())
        bundleEntry.addChild(groupEntry1)
        bundleEntry.addChild(NotificationEntryBuilder().build())
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(2)

        bundleEntry.clearChildren()
        assertThat(bundleEntry.bundleRepository.numberOfChildren).isEqualTo(0)
    }
}
