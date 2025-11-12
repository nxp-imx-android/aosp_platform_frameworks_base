/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.desktop.domain.interactor.enableUsingDesktopStatusBar
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.notification.data.repository.FakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class KeyguardStatusBarViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    val Kosmos.underTest by
        Kosmos.Fixture {
            keyguardStatusBarViewModelFactory.create().apply { activateIn(testScope) }
        }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    fun isVisible_lockscreen_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)

            assertThat(latest).isTrue()
        }

    @Test
    fun isVisible_communal_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Communal)

            assertThat(latest).isTrue()
        }

    @Test
    fun isVisible_dozing_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)

            fakeKeyguardRepository.setIsDozing(true)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_sceneShade_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Shade)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_notificationsShadeOverlay_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.sceneContainerRepository.showOverlay(Overlays.NotificationsShade)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_quickSettingsShadeOverlay_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.sceneContainerRepository.showOverlay(Overlays.QuickSettingsShade)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_sceneBouncer_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.sceneContainerRepository.showOverlay(Overlays.Bouncer)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_useDesktopStatusBarEnabled_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)

            assertThat(latest).isTrue()

            kosmos.enableUsingDesktopStatusBar()

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isVisible_headsUpShown_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            // WHEN HUN displayed on the bypass lock screen
            headsUpNotificationRepository.setNotifications(FakeHeadsUpRowRepository("key 0", isPinned = true))
            fakeKeyguardTransitionRepository.emitInitialStepsFromOff(
                KeyguardState.LOCKSCREEN,
                testSetup = true,
            )
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            fakeDeviceEntryFaceAuthRepository.isBypassEnabled.value = true

            // THEN KeyguardStatusBar is still visible
            assertThat(latest).isTrue()
        }

    @Test
    fun isVisible_sceneLockscreen_andNotDozing_andNotShowingHeadsUpStatusBar_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            fakeKeyguardRepository.setIsDozing(false)

            assertThat(latest).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun signOutButton_isVisible_whenUserManagerLogoutIsEnabled() {
        kosmos.runTest {
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(true)
            val logoutToSystemUserCount = fakeUserRepository.logOutWithUserManagerCallCount
            fakeUserRepository.setUserManagerLogoutEnabled(true)
            fakeUserRepository.setPolicyManagerLogoutEnabled(false)
            assertThat(underTest.isSignOutButtonEnabled).isTrue()
            assertThat(underTest.isSignOutButtonVisible).isTrue()
            underTest.onSignOut()

            assertThat(fakeUserRepository.logOutWithUserManagerCallCount)
                .isEqualTo(logoutToSystemUserCount + 1)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun signOutButton_isNotVisible_whenUserManagerLogoutIsDisabled() {
        kosmos.runTest {
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(true)
            fakeUserRepository.setUserManagerLogoutEnabled(false)
            fakeUserRepository.setPolicyManagerLogoutEnabled(true)
            assertThat(underTest.isSignOutButtonEnabled).isTrue()
            assertThat(underTest.isSignOutButtonVisible).isFalse()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun signOutButton_isDisabled_whenDisabledInConfig() {
        kosmos.runTest {
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(false)
            assertThat(underTest.isSignOutButtonEnabled).isFalse()
        }
    }
}
