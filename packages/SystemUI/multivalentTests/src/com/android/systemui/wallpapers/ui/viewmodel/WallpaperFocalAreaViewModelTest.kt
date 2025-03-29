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

package com.android.systemui.wallpapers.ui.viewmodel

import android.content.mockedContext
import android.content.res.Resources
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.testScope
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.setActiveNotifs
import com.android.systemui.testKosmos
import com.android.systemui.wallpapers.data.repository.wallpaperFocalAreaRepository
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractor
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractorTest.Companion.overrideMockedResources
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractorTest.OverrideResources
import com.android.systemui.wallpapers.domain.interactor.wallpaperFocalAreaInteractor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@SmallTest
@RunWith(AndroidJUnit4::class)
class WallpaperFocalAreaViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private lateinit var mockedResources: Resources
    lateinit var underTest: WallpaperFocalAreaViewModel

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockedResources = mock<Resources>()
        overrideMockedResources(
            mockedResources,
            OverrideResources(screenWidth = 1000, screenHeight = 2000, centerAlignFocalArea = false),
        )
        whenever(kosmos.mockedContext.resources).thenReturn(mockedResources)
        whenever(
                mockedResources.getFloat(
                    Resources.getSystem()
                        .getIdentifier(
                            /* name= */ "config_wallpaperMaxScale",
                            /* defType= */ "dimen",
                            /* defPackage= */ "android",
                        )
                )
            )
            .thenReturn(1f)
        kosmos.wallpaperFocalAreaInteractor =
            WallpaperFocalAreaInteractor(
                context = kosmos.mockedContext,
                wallpaperFocalAreaRepository = kosmos.wallpaperFocalAreaRepository,
                shadeRepository = kosmos.shadeRepository,
            )
        underTest =
            WallpaperFocalAreaViewModel(
                wallpaperFocalAreaInteractor = kosmos.wallpaperFocalAreaInteractor,
                keyguardTransitionInteractor = kosmos.keyguardTransitionInteractor,
            )
    }

    @Test
    @DisableSceneContainer
    fun focalAreaBoundsSent_whenFinishTransitioningToLockscreen() =
        testScope.runTest {
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.wallpaperFocalAreaRepository.setHasFocalArea(true)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.STARTED, to = LOCKSCREEN)),
                testScope,
            )
            setTestFocalAreaBounds()
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.FINISHED, to = LOCKSCREEN)),
                testScope,
            )
            assertThat(bounds).isNotNull()
        }

    @Test
    @DisableSceneContainer
    fun wallpaperHasFocalArea_shouldSendBounds() =
        testScope.runTest {
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.wallpaperFocalAreaRepository.setHasFocalArea(true)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.STARTED, to = LOCKSCREEN)),
                testScope,
            )
            setTestFocalAreaBounds()
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.FINISHED, to = LOCKSCREEN)),
                testScope,
            )

            assertThat(bounds).isNotNull()
        }

    @Test
    @DisableSceneContainer
    fun wallpaperDoesNotHaveFocalArea_shouldNotSendBounds() =
        testScope.runTest {
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.wallpaperFocalAreaRepository.setHasFocalArea(false)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.STARTED, to = LOCKSCREEN)),
                testScope,
            )
            kosmos.wallpaperFocalAreaRepository.setWallpaperFocalAreaBounds(
                DEFAULT_FOCAL_AREA_BOUNDS
            )
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.FINISHED, to = LOCKSCREEN)),
                testScope,
            )

            assertThat(bounds).isNull()
        }

    @Test
    @DisableSceneContainer
    fun boundsChangeWhenGoingFromLockscreenToGone_shouldNotSendBounds() =
        testScope.runTest {
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.wallpaperFocalAreaRepository.setHasFocalArea(true)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.STARTED, to = LOCKSCREEN)),
                testScope,
            )

            setTestFocalAreaBounds()
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.FINISHED, to = LOCKSCREEN)),
                testScope,
            )
            assertThat(bounds?.top).isEqualTo(20F)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    TransitionStep(
                        transitionState = TransitionState.STARTED,
                        from = LOCKSCREEN,
                        to = GONE,
                    )
                ),
                testScope,
            )
            setTestFocalAreaBounds(
                activeNotifs = 3,
                shortcutAbsoluteTop = 400F,
                notificationDefaultTop = 20F,
                notificationStackAbsoluteBottom = 200F,
            )
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    TransitionStep(
                        transitionState = TransitionState.FINISHED,
                        from = LOCKSCREEN,
                        to = GONE,
                    )
                ),
                testScope,
            )
            assertThat(bounds?.top).isEqualTo(20F)
        }

    @Test
    @DisableSceneContainer
    fun boundsChangeOnLockscreen_shouldSendBounds() =
        testScope.runTest {
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            kosmos.wallpaperFocalAreaRepository.setHasFocalArea(true)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.STARTED, to = LOCKSCREEN)),
                testScope,
            )
            setTestFocalAreaBounds()

            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.FINISHED, to = LOCKSCREEN)),
                testScope,
            )

            assertThat(bounds).isNotNull()
        }

    private fun setTestFocalAreaBounds(
        shadeLayoutWide: Boolean = false,
        activeNotifs: Int = 0,
        shortcutAbsoluteTop: Float = 400F,
        notificationDefaultTop: Float = 20F,
        notificationStackAbsoluteBottom: Float = 20F,
    ) {
        kosmos.shadeRepository.setShadeLayoutWide(shadeLayoutWide)
        kosmos.activeNotificationListRepository.setActiveNotifs(activeNotifs)
        kosmos.wallpaperFocalAreaRepository.setShortcutAbsoluteTop(shortcutAbsoluteTop)
        kosmos.wallpaperFocalAreaRepository.setNotificationDefaultTop(notificationDefaultTop)
        kosmos.wallpaperFocalAreaRepository.setNotificationStackAbsoluteBottom(
            notificationStackAbsoluteBottom
        )
    }

    companion object {
        val DEFAULT_FOCAL_AREA_BOUNDS = RectF(0f, 400f, 1000F, 1900F)
    }
}
