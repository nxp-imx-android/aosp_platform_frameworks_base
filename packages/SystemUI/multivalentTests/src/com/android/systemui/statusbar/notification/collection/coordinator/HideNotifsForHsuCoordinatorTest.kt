/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import android.multiuser.Flags.FLAG_HSU_DISABLE_NOTIFICATIONS
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter
import com.android.systemui.statusbar.notification.collection.notifPipeline
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.fakeSelectedUserInteractor
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class HideNotifsForHsuCoordinatorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val pipeline = kosmos.notifPipeline
    private val selectedUserInteractor = kosmos.fakeSelectedUserInteractor
    private val entry = kosmos.buildNotificationEntry()

    private lateinit var filter: NotifFilter

    @Before
    fun setUp() {
        val coordinator = HideNotifsForHsuCoordinator(selectedUserInteractor)
        coordinator.attach(pipeline)
        filter = withArgCaptor { verify(pipeline).addPreGroupFilter(capture()) }
    }

    @DisableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testDoNotFilterOutNotifsForNonHsu_disableNotificationsFlagDisabled() {
        // GIVEN that the current user is NOT the headless system user
        whenever(selectedUserInteractor.isCurrentUserHeadlessSystemUser)
            .thenReturn(MutableStateFlow(false))

        // THEN notifications should NOT be filtered out
        assertWithMessage("shouldFilterOut()").that(filter.shouldFilterOut(entry, 0)).isFalse()
    }

    @EnableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testDoNotFilterOutNotifsForNonHsu_disableNotificationsFlagEnabled() {
        // GIVEN that the current user is NOT the headless system user
        whenever(selectedUserInteractor.isCurrentUserHeadlessSystemUser)
            .thenReturn(MutableStateFlow(false))

        // THEN notifications should NOT be filtered out
        assertWithMessage("shouldFilterOut()").that(filter.shouldFilterOut(entry, 0)).isFalse()
    }

    @DisableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testDoNotFilterOutNotifsForHsu_disableNotificationsFlagDisabled() {
        // GIVEN that the current user IS the headless system user AND the flag is disabled
        whenever(selectedUserInteractor.isCurrentUserHeadlessSystemUser)
            .thenReturn(MutableStateFlow(true))

        // THEN notifications should NOT be filtered out
        assertWithMessage("shouldFilterOut()").that(filter.shouldFilterOut(entry, 0)).isFalse()
    }

    @EnableFlags(FLAG_HSU_DISABLE_NOTIFICATIONS)
    @Test
    fun testFilterOutNotifsForHsu_disableNotificationsFlagEnabled() {
        // GIVEN that the current user IS the headless system user AND the flag is enabled
        whenever(selectedUserInteractor.isCurrentUserHeadlessSystemUser)
            .thenReturn(MutableStateFlow(true))

        // THEN notifications SHOULD be filtered out
        assertWithMessage("shouldFilterOut()").that(filter.shouldFilterOut(entry, 0)).isTrue()
    }
}
