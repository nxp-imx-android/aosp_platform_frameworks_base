/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.dreams.ui.viewmodel

import android.content.ComponentName
import android.content.pm.UserInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.dreams.data.repository.dreamRepository
import com.android.systemui.dreams.data.repository.fake
import com.android.systemui.dreams.shared.model.DreamItemModel
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.settings.userTracker
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamEdgeSwipeViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { dreamEdgeSwipeViewModel }

    @Before
    fun setUp() {
        val job = with(kosmos) { underTest.activateIn(testScope) }
        onTeardown { job.cancel() }
        kosmos.fakeUserRepository.setUserInfos(listOf(USER))
        runBlocking { kosmos.fakeUserRepository.setSelectedUserInfo(USER) }
    }

    @Test
    fun onSwipeStarted_ltr_left_goesToPrevious() =
        kosmos.runTest {
            setLayoutDirection(isLeftToRight = true)
            setDreamPlaylist(PREVIOUS_DREAM, ACTIVE_DREAM, NEXT_DREAM, activeIndex = 1)

            val claimed = underTest.onSwipeStarted(isFromLeft = true, startY = 100f)

            assertThat(claimed).isTrue()
            assertThat(underTest.uiState.isVisible).isTrue()
            assertThat(underTest.uiState.targetDream?.componentName)
                .isEqualTo(PREVIOUS_DREAM.componentName)
            assertThat(underTest.uiState.isFromLeft).isTrue()
        }

    @Test
    fun onSwipeStarted_ltr_right_goesToNext() =
        kosmos.runTest {
            setLayoutDirection(isLeftToRight = true)
            setDreamPlaylist(PREVIOUS_DREAM, ACTIVE_DREAM, NEXT_DREAM, activeIndex = 1)

            val claimed = underTest.onSwipeStarted(isFromLeft = false, startY = 100f)

            assertThat(claimed).isTrue()
            assertThat(underTest.uiState.isVisible).isTrue()
            assertThat(underTest.uiState.targetDream?.componentName)
                .isEqualTo(NEXT_DREAM.componentName)
            assertThat(underTest.uiState.isFromLeft).isFalse()
        }

    @Test
    fun onSwipeStarted_rtl_left_goesToNext() =
        kosmos.runTest {
            setLayoutDirection(isLeftToRight = false)
            setDreamPlaylist(PREVIOUS_DREAM, ACTIVE_DREAM, NEXT_DREAM, activeIndex = 1)

            val claimed = underTest.onSwipeStarted(isFromLeft = true, startY = 100f)

            assertThat(claimed).isTrue()
            assertThat(underTest.uiState.isVisible).isTrue()
            assertThat(underTest.uiState.targetDream?.componentName)
                .isEqualTo(NEXT_DREAM.componentName)
            assertThat(underTest.uiState.isFromLeft).isTrue()
        }

    @Test
    fun onSwipeStarted_rtl_right_goesToPrevious() =
        kosmos.runTest {
            setLayoutDirection(isLeftToRight = false)
            setDreamPlaylist(PREVIOUS_DREAM, ACTIVE_DREAM, NEXT_DREAM, activeIndex = 1)

            val claimed = underTest.onSwipeStarted(isFromLeft = false, startY = 100f)

            assertThat(claimed).isTrue()
            assertThat(underTest.uiState.isVisible).isTrue()
            assertThat(underTest.uiState.targetDream?.componentName)
                .isEqualTo(PREVIOUS_DREAM.componentName)
            assertThat(underTest.uiState.isFromLeft).isFalse()
        }

    @Test
    fun onSwipeStarted_noTarget_returnsFalse() =
        kosmos.runTest {
            setLayoutDirection(isLeftToRight = true)
            setDreamPlaylist(ACTIVE_DREAM, activeIndex = 0) // Only one dream

            val claimed = underTest.onSwipeStarted(isFromLeft = true, startY = 100f)

            assertThat(claimed).isFalse()
            assertThat(underTest.uiState.isVisible).isFalse()
        }

    @Test
    fun onSwipeProgress_updatesProgress() =
        kosmos.runTest {
            setDreamPlaylist(PREVIOUS_DREAM, ACTIVE_DREAM, NEXT_DREAM, activeIndex = 1)
            underTest.onSwipeStarted(isFromLeft = true, startY = 100f)

            underTest.onSwipeProgress(dx = 50f, swipeThreshold = 100f)
            assertThat(underTest.swipeProgress).isEqualTo(0.5f)

            underTest.onSwipeProgress(dx = 150f, swipeThreshold = 100f)
            assertThat(underTest.swipeProgress).isEqualTo(1.5f) // Coerced to 1.5
        }

    @Test
    fun onSwipeEnded_committed_switchesDreamAndResetsUi() =
        kosmos.runTest {
            setDreamPlaylist(PREVIOUS_DREAM, ACTIVE_DREAM, NEXT_DREAM, activeIndex = 1)
            underTest.onSwipeStarted(isFromLeft = true, startY = 100f) // Target: PREVIOUS_DREAM

            underTest.onSwipeEnded(committed = true)

            // Verify Active Dream Switched
            val dreamState by collectLastValue(dreamRepository.dreamState)
            assertThat(dreamState?.activeDream?.componentName)
                .isEqualTo(PREVIOUS_DREAM.componentName)

            // Wait for switch to be observed and UI reset
            // Since fake repository updates immediately, waitForDreamSwitch should complete.

            assertThat(underTest.uiState.isVisible).isFalse()
            assertThat(underTest.swipeProgress).isEqualTo(0f)
        }

    @Test
    fun onSwipeEnded_cancelled_resetsUiAfterDelay() =
        kosmos.runTest {
            setDreamPlaylist(PREVIOUS_DREAM, ACTIVE_DREAM, NEXT_DREAM, activeIndex = 1)
            underTest.onSwipeStarted(isFromLeft = true, startY = 100f)

            underTest.onSwipeEnded(committed = false)

            assertThat(underTest.uiState.isReleasing).isTrue()
            assertThat(underTest.uiState.isCommitted).isFalse()

            // Verify Active Dream NOT Switched
            val dreamState by collectLastValue(dreamRepository.dreamState)
            assertThat(dreamState?.activeDream?.componentName).isEqualTo(ACTIVE_DREAM.componentName)

            // Advance time to pass cancellation delay (250ms)
            advanceTimeBy(300.milliseconds)

            assertThat(underTest.uiState.isVisible).isFalse()
            assertThat(underTest.swipeProgress).isEqualTo(0f)
        }

    private fun Kosmos.setLayoutDirection(isLeftToRight: Boolean) {
        val configuration = context.resources.configuration
        if (isLeftToRight) {
            configuration.setLayoutDirection(Locale.forLanguageTag("en-US"))
        } else {
            configuration.setLayoutDirection(Locale.forLanguageTag("ar-EG"))
        }
        fakeConfigurationRepository.onConfigurationChange(configuration)
    }

    private fun Kosmos.setDreamPlaylist(vararg dreams: DreamItemModel, activeIndex: Int = 0) {
        val playlist = DreamPlaylistModel(dreams.toList(), activeIndex)
        dreamRepository.fake.setDreamState(userTracker.userHandle, playlist)
    }

    private companion object {
        val USER = UserInfo(0, "user", UserInfo.FLAG_MAIN)

        val PREVIOUS_DREAM = DreamItemModel(ComponentName("pkg", "previous"))
        val ACTIVE_DREAM = DreamItemModel(ComponentName("pkg", "active"))
        val NEXT_DREAM = DreamItemModel(ComponentName("pkg", "next"))
    }
}
