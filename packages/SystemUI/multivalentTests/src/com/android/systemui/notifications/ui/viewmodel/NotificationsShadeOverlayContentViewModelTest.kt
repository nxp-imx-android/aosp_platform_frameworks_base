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

package com.android.systemui.notifications.ui.viewmodel

import android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS
import android.content.res.Configuration
import android.content.res.mainResources
import android.content.testableContext
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_NOTIFICATION_SHADE_BLUR
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.AuthenticationResult
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.domain.pipeline.interactor.mediaCarouselInteractor
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.media.remedia.ui.viewmodel.factory.mediaViewModelFactory
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.ui.viewmodel.notificationsShadeOverlayContentViewModel
import com.android.systemui.shade.ui.viewmodel.shadeHeaderViewModelFactory
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.domain.interactor.disableFlagsInteractor
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.android.systemui.window.domain.interactor.windowRootViewBlurInteractor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class NotificationsShadeOverlayContentViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val sceneInteractor by lazy { kosmos.sceneInteractor }
    private val underTest by lazy { kosmos.notificationsShadeOverlayContentViewModel }

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
        kosmos.enableDualShade()
        kosmos.runCurrent()
        underTest.activateIn(testScope)
    }

    @Test
    fun showHeader_desktopFeatureSetDisabled_true() =
        testScope.runTest {
            setEnableDesktopFeatureSet(false)
            assertThat(underTest.showHeader).isTrue()
        }

    @Test
    @EnableFlags(StatusBarForDesktop.FLAG_NAME)
    fun showHeader_desktopFeatureSetEnabled_statusBarForDesktopEnabled_false() =
        testScope.runTest {
            setEnableDesktopFeatureSet(true)
            assertThat(underTest.showHeader).isFalse()
        }

    @Test
    @DisableFlags(StatusBarForDesktop.FLAG_NAME)
    fun showHeader_desktopFeatureSetEnabled_statusBarForDesktopDisabled_true() =
        testScope.runTest {
            setEnableDesktopFeatureSet(true)
            assertThat(underTest.showHeader).isTrue()
        }

    @Test
    @DisableFlags(StatusBarForDesktop.FLAG_NAME)
    fun alignmentOnWideScreens_statusBarForDesktopDisabled_topStart() =
        testScope.runTest {
            assertThat(kosmos.createTestInstance().alignmentOnWideScreens)
                .isEqualTo(Alignment.TopStart)
        }

    @Test
    @EnableFlags(StatusBarForDesktop.FLAG_NAME)
    fun alignmentOnWideScreens_configDisabled_statusBarForDesktopEnabled_topStart() =
        testScope.runTest {
            overrideConfig(R.bool.config_notificationShadeOnTopEnd, false)
            assertThat(kosmos.createTestInstance().alignmentOnWideScreens)
                .isEqualTo(Alignment.TopStart)
        }

    @Test
    @EnableFlags(StatusBarForDesktop.FLAG_NAME)
    fun alignmentOnWideScreens_configEnabled_statusBarForDesktopEnabled_topEnd() =
        testScope.runTest {
            overrideConfig(R.bool.config_notificationShadeOnTopEnd, true)
            assertThat(kosmos.createTestInstance().alignmentOnWideScreens)
                .isEqualTo(Alignment.TopEnd)
        }

    @Test
    fun onScrimClicked_hidesShade() =
        testScope.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            sceneInteractor.showOverlay(Overlays.NotificationsShade, "test")
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            underTest.onScrimClicked()

            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun deviceLocked_hidesShade() =
        testScope.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            unlockDevice()
            sceneInteractor.showOverlay(Overlays.NotificationsShade, "test")
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            lockDevice()

            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun shadeNotTouchable_hidesShade() =
        testScope.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            val isShadeTouchable by collectLastValue(kosmos.shadeInteractor.isShadeTouchable)
            assertThat(isShadeTouchable).isTrue()
            sceneInteractor.showOverlay(Overlays.NotificationsShade, "test")
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            lockDevice()
            assertThat(isShadeTouchable).isFalse()
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun showMedia_activeMedia_true() =
        testScope.runTest {
            kosmos.mediaPipelineRepository.addCurrentUserMediaEntry(MediaData(active = true))
            runCurrent()

            assertThat(underTest.showMedia).isTrue()
        }

    @Test
    fun showMedia_InactiveMedia_false() =
        testScope.runTest {
            kosmos.mediaPipelineRepository.addCurrentUserMediaEntry(MediaData(active = false))
            runCurrent()

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    fun showMedia_noMedia_false() =
        testScope.runTest {
            kosmos.mediaPipelineRepository.addCurrentUserMediaEntry(MediaData(active = true))
            kosmos.mediaPipelineRepository.clearCurrentUserMedia()
            runCurrent()

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    fun showMedia_qsDisabled_false() =
        testScope.runTest {
            kosmos.mediaPipelineRepository.addCurrentUserMediaEntry(MediaData(active = true))
            kosmos.fakeDisableFlagsRepository.disableFlags.update {
                it.copy(disable2 = DISABLE2_QUICK_SETTINGS)
            }
            runCurrent()

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOff_isDisabled() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            runCurrent()

            assertThat(underTest.isTransparencyEnabled).isFalse()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOn_blurSupported_isEnabled() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            runCurrent()

            assertThat(underTest.isTransparencyEnabled).isTrue()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOn_blurUnsupported_isDisabled() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = false
            runCurrent()

            assertThat(underTest.isTransparencyEnabled).isFalse()
        }

    @Test
    fun shadeModeChanged_single_switchesToShadeScene() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            kosmos.enableDualShade()
            kosmos.shadeInteractor.expandNotificationsShade("test")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            kosmos.enableSingleShade()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun shadeModeChanged_split_switchesToShadeScene() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            kosmos.enableDualShade()
            kosmos.shadeInteractor.expandNotificationsShade("test")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            kosmos.enableSplitShade()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    private fun TestScope.lockDevice() {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        kosmos.powerInteractor.setAsleepForTest()
        runCurrent()

        assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
    }

    private fun setEnableDesktopFeatureSet(enable: Boolean) {
        overrideConfig(R.bool.config_enableDesktopFeatureSet, enable)
        kosmos.configurationController.onConfigurationChanged(Configuration())
    }

    private fun overrideConfig(configId: Int, value: Boolean) {
        kosmos.testableContext.orCreateTestableResources.addOverride(configId, value)
    }

    private fun Kosmos.createTestInstance(): NotificationsShadeOverlayContentViewModel {
        val desktopInteractor =
            DesktopInteractor(
                resources = mainResources,
                scope = backgroundScope,
                configurationController = configurationController,
            )
        return NotificationsShadeOverlayContentViewModel(
            mainDispatcher = testDispatcher,
            shadeHeaderViewModelFactory = shadeHeaderViewModelFactory,
            notificationsPlaceholderViewModelFactory = notificationsPlaceholderViewModelFactory,
            sceneInteractor = sceneInteractor,
            shadeInteractor = shadeInteractor,
            shadeModeInteractor = shadeModeInteractor,
            disableFlagsInteractor = disableFlagsInteractor,
            mediaCarouselInteractor = mediaCarouselInteractor,
            windowRootViewBlurInteractor = windowRootViewBlurInteractor,
            desktopInteractor = desktopInteractor,
            mediaViewModelFactory = mediaViewModelFactory,
        )
    }

    private suspend fun TestScope.unlockDevice() {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        kosmos.powerInteractor.setAwakeForTest()
        runCurrent()
        assertThat(
                kosmos.authenticationInteractor.authenticate(
                    FakeAuthenticationRepository.DEFAULT_PIN
                )
            )
            .isEqualTo(AuthenticationResult.SUCCEEDED)

        assertThat(currentScene).isEqualTo(Scenes.Gone)
    }

    @Test
    fun onShadeBoundsChanged_forwardsToShadeOverlayInteractor() =
        testScope.runTest {
            var shadeBounds: android.graphics.Rect? = null
            kosmos.shadeInteractor.addShadeOverlayBoundsListener { shadeBounds = it }
            assertThat(shadeBounds).isNull()

            val bounds = Rect(0f, 0f, 100f, 100f)
            val expectedShadeBounds = android.graphics.Rect(0, 0, 100, 100)
            underTest.onShadeOverlayBoundsChanged(bounds)

            assertThat(shadeBounds).isEqualTo(expectedShadeBounds)
        }
}
