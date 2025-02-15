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

package com.android.systemui.statusbar.notification.promoted.domain.interactor

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.chips.notification.domain.interactor.statusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.notification.shared.StatusBarNotifChips
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.notification.buildNotificationEntry
import com.android.systemui.statusbar.notification.buildOngoingCallEntry
import com.android.systemui.statusbar.notification.buildPromotedOngoingEntry
import com.android.systemui.statusbar.notification.domain.interactor.renderNotificationListInteractor
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(
    PromotedNotificationUi.FLAG_NAME,
    StatusBarNotifChips.FLAG_NAME,
    StatusBarChipsModernization.FLAG_NAME,
    StatusBarRootModernization.FLAG_NAME,
)
class PromotedNotificationsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Fixture { promotedNotificationsInteractor }

    @Before
    fun setUp() {
        kosmos.statusBarNotificationChipsInteractor.start()
    }

    @Test
    fun orderedChipNotificationKeys_containsNonPromotedCalls() =
        kosmos.runTest {
            // GIVEN a call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = false)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(
                listOf(callEntry, ronEntry, otherEntry)
            )

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            // THEN the order of the notification keys should be the call then the RON
            assertThat(orderedChipNotificationKeys)
                .containsExactly("0|test_pkg|0|call|0", "0|test_pkg|0|ron|0")
        }

    @Test
    fun orderedChipNotificationKeys_containsPromotedCalls() =
        kosmos.runTest {
            // GIVEN a call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = true)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(
                listOf(callEntry, ronEntry, otherEntry)
            )

            val orderedChipNotificationKeys by
                collectLastValue(underTest.orderedChipNotificationKeys)

            // THEN the order of the notification keys should be the call then the RON
            assertThat(orderedChipNotificationKeys)
                .containsExactly("0|test_pkg|0|call|0", "0|test_pkg|0|ron|0")
        }

    @Test
    fun topPromotedNotificationContent_skipsNonPromotedCalls() =
        kosmos.runTest {
            // GIVEN a non-promoted call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = false)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(
                listOf(callEntry, ronEntry, otherEntry)
            )

            val topPromotedNotificationContent by
                collectLastValue(underTest.topPromotedNotificationContent)

            // THEN the ron is first because the call has no content
            assertThat(topPromotedNotificationContent?.identity?.key)
                .isEqualTo("0|test_pkg|0|ron|0")
        }

    @Test
    fun topPromotedNotificationContent_includesPromotedCalls() =
        kosmos.runTest {
            // GIVEN a promoted call and a promoted ongoing notification
            val callEntry = buildOngoingCallEntry(promoted = true)
            val ronEntry = buildPromotedOngoingEntry()
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(
                listOf(callEntry, ronEntry, otherEntry)
            )

            val topPromotedNotificationContent by
                collectLastValue(underTest.topPromotedNotificationContent)

            // THEN the call is the top notification
            assertThat(topPromotedNotificationContent?.identity?.key)
                .isEqualTo("0|test_pkg|0|call|0")
        }

    @Test
    fun topPromotedNotificationContent_nullWithNoPromotedNotifications() =
        kosmos.runTest {
            // GIVEN a a non-promoted call and no promoted ongoing entry
            val callEntry = buildOngoingCallEntry(promoted = false)
            val otherEntry = buildNotificationEntry(tag = "other")

            renderNotificationListInteractor.setRenderedList(listOf(callEntry, otherEntry))

            val topPromotedNotificationContent by
                collectLastValue(underTest.topPromotedNotificationContent)

            // THEN there is no top promoted notification
            assertThat(topPromotedNotificationContent).isNull()
        }
}
