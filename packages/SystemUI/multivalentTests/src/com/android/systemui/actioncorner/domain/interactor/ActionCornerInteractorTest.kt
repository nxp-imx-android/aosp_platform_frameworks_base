/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.actioncorner.domain.interactor

import android.provider.Settings.Secure.ACTION_CORNER_ACTION_HOME
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_NOTIFICATIONS
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_OVERVIEW
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_QUICK_SETTINGS
import android.provider.Settings.Secure.ACTION_CORNER_BOTTOM_LEFT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_BOTTOM_RIGHT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_TOP_LEFT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_TOP_RIGHT_ACTION
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.LauncherProxyService
import com.android.systemui.SysuiTestCase
import com.android.systemui.actioncorner.data.model.ActionCornerRegion
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerState.ActiveActionCorner
import com.android.systemui.actioncorner.data.repository.ActionCornerSettingRepository
import com.android.systemui.actioncorner.data.repository.FakeActionCornerRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.HOME
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.OVERVIEW
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.data.repository.userAwareSecureSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@EnableSceneContainer
@RunWith(AndroidJUnit4::class)
class ActionCornerInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.actionCornerRepository by Fixture { FakeActionCornerRepository() }

    private val settingsRepository = kosmos.userAwareSecureSettingsRepository
    private val Kosmos.actionCornerSettingRepository by Fixture {
        ActionCornerSettingRepository(settingsRepository, testScope.backgroundScope, testDispatcher)
    }

    private val Kosmos.launcherProxyService by Fixture { mock<LauncherProxyService>() }
    private val Kosmos.underTest by Fixture {
        ActionCornerInteractor(
            testScope.coroutineContext,
            actionCornerRepository,
            launcherProxyService,
            shadeModeInteractor,
            shadeInteractor,
            actionCornerSettingRepository,
        )
    }

    @Before
    fun setUp() {
        kosmos.enableDualShade()
        kosmos.underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun bottomLeftCornerActivated_overviewActionConfigured_notifyLauncherOfOverviewAction() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_BOTTOM_LEFT_ACTION,
                ACTION_CORNER_ACTION_OVERVIEW,
            )
            actionCornerRepository.addState(ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY))
            verify(launcherProxyService).onActionCornerActivated(OVERVIEW, DEFAULT_DISPLAY)
        }

    @Test
    fun bottomRightCornerActivated_homeActionConfigured_notifyLauncherOfHomeAction() =
        kosmos.runTest {
            settingsRepository.setInt(ACTION_CORNER_BOTTOM_RIGHT_ACTION, ACTION_CORNER_ACTION_HOME)
            actionCornerRepository.addState(ActiveActionCorner(BOTTOM_RIGHT, DEFAULT_DISPLAY))
            verify(launcherProxyService).onActionCornerActivated(HOME, DEFAULT_DISPLAY)
        }

    @Test
    fun shadeCollapsed_topLeftCornerActivated_notificationsActionConfigured_expandNotificationShade() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_TOP_LEFT_ACTION,
                ACTION_CORNER_ACTION_NOTIFICATIONS,
            )
            shadeTestUtil.setShadeExpansion(0f)

            actionCornerRepository.addState(
                ActiveActionCorner(ActionCornerRegion.TOP_LEFT, DEFAULT_DISPLAY)
            )

            assertThat(sceneInteractor.currentOverlays.value)
                .containsExactly(Overlays.NotificationsShade)
        }

    @Test
    fun shadeExpanded_topLeftCornerActivated_notificationsActionConfigured_collapseNotificationShade() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_TOP_LEFT_ACTION,
                ACTION_CORNER_ACTION_NOTIFICATIONS,
            )
            shadeTestUtil.setShadeExpansion(1f)

            actionCornerRepository.addState(
                ActiveActionCorner(ActionCornerRegion.TOP_LEFT, DEFAULT_DISPLAY)
            )

            assertThat(sceneInteractor.currentOverlays.value)
                .doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun qsCollapsed_topRightCornerActivated_qsActionConfigured_expandQsPanel() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_TOP_RIGHT_ACTION,
                ACTION_CORNER_ACTION_QUICK_SETTINGS,
            )
            shadeTestUtil.setQsExpansion(0f)

            actionCornerRepository.addState(
                ActiveActionCorner(ActionCornerRegion.TOP_RIGHT, DEFAULT_DISPLAY)
            )

            assertThat(sceneInteractor.currentOverlays.value)
                .containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    fun qsExpanded_topRightCornerActivated_qsActionConfigured_collapseQsPanel() =
        kosmos.runTest {
            settingsRepository.setInt(
                ACTION_CORNER_TOP_RIGHT_ACTION,
                ACTION_CORNER_ACTION_QUICK_SETTINGS,
            )
            shadeTestUtil.setQsExpansion(1f)

            actionCornerRepository.addState(
                ActiveActionCorner(ActionCornerRegion.TOP_RIGHT, DEFAULT_DISPLAY)
            )

            assertThat(sceneInteractor.currentOverlays.value)
                .doesNotContain(Overlays.QuickSettingsShade)
        }
}
