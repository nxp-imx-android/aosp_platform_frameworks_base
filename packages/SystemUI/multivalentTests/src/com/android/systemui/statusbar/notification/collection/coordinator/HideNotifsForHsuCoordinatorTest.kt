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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter
import com.android.systemui.statusbar.notification.collection.notifPipeline
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.fakeSelectedUserInteractor
import com.android.systemui.util.mockito.withArgCaptor
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun testDoNotFilterOutNotifsForNonHsu() {
        // GIVEN that the current user is NOT the headless system user
        whenever(selectedUserInteractor.isCurrentUserHeadlessSystemUser)
            .thenReturn(MutableStateFlow(false))

        // THEN notifications should NOT be filtered out
        assertFalse(filter.shouldFilterOut(entry, 0))
    }

    @Test
    fun testFilterOutNotifsForHsu() {
        // GIVEN that the current user IS the headless system user
        whenever(selectedUserInteractor.isCurrentUserHeadlessSystemUser)
            .thenReturn(MutableStateFlow(true))

        // THEN notifications SHOULD be filtered out
        assertTrue(filter.shouldFilterOut(entry, 0))
    }
}
