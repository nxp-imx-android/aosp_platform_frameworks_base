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
package com.android.systemui.deviceentry.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RestrictedModeInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    val underTest: RestrictedModeInteractor by lazy { kosmos.restrictedModeInteractor }

    @Test
    fun filterUserActions_filtersOutAllActionThatNavigateAwayFromBouncerOrLockscreen() =
        kosmos.runTest {
            val unfilteredFlow = MutableStateFlow<Map<UserAction, UserActionResult>>(emptyMap())
            val filteredFlow by collectLastValue(underTest.filteredUserActions(unfilteredFlow))

            fakeMobileConnectionsRepository.isAnySimSecure.value = false
            val unfilteredActions =
                mapOf(
                    Back to Scenes.Shade,
                    Swipe.Down to Scenes.QuickSettings,
                    Swipe.Up to Overlays.Bouncer,
                    Swipe.Left to Scenes.Lockscreen,
                    Swipe.Right to Scenes.Occluded,
                    Swipe.Start to Scenes.Dream,
                    Swipe.End to Scenes.Gone,
                )
            unfilteredFlow.value = unfilteredActions
            runCurrent()

            assertThat(filteredFlow).isEqualTo(unfilteredActions)

            fakeMobileConnectionsRepository.isAnySimSecure.value = true
            runCurrent()
            val expectedFilteredActions =
                mapOf(
                    Swipe.Up to Overlays.Bouncer,
                    Swipe.Left to Scenes.Lockscreen,
                    Swipe.Right to Scenes.Occluded,
                    Swipe.Start to Scenes.Dream,
                )

            assertThat(filteredFlow).isEqualTo(expectedFilteredActions)
        }

    @Test
    fun isSceneChangeAllowed_allowsOnlyLockscreenAndOccludedSceneChanges() =
        kosmos.runTest {
            fakeMobileConnectionsRepository.isAnySimSecure.value = false
            assertThat(underTest.isSceneChangeAllowed(Scenes.Shade)).isTrue()
            assertThat(underTest.isSceneChangeAllowed(Scenes.QuickSettings)).isTrue()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Dream)).isTrue()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Communal)).isTrue()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Gone)).isTrue()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Lockscreen)).isTrue()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Occluded)).isTrue()

            fakeMobileConnectionsRepository.isAnySimSecure.value = true
            runCurrent()

            assertThat(underTest.isSceneChangeAllowed(Scenes.Shade)).isFalse()
            assertThat(underTest.isSceneChangeAllowed(Scenes.QuickSettings)).isFalse()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Dream)).isTrue()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Communal)).isFalse()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Gone)).isFalse()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Lockscreen)).isTrue()
            assertThat(underTest.isSceneChangeAllowed(Scenes.Occluded)).isTrue()
        }

    @Test
    fun isOverlayChangeAllowed_allowsOnlyBouncerOverlayChanges() =
        kosmos.runTest {
            fakeMobileConnectionsRepository.isAnySimSecure.value = false
            assertThat(underTest.isOverlayChangeAllowed(Overlays.Bouncer)).isTrue()
            assertThat(underTest.isOverlayChangeAllowed(Overlays.NotificationsShade)).isTrue()
            assertThat(underTest.isOverlayChangeAllowed(Overlays.QuickSettingsShade)).isTrue()

            fakeMobileConnectionsRepository.isAnySimSecure.value = true
            runCurrent()

            assertThat(underTest.isOverlayChangeAllowed(Overlays.Bouncer)).isTrue()
            assertThat(underTest.isOverlayChangeAllowed(Overlays.NotificationsShade)).isFalse()
            assertThat(underTest.isOverlayChangeAllowed(Overlays.QuickSettingsShade)).isFalse()
        }
}
