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

package com.android.systemui.scene.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.blurChoreographer
import com.android.systemui.scene.sceneTransitionBlurViewModel
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.android.systemui.wallpapers.domain.interactor.fakeWallpaperRepository
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.android.systemui.window.shared.model.BlurEffect
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SceneTransitionBlurViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: SceneTransitionBlurViewModel
        get() = kosmos.sceneTransitionBlurViewModel

    private val activationJob: Job = Job()

    @Before
    fun setUp() {
        kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
        underTest.activateIn(kosmos.testScope, activationJob)
    }

    @Test
    fun idleOnLockscreen_mapsToMinBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Lockscreen), 1f)

            verify(blurChoreographer).applyBlur(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    fun idleOnLockscreen_withAodAndAmbientModeDisabled_mapsToMinBlur() =
        kosmos.runTest {
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )
            runCurrent()
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Lockscreen), 1f)

            verify(blurChoreographer).applyBlur(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    fun idleOnLockscreenAod_withAodAndAmbientModeEnabled_mapsToCorrectBlurValue() =
        kosmos.runTest {
            fakeWallpaperRepository.setWallpaperSupportsAmbientMode(true)
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope = testScope,
            )
            runCurrent()
            assertThat(keyguardTransitionInteractor.currentKeyguardState.value)
                .isEqualTo(KeyguardState.AOD)
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Lockscreen), 1f)

            verify(blurChoreographer).applyBlur(BlurEffect(blurConfig.maxBlurRadiusPx / 2, 1f))
        }

    @Test
    fun idleOnCommunal_mapsToMaxBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Communal), 1f)

            verify(blurChoreographer).applyBlur(BlurEffect(blurConfig.maxBlurRadiusPx, 0.95f))
        }

    @Test
    fun idleOnQuickSettingsScene_isIgnored() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.QuickSettings), 1f)

            verify(blurChoreographer, never()).applyBlur(any())
        }

    @Test
    fun idleOnNotificationShadeScene_isIgnored() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Shade), 1f)

            verify(blurChoreographer, never()).applyBlur(any())
        }

    @Test
    fun idleOnDreamScene_mapsToMinBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Dream), 1f)

            verify(blurChoreographer).applyBlur(eq(BlurEffect(blurConfig.minBlurRadiusPx, 1f)))
        }

    @Test
    fun idleOnOccludedScene_mapsToMinBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Occluded), 1f)

            verify(blurChoreographer).applyBlur(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }

    @Test
    fun idleOnGoneScene_mapsToMinBlur() =
        kosmos.runTest {
            underTest.requestWindowBackgroundBlur(TransitionState.Idle(Scenes.Gone), 1f)

            verify(blurChoreographer).applyBlur(BlurEffect(blurConfig.minBlurRadiusPx, 1f))
        }
}
