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
 * limitations under the License.
 */
package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.NotificationChannel
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.AssistantFeedbackController
import com.android.systemui.statusbar.notification.FeedbackIcon
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.listbuilder.NotifSection
import com.android.systemui.statusbar.notification.collection.listbuilder.OnAfterRenderEntryListener
import com.android.systemui.statusbar.notification.collection.listbuilder.OnBeforeRenderListListener
import com.android.systemui.statusbar.notification.collection.notifCollection
import com.android.systemui.statusbar.notification.collection.provider.SectionStyleProvider
import com.android.systemui.statusbar.notification.collection.render.NotifRowController
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.withArgCaptor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations.initMocks

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class RowAppearanceCoordinatorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var coordinator: RowAppearanceCoordinator
    private lateinit var beforeRenderListListener: OnBeforeRenderListListener
    private lateinit var afterRenderEntryListener: OnAfterRenderEntryListener

    private lateinit var entry1: NotificationEntry
    private lateinit var entry2: NotificationEntry
    private lateinit var entry3: NotificationEntry

    @Mock private lateinit var pipeline: NotifPipeline
    @Mock private lateinit var assistantFeedbackController: AssistantFeedbackController
    @Mock private lateinit var sectionStyleProvider: SectionStyleProvider

    @Mock private lateinit var section1: NotifSection
    @Mock private lateinit var section2: NotifSection
    @Mock private lateinit var section3: NotifSection
    @Mock private lateinit var controller1: NotifRowController
    @Mock private lateinit var controller2: NotifRowController
    @Mock private lateinit var controller3: NotifRowController

    @Before
    fun setUp() {
        initMocks(this)
        coordinator = RowAppearanceCoordinator(
            mContext, assistantFeedbackController, sectionStyleProvider, kosmos.notifCollection)
        coordinator.attach(pipeline)
        beforeRenderListListener = withArgCaptor {
            verify(pipeline).addOnBeforeRenderListListener(capture())
        }
        afterRenderEntryListener = withArgCaptor {
            verify(pipeline).addOnAfterRenderEntryListener(capture())
        }
        whenever(assistantFeedbackController.getFeedbackIcon(any())).thenReturn(FeedbackIcon(1, 2))
        entry1 = kosmos.buildNotificationEntry { setSection(section1) }
        entry2 = kosmos.buildNotificationEntry { setSection(section2) }
        entry3 =
            kosmos.buildNotificationEntry {
                setChannel(NotificationChannel(NotificationChannel.RECS_ID, "recs", 2))
                setSection(section2)
            }
    }

    @Test
    fun testSetSystemExpandedOnlyOnFirstIfNotBundle() {
        whenever(sectionStyleProvider.isMinimizedSection(eq(section1))).thenReturn(false)
        whenever(sectionStyleProvider.isMinimizedSection(eq(section1))).thenReturn(false)
        beforeRenderListListener.onBeforeRenderList(listOf(entry1, entry2))
        afterRenderEntryListener.onAfterRenderEntry(entry1, controller1)
        verify(controller1).setSystemExpanded(eq(true))
        afterRenderEntryListener.onAfterRenderEntry(entry2, controller2)
        verify(controller2).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetSystemExpandedNeverIfMinimized() {
        whenever(sectionStyleProvider.isMinimizedSection(eq(section1))).thenReturn(true)
        whenever(sectionStyleProvider.isMinimizedSection(eq(section1))).thenReturn(true)
        beforeRenderListListener.onBeforeRenderList(listOf(entry1, entry2))
        afterRenderEntryListener.onAfterRenderEntry(entry1, controller1)
        verify(controller1).setSystemExpanded(eq(false))
        afterRenderEntryListener.onAfterRenderEntry(entry2, controller2)
        verify(controller2).setSystemExpanded(eq(false))
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun testSetSystemExpanded_Bundled_NotInGroup() {
        whenever(sectionStyleProvider.isMinimizedSection(eq(section3))).thenReturn(false)
        beforeRenderListListener.onBeforeRenderList(listOf(entry3))
        afterRenderEntryListener.onAfterRenderEntry(entry3, controller3)
        verify(controller3).setSystemExpanded(eq(true))
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun testSetSystemExpanded_Bundled_SingleNotifInGroup() {
        entry3.sbn.overrideGroupKey = "bundled"
        whenever(sectionStyleProvider.isMinimizedSection(eq(section3))).thenReturn(false)
        whenever(kosmos.notifCollection.isOnlyChildInGroup(entry3)).thenReturn(true)

        beforeRenderListListener.onBeforeRenderList(listOf(entry3))
        afterRenderEntryListener.onAfterRenderEntry(entry3, controller3)
        verify(controller3).setSystemExpanded(eq(true))
    }

    @Test
    @EnableFlags(NotificationBundleUi.FLAG_NAME)
    fun testSetSystemExpanded_Bundled_NotMultiChildGroup() {
        entry3.sbn.overrideGroupKey = "bundled"
        whenever(sectionStyleProvider.isMinimizedSection(eq(section3))).thenReturn(false)
        whenever(kosmos.notifCollection.isOnlyChildInGroup(entry3)).thenReturn(false)

        beforeRenderListListener.onBeforeRenderList(listOf(entry3))
        afterRenderEntryListener.onAfterRenderEntry(entry3, controller3)
        verify(controller3).setSystemExpanded(eq(false))
    }

    @Test
    fun testSetFeedbackIcon() {
        afterRenderEntryListener.onAfterRenderEntry(entry1, controller1)
        verify(controller1).setFeedbackIcon(eq(FeedbackIcon(1, 2)))
    }
}
