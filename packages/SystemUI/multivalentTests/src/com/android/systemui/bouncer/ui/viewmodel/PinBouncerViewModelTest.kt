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

import android.platform.test.annotations.EnableFlags
import android.view.KeyEvent.KEYCODE_0
import android.view.KeyEvent.KEYCODE_4
import android.view.KeyEvent.KEYCODE_A
import android.view.KeyEvent.KEYCODE_DEL
import android.view.KeyEvent.KEYCODE_NUMPAD_0
import androidx.compose.ui.input.key.KeyEventType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.data.repository.fakeSimBouncerRepository
import com.android.systemui.haptics.msdl.bouncerHapticPlayer
import com.android.systemui.haptics.msdl.fakeMSDLPlayer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.testKosmos
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlin.random.nextInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class PinBouncerViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val onIntentionalUserInputMock: () -> Unit = mock()

    private val underTest by lazy {
        kosmos.pinBouncerViewModelFactory.create(
            isInputEnabled = MutableStateFlow(true),
            onIntentionalUserInput = onIntentionalUserInputMock,
            authenticationMethod = AuthenticationMethodModel.Pin,
            bouncerHapticPlayer = kosmos.bouncerHapticPlayer,
        )
    }

    @Before
    fun setUp() {
        overrideResource(R.string.keyguard_enter_your_pin, ENTER_YOUR_PIN)
        overrideResource(R.string.kg_wrong_pin, WRONG_PIN)
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun onShown() =
        kosmos.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            assertThat(pin).isEmpty()
            assertThat(underTest.authenticationMethod).isEqualTo(AuthenticationMethodModel.Pin)
        }

    @Test
    fun simBouncerViewModel_simAreaIsVisible() =
        kosmos.runTest {
            val underTest =
                kosmos.pinBouncerViewModelFactory.create(
                    isInputEnabled = MutableStateFlow(true),
                    onIntentionalUserInput = {},
                    authenticationMethod = AuthenticationMethodModel.Sim,
                    bouncerHapticPlayer = kosmos.bouncerHapticPlayer,
                )

            assertThat(underTest.isSimAreaVisible).isTrue()
        }

    @Test
    fun onErrorDialogDismissed_clearsDialogMessage() =
        kosmos.runTest {
            val dialogMessage by collectLastValue(underTest.errorDialogMessage)
            kosmos.fakeSimBouncerRepository.setSimVerificationErrorMessage("abc")
            assertThat(dialogMessage).isEqualTo("abc")

            underTest.onErrorDialogDismissed()

            assertThat(dialogMessage).isNull()
        }

    @Test
    fun simBouncerViewModel_autoConfirmEnabled_hintedPinLengthIsNull() =
        kosmos.runTest {
            val underTest =
                kosmos.pinBouncerViewModelFactory.create(
                    isInputEnabled = MutableStateFlow(true),
                    onIntentionalUserInput = {},
                    authenticationMethod = AuthenticationMethodModel.Pin,
                    bouncerHapticPlayer = kosmos.bouncerHapticPlayer,
                )
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)

            assertThat(hintedPinLength).isNull()
        }

    @Test
    fun onPinButtonClicked() =
        kosmos.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            verify(onIntentionalUserInputMock, never()).invoke()

            underTest.onPinButtonClicked(1)

            verify(onIntentionalUserInputMock, times(1)).invoke()
            assertThat(pin).containsExactly(1)
        }

    @Test
    fun onBackspaceButtonClicked() =
        kosmos.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            assertThat(pin).hasSize(1)

            underTest.onBackspaceButtonClicked()

            assertThat(pin).isEmpty()
        }

    @Test
    fun onPinEdit() =
        kosmos.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            underTest.onPinButtonClicked(2)
            underTest.onPinButtonClicked(3)
            underTest.onBackspaceButtonClicked()
            underTest.onBackspaceButtonClicked()
            underTest.onPinButtonClicked(4)
            underTest.onPinButtonClicked(5)

            assertThat(pin).containsExactly(1, 4, 5).inOrder()
        }

    @Test
    fun onBackspaceButtonLongPressed() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(kosmos.sceneInteractor.currentOverlays)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            underTest.onPinButtonClicked(2)
            underTest.onPinButtonClicked(3)
            underTest.onPinButtonClicked(4)
            runCurrent()

            underTest.onBackspaceButtonLongPressed()

            assertThat(pin).isEmpty()
            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    @Test
    fun onAuthenticateButtonClicked_whenCorrect() =
        kosmos.runTest {
            val authResult by
                collectLastValue(kosmos.authenticationInteractor.onAuthenticationResult)
            lockDeviceAndOpenPinBouncer()

            FakeAuthenticationRepository.DEFAULT_PIN.forEach(underTest::onPinButtonClicked)

            underTest.onAuthenticateButtonClicked()

            assertThat(authResult).isTrue()
        }

    @Test
    fun onAuthenticateButtonClicked_whenWrong() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(kosmos.sceneInteractor.currentOverlays)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            underTest.onPinButtonClicked(2)
            underTest.onPinButtonClicked(3)
            underTest.onPinButtonClicked(4)
            underTest.onPinButtonClicked(5) // PIN is now wrong!

            underTest.onAuthenticateButtonClicked()

            assertThat(pin).isEmpty()
            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    @Test
    fun onAuthenticateButtonClicked_correctAfterWrong() =
        kosmos.runTest {
            val authResult by
                collectLastValue(kosmos.authenticationInteractor.onAuthenticationResult)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            underTest.onPinButtonClicked(2)
            underTest.onPinButtonClicked(3)
            underTest.onPinButtonClicked(4)
            underTest.onPinButtonClicked(5) // PIN is now wrong!
            underTest.onAuthenticateButtonClicked()
            assertThat(pin).isEmpty()
            assertThat(authResult).isFalse()

            // Enter the correct PIN:
            FakeAuthenticationRepository.DEFAULT_PIN.forEach(underTest::onPinButtonClicked)

            underTest.onAuthenticateButtonClicked()

            assertThat(authResult).isTrue()
        }

    @Test
    fun onAutoConfirm_whenCorrect() =
        kosmos.runTest {
            // TODO(b/332768183) remove this after the bug if fixed.
            // Collect the flow so that it is hot, in the real application this is done by using a
            // refreshingFlow that relies on the UI to make this flow hot.
            val autoConfirmEnabled by
                collectLastValue(kosmos.authenticationInteractor.isAutoConfirmEnabled)
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            assertThat(autoConfirmEnabled).isTrue()
            val authResult by
                collectLastValue(kosmos.authenticationInteractor.onAuthenticationResult)
            lockDeviceAndOpenPinBouncer()

            FakeAuthenticationRepository.DEFAULT_PIN.forEach(underTest::onPinButtonClicked)

            assertThat(authResult).isTrue()
        }

    @Test
    fun onAutoConfirm_whenWrong() =
        kosmos.runTest {
            // TODO(b/332768183) remove this after the bug if fixed.
            // Collect the flow so that it is hot, in the real application this is done by using a
            // refreshingFlow that relies on the UI to make this flow hot.
            val autoConfirmEnabled by
                collectLastValue(kosmos.authenticationInteractor.isAutoConfirmEnabled)

            val currentOverlays by collectLastValue(kosmos.sceneInteractor.currentOverlays)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            assertThat(autoConfirmEnabled).isTrue()
            lockDeviceAndOpenPinBouncer()

            FakeAuthenticationRepository.DEFAULT_PIN.dropLast(1).forEach { digit ->
                underTest.onPinButtonClicked(digit)
            }
            underTest.onPinButtonClicked(
                FakeAuthenticationRepository.DEFAULT_PIN.last() + 1
            ) // PIN is now wrong!

            assertThat(pin).isEmpty()
            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    @Test
    fun onShown_againAfterSceneChange_resetsPin() =
        kosmos.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            // The user types a PIN.
            FakeAuthenticationRepository.DEFAULT_PIN.forEach(underTest::onPinButtonClicked)
            assertThat(pin).isNotEmpty()

            // The user doesn't confirm the PIN, but navigates back to the lockscreen instead.
            hideBouncer()

            // The user navigates to the bouncer again.
            showBouncer()

            // Ensure the previously-entered PIN is not shown.
            assertThat(pin).isEmpty()
        }

    @Test
    fun backspaceButtonAppearance_withoutAutoConfirm_alwaysShown() =
        kosmos.runTest {
            val backspaceButtonAppearance by collectLastValue(underTest.backspaceButtonAppearance)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )

            assertThat(backspaceButtonAppearance).isEqualTo(ActionButtonAppearance.Shown)
        }

    @Test
    fun backspaceButtonAppearance_withAutoConfirmButNoInput_isHidden() =
        kosmos.runTest {
            val backspaceButtonAppearance by collectLastValue(underTest.backspaceButtonAppearance)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            assertThat(backspaceButtonAppearance).isEqualTo(ActionButtonAppearance.Hidden)
        }

    @Test
    fun backspaceButtonAppearance_withAutoConfirmAndInput_isShownQuiet() =
        kosmos.runTest {
            val backspaceButtonAppearance by collectLastValue(underTest.backspaceButtonAppearance)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)
            runCurrent()

            underTest.onPinButtonClicked(1)

            assertThat(backspaceButtonAppearance).isEqualTo(ActionButtonAppearance.Subtle)
        }

    @Test
    fun confirmButtonAppearance_withoutAutoConfirm_alwaysShown() =
        kosmos.runTest {
            val confirmButtonAppearance by collectLastValue(underTest.confirmButtonAppearance)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )

            assertThat(confirmButtonAppearance).isEqualTo(ActionButtonAppearance.Shown)
        }

    @Test
    fun confirmButtonAppearance_withAutoConfirm_isHidden() =
        kosmos.runTest {
            val confirmButtonAppearance by collectLastValue(underTest.confirmButtonAppearance)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            assertThat(confirmButtonAppearance).isEqualTo(ActionButtonAppearance.Hidden)
        }

    @Test
    fun isDigitButtonAnimationEnabled() =
        kosmos.runTest {
            val isAnimationEnabled by collectLastValue(underTest.isDigitButtonAnimationEnabled)

            kosmos.fakeAuthenticationRepository.setPinEnhancedPrivacyEnabled(true)
            assertThat(isAnimationEnabled).isFalse()

            kosmos.fakeAuthenticationRepository.setPinEnhancedPrivacyEnabled(false)
            assertThat(isAnimationEnabled).isTrue()
        }

    @Test
    fun onPinButtonClicked_whenInputSameLengthAsHintedPin_ignoresClick() =
        kosmos.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)
            assertThat(hintedPinLength).isEqualTo(FakeAuthenticationRepository.HINTING_PIN_LENGTH)
            lockDeviceAndOpenPinBouncer()

            repeat(FakeAuthenticationRepository.HINTING_PIN_LENGTH - 1) { repetition ->
                underTest.onPinButtonClicked(repetition + 1)
                runCurrent()
            }
            kosmos.fakeAuthenticationRepository.pauseCredentialChecking()
            // If credential checking were not paused, this would check the credentials and succeed.
            underTest.onPinButtonClicked(FakeAuthenticationRepository.HINTING_PIN_LENGTH)
            runCurrent()

            // This one should be ignored because the user has already entered a number of digits
            // that's equal to the length of the hinting PIN length. It should result in a PIN
            // that's exactly the same length as the hinting PIN length.
            underTest.onPinButtonClicked(FakeAuthenticationRepository.HINTING_PIN_LENGTH + 1)
            runCurrent()

            assertThat(pin)
                .isEqualTo(
                    buildList {
                        repeat(FakeAuthenticationRepository.HINTING_PIN_LENGTH) { index ->
                            add(index + 1)
                        }
                    }
                )

            kosmos.fakeAuthenticationRepository.unpauseCredentialChecking()
            runCurrent()
            assertThat(pin).isEmpty()
        }

    @Test
    fun onPinButtonClicked_whenPinNotHinted_doesNotIgnoreClick() =
        kosmos.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(false)
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)
            assertThat(hintedPinLength).isNull()
            lockDeviceAndOpenPinBouncer()

            repeat(FakeAuthenticationRepository.HINTING_PIN_LENGTH + 1) { repetition ->
                underTest.onPinButtonClicked(repetition + 1)
                runCurrent()
            }

            assertThat(pin).hasSize(FakeAuthenticationRepository.HINTING_PIN_LENGTH + 1)
        }

    @Test
    fun onKeyboardInput_pinInput_isUpdated() =
        kosmos.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()
            val random = Random(System.currentTimeMillis())
            // Generate a random 4 digit PIN
            val expectedPin =
                with(random) { arrayOf(nextInt(0..9), nextInt(0..9), nextInt(0..9), nextInt(0..9)) }

            // Enter the PIN using NUM pad and normal number keyboard events
            underTest.onKeyEvent(KeyEventType.KeyDown, KEYCODE_0 + expectedPin[0])
            underTest.onKeyEvent(KeyEventType.KeyUp, KEYCODE_0 + expectedPin[0])

            underTest.onKeyEvent(KeyEventType.KeyDown, KEYCODE_NUMPAD_0 + expectedPin[1])
            underTest.onKeyEvent(KeyEventType.KeyUp, KEYCODE_NUMPAD_0 + expectedPin[1])

            underTest.onKeyEvent(KeyEventType.KeyDown, KEYCODE_0 + expectedPin[2])
            underTest.onKeyEvent(KeyEventType.KeyUp, KEYCODE_0 + expectedPin[2])

            // Enter an additional digit in between and delete it
            underTest.onKeyEvent(KeyEventType.KeyDown, KEYCODE_4)
            underTest.onKeyEvent(KeyEventType.KeyUp, KEYCODE_4)

            // Delete that additional digit
            underTest.onKeyEvent(KeyEventType.KeyDown, KEYCODE_DEL)
            underTest.onKeyEvent(KeyEventType.KeyUp, KEYCODE_DEL)

            // Try entering a non digit character, this should be ignored.
            underTest.onKeyEvent(KeyEventType.KeyDown, KEYCODE_A)
            underTest.onKeyEvent(KeyEventType.KeyUp, KEYCODE_A)

            underTest.onKeyEvent(KeyEventType.KeyDown, KEYCODE_NUMPAD_0 + expectedPin[3])
            underTest.onKeyEvent(KeyEventType.KeyUp, KEYCODE_NUMPAD_0 + expectedPin[3])

            assertThat(pin).containsExactly(*expectedPin)
        }

    @Test
    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    fun onDigiButtonDown_deliversKeyStandardToken() =
        kosmos.runTest {
            underTest.onDigitButtonDown(null)

            assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed)
                .isEqualTo(MSDLToken.KEYPRESS_STANDARD)
            assertThat(kosmos.fakeMSDLPlayer.latestPropertiesPlayed).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    fun onBackspaceButtonPressed_deliversKeyDeleteToken() {
        underTest.onBackspaceButtonPressed(null)

        assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isEqualTo(MSDLToken.KEYPRESS_DELETE)
        assertThat(kosmos.fakeMSDLPlayer.latestPropertiesPlayed).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    fun onBackspaceButtonLongPressed_deliversLongPressToken() {
        underTest.onBackspaceButtonLongPressed()

        assertThat(kosmos.fakeMSDLPlayer.latestTokenPlayed).isEqualTo(MSDLToken.LONG_PRESS)
        assertThat(kosmos.fakeMSDLPlayer.latestPropertiesPlayed).isNull()
    }

    private fun Kosmos.showBouncer() {
        val currentOverlays by collectLastValue(kosmos.sceneInteractor.currentOverlays)
        kosmos.sceneInteractor.showOverlay(Overlays.Bouncer, "reason")
        runCurrent()

        assertThat(currentOverlays).contains(Overlays.Bouncer)
    }

    private fun Kosmos.hideBouncer() {
        val currentOverlays by collectLastValue(kosmos.sceneInteractor.currentOverlays)
        kosmos.sceneInteractor.hideOverlay(Overlays.Bouncer, "reason")
        underTest.onHidden()
        runCurrent()

        assertThat(currentOverlays).doesNotContain(Overlays.Bouncer)
    }

    private fun Kosmos.lockDeviceAndOpenPinBouncer() {
        kosmos.fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
        showBouncer()
    }

    companion object {
        private const val ENTER_YOUR_PIN = "Enter your pin"
        private const val WRONG_PIN = "Wrong pin"
    }
}
