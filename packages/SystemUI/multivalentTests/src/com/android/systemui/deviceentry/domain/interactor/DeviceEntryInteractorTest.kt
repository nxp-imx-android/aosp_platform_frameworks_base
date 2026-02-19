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

package com.android.systemui.deviceentry.domain.interactor

import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.None
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Password
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pattern
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Sim
import com.android.systemui.biometrics.data.repository.fakeFingerprintPropertyRepository
import com.android.systemui.bouncer.data.repository.keyguardBouncerRepository
import com.android.systemui.bouncer.domain.interactor.alternateBouncerInteractor
import com.android.systemui.dreams.dreamStartable
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.biometricSettingsRepository
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardDismissActionInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.DismissAction
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.KeyguardDone
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useStandardTestDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.scene.data.model.SceneStack
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.domain.interactor.sceneBackInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
@TestableLooper.RunWithLooper
class DeviceEntryInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val trustRepository by lazy { kosmos.fakeTrustRepository }
    private val underTest: DeviceEntryInteractor by lazy { kosmos.deviceEntryInteractor }

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
    }

    @Test
    fun canSwipeToEnter_startsNull() =
        testKosmos().useStandardTestDispatcher().runTest {
            val underTest = deviceEntryInteractor
            val values by collectValues(underTest.canSwipeToEnter)
            assertThat(values[0]).isNull()
        }

    @Test
    fun isUnlocked_whenAuthMethodIsNoneAndLockscreenDisabled_isTrue() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(None)
            fakeKeyguardRepository.setKeyguardEnabled(false)

            val isUnlocked by collectLastValue(underTest.isUnlocked)
            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun isUnlocked_whenAuthMethodIsNoneAndLockscreenEnabled_isTrue() =
        kosmos.runTest {
            setupSwipeDeviceEntryMethod()

            val isUnlocked by collectLastValue(underTest.isUnlocked)
            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun isUnlocked_whenAuthMethodIsSimAndUnlocked_isFalse() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(Sim)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            val isUnlocked by collectLastValue(underTest.isUnlocked)
            assertThat(isUnlocked).isFalse()
        }

    @Test
    fun isDeviceEntered_onLockscreenWithSwipe_isFalse() =
        kosmos.runTest {
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)

            assertThat(isDeviceEntered).isFalse()
        }

    @Test
    fun isDeviceEntered_onShadeBeforeDismissingLockscreenWithSwipe_isFalse() =
        kosmos.runTest {
            enableSingleShade()
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)

            assertThat(isDeviceEntered).isFalse()
        }

    @Test
    fun isDeviceEntered_afterDismissingLockscreenWithSwipe_isTrue() =
        kosmos.runTest {
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Gone)

            assertThat(isDeviceEntered).isTrue()
        }

    @Test
    fun isDeviceEntered_onShadeAfterDismissingLockscreenWithSwipe_isTrue() =
        kosmos.runTest {
            enableSingleShade()
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Gone)
            switchToScene(Scenes.Shade)

            assertThat(isDeviceEntered).isTrue()
        }

    @Test
    fun isDeviceEntered_onBouncer_isFalse() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            fakeKeyguardRepository.setKeyguardEnabled(true)
            switchToScene(Scenes.Lockscreen)
            showBouncer()

            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            assertThat(isDeviceEntered).isFalse()
        }

    @Test
    fun isDeviceEntered_onDreamAfterDeviceLock_isFalse() =
        kosmos.runTest {
            dreamStartable.start()

            // Unlock device and go to home screen.
            fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            // Verify device is entered.
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).isEmpty()
            assertThat(isDeviceEntered).isTrue()

            // Dream starts, and device is unlocked.
            keyguardInteractor.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)

            // Verify device remains entered.
            assertThat(currentScene).isEqualTo(Scenes.Dream)
            assertThat(currentOverlays).isEmpty()
            assertThat(isDeviceEntered).isTrue()

            // Device locks due to timeout.
            underTest.lockNow("timeout")

            // Verify device is no longer entered.
            assertThat(currentScene).isEqualTo(Scenes.Dream)
            assertThat(currentOverlays).isEmpty()
            assertThat(isDeviceEntered).isFalse()
        }

    @Test
    fun canSwipeToEnter_onLockscreenWithSwipe_isTrue() =
        kosmos.runTest {
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)

            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            assertThat(canSwipeToEnter).isTrue()
        }

    @Test
    fun canSwipeToEnter_onLockscreenWithPin_isFalse() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            fakeKeyguardRepository.setKeyguardEnabled(true)
            switchToScene(Scenes.Lockscreen)

            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            assertThat(canSwipeToEnter).isFalse()
        }

    @Test
    fun canSwipeToEnter_afterLockscreenDismissedInSwipeMode_isFalse() =
        kosmos.runTest {
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Gone)

            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            assertThat(canSwipeToEnter).isFalse()
        }

    @Test
    fun canSwipeToEnter_whenTrustedByTrustManager_isTrue() =
        kosmos.runTest {
            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            fakeAuthenticationRepository.setAuthenticationMethod(Password)
            switchToScene(Scenes.Lockscreen)
            assertThat(canSwipeToEnter).isFalse()

            trustRepository.setCurrentUserTrusted(true)

            assertThat(canSwipeToEnter).isTrue()
        }

    @Test
    fun canSwipeToEnter_whenAuthenticatedByFace_isTrue() =
        kosmos.runTest {
            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            fakeAuthenticationRepository.setAuthenticationMethod(Password)
            switchToScene(Scenes.Lockscreen)
            assertThat(canSwipeToEnter).isFalse()

            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_NONE_UNLOCKED,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            trustRepository.setCurrentUserTrusted(false)

            assertThat(canSwipeToEnter).isTrue()
        }

    @Test
    fun canSwipeToEnter_whenNotAuthenticatedByFace_isFalse() =
        kosmos.runTest {
            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            fakeAuthenticationRepository.setAuthenticationMethod(Password)
            switchToScene(Scenes.Lockscreen)
            assertThat(canSwipeToEnter).isFalse()

            // MODE_ONLY_WAKE can occur if unlocking isn't allowed:
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_ONLY_WAKE,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            trustRepository.setCurrentUserTrusted(false)

            assertThat(canSwipeToEnter).isFalse()
        }

    @Test
    fun isAuthenticationRequired_lockedAndSecured_true() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertThat(underTest.isAuthenticationRequired()).isTrue()
        }

    @Test
    fun isAuthenticationRequired_lockedAndNotSecured_false() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            assertThat(underTest.isAuthenticationRequired()).isFalse()
        }

    @Test
    fun isAuthenticationRequired_unlockedAndSecured_false() =
        kosmos.runTest {
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertThat(underTest.isAuthenticationRequired()).isFalse()
        }

    @Test
    fun isAuthenticationRequired_unlockedAndNotSecured_false() =
        kosmos.runTest {
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            assertThat(underTest.isAuthenticationRequired()).isFalse()
        }

    @Test
    fun showOrUnlockDevice_notLocked_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_notLocked_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_authMethodNotSecure_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_authMethodNotSecure_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_authMethodNotSecure_switchesToGoneSceneWhenOnCommunal() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Communal)
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(backStack!!.asIterable().toList()).isEmpty()
        }

    @Test
    fun showOrUnlockDevice_dismissActionAnimates_runsTransitionToGone() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            // Since the dismiss action wants to animate over the lockscreen, attempting device
            // entry should animate Lockscreen -> Gone instead of replacing Lockscreen in the back
            // stack with no animation.
            kosmos.keyguardDismissActionInteractor.setDismissAction(
                DismissAction.RunAfterKeyguardGone(
                    dismissAction = { KeyguardDone.LATER },
                    onCancelAction = {},
                    message = "",
                    willAnimateOnLockscreen = true,
                )
            )
            runCurrent()
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")
            runCurrent()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(backStack!!.asIterable().toList()).isEqualTo(emptyList<SceneStack>())
        }

    @Test
    fun attemptDeviceEntrySingleShade_leaveOpenOnKeyguardHide_expandsNotificationShade() =
        kosmos.runTest {
            // GIVEN single shade
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)

            // GIVEN authentication is not required
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN keyguard showing
            switchToScene(Scenes.Lockscreen)
            runCurrent()

            // GIVEN setLeaveOpenOnKeyguardHide=true
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            runCurrent()

            // WHEN attemptDeviceEntry
            underTest.attemptDeviceEntry("test")
            runCurrent()

            // THEN shade shows and the backstack is Gone (not Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).contains(Scenes.Gone)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun attemptDeviceEntryDualShade_leaveOpenOnKeyguardHide_expandsNotificationShade() =
        kosmos.runTest {
            // GIVEN dual shade
            enableDualShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val overlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN authentication is not required
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN keyguard showing
            switchToScene(Scenes.Lockscreen)
            runCurrent()

            // GIVEN setLeaveOpenOnKeyguardHide=true
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            runCurrent()

            // WHEN attemptDeviceEntry
            underTest.attemptDeviceEntry("test")
            runCurrent()

            // THEN shade shows on the Gone Scene
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(overlays).contains(Overlays.NotificationsShade)
        }

    @Test
    fun attemptDeviceEntrySingleShade_leaveOpenOnKeyguardHide_replacesLockscreenWithGone() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)

            // GIVEN authentication is not required
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN the shade showing over keyguard
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            // GIVEN setLeaveOpenOnKeyguarddHide=true
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            runCurrent()

            // WHEN attempt device entry
            underTest.attemptDeviceEntry("test")
            runCurrent()

            // THEN shade continues to show and the backstack is Gone (not Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Gone)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun attemptDeviceEntryDualShade_leaveOpenOnKeyguardHide() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val overlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN authentication is not required
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN the shade showing over keyguard
            switchToScene(Scenes.Lockscreen)
            showOverlay(Overlays.NotificationsShade)

            // GIVEN setLeaveOpenOnKeyguarddHide=true
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            runCurrent()

            // WHEN attempt device entry
            underTest.attemptDeviceEntry("test")
            runCurrent()

            // THEN shade continues to show on Scenes.Gone
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(overlays).contains(Overlays.NotificationsShade)
        }

    @Test
    fun showOrUnlockDevice_authMethodSwipe_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeKeyguardRepository.setKeyguardEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_authMethodSwipe_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            fakeKeyguardRepository.setKeyguardEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_noAlternateBouncer_switchesToBouncerScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeFingerprintPropertyRepository.supportsRearFps() // altBouncer unsupported
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            underTest.attemptDeviceEntry("test")

            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    @Test
    fun showOrUnlockDevice_showsAlternateBouncer_staysOnLockscreenScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            givenCanShowAlternateBouncer()

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun successfulAuthenticationChallengeAttempt_updatesIsUnlockedState() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            fakeKeyguardRepository.setKeyguardEnabled(true)
            assertThat(isUnlocked).isFalse()

            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)

            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun isDeviceEntered_unlockedWhileOnShade_emitsTrue() =
        kosmos.runTest {
            enableSingleShade()
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            val isDeviceEnteredDirectly by collectLastValue(underTest.isDeviceEnteredDirectly)
            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Navigate to shade and bouncer:
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            // Simulating a "leave it open when the keyguard is hidden" which means the bouncer will
            // be shown and successful authentication should take the user back to where they are,
            // the shade scene.
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            showBouncer()
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            // Authenticate with PIN to unlock and dismiss the lockscreen:
            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)

            assertThat(isDeviceEntered).isTrue()
            assertThat(isDeviceEnteredDirectly).isFalse()
        }

    @Test
    fun isDeviceEntered_goneToCommunalAndLockDevice_emitsFalse() =
        kosmos.runTest {
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            val isDeviceEnteredDirectly by collectLastValue(underTest.isDeviceEnteredDirectly)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Navigate to communal
            switchToScene(Scenes.Communal)
            assertThat(currentScene).isEqualTo(Scenes.Communal)

            // Unlock and verify device entered
            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            switchToScene(Scenes.Gone)
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(isDeviceEntered).isTrue()
            assertThat(isDeviceEnteredDirectly).isTrue()

            // Return to communal, lock device, and verify device is not entered
            switchToScene(Scenes.Communal)
            underTest.lockNow("test")
            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
        }

    @Test
    fun lockNow_authMethodSecure_locksAndSwitchesToLockscreen() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            switchToScene(Scenes.Gone)
            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            underTest.lockNow("test")

            assertThat(isUnlocked).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun lockNow_swipeAuthMethod_switchesToLockscreen() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            setupSwipeDeviceEntryMethod() // sets auth to None and lockscreen enabled
            switchToScene(Scenes.Gone)
            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            underTest.lockNow("test")

            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun lockNow_swipeAuthMethod_whenDreamingRemainsDreamingAndAddsLockscreenToBackstack() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            setupSwipeDeviceEntryMethod() // sets auth to None and lockscreen enabled
            switchToScene(Scenes.Gone)

            // Simulate dreaming
            fakeKeyguardRepository.setDreaming(true)
            fakeKeyguardRepository.setDreamingWithOverlay(true)
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            testScope.advanceTimeBy(KeyguardInteractor.IS_DREAMING_NOT_DOZING_DELAY_MS + 100L)
            switchToScene(Scenes.Dream)
            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Dream)

            underTest.lockNow("test")

            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Dream)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)
        }

    @Test
    fun lockNow_lockscreenDisabled_doesNothing() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            fakeKeyguardRepository.setKeyguardEnabled(false)
            fakeAuthenticationRepository.setAuthenticationMethod(None)
            switchToScene(Scenes.Gone)
            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            underTest.lockNow("test")

            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    private fun Kosmos.setupSwipeDeviceEntryMethod() {
        fakeAuthenticationRepository.setAuthenticationMethod(None)
        fakeKeyguardRepository.setKeyguardEnabled(true)
    }

    private fun Kosmos.switchToScene(sceneKey: SceneKey) {
        sceneInteractor.changeScene(sceneKey, "reason")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(sceneKey)))
    }

    private fun Kosmos.showBouncer() {
        showOverlay(Overlays.Bouncer)
    }

    private fun Kosmos.showOverlay(overlay: OverlayKey) {
        sceneInteractor.showOverlay(overlay, "reason")
        sceneInteractor.setTransitionState(
            flowOf(
                ObservableTransitionState.Idle(sceneInteractor.currentScene.value, setOf(overlay))
            )
        )
    }

    private suspend fun Kosmos.givenCanShowAlternateBouncer() {
        val canShowAlternateBouncer by
            collectLastValue(alternateBouncerInteractor.canShowAlternateBouncer)
        fakeFingerprintPropertyRepository.supportsUdfps()
        fakeKeyguardTransitionRepository.sendTransitionSteps(
            from = KeyguardState.GONE,
            to = KeyguardState.LOCKSCREEN,
            testScheduler = testScope.testScheduler,
        )
        deviceEntryFingerprintAuthRepository.setLockedOut(false)
        biometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
        fakeKeyguardRepository.setKeyguardDismissible(false)
        keyguardBouncerRepository.setPrimaryShow(false)
        assertThat(canShowAlternateBouncer).isTrue()
    }

    private companion object {
        // A delay to move past the initial dreaming delay.
        const val DREAMING_DELAY_MS = KeyguardInteractor.IS_DREAMING_NOT_DOZING_DELAY_MS + 100L
    }
}
