/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package com.android.systemui.statusbar.notification.icon.domain.interactor

import android.content.applicationContext
import android.platform.test.annotations.DisableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryBypassRepository
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.data.repository.notificationListenerSettingsRepository
import com.android.systemui.statusbar.headsup.shared.StatusBarNoHunBehavior
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.notificationsKeyguardViewStateRepository
import com.android.systemui.statusbar.notification.domain.interactor.headsUpNotificationIconInteractor
import com.android.systemui.statusbar.notification.promoted.domain.interactor.aodPromotedNotificationInteractor
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style.Base
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.shared.byAssociatedNotifModel
import com.android.systemui.statusbar.notification.shared.byIconIsAmbient
import com.android.systemui.statusbar.notification.shared.byIconNotifKey
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationsKeyguardInteractor
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.whenever
import com.android.wm.shell.bubbles.bubbles
import com.android.wm.shell.bubbles.bubblesOptional
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationIconsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope
        get() = kosmos.testScope

    private val activeNotificationListRepository
        get() = kosmos.activeNotificationListRepository

    private val notificationsKeyguardInteractor
        get() = kosmos.notificationsKeyguardInteractor

    private val underTest =
        NotificationIconsInteractor(
            kosmos.activeNotificationListRepository,
            kosmos.bubblesOptional,
            kosmos.headsUpNotificationIconInteractor,
            kosmos.aodPromotedNotificationInteractor,
            kosmos.notificationsKeyguardViewStateRepository,
            kosmos.applicationContext,
        )

    @Before
    fun setup() {
        testScope.apply {
            activeNotificationListRepository.activeNotifications.value =
                ActiveNotificationsStore.Builder()
                    .apply { testIcons.forEach(::addIndividualNotif) }
                    .build()
        }
    }

    @Test
    fun filteredEntrySet() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.filteredNotifSet())
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsExactlyElementsIn(testIcons)
        }

    @Test
    fun filteredEntrySet_noExpandedBubbles() =
        testScope.runTest {
            whenever(kosmos.bubbles.isBubbleExpanded(eq("notif1"))).thenReturn(true)
            val filteredSet by collectLastValue(underTest.filteredNotifSet())
            assertThat(filteredSet).comparingElementsUsing(byIconNotifKey).doesNotContain("notif1")
        }

    @Test
    fun filteredEntrySet_noAmbient() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.filteredNotifSet(showAmbient = false))
            assertThat(filteredSet).comparingElementsUsing(byIconIsAmbient).doesNotContain(true)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isSuppressedFromStatusBar })
        }

    @Test
    fun filteredEntrySet_noLowPriority() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.filteredNotifSet(showLowPriority = false))
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isSilent })
        }

    @Test
    fun filteredEntrySet_noDismissed() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.filteredNotifSet(showDismissed = false))
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isRowDismissed })
        }

    @Test
    fun filteredEntrySet_noRepliedMessages() =
        testScope.runTest {
            val filteredSet by
                collectLastValue(underTest.filteredNotifSet(showRepliedMessages = false))
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isLastMessageFromReply })
        }

    @Test
    fun filteredEntrySet_noPulsing_notifsNotFullyHidden() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.filteredNotifSet(showPulsing = false))
            notificationsKeyguardInteractor.setNotificationsFullyHidden(false)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isPulsing })
        }

    @Test
    fun filteredEntrySet_noPulsing_notifsFullyHidden() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.filteredNotifSet(showPulsing = false))
            notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsAnyIn(testIcons.filter { it.isPulsing })
        }

    @Test
    fun filteredEntrySet_showAodPromoted() {
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.filteredNotifSet(showAodPromoted = true))
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsAnyIn(testIcons.filter { it.promotedContent != null })
        }
    }

    @Test
    fun filteredEntrySet_noAodPromoted() {
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.filteredNotifSet(showAodPromoted = false))
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.promotedContent != null })
        }
    }
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class AlwaysOnDisplayNotificationIconsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope
        get() = kosmos.testScope

    private val underTest
        get() = kosmos.alwaysOnDisplayNotificationIconsInteractor

    @Before
    fun setup() {
        kosmos.activeNotificationListRepository.activeNotifications.value =
            ActiveNotificationsStore.Builder()
                .apply { testIcons.forEach(::addIndividualNotif) }
                .build()
    }

    @Test
    fun filteredEntrySet_noExpandedBubbles() =
        testScope.runTest {
            whenever(kosmos.bubbles.isBubbleExpanded(eq("notif1"))).thenReturn(true)
            val filteredSet by collectLastValue(underTest.aodNotifs)
            assertThat(filteredSet).comparingElementsUsing(byIconNotifKey).doesNotContain("notif1")
        }

    @Test
    fun filteredEntrySet_noAmbient() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.aodNotifs)
            assertThat(filteredSet).comparingElementsUsing(byIconIsAmbient).doesNotContain(true)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isSuppressedFromStatusBar })
        }

    @Test
    fun filteredEntrySet_noDismissed() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.aodNotifs)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isRowDismissed })
        }

    @Test
    fun filteredEntrySet_noRepliedMessages() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.aodNotifs)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isLastMessageFromReply })
        }

    @Test
    fun filteredEntrySet_showPulsing_notifsNotFullyHidden_bypassDisabled() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.aodNotifs)
            kosmos.fakeDeviceEntryBypassRepository.setBypassEnabled(false)
            kosmos.notificationsKeyguardInteractor.setNotificationsFullyHidden(false)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsAnyIn(testIcons.filter { it.isPulsing })
        }

    @Test
    fun filteredEntrySet_showPulsing_notifsFullyHidden_bypassDisabled() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.aodNotifs)
            kosmos.fakeDeviceEntryBypassRepository.setBypassEnabled(false)
            kosmos.notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsAnyIn(testIcons.filter { it.isPulsing })
        }

    @Test
    fun filteredEntrySet_noPulsing_notifsNotFullyHidden_bypassEnabled() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.aodNotifs)
            kosmos.fakeDeviceEntryBypassRepository.setBypassEnabled(true)
            kosmos.notificationsKeyguardInteractor.setNotificationsFullyHidden(false)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isPulsing })
        }

    @Test
    fun filteredEntrySet_showPulsing_notifsFullyHidden_bypassEnabled() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.aodNotifs)
            kosmos.fakeDeviceEntryBypassRepository.setBypassEnabled(true)
            kosmos.notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsAnyIn(testIcons.filter { it.isPulsing })
        }
}

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarNotificationIconsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val underTest =
        StatusBarNotificationIconsInteractor(
            kosmos.testDispatcher,
            kosmos.notificationIconsInteractor,
            kosmos.notificationListenerSettingsRepository,
        )

    @Before
    fun setup() {
        testScope.apply {
            kosmos.activeNotificationListRepository.activeNotifications.value =
                ActiveNotificationsStore.Builder()
                    .apply { testIcons.forEach(::addIndividualNotif) }
                    .build()
        }
    }

    @Test
    fun filteredEntrySet_noExpandedBubbles() =
        testScope.runTest {
            whenever(kosmos.bubbles.isBubbleExpanded(eq("notif1"))).thenReturn(true)
            val filteredSet by collectLastValue(underTest.statusBarNotifs)
            assertThat(filteredSet).comparingElementsUsing(byIconNotifKey).doesNotContain("notif1")
        }

    @Test
    fun filteredEntrySet_noAmbient() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.statusBarNotifs)
            assertThat(filteredSet).comparingElementsUsing(byIconIsAmbient).doesNotContain(true)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isSuppressedFromStatusBar })
        }

    @Test
    fun filteredEntrySet_noLowPriority_whenDontShowSilentIcons() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.statusBarNotifs)
            kosmos.notificationListenerSettingsRepository.showSilentStatusIcons.value = false
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isSilent })
        }

    @Test
    fun filteredEntrySet_showLowPriority_whenShowSilentIcons() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.statusBarNotifs)
            kosmos.notificationListenerSettingsRepository.showSilentStatusIcons.value = true
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsAnyIn(testIcons.filter { it.isSilent })
        }

    @Test
    fun filteredEntrySet_noDismissed() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.statusBarNotifs)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isRowDismissed })
        }

    @Test
    fun filteredEntrySet_noRepliedMessages() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.statusBarNotifs)
            assertThat(filteredSet)
                .comparingElementsUsing(byAssociatedNotifModel)
                .containsNoneIn(testIcons.filter { it.isLastMessageFromReply })
        }

    @Test
    @DisableFlags(StatusBarNoHunBehavior.FLAG_NAME)
    fun filteredEntrySet_includesIsolatedIcon() =
        testScope.runTest {
            val filteredSet by collectLastValue(underTest.statusBarNotifs)
            kosmos.headsUpNotificationIconInteractor.setIsolatedIconNotificationKey("notif5")
            assertThat(filteredSet).comparingElementsUsing(byIconNotifKey).contains("notif5")
        }
}

private val testIcons =
    listOf(
        activeNotificationModel(key = "notif1", groupKey = "g1"),
        activeNotificationModel(key = "notif2", groupKey = "g2", isAmbient = true),
        activeNotificationModel(key = "notif3", groupKey = "g3", isRowDismissed = true),
        activeNotificationModel(key = "notif4", groupKey = "g4", isSilent = true),
        activeNotificationModel(key = "notif5", groupKey = "g5", isLastMessageFromReply = true),
        activeNotificationModel(key = "notif6", groupKey = "g6", isSuppressedFromStatusBar = true),
        activeNotificationModel(key = "notif7", groupKey = "g7", isPulsing = true),
        activeNotificationModel(
            key = "notif8",
            groupKey = "g8",
            promotedContent = promotedContent("notif8", Base),
        ),
    )

private fun promotedContent(
    key: String,
    style: PromotedNotificationContentModel.Style,
): PromotedNotificationContentModels {
    return PromotedNotificationContentBuilder(key).applyToShared { this.style = style }.build()
}
