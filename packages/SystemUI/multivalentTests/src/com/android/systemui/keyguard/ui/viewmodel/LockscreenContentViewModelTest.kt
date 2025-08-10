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

package com.android.systemui.keyguard.ui.viewmodel

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.transition.fakeKeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.shared.transition.keyguardTransitionAnimationCallbackDelegator
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class LockscreenContentViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos: Kosmos = testKosmos()

    private lateinit var underTest: LockscreenContentViewModel
    private val activationJob = Job()

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

    @Before
    fun setup() {
        with(kosmos) {
            shadeRepository.setShadeLayoutWide(false)
            underTest =
                lockscreenContentViewModelFactory.create(fakeKeyguardTransitionAnimationCallback)
            underTest.activateIn(testScope, activationJob)
        }
    }

    @Test
    fun isContentVisible_whenNotOccluded_visible() =
        kosmos.runTest {
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(false, null)
            runCurrent()
            assertThat(underTest.isContentVisible).isTrue()
        }

    @Test
    fun isContentVisible_whenOccluded_notVisible() =
        kosmos.runTest {
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true, null)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.OCCLUDED,
            )
            runCurrent()
            assertThat(underTest.isContentVisible).isFalse()
        }

    @Test
    fun isContentVisible_whenOccluded_notVisible_evenIfShadeShown() =
        kosmos.runTest {
            enableSingleShade()
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true, null)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.OCCLUDED,
            )
            runCurrent()

            sceneInteractor.snapToScene(Scenes.Shade, "")
            runCurrent()
            assertThat(underTest.isContentVisible).isFalse()
        }

    @Test
    fun activate_setsDelegate_onKeyguardTransitionAnimationCallbackDelegator() =
        kosmos.runTest {
            runCurrent()
            assertThat(keyguardTransitionAnimationCallbackDelegator.delegate)
                .isSameInstanceAs(fakeKeyguardTransitionAnimationCallback)
        }

    @Test
    fun deactivate_clearsDelegate_onKeyguardTransitionAnimationCallbackDelegator() =
        kosmos.runTest {
            activationJob.cancel()
            runCurrent()
            assertThat(keyguardTransitionAnimationCallbackDelegator.delegate).isNull()
        }

    @Test
    fun isContentVisible_whenOccluded_notVisibleInOccluded_visibleInAod() =
        kosmos.runTest {
            enableSingleShade()
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(true, null)
            fakeKeyguardTransitionRepository.transitionTo(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.OCCLUDED,
            )
            runCurrent()

            sceneInteractor.snapToScene(Scenes.Shade, "")
            runCurrent()
            assertThat(underTest.isContentVisible).isFalse()

            fakeKeyguardTransitionRepository.transitionTo(KeyguardState.OCCLUDED, KeyguardState.AOD)
            runCurrent()

            sceneInteractor.snapToScene(Scenes.Lockscreen, "")
            runCurrent()

            assertThat(underTest.isContentVisible).isTrue()
        }
}
