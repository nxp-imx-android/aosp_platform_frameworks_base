/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionState.RUNNING
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.sysuiStatusBarStateController
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.emptyFlow
import com.android.systemui.scene.data.repository.sceneContainerRepository

@SmallTest
@RunWith(AndroidJUnit4::class)
class AlternateBouncerToGoneTransitionViewModelTest : SysuiTestCase() {
    val kosmos =
        testKosmos().apply {
            fakeFeatureFlagsClassic.apply { set(Flags.FULL_SCREEN_USER_SWITCHER, false) }
        }

    private val testScope = kosmos.testScope
    private val keyguardTransitionRepository = kosmos.fakeKeyguardTransitionRepository
    private val sysuiStatusBarStateController = kosmos.sysuiStatusBarStateController
    private val underTest by lazy { kosmos.alternateBouncerToGoneTransitionViewModel }

    @Test
    @EnableSceneContainer
    fun notificationAlpha() =
        testScope.runTest {
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Lockscreen)
                )
            kosmos.sceneContainerRepository.setTransitionState(transitionState)
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Gone,
                    emptyFlow(),
                    emptyFlow(),
                    false,
                    emptyFlow()
                )
            val viewState = ViewStateAccessor(alpha = { 1f })
            val values by collectValues(underTest.notificationAlpha(ViewStateAccessor()))
            runCurrent()

            sysuiStatusBarStateController.setLeaveOpenOnKeyguardHide(false)
            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0f),
                    step(0.3f),
                    step(1f),
                ),
                testScope,
            )
            runCurrent()

            // Assert alpha value goes to 0
            assertThat(values.size).isGreaterThan(0)
            assertThat(values[values.size - 1]).isEqualTo(0f)
        }

    @Test
    @DisableSceneContainer
    fun deviceEntryParentViewDisappear() =
        testScope.runTest {
            val values by collectValues(underTest.deviceEntryParentViewAlpha)

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0f),
                    step(0.1f),
                    step(0.2f),
                    step(0.3f),
                    step(1f),
                ),
                testScope,
            )

            values.forEach { assertThat(it).isEqualTo(0f) }
        }

    @Test
    @DisableSceneContainer
    fun lockscreenAlpha() =
        testScope.runTest {
            val startAlpha = 0.6f
            val viewState = ViewStateAccessor(alpha = { startAlpha })
            val alpha by collectLastValue(underTest.lockscreenAlpha(viewState))
            runCurrent()

            keyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    step(0f, TransitionState.STARTED),
                    step(0.25f),
                    step(0.5f),
                    step(0.75f),
                    step(1f),
                ),
                testScope,
            )

            // Alpha starts at the starting value from ViewStateAccessor.
            keyguardTransitionRepository.sendTransitionStep(
                step(0f, state = TransitionState.STARTED)
            )
            runCurrent()
            assertThat(alpha).isEqualTo(startAlpha)

            // Alpha finishes in 200ms out of 500ms, check the alpha at the halfway point.
            val progress = 0.2f
            keyguardTransitionRepository.sendTransitionStep(step(progress))
            runCurrent()
            assertThat(alpha).isEqualTo(0.3f)

            // Alpha ends at 0.
            keyguardTransitionRepository.sendTransitionStep(step(1f))
            runCurrent()
            assertThat(alpha).isEqualTo(0f)
        }

    @Test
    @DisableSceneContainer
    fun notificationAlpha_leaveShadeOpen() =
        testScope.runTest {
            val values by collectValues(underTest.notificationAlpha(ViewStateAccessor()))
            runCurrent()

            sysuiStatusBarStateController.setLeaveOpenOnKeyguardHide(true)

            keyguardTransitionRepository.sendTransitionStep(step(0f, TransitionState.STARTED))
            keyguardTransitionRepository.sendTransitionStep(step(1f))

            assertThat(values.size).isEqualTo(2)
            values.forEach { assertThat(it).isEqualTo(1f) }
        }

    @Test
    @DisableSceneContainer
    fun lockscreenAlpha_zeroInitialAlpha() =
        testScope.runTest {
            // ViewState starts at 0 alpha.
            val viewState = ViewStateAccessor(alpha = { 0f })
            val alpha by collectValues(underTest.lockscreenAlpha(viewState))

            keyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.ALTERNATE_BOUNCER,
                to = GONE,
                testScope
            )

            // Alpha starts and ends at 0.
            alpha.forEach { assertThat(it).isEqualTo(0f) }
        }

    private fun step(value: Float, state: TransitionState = RUNNING): TransitionStep {
        return TransitionStep(
            from = KeyguardState.ALTERNATE_BOUNCER,
            to = if (SceneContainerFlag.isEnabled) KeyguardState.UNDEFINED
                else GONE,
            value = value,
            transitionState = state,
            ownerName = "AlternateBouncerToGoneTransitionViewModelTest"
        )
    }
}
