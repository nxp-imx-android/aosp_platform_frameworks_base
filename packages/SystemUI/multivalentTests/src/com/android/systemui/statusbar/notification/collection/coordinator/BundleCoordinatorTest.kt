/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.NotificationChannel
import android.app.NotificationChannel.NEWS_ID
import android.app.NotificationChannel.PROMOTIONS_ID
import android.app.NotificationChannel.RECS_ID
import android.app.NotificationChannel.SOCIAL_MEDIA_ID
import android.os.UserHandle
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.InternalNotificationsApi
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.collection.render.BundleBarn
import com.android.systemui.statusbar.notification.collection.render.NodeController
import com.android.systemui.statusbar.notification.row.data.model.AppData
import com.android.systemui.statusbar.notification.row.data.repository.TEST_BUNDLE_SPEC
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class BundleCoordinatorTest : SysuiTestCase() {
    @Mock private lateinit var newsController: NodeController
    @Mock private lateinit var socialController: NodeController
    @Mock private lateinit var recsController: NodeController
    @Mock private lateinit var promoController: NodeController
    @Mock private lateinit var bundleBarn: BundleBarn

    private lateinit var coordinator: BundleCoordinator

    private val pkg1 = "pkg1"
    private val pkg2 = "pkg2"

    private val user1 = UserHandle.of(0)
    private val user2 = UserHandle.of(1)

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        coordinator =
            BundleCoordinator(
                newsController,
                socialController,
                recsController,
                promoController,
                bundleBarn,
            )
    }

    @Test
    fun newsSectioner() {
        assertThat(coordinator.newsSectioner.isInSection(makeEntryOfChannelType(NEWS_ID))).isTrue()
        assertThat(coordinator.newsSectioner.isInSection(makeEntryOfChannelType("news"))).isFalse()
    }

    @Test
    fun socialSectioner() {
        assertThat(coordinator.socialSectioner.isInSection(makeEntryOfChannelType(SOCIAL_MEDIA_ID)))
            .isTrue()
        assertThat(coordinator.socialSectioner.isInSection(makeEntryOfChannelType("social")))
            .isFalse()
    }

    @Test
    fun recsSectioner() {
        assertThat(coordinator.recsSectioner.isInSection(makeEntryOfChannelType(RECS_ID))).isTrue()
        assertThat(coordinator.recsSectioner.isInSection(makeEntryOfChannelType("recommendations")))
            .isFalse()
    }

    @Test
    fun promoSectioner() {
        assertThat(coordinator.promoSectioner.isInSection(makeEntryOfChannelType(PROMOTIONS_ID)))
            .isTrue()
        assertThat(coordinator.promoSectioner.isInSection(makeEntryOfChannelType("promo")))
            .isFalse()
    }

    @Test
    fun testBundler_getBundleIdOrNull_returnBundleId() {
        val classifiedEntry = makeEntryOfChannelType(PROMOTIONS_ID)
        assertEquals(coordinator.bundler.getBundleIdOrNull(classifiedEntry), PROMOTIONS_ID)
    }

    @Test
    fun testBundler_getBundleIdOrNull_returnNull() {
        val unclassifiedEntry = makeEntryOfChannelType("not system channel")
        assertEquals(coordinator.bundler.getBundleIdOrNull(unclassifiedEntry), null)
    }

    @Test
    fun testUpdateAppData_emptyChildren_setsEmptyAppList() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)
        assertThat(bundle.children).isEmpty()

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(bundle.bundleRepository.appDataList).isEmpty()
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testUpdateAppData_twoNotifs() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)

        val notif1 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        val notif2 = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()

        bundle.addChild(notif1)
        bundle.addChild(notif2)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(bundle.bundleRepository.appDataList)
            .containsExactly(AppData(pkg1, user1), AppData(pkg2, user2))
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun testUpdateAppData_notifAndGroup() {
        val bundle = BundleEntry(TEST_BUNDLE_SPEC)

        val notif1 = NotificationEntryBuilder().setPkg(pkg1).setUser(user1).build()
        val group1 = GroupEntry("key", 0L)
        val groupChild = NotificationEntryBuilder().setPkg(pkg2).setUser(user2).build()
        group1.rawChildren.add(groupChild)
        val groupSummary =
            NotificationEntryBuilder()
                .setPkg(pkg2)
                .setUser(user2)
                .setGroupSummary(context, true)
                .build()
        group1.summary = groupSummary

        bundle.addChild(notif1)
        bundle.addChild(group1)

        coordinator.bundleAppDataUpdater.onBeforeRenderList(listOf(bundle))

        assertThat(bundle.bundleRepository.appDataList)
            .containsExactly(AppData(pkg1, user1), AppData(pkg2, user2))
    }

    private fun makeEntryOfChannelType(
        type: String,
        buildBlock: NotificationEntryBuilder.() -> Unit = {},
    ): NotificationEntry {
        val channel: NotificationChannel = NotificationChannel(type, type, 2)
        val entry =
            NotificationEntryBuilder()
                .updateRanking { it.setChannel(channel) }
                .also(buildBlock)
                .build()
        return entry
    }
}
