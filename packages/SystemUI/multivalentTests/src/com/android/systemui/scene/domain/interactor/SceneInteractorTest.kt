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

package com.android.systemui.scene.domain.interactor

import android.app.StatusBarManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.ObservableTransitionState.Transition.ShowOrHideOverlay
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.lockscreenSceneTransitionRepository
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardEnabledInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.data.repository.unlockDevice
import com.android.systemui.scene.domain.resolver.homeSceneFamilyResolver
import com.android.systemui.scene.overlayKeys
import com.android.systemui.scene.sceneContainerConfig
import com.android.systemui.scene.sceneKeys
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.shared.model.DisableFlagsModel
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SceneInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { sceneInteractor }

    @Before
    fun setUp() {
        // Init lazy Fixtures. Accessing them once makes sure that the singletons are initialized
        // and therefore starts to collect StateFlows eagerly (when there are any).
        kosmos.deviceUnlockedInteractor
        kosmos.keyguardEnabledInteractor
    }

    // TODO(b/356596436): Add tests for showing, hiding, and replacing overlays after we've defined
    //  them.
    @Test
    fun allContentKeys() =
        kosmos.runTest { assertThat(underTest.allContentKeys).isEqualTo(sceneKeys + overlayKeys) }

    @Test
    fun changeScene_toUnknownScene_doesNothing() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            val unknownScene = SceneKey("UNKNOWN")
            val previousScene = currentScene
            assertThat(previousScene).isNotEqualTo(unknownScene)
            underTest.changeScene(unknownScene, "reason")
            assertThat(currentScene).isEqualTo(previousScene)
        }

    @Test
    fun changeScene() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            underTest.changeScene(Scenes.Shade, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun changeScene_sameScene_hidesOverlays() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(underTest.currentScene)
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            underTest.showOverlay(Overlays.NotificationsShade, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isNotEmpty()

            underTest.changeScene(Scenes.Lockscreen, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun changeScene_sameScene_requestToKeepOverlays_keepsOverlays() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(underTest.currentScene)
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            underTest.showOverlay(Overlays.NotificationsShade, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)

            underTest.changeScene(Scenes.Lockscreen, "reason", hideAllOverlays = false)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
        }

    @Test
    fun changeScene_toGoneWhenUnl_doesNotThrow() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.changeScene(Scenes.Gone, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun changeScene_toGoneWhenStillLocked_throws() =
        kosmos.runTest {
            assertThrows(IllegalStateException::class.java) {
                underTest.changeScene(Scenes.Gone, "reason")
            }
        }

    @Test
    fun changeScene_toGoneWhenTransitionToLockedFromGone() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            val transitionTo by collectLastValue(underTest.transitioningTo)
            sceneContainerRepository.setTransitionState(
                flowOf(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Gone,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = flowOf(.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(transitionTo).isEqualTo(Scenes.Lockscreen)

            underTest.changeScene(Scenes.Gone, "simulate double tap power")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun changeScene_toHomeSceneFamily() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)

            underTest.changeScene(SceneFamilies.Home, "reason")

            assertThat(currentScene).isEqualTo(kosmos.homeSceneFamilyResolver.resolvedScene.value)
        }

    @Test
    fun snapToScene_toUnknownScene_doesNotChangeScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            val previousScene = currentScene
            val unknownScene = SceneKey("UNKNOWN")
            assertThat(previousScene).isNotEqualTo(unknownScene)
            underTest.snapToScene(unknownScene, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(previousScene)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun snapToScene_toUnknownScene_hidesOverlays() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(underTest.currentScene)
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            underTest.showOverlay(Overlays.NotificationsShade, "reason")
            val previousScene = currentScene
            val unknownScene = SceneKey("UNKNOWN")
            assertThat(previousScene).isNotEqualTo(unknownScene)
            assertThat(currentOverlays).isNotEmpty()

            underTest.snapToScene(unknownScene, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(previousScene)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    fun snapToScene() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            underTest.snapToScene(Scenes.Shade, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun snapToScene_sameScene_hidesOverlays() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(underTest.currentScene)
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            underTest.showOverlay(Overlays.NotificationsShade, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isNotEmpty()

            underTest.snapToScene(Scenes.Lockscreen, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun snapToScene_sameScene_requestToKeepOverlays_keepsOverlays() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(underTest.currentScene)
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            underTest.showOverlay(Overlays.NotificationsShade, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)

            underTest.snapToScene(Scenes.Lockscreen, "reason", hideAllOverlays = false)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
        }

    @Test
    fun snapToScene_toGoneWhenUnl_doesNotThrow() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.snapToScene(Scenes.Gone, loggingReason = "reason")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun snapToScene_toGoneWhenStillLocked_throws() =
        kosmos.runTest {
            assertThrows(IllegalStateException::class.java) {
                underTest.snapToScene(Scenes.Gone, loggingReason = "reason")
            }
        }

    @Test
    fun snapToScene_toHomeSceneFamily() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)

            underTest.snapToScene(SceneFamilies.Home, loggingReason = "reason")

            assertThat(currentScene).isEqualTo(homeSceneFamilyResolver.resolvedScene.value)
        }

    @Test
    fun sceneChanged_inDataSource() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeSceneDataSource.changeScene(Scenes.Shade)

            assertThat(currentScene).isEqualTo(Scenes.Shade)
        }

    @Test
    fun transitionState() =
        kosmos.runTest {
            enableSingleShade()
            val sceneContainerRepository = kosmos.sceneContainerRepository
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Lockscreen)
                )
            sceneContainerRepository.setTransitionState(transitionState)
            val reflectedTransitionState by
                collectLastValue(sceneContainerRepository.transitionStateFlow)
            assertThat(reflectedTransitionState).isEqualTo(transitionState.value)

            val progress = MutableStateFlow(1f)
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Shade,
                    currentScene = flowOf(Scenes.Shade),
                    progress = progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            assertThat(reflectedTransitionState).isEqualTo(transitionState.value)

            progress.value = 0.1f
            assertThat(reflectedTransitionState).isEqualTo(transitionState.value)

            progress.value = 0.9f
            assertThat(reflectedTransitionState).isEqualTo(transitionState.value)

            sceneContainerRepository.setTransitionState(null)
            assertThat(reflectedTransitionState)
                .isEqualTo(ObservableTransitionState.Idle(sceneContainerConfig.initialSceneKey))
        }

    @Test
    fun transitioningTo_sceneChange() =
        kosmos.runTest {
            enableSingleShade()
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(underTest.currentScene.value)
                )
            underTest.setTransitionState(transitionState)

            val transitionTo by collectLastValue(underTest.transitioningTo)
            assertThat(transitionTo).isNull()

            underTest.changeScene(Scenes.Shade, "reason")
            assertThat(transitionTo).isNull()

            val progress = MutableStateFlow(0f)
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = underTest.currentScene.value,
                    toScene = Scenes.Shade,
                    currentScene = flowOf(Scenes.Shade),
                    progress = progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            assertThat(transitionTo).isEqualTo(Scenes.Shade)

            progress.value = 0.5f
            assertThat(transitionTo).isEqualTo(Scenes.Shade)

            progress.value = 1f
            assertThat(transitionTo).isEqualTo(Scenes.Shade)

            transitionState.value = ObservableTransitionState.Idle(Scenes.Shade)
            assertThat(transitionTo).isNull()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun transitioningTo_overlayChange() =
        kosmos.runTest {
            enableDualShade()
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(underTest.currentScene.value)
                )
            underTest.setTransitionState(transitionState)

            val transitionTo by collectLastValue(underTest.transitioningTo)
            assertThat(transitionTo).isNull()

            underTest.showOverlay(Overlays.NotificationsShade, "reason")
            assertThat(transitionTo).isNull()

            val progress = MutableStateFlow(0f)
            transitionState.value =
                ShowOrHideOverlay(
                    overlay = Overlays.NotificationsShade,
                    fromContent = underTest.currentScene.value,
                    toContent = Overlays.NotificationsShade,
                    currentScene = underTest.currentScene.value,
                    currentOverlays = underTest.currentOverlays,
                    progress = progress,
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = flowOf(true),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            assertThat(transitionTo).isEqualTo(Overlays.NotificationsShade)

            progress.value = 0.5f
            assertThat(transitionTo).isEqualTo(Overlays.NotificationsShade)

            progress.value = 1f
            assertThat(transitionTo).isEqualTo(Overlays.NotificationsShade)

            transitionState.value =
                ObservableTransitionState.Idle(
                    currentScene = underTest.currentScene.value,
                    currentOverlays = setOf(Overlays.NotificationsShade),
                )
            assertThat(transitionTo).isNull()
        }

    @Test
    fun isTransitionUserInputOngoing_idle_false() =
        kosmos.runTest {
            enableSingleShade()
            val transitionState = flowOf(ObservableTransitionState.Idle(Scenes.Shade))
            val isTransitionUserInputOngoing by
                collectLastValue(underTest.isTransitionUserInputOngoing)
            underTest.setTransitionState(transitionState)

            assertThat(isTransitionUserInputOngoing).isFalse()
        }

    @Test
    fun isTransitionUserInputOngoing_transition_true() =
        kosmos.runTest {
            enableSingleShade()
            val transitionState =
                flowOf(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            val isTransitionUserInputOngoing by
                collectLastValue(underTest.isTransitionUserInputOngoing)
            underTest.setTransitionState(transitionState)

            assertThat(isTransitionUserInputOngoing).isTrue()
        }

    @Test
    fun isTransitionUserInputOngoing_updateMidTransition_false() =
        kosmos.runTest {
            enableSingleShade()
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            val isTransitionUserInputOngoing by
                collectLastValue(underTest.isTransitionUserInputOngoing)
            underTest.setTransitionState(transitionState)

            assertThat(isTransitionUserInputOngoing).isTrue()

            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Shade,
                    toScene = Scenes.Lockscreen,
                    currentScene = flowOf(Scenes.Lockscreen),
                    progress = flowOf(0.6f),
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = flowOf(false),
                )

            assertThat(isTransitionUserInputOngoing).isFalse()
        }

    @Test
    fun isTransitionUserInputOngoing_updateOnIdle_false() =
        kosmos.runTest {
            enableSingleShade()
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            val isTransitionUserInputOngoing by
                collectLastValue(underTest.isTransitionUserInputOngoing)
            underTest.setTransitionState(transitionState)

            assertThat(isTransitionUserInputOngoing).isTrue()

            transitionState.value = ObservableTransitionState.Idle(currentScene = Scenes.Lockscreen)

            assertThat(isTransitionUserInputOngoing).isFalse()
        }

    @Test
    fun isVisible_duringRemoteUserInteraction_forcedVisible() =
        kosmos.runTest {
            unlockDevice()
            underTest.changeScene(Scenes.Gone, "Switch to Gone to make isVisible be false.")
            assertThat(underTest.isVisible).isFalse()

            underTest.onRemoteUserInputStarted("reason")
            assertThat(underTest.isVisible).isTrue()

            underTest.onUserInputFinished()
            assertThat(underTest.isVisible).isFalse()
        }

    @Test
    fun resolveSceneFamily_home() =
        kosmos.runTest {
            assertThat(underTest.resolveSceneFamily(SceneFamilies.Home).first())
                .isEqualTo(homeSceneFamilyResolver.resolvedScene.value)
        }

    @Test
    fun resolveSceneFamily_nonFamily() =
        kosmos.runTest {
            val resolved = underTest.resolveSceneFamily(Scenes.Gone).toList()
            assertThat(resolved).containsExactly(Scenes.Gone).inOrder()
        }

    @Test
    fun transitionValue_test_idle() =
        kosmos.runTest {
            val transitionValue by collectLastValue(underTest.transitionProgress(Scenes.Gone))

            setSceneTransition(Idle(Scenes.Gone), skipChangeScene = true)
            assertThat(transitionValue).isEqualTo(1f)

            setSceneTransition(Idle(Scenes.Lockscreen))
            assertThat(transitionValue).isEqualTo(0f)
        }

    @Test
    fun transitionValue_test_transitions() =
        kosmos.runTest {
            val transitionValue by collectLastValue(underTest.transitionProgress(Scenes.Gone))
            val progress = MutableStateFlow(0f)

            setSceneTransition(
                Transition(from = Scenes.Lockscreen, to = Scenes.Gone, progress = progress),
                skipChangeScene = true,
            )
            assertThat(transitionValue).isEqualTo(0f)

            progress.value = 0.4f
            assertThat(transitionValue).isEqualTo(0.4f)

            setSceneTransition(
                Transition(from = Scenes.Gone, to = Scenes.Lockscreen, progress = progress)
            )
            progress.value = 0.7f
            assertThat(transitionValue).isEqualTo(0.3f)

            setSceneTransition(
                Transition(from = Scenes.Lockscreen, to = Scenes.Shade, progress = progress)
            )
            progress.value = 0.9f
            assertThat(transitionValue).isEqualTo(0f)
        }

    @Test
    fun changeScene_toGone_whenKeyguardDisabled_doesNotThrow() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            keyguardEnabledInteractor.notifyKeyguardEnabled(false)

            underTest.changeScene(Scenes.Gone, "")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun showOverlay_overlayDisabled_doesNothing() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            val disabledOverlay = Overlays.QuickSettingsShade
            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(disable2 = StatusBarManager.DISABLE2_QUICK_SETTINGS)
            assertThat(disabledContentInteractor.isDisabled(disabledOverlay)).isTrue()
            assertThat(currentOverlays).doesNotContain(disabledOverlay)

            underTest.showOverlay(disabledOverlay, "reason")

            assertThat(currentOverlays).doesNotContain(disabledOverlay)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun replaceOverlay_withDisabledOverlay_doesNothing() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            val showingOverlay = Overlays.NotificationsShade
            underTest.showOverlay(showingOverlay, "reason")
            assertThat(currentOverlays).isEqualTo(setOf(showingOverlay))
            val disabledOverlay = Overlays.QuickSettingsShade
            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(disable2 = StatusBarManager.DISABLE2_QUICK_SETTINGS)
            assertThat(disabledContentInteractor.isDisabled(disabledOverlay)).isTrue()

            underTest.replaceOverlay(showingOverlay, disabledOverlay, "reason")

            assertThat(currentOverlays).isEqualTo(setOf(showingOverlay))
        }

    @Test
    fun changeScene_toDisabledScene_doesNothing() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(underTest.currentScene)
            val disabledScene = Scenes.Shade
            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(disable2 = StatusBarManager.DISABLE2_NOTIFICATION_SHADE)
            assertThat(disabledContentInteractor.isDisabled(disabledScene)).isTrue()
            assertThat(currentScene).isNotEqualTo(disabledScene)

            underTest.changeScene(disabledScene, "reason")

            assertThat(currentScene).isNotEqualTo(disabledScene)
        }

    @Test
    fun transitionAnimations() =
        kosmos.runTest {
            unlockDevice()
            underTest.changeScene(Scenes.Gone, "Switch to Gone to make isVisible be false.")
            assertThat(underTest.isVisible).isFalse()

            underTest.onTransitionAnimationStart()
            // One animation is active, forced visible.
            assertThat(underTest.isVisible).isTrue()

            underTest.onTransitionAnimationEnd()
            // No more active animations, not forced visible.
            assertThat(underTest.isVisible).isFalse()

            underTest.onTransitionAnimationStart()
            // One animation is active, forced visible.
            assertThat(underTest.isVisible).isTrue()

            underTest.onTransitionAnimationCancelled()
            // No more active animations, not forced visible.
            assertThat(underTest.isVisible).isFalse()

            underTest.changeScene(
                Scenes.Lockscreen,
                "Switch to Lockscreen to make isVisible be false.",
            )
            assertThat(underTest.isVisible).isTrue()

            underTest.onTransitionAnimationStart()
            underTest.onTransitionAnimationStart()
            // Two animations are active, forced visible.
            assertThat(underTest.isVisible).isTrue()

            unlockDevice()
            underTest.changeScene(Scenes.Gone, "Switch to Gone to make isVisible be false.")
            // Two animations are active, forced visible.
            assertThat(underTest.isVisible).isTrue()

            underTest.onTransitionAnimationEnd()
            // One animation is still active, forced visible.
            assertThat(underTest.isVisible).isTrue()

            underTest.onTransitionAnimationEnd()
            // No more active animations, not forced visible.
            assertThat(underTest.isVisible).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun changeScene_toIncorrectShade_crashes() =
        kosmos.runTest {
            enableDualShade()
            assertThrows(IllegalStateException::class.java) {
                underTest.changeScene(Scenes.Shade, "reason")
            }
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun changeScene_toIncorrectQuickSettings_crashes() =
        kosmos.runTest {
            enableDualShade()
            assertThrows(IllegalStateException::class.java) {
                underTest.changeScene(Scenes.QuickSettings, "reason")
            }
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun snapToScene_toIncorrectShade_crashes() =
        kosmos.runTest {
            enableDualShade()
            assertThrows(IllegalStateException::class.java) {
                underTest.snapToScene(Scenes.Shade, loggingReason = "reason")
            }
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun snapToScene_toIncorrectQuickSettings_crashes() =
        kosmos.runTest {
            enableDualShade()
            assertThrows(IllegalStateException::class.java) {
                underTest.changeScene(Scenes.QuickSettings, "reason")
            }
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun showOverlay_incorrectShadeOverlay_crashes() =
        kosmos.runTest {
            disableDualShade()
            assertThrows(IllegalStateException::class.java) {
                underTest.showOverlay(Overlays.NotificationsShade, "reason")
            }
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun showOverlay_incorrectQuickSettingsOverlay_crashes() =
        kosmos.runTest {
            disableDualShade()
            assertThrows(IllegalStateException::class.java) {
                underTest.showOverlay(Overlays.QuickSettingsShade, "reason")
            }
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun instantlyShowOverlay() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(underTest.currentScene)
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            val originalScene = currentScene
            assertThat(currentOverlays).isEmpty()

            val overlay = Overlays.NotificationsShade
            underTest.instantlyShowOverlay(overlay, "reason")

            assertThat(currentScene).isEqualTo(originalScene)
            assertThat(currentOverlays).contains(overlay)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun instantlyHideOverlay() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(underTest.currentScene)
            val currentOverlays by collectLastValue(underTest.currentOverlays)
            val overlay = Overlays.QuickSettingsShade
            underTest.showOverlay(overlay, "reason")
            val originalScene = currentScene
            assertThat(currentOverlays).contains(overlay)

            underTest.instantlyHideOverlay(overlay, "reason")

            assertThat(currentScene).isEqualTo(originalScene)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    fun changeScene_setsKeyguardState() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            // Unlock so transitioning to the Gone scene becomes possible.
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            underTest.changeScene(toScene = Scenes.Gone, loggingReason = "")
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            underTest.changeScene(
                toScene = Scenes.Lockscreen,
                keyguardState = KeyguardState.AOD,
                loggingReason = "",
            )
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            assertEquals(
                kosmos.lockscreenSceneTransitionRepository.nextLockscreenTargetState.value,
                KeyguardState.AOD,
            )
        }

    @Test
    fun changeScene_sameScene_withFreeze() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(fakeSceneDataSource.freezeAndAnimateToCurrentStateCallCount).isEqualTo(0)

            underTest.changeScene(
                toScene = Scenes.Lockscreen,
                loggingReason = "test",
                keyguardState = KeyguardState.AOD,
                forceSettleToTargetScene = true,
            )

            assertEquals(
                kosmos.lockscreenSceneTransitionRepository.nextLockscreenTargetState.value,
                KeyguardState.AOD,
            )
            assertThat(fakeSceneDataSource.freezeAndAnimateToCurrentStateCallCount).isEqualTo(1)
        }

    @Test
    fun changeScene_sameScene_withoutFreeze() =
        kosmos.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(fakeSceneDataSource.freezeAndAnimateToCurrentStateCallCount).isEqualTo(0)

            underTest.changeScene(
                toScene = Scenes.Lockscreen,
                loggingReason = "test",
                keyguardState = KeyguardState.AOD,
                forceSettleToTargetScene = false,
            )

            assertEquals(
                KeyguardState.AOD,
                kosmos.lockscreenSceneTransitionRepository.nextLockscreenTargetState.value,
            )
            assertThat(fakeSceneDataSource.freezeAndAnimateToCurrentStateCallCount).isEqualTo(0)
        }

    @Test
    fun topmostContent_sceneChange_noOverlays() =
        kosmos.runTest {
            val topmostContent by collectLastValue(underTest.topmostContent)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.snapToScene(Scenes.Lockscreen, loggingReason = "reason")

            assertThat(topmostContent).isEqualTo(Scenes.Lockscreen)

            underTest.changeScene(Scenes.Gone, "reason")

            assertThat(topmostContent).isEqualTo(Scenes.Gone)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun topmostContent_sceneChange_withOverlay() =
        kosmos.runTest {
            enableDualShade()

            val topmostContent by collectLastValue(underTest.topmostContent)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.snapToScene(Scenes.Lockscreen, loggingReason = "reason")
            underTest.showOverlay(Overlays.NotificationsShade, "reason")

            assertThat(topmostContent).isEqualTo(Overlays.NotificationsShade)

            underTest.changeScene(Scenes.Gone, loggingReason = "reason", hideAllOverlays = false)

            assertThat(topmostContent).isEqualTo(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun topmostContent_overlayChange_higherZOrder() =
        kosmos.runTest {
            enableDualShade()

            val topmostContent by collectLastValue(underTest.topmostContent)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.snapToScene(Scenes.Lockscreen, loggingReason = "reason")
            underTest.showOverlay(Overlays.NotificationsShade, "reason")

            assertThat(topmostContent).isEqualTo(Overlays.NotificationsShade)

            underTest.showOverlay(Overlays.QuickSettingsShade, "reason")

            assertThat(topmostContent).isEqualTo(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun topmostContent_overlayChange_lowerZOrder() =
        kosmos.runTest {
            enableDualShade()

            val topmostContent by collectLastValue(underTest.topmostContent)

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.snapToScene(Scenes.Lockscreen, loggingReason = "reason")
            underTest.showOverlay(Overlays.QuickSettingsShade, "reason")

            assertThat(topmostContent).isEqualTo(Overlays.QuickSettingsShade)

            underTest.showOverlay(Overlays.NotificationsShade, "reason")

            assertThat(topmostContent).isEqualTo(Overlays.QuickSettingsShade)
        }

    @Test
    fun isVisible_goneVsSingleShade() =
        kosmos.runTest {
            enableSingleShade()
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            underTest.snapToScene(Scenes.Gone, "gone to make isVisible be false")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.isVisible).isFalse()

            underTest.changeScene(Scenes.Shade, "shade to make isVisible be true")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Shade)
            assertThat(underTest.isVisible).isTrue()

            underTest.changeScene(Scenes.Gone, "gone to make isVisible be false again")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.isVisible).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isVisible_goneVsDualShade() =
        kosmos.runTest {
            enableDualShade()
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            underTest.snapToScene(Scenes.Gone, "gone to make isVisible be false")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()
            assertThat(underTest.isVisible).isFalse()

            underTest.showOverlay(
                Overlays.NotificationsShade,
                "notif shade to make isVisible be true",
            )
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.transitionState.currentOverlays)
                .isEqualTo(setOf(Overlays.NotificationsShade))
            assertThat(underTest.isVisible).isTrue()

            underTest.hideOverlay(
                Overlays.NotificationsShade,
                "hide notif shade to make isVisible be false",
            )
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()
            assertThat(underTest.isVisible).isFalse()

            underTest.showOverlay(Overlays.QuickSettingsShade, "qs shade to make isVisible be true")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.transitionState.currentOverlays)
                .isEqualTo(setOf(Overlays.QuickSettingsShade))
            assertThat(underTest.isVisible).isTrue()

            underTest.hideOverlay(
                Overlays.QuickSettingsShade,
                "hide qs shade to make isVisible be false",
            )
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()
            assertThat(underTest.isVisible).isFalse()
        }

    @Test
    fun isVisible_lockscreenBouncerAndUnlock() =
        kosmos.runTest {
            enableSingleShade()
            underTest.snapToScene(
                Scenes.Lockscreen,
                "lockscreen to lock device and make isVisible be true",
            )
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Lockscreen)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()
            assertThat(underTest.isVisible).isTrue()

            underTest.showOverlay(Overlays.Bouncer, "show bouncer to emulate user unlock stes")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Lockscreen)
            assertThat(underTest.transitionState.currentOverlays).isEqualTo(setOf(Overlays.Bouncer))
            assertThat(underTest.isVisible).isTrue()

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            underTest.changeScene(Scenes.Gone, "gone to unlock device and make isVisible be false")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()
            assertThat(underTest.isVisible).isFalse()
        }

    @Test
    fun isVisible_huns() =
        kosmos.runTest {
            underTest.snapToScene(Scenes.Lockscreen, "lockscreen to start visible")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Lockscreen)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()
            assertThat(underTest.isVisible).isTrue()

            // Show a HUN
            underTest.handleEvent(SceneInteractor.Event.HeadsUpNotificationVisibilityChange(true))
            assertThat(underTest.isVisible).isTrue()

            // Hide a HUN
            underTest.handleEvent(SceneInteractor.Event.HeadsUpNotificationVisibilityChange(false))
            assertThat(underTest.isVisible).isTrue() // still visible because, on lockscreen.

            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            underTest.changeScene(Scenes.Gone, "gone to start off not visible")
            assertThat(underTest.currentSceneAsState).isEqualTo(Scenes.Gone)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()
            assertThat(underTest.isVisible).isFalse()

            // Show a HUN
            underTest.handleEvent(SceneInteractor.Event.HeadsUpNotificationVisibilityChange(true))
            assertThat(underTest.isVisible).isTrue() // now visible even though on Gone.

            // Hide a HUN
            underTest.handleEvent(SceneInteractor.Event.HeadsUpNotificationVisibilityChange(false))
            assertThat(underTest.isVisible).isFalse()
        }

    @Test
    fun showOverlay_bouncerWhenUnlocked_doesNotShowBouncer() =
        kosmos.runTest {
            unlockDevice()
            underTest.snapToScene(Scenes.Lockscreen, "reason")
            assertThat(underTest.transitionState.currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()

            underTest.showOverlay(Overlays.Bouncer, "reason")
            runCurrent()
            assertThat(underTest.transitionState.currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(underTest.transitionState.currentOverlays).isEmpty()
        }
}
