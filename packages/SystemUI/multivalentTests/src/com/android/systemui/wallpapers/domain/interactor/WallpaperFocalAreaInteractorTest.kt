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

package com.android.systemui.wallpapers.domain.interactor

import android.content.mockedContext
import android.content.res.Resources
import android.graphics.PointF
import android.util.DisplayMetrics
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardSmartspaceInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.ShadeRepository
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.testKosmos
import com.android.systemui.wallpapers.data.repository.wallpaperFocalAreaRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@SmallTest
@RunWith(AndroidJUnit4::class)
class WallpaperFocalAreaInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    lateinit var shadeRepository: ShadeRepository
    private lateinit var mockedResources: Resources
    private lateinit var underTest: WallpaperFocalAreaInteractor
    private var wallpaperInteractor: WallpaperInteractor = spy(kosmos.wallpaperInteractorFaked)

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockedResources = mock<Resources>()
        whenever(kosmos.mockedContext.resources).thenReturn(mockedResources)
        whenever(
                kosmos.mockedContext.resources.getDimensionPixelSize(
                    com.android.systemui.customization.clocks.R.dimen.enhanced_smartspace_height
                )
            )
            .thenReturn(200)
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
            .thenReturn(2f)

        underTest =
            WallpaperFocalAreaInteractor(
                context = kosmos.mockedContext,
                wallpaperFocalAreaRepository = kosmos.wallpaperFocalAreaRepository,
                shadeModeInteractor = kosmos.shadeModeInteractor,
                smartspaceInteractor = kosmos.keyguardSmartspaceInteractor,
                keyguardTransitionInteractor = kosmos.keyguardTransitionInteractor,
                sceneInteractor = kosmos.sceneInteractor,
                backgroundScope = kosmos.backgroundScope,
                wallpaperInteractor = wallpaperInteractor,
            )

        kosmos.fakeWallpaperRepository.setShouldSendFocalArea(true)
    }

    @Test
    fun focalAreaBounds_noNotifications_noSmartspaceCard_inHandheldDevices() =
        testScope.runTest {
            setupHandheldDevice()
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            setTestFocalAreaBounds(
                shadeLayoutWide = false,
                shortcutAbsoluteTop = 1800F,
                notificationDefaultTop = 400F,
                notificationStackAbsoluteBottom = 400F,
                smallClockViewBottom = 400F,
                smartspaceCardBottom = 400F,
                smartspaceVisibility = INVISIBLE,
            )

            assertThat(bounds?.top).isEqualTo(700F)
            assertThat(bounds?.bottom).isEqualTo(1400F)
        }

    @Test
    fun focalAreaBelowNotifs_hasNotifications_noSmartspaceCard_inHandheldDevices() =
        testScope.runTest {
            setupHandheldDevice()
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            setTestFocalAreaBounds(
                shadeLayoutWide = false,
                shortcutAbsoluteTop = 1800F,
                notificationDefaultTop = 400F,
                notificationStackAbsoluteBottom = 600F,
                smallClockViewBottom = 400F,
                smartspaceCardBottom = 400F,
                smartspaceVisibility = INVISIBLE,
            )
            assertThat(bounds?.top).isEqualTo(800F)
            assertThat(bounds?.bottom).isEqualTo(1400F)
        }

    @Test
    fun focalAreaBelowSmartspace_noNotifcations_hasSmartspaceCard_inHandheldDevice() =
        testScope.runTest {
            setupHandheldDevice()
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            setTestFocalAreaBounds(
                shadeLayoutWide = false,
                shortcutAbsoluteTop = 1800F,
                notificationDefaultTop = 400F,
                notificationStackAbsoluteBottom = 400F,
                smallClockViewBottom = 400F,
                smartspaceCardBottom = 600F,
                smartspaceVisibility = VISIBLE,
            )

            assertThat(bounds?.top).isEqualTo(800F)
            assertThat(bounds?.bottom).isEqualTo(1400F)
        }

    @Test
    fun focalAreaBelowNotifs_hasNotifcations_hasSmartspaceCard_inHandheldDevice() =
        testScope.runTest {
            setupHandheldDevice()
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            setTestFocalAreaBounds(
                shadeLayoutWide = false,
                shortcutAbsoluteTop = 1800F,
                notificationDefaultTop = 400F,
                notificationStackAbsoluteBottom = 1000F,
                smallClockViewBottom = 400F,
                smartspaceCardBottom = 600F,
                smartspaceVisibility = VISIBLE,
            )

            assertThat(bounds?.top).isEqualTo(1000F)
            assertThat(bounds?.bottom).isEqualTo(1400F)
        }

    @Test
    fun focalAreaNotBelowNotifs_hasNotification_noSmartspaceCard_inUnfoldLandscape() =
        testScope.runTest {
            setupUnfoldLandscape()
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            setTestFocalAreaBounds(
                shadeLayoutWide = true,
                shortcutAbsoluteTop = 1800F,
                notificationDefaultTop = 200F,
                notificationStackAbsoluteBottom = 300F,
                smallClockViewBottom = 200F,
                smartspaceCardBottom = 200F,
                smartspaceVisibility = INVISIBLE,
            )
            assertThat(bounds?.top).isEqualTo(600F)
            assertThat(bounds?.bottom).isEqualTo(1400F)
        }

    @Test
    fun focalAreaBoundsBelowSmartspace_noNotifcations_hasSmartspaceCard_inUnfoldLandscape() =
        testScope.runTest {
            setupUnfoldLandscape()
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            setTestFocalAreaBounds(
                shadeLayoutWide = true,
                shortcutAbsoluteTop = 1800F,
                notificationDefaultTop = 400F,
                notificationStackAbsoluteBottom = 400F,
                smallClockViewBottom = 400F,
                smartspaceCardBottom = 600F,
                smartspaceVisibility = VISIBLE,
            )
            assertThat(bounds?.top).isEqualTo(800F)
            assertThat(bounds?.bottom).isEqualTo(1400F)
        }

    @Test
    fun focalAreaBoundsAlwaysCentered_noNotifications_noSmartspaceCard_inTabletLandscape() =
        testScope.runTest {
            setupTabletLandscape()
            val bounds by collectLastValue(underTest.wallpaperFocalAreaBounds)
            setTestFocalAreaBounds(
                shadeLayoutWide = true,
                shortcutAbsoluteTop = 1800F,
                notificationDefaultTop = 300F,
                notificationStackAbsoluteBottom = 300F,
                smallClockViewBottom = 300F,
                smartspaceCardBottom = 300F,
                smartspaceVisibility = INVISIBLE,
            )

            assertThat(bounds?.top).isEqualTo(600F)
            assertThat(bounds?.bottom).isEqualTo(1400F)

            setTestFocalAreaBounds(
                shadeLayoutWide = true,
                shortcutAbsoluteTop = 1800F,
                notificationDefaultTop = 100F,
                notificationStackAbsoluteBottom = 1000F,
                smallClockViewBottom = 300F,
                smartspaceCardBottom = 300F,
                smartspaceVisibility = INVISIBLE,
            )
            assertThat(bounds?.top).isEqualTo(600F)
        }

    @Test
    fun onTapInFocalBounds_sendTapPosition() =
        testScope.runTest {
            setupHandheldDevice()
            underTest.sendTapPosition(750F, 750F)
            verify(wallpaperInteractor).sendTapPosition(PointF(625F, 875F))
        }

    @Test
    fun shouldNotCollectFocalArea_notHasFocalArea() =
        testScope.runTest {
            val shouldCollectFocalArea by collectLastValue(underTest.shouldCollectFocalArea)
            assertThat(shouldCollectFocalArea).isTrue()
            kosmos.fakeWallpaperRepository.setShouldSendFocalArea(false)
            assertThat(shouldCollectFocalArea).isFalse()
        }

    @Test
    fun shouldCollectFocalBounds_onReboot() =
        testScope.runTest {
            val shouldCollectFocalArea by collectLastValue(underTest.shouldCollectFocalArea)
            assertThat(shouldCollectFocalArea).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun shouldNotSendBounds_whenGoingFromLockscreenToGone() =
        testScope.runTest {
            val shouldCollectFocalArea by collectLastValue(underTest.shouldCollectFocalArea)
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(
                    TransitionStep(
                        transitionState = TransitionState.STARTED,
                        from = KeyguardState.OFF,
                        to = LOCKSCREEN,
                    ),
                    TransitionStep(
                        transitionState = TransitionState.FINISHED,
                        from = KeyguardState.OFF,
                        to = LOCKSCREEN,
                    ),
                ),
                testScope,
            )
            assertThat(shouldCollectFocalArea).isTrue()

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
            runCurrent()
            assertThat(shouldCollectFocalArea).isFalse()
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
            assertThat(shouldCollectFocalArea).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun shouldNotCollectFocalArea_isIdleInLockscreenWithDualShadeOverlays() =
        testScope.runTest {
            kosmos.enableDualShade()
            val shouldCollectFocalArea by collectLastValue(underTest.shouldCollectFocalArea)
            kosmos.sceneInteractor.changeScene(toScene = Scenes.Lockscreen, loggingReason = "test")
            kosmos.sceneInteractor.setTransitionState(
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Lockscreen)
                )
            )
            runCurrent()
            assertThat(shouldCollectFocalArea).isTrue()

            kosmos.sceneInteractor.showOverlay(
                overlay = Overlays.QuickSettingsShade,
                loggingReason = "test",
            )
            kosmos.sceneInteractor.setTransitionState(
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(
                        Scenes.Lockscreen,
                        setOf(Overlays.QuickSettingsShade),
                    )
                )
            )
            runCurrent()
            assertThat(shouldCollectFocalArea).isFalse()

            kosmos.sceneInteractor.hideOverlay(
                overlay = Overlays.QuickSettingsShade,
                loggingReason = "test",
            )
            kosmos.sceneInteractor.setTransitionState(
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Lockscreen)
                )
            )

            runCurrent()
            assertThat(shouldCollectFocalArea).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun shouldSendBounds_onLockscreen() =
        testScope.runTest {
            val shouldCollectFocalArea by collectLastValue(underTest.shouldCollectFocalArea)
            assertThat(shouldCollectFocalArea).isTrue()
            kosmos.fakeKeyguardTransitionRepository.sendTransitionSteps(
                listOf(TransitionStep(transitionState = TransitionState.STARTED, to = LOCKSCREEN)),
                testScope,
            )
            runCurrent()
            assertThat(shouldCollectFocalArea).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun shouldCollectFocalArea_isIdleInLockscreen() =
        testScope.runTest {
            val shouldCollectFocalArea by collectLastValue(underTest.shouldCollectFocalArea)
            kosmos.sceneInteractor.changeScene(toScene = Scenes.Lockscreen, loggingReason = "test")
            assertThat(shouldCollectFocalArea).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun shouldNotCollectFocalArea_transitioningFromLockscreen() =
        testScope.runTest {
            val shouldCollectFocalArea by collectLastValue(underTest.shouldCollectFocalArea)
            kosmos.sceneInteractor.setTransitionState(
                MutableStateFlow(ObservableTransitionState.Idle(currentScene = Scenes.Lockscreen))
            )
            assertThat(shouldCollectFocalArea).isTrue()

            kosmos.sceneInteractor.setTransitionState(
                MutableStateFlow(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )
            assertThat(shouldCollectFocalArea).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun shouldNotCollectFocalArea_transitioningFromShadeToLockscreen() =
        testScope.runTest {
            val shouldCollectFocalArea by collectLastValue(underTest.shouldCollectFocalArea)
            kosmos.sceneInteractor.setTransitionState(
                MutableStateFlow(ObservableTransitionState.Idle(currentScene = Scenes.Shade))
            )
            assertThat(shouldCollectFocalArea).isFalse()

            kosmos.sceneInteractor.setTransitionState(
                MutableStateFlow(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )
            assertThat(shouldCollectFocalArea).isFalse()
        }

    @Test
    fun lockscreenWallpaperNotHasFocalAreaTarget_hasFocalAreaIsTrue() =
        testScope.runTest {
            val hasFocalArea by collectLastValue(underTest.hasFocalArea)
            kosmos.fakeWallpaperRepository.setShouldSendFocalArea(true)
            assertThat(hasFocalArea).isTrue()
        }

    @Test
    fun lockscreenWallpaperHasFocalAreaTarget_hasFocalAreaIsFalse() =
        testScope.runTest {
            val hasFocalArea by collectLastValue(underTest.hasFocalArea)
            kosmos.fakeWallpaperRepository.setShouldSendFocalArea(false)
            assertThat(hasFocalArea).isFalse()
        }

    data class OverrideResources(
        val screenWidth: Int,
        val screenHeight: Int,
        val centerAlignFocalArea: Boolean,
    )

    private fun setupHandheldDevice() {
        overrideMockedResources(
            mockedResources,
            OverrideResources(screenWidth = 1000, screenHeight = 2000, centerAlignFocalArea = false),
        )
        kosmos.enableSingleShade()
    }

    private fun setupTabletLandscape() {
        overrideMockedResources(
            mockedResources,
            OverrideResources(screenWidth = 3000, screenHeight = 2000, centerAlignFocalArea = true),
        )
        kosmos.enableSplitShade()
    }

    private fun setupUnfoldLandscape() {
        overrideMockedResources(
            mockedResources,
            OverrideResources(screenWidth = 2500, screenHeight = 2000, centerAlignFocalArea = false),
        )
        kosmos.enableSplitShade()
    }

    private fun setTestFocalAreaBounds(
        shadeLayoutWide: Boolean = false,
        shortcutAbsoluteTop: Float = 400F,
        notificationDefaultTop: Float = 20F,
        notificationStackAbsoluteBottom: Float = 20F,
        smallClockViewBottom: Float = 20F,
        smartspaceCardBottom: Float = 0F,
        smartspaceVisibility: Int = INVISIBLE,
    ) {
        kosmos.shadeRepository.setShadeLayoutWide(shadeLayoutWide)
        if (SceneContainerFlag.isEnabled) {
            kosmos.wallpaperFocalAreaRepository.shortcutAbsoluteTop.value = shortcutAbsoluteTop
            kosmos.wallpaperFocalAreaRepository.smallClockViewBottom.value = smallClockViewBottom
            kosmos.wallpaperFocalAreaRepository.smartspaceCardBottom.value = smartspaceCardBottom
            kosmos.wallpaperFocalAreaRepository.notificationStackAbsoluteBottom.value =
                notificationStackAbsoluteBottom
        } else {
            kosmos.wallpaperFocalAreaRepository.shortcutAbsoluteTop.value = shortcutAbsoluteTop
            kosmos.wallpaperFocalAreaRepository.notificationDefaultTop.value =
                notificationDefaultTop
            kosmos.wallpaperFocalAreaRepository.notificationStackAbsoluteBottom.value =
                notificationStackAbsoluteBottom
            kosmos.keyguardSmartspaceInteractor.setBcSmartspaceVisibility(smartspaceVisibility)
        }
    }

    companion object {
        fun overrideMockedResources(
            mockedResources: Resources,
            overrideResources: OverrideResources,
        ) {
            val displayMetrics =
                DisplayMetrics().apply {
                    widthPixels = overrideResources.screenWidth
                    heightPixels = overrideResources.screenHeight
                    density = 2f
                }
            whenever(mockedResources.displayMetrics).thenReturn(displayMetrics)
            whenever(mockedResources.getBoolean(R.bool.center_align_focal_area_shape))
                .thenReturn(overrideResources.centerAlignFocalArea)
        }
    }
}
