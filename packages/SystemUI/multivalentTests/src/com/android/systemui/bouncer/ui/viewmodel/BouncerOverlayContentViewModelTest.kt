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

package com.android.systemui.bouncer.ui.viewmodel

import android.content.testableContext
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.None
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Password
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pattern
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Sim
import com.android.systemui.bouncer.domain.interactor.bouncerInteractor
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags.FULL_SCREEN_USER_SWITCHER
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.DismissAction
import com.android.systemui.keyguard.shared.model.KeyguardDone
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class BouncerOverlayContentViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private lateinit var underTest: BouncerOverlayContentViewModel

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
        underTest = kosmos.bouncerOverlayContentViewModel
        underTest.activateIn(testScope)
    }

    @Test
    fun authMethod_nonNullForSecureMethods_nullForNotSecureMethods() =
        testScope.runTest {
            var authMethodViewModel: AuthMethodBouncerViewModel?

            authMethodsToTest().forEach { authMethod ->
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                runCurrent()
                authMethodViewModel = underTest.authMethodViewModel

                if (authMethod.isSecure) {
                    assertWithMessage("View-model unexpectedly null for auth method $authMethod")
                        .that(authMethodViewModel)
                        .isNotNull()
                } else {
                    assertWithMessage(
                            "View-model unexpectedly non-null for auth method $authMethod"
                        )
                        .that(authMethodViewModel)
                        .isNull()
                }
            }
        }

    @Test
    fun authMethodChanged_doesNotReuseInstances() =
        testScope.runTest {
            val seen = mutableMapOf<AuthenticationMethodModel, AuthMethodBouncerViewModel>()

            // First pass, populate our "seen" map:
            authMethodsToTest().forEach { authMethod ->
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                runCurrent()
                underTest.authMethodViewModel?.let { seen[authMethod] = it }
            }

            // Second pass, assert same instances are not reused:
            authMethodsToTest().forEach { authMethod ->
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                runCurrent()
                underTest.authMethodViewModel?.let {
                    assertThat(it.authenticationMethod).isEqualTo(authMethod)
                    assertThat(it).isNotSameInstanceAs(seen[authMethod])
                }
            }
        }

    @Test
    fun authMethodUnchanged_reusesInstances() =
        testScope.runTest {
            authMethodsToTest().forEach { authMethod ->
                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                runCurrent()
                val firstInstance: AuthMethodBouncerViewModel? = underTest.authMethodViewModel

                kosmos.fakeAuthenticationRepository.setAuthenticationMethod(authMethod)
                runCurrent()
                val secondInstance: AuthMethodBouncerViewModel? = underTest.authMethodViewModel

                firstInstance?.let { assertThat(it.authenticationMethod).isEqualTo(authMethod) }
                assertThat(secondInstance).isSameInstanceAs(firstInstance)
            }
        }

    @Test
    fun authMethodsToTest_returnsCompleteSampleOfAllAuthMethodTypes() {
        assertThat(authMethodsToTest().map { it::class }.toSet())
            .isEqualTo(
                AuthenticationMethodModel::class
                    .sealedSubclasses
                    .filter { it != AuthenticationMethodModel.Biometric::class }
                    .toSet()
            )
    }

    @Test
    fun isInputEnabled() =
        testScope.runTest {
            val isInputEnabled by
                collectLastValue(
                    snapshotFlow { underTest.authMethodViewModel }
                        .flatMapLatest { authViewModel ->
                            authViewModel?.isInputEnabled ?: emptyFlow()
                        }
                )
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(isInputEnabled).isTrue()

            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) {
                kosmos.bouncerInteractor.authenticate(WRONG_PIN)
            }
            assertThat(isInputEnabled).isFalse()

            val lockoutEndTime = kosmos.authenticationInteractor.lockoutEndTime ?: 0.milliseconds
            advanceTimeBy(lockoutEndTime - testScope.currentTime.milliseconds)
            assertThat(isInputEnabled).isTrue()
        }

    @Test
    fun isOneHandedModeSupported() =
        testScope.runTest {
            val isOneHandedModeSupported by collectLastValue(underTest.isOneHandedModeSupported)
            kosmos.fakeFeatureFlagsClassic.set(FULL_SCREEN_USER_SWITCHER, true)
            kosmos.testableContext.orCreateTestableResources.addOverride(
                R.bool.config_enableBouncerUserSwitcher,
                true,
            )
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(isOneHandedModeSupported).isTrue()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertThat(isOneHandedModeSupported).isTrue()

            kosmos.fakeFeatureFlagsClassic.set(FULL_SCREEN_USER_SWITCHER, false)
            kosmos.testableContext.orCreateTestableResources.addOverride(
                R.bool.can_use_one_handed_bouncer,
                true,
            )
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(isOneHandedModeSupported).isTrue()

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertThat(isOneHandedModeSupported).isFalse()
            kosmos.testableContext.orCreateTestableResources.removeOverride(
                R.bool.config_enableBouncerUserSwitcher
            )
            kosmos.testableContext.orCreateTestableResources.removeOverride(
                R.bool.can_use_one_handed_bouncer
            )
        }

    @Test
    fun isFoldSplitRequired() =
        testScope.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            runCurrent()
            assertThat(underTest.isFoldSplitRequired).isTrue()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            runCurrent()
            assertThat(underTest.isFoldSplitRequired).isFalse()

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            runCurrent()
            assertThat(underTest.isFoldSplitRequired).isTrue()
        }

    @Test
    @DisableFlags(FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun dontShowSignInButton_whenFlagDisabled() =
        testScope.runTest {
            kosmos.testableContext.orCreateTestableResources.addOverride(
                R.bool.config_improveLargeScreenInteractionOnLockscreen,
                true,
            )

            assertThat(underTest.showSignInButton).isFalse()

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(underTest.showSignInButton).isFalse()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertThat(underTest.showSignInButton).isFalse()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            assertThat(underTest.showSignInButton).isFalse()
        }

    @Test
    @EnableFlags(FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun showSignInButton_whenEnabledInConfig() =
        testScope.runTest {
            kosmos.testableContext.orCreateTestableResources.addOverride(
                R.bool.config_improveLargeScreenInteractionOnLockscreen,
                true,
            )

            assertThat(underTest.showSignInButton).isFalse()

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            runCurrent()
            assertThat(underTest.showSignInButton).isFalse()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            runCurrent()
            assertThat(underTest.showSignInButton).isTrue()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            runCurrent()
            assertThat(underTest.showSignInButton).isFalse()
        }

    @Test
    @EnableFlags(FLAG_MORE_INDICATORS_AND_BUTTONS_ON_PASSWORD_BOUNCER)
    fun dontShowSignInButton_whenDisabledInConfig() =
        testScope.runTest {
            kosmos.testableContext.orCreateTestableResources.addOverride(
                R.bool.config_improveLargeScreenInteractionOnLockscreen,
                false,
            )

            assertThat(underTest.showSignInButton).isFalse()

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(underTest.showSignInButton).isFalse()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            assertThat(underTest.showSignInButton).isFalse()
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            assertThat(underTest.showSignInButton).isFalse()
        }

    @Test
    fun isSignInButtonEnabled_PasswordBouncer() =
        kosmos.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(Password)
            runCurrent()

            val passwordBouncerViewModel = underTest.authMethodViewModel as PasswordBouncerViewModel

            assertThat(underTest.isSignInButtonEnabled).isFalse()

            runOnMainThreadAndWaitForIdleSync {
                passwordBouncerViewModel.textFieldState.setTextAndPlaceCursorAtEnd("ab")
            }
            kosmos.runCurrent()
            assertThat(underTest.isSignInButtonEnabled).isTrue()

            runOnMainThreadAndWaitForIdleSync {
                passwordBouncerViewModel.textFieldState.clearText()
            }
            kosmos.runCurrent()
            assertThat(underTest.isSignInButtonEnabled).isFalse()
        }

    @Test
    fun onUiDestroyed_clearsPendingDismissAction() =
        kosmos.runTest {
            val dismissAction by collectLastValue(fakeKeyguardRepository.dismissAction)
            fakeKeyguardRepository.setDismissAction(
                DismissAction.RunImmediately(
                    onDismissAction = { KeyguardDone.IMMEDIATE },
                    onCancelAction = {},
                    message = "",
                    willAnimateOnLockscreen = true,
                )
            )
            assertThat(dismissAction).isNotEqualTo(DismissAction.None)

            underTest.onUiDestroyed()

            assertThat(dismissAction).isEqualTo(DismissAction.None)
        }

    @Test
    fun navigateBack_hidesBouncerOverlay() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            // Show bouncer
            sceneInteractor.showOverlay(Overlays.Bouncer, "reason")
            runCurrent()
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            // Navigate back
            underTest.navigateBack()
            runCurrent()
            assertThat(currentOverlays).doesNotContain(Overlays.Bouncer)
        }

    @Test
    fun backgroundColor_changesBasedOnWhetherBlurIsSupported() =
        kosmos.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = false
            runCurrent()

            assertThat(underTest.backgroundColor.alpha).isEqualTo(1.0f)

            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            runCurrent()

            assertThat(underTest.backgroundColor.alpha).isLessThan(1.0f)
        }

    private fun authMethodsToTest(): List<AuthenticationMethodModel> {
        return listOf(None, Pin, Password, Pattern, Sim)
    }

    companion object {
        private val WRONG_PIN = FakeAuthenticationRepository.DEFAULT_PIN.map { it + 1 }
    }
}
