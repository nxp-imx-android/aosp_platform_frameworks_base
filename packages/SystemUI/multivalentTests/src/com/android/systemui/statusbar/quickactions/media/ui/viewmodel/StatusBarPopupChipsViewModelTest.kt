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

package com.android.systemui.statusbar.quickactions.media.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaRepository
import com.android.systemui.media.remedia.shared.flag.MediaControlsInComposeFlag
import com.android.systemui.statusbar.quickactions.media.domain.interactor.mediaControlChipInteractor
import com.android.systemui.statusbar.quickactions.popups.StatusBarPopupChips
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.statusBarPopupChipsViewModelFactory
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipId
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@EnableFlags(StatusBarPopupChips.FLAG_NAME)
@RunWith(AndroidJUnit4::class)
class StatusBarPopupChipsViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest =
        kosmos.statusBarPopupChipsViewModelFactory.create(Display.DEFAULT_DISPLAY)

    @Before
    fun setUp() {
        kosmos.mediaControlChipInteractor.initialize()
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun shownPopupChips_allHidden_empty() =
        kosmos.runTest {
            val shownPopupChips = underTest.shownQuickActionChips
            assertThat(shownPopupChips).isEmpty()
        }

    @Test
    fun shownPopupChips_activeMedia_restHidden_mediaControlChipShown() =
        kosmos.runTest {
            val userMedia = MediaData(active = true, song = "test")

            updateMedia(userMedia)

            assertThat(underTest.shownQuickActionChips).hasSize(1)
            assertThat(underTest.shownQuickActionChips.first().chipId)
                .isEqualTo(QuickActionChipId.MediaControl)
        }

    @Test
    fun shownPopupChips_mediaChipToggled_popupShown() =
        kosmos.runTest {
            val userMedia = MediaData(active = true, song = "test")

            updateMedia(userMedia)

            assertThat(underTest.shownQuickActionChips).hasSize(1)
            val mediaChip = underTest.shownQuickActionChips.first()
            assertThat(mediaChip.isPopupShown).isFalse()

            mediaChip.showPopup.invoke(context)
            assertThat(underTest.shownQuickActionChips.first().isPopupShown).isTrue()
        }

    @Test
    fun isPopupShown_chipHiddenThenReshown_popupHidden() =
        kosmos.runTest {
            val userMedia = MediaData(active = true, song = "test")
            updateMedia(userMedia)

            assertThat(underTest.shownQuickActionChips).hasSize(1)
            var mediaChip = underTest.shownQuickActionChips.first()
            assertThat(mediaChip.isPopupShown).isFalse()

            mediaChip.showPopup.invoke(context)

            assertThat(underTest.shownQuickActionChips.first().isPopupShown).isTrue()

            // Update the media to hide the chip while the popup is still showing.
            val noMedia = MediaData(active = false, song = "")
            updateMedia(noMedia)

            assertThat(underTest.shownQuickActionChips).hasSize(0)

            updateMedia(userMedia)

            assertThat(underTest.shownQuickActionChips).hasSize(1)
            mediaChip = underTest.shownQuickActionChips.first()
            assertThat(mediaChip.isPopupShown).isFalse()
        }

    private fun Kosmos.updateMedia(mediaData: MediaData) {
        if (MediaControlsInComposeFlag.isEnabled) {
            mediaRepository.addCurrentUserMediaEntry(mediaData)
        } else {
            mediaControlChipInteractor.updateMediaControlChipModelLegacy(mediaData)
        }
    }
}
