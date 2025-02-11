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

package com.android.systemui.communal

import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.provider.Settings
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Flags.FLAG_COMMUNAL_HUB
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.domain.interactor.setCommunalAvailable
import com.android.systemui.communal.shared.log.CommunalUiEvent
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.dock.dockManager
import com.android.systemui.flags.Flags.COMMUNAL_SERVICE_ENABLED
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.notificationShadeWindowController
import com.android.systemui.statusbar.phone.centralSurfacesOptional
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableFlags(FLAG_COMMUNAL_HUB)
class CommunalSceneStartableTest(flags: FlagsParameterization) : SysuiTestCase() {

    companion object {
        private const val SCREEN_TIMEOUT = 1000

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos()

    private lateinit var underTest: CommunalSceneStartable

    @Before
    fun setUp() {
        with(kosmos) {
            fakeSettings.putIntForUser(
                Settings.System.SCREEN_OFF_TIMEOUT,
                SCREEN_TIMEOUT,
                UserHandle.USER_CURRENT,
            )
            kosmos.fakeFeatureFlagsClassic.set(COMMUNAL_SERVICE_ENABLED, true)

            underTest =
                CommunalSceneStartable(
                        dockManager = dockManager,
                        communalInteractor = communalInteractor,
                        communalSettingsInteractor = communalSettingsInteractor,
                        communalSceneInteractor = communalSceneInteractor,
                        keyguardTransitionInteractor = keyguardTransitionInteractor,
                        keyguardInteractor = keyguardInteractor,
                        systemSettings = fakeSettings,
                        notificationShadeWindowController = notificationShadeWindowController,
                        applicationScope = applicationCoroutineScope,
                        bgScope = applicationCoroutineScope,
                        mainDispatcher = testDispatcher,
                        centralSurfacesOpt = centralSurfacesOptional,
                        uiEventLogger = uiEventLoggerFake,
                    )
                    .apply { start() }

            // Make communal available so that communalInteractor.desiredScene accurately reflects
            // scene changes instead of just returning Blank.
            with(kosmos.testScope) {
                launch { setCommunalAvailable(true) }
                testScheduler.runCurrent()
            }
        }
    }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_whenDreaming_goesToBlank() =
        with(kosmos) {
            testScope.runTest {
                // Device is dreaming and on communal.
                updateDreaming(true)
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

                val scene by collectLastValue(communalSceneInteractor.currentScene)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Scene times out back to blank after the screen timeout.
                advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
                assertThat(scene).isEqualTo(CommunalScenes.Blank)
            }
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_notDreaming_staysOnCommunal() =
        with(kosmos) {
            testScope.runTest {
                // Device is not dreaming and on communal.
                updateDreaming(false)
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

                // Scene stays as Communal
                advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
                val scene by collectLastValue(communalSceneInteractor.currentScene)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)
            }
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_dreamStopped_staysOnCommunal() =
        with(kosmos) {
            testScope.runTest {
                // Device is dreaming and on communal.
                updateDreaming(true)
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

                val scene by collectLastValue(communalSceneInteractor.currentScene)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Wait a bit, but not long enough to timeout.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Dream stops, timeout is cancelled and device stays on hub, because the regular
                // screen timeout will take effect at this point.
                updateDreaming(false)
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)
            }
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_dreamStartedHalfway_goesToCommunal() =
        with(kosmos) {
            testScope.runTest {
                // Device is on communal, but not dreaming.
                updateDreaming(false)
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

                val scene by collectLastValue(communalSceneInteractor.currentScene)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Wait a bit, but not long enough to timeout, then start dreaming.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                updateDreaming(true)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Device times out after one screen timeout interval, dream doesn't reset timeout.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(CommunalScenes.Blank)
            }
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_dreamAfterInitialTimeout_goesToBlank() =
        with(kosmos) {
            testScope.runTest {
                // Device is on communal.
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

                // Device stays on the hub after the timeout since we're not dreaming.
                advanceTimeBy(SCREEN_TIMEOUT.milliseconds * 2)
                val scene by collectLastValue(communalSceneInteractor.currentScene)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Start dreaming.
                updateDreaming(true)
                advanceTimeBy(KeyguardInteractor.IS_ABLE_TO_DREAM_DELAY_MS)

                // Hub times out immediately.
                assertThat(scene).isEqualTo(CommunalScenes.Blank)
            }
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_userActivityTriggered_resetsTimeout() =
        with(kosmos) {
            testScope.runTest {
                // Device is dreaming and on communal.
                updateDreaming(true)
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

                val scene by collectLastValue(communalSceneInteractor.currentScene)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Wait a bit, but not long enough to timeout.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)

                // Send user interaction to reset timeout.
                communalInteractor.signalUserInteraction()

                // If user activity didn't reset timeout, we would have gone back to Blank by now.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Timeout happens one interval after the user interaction.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(CommunalScenes.Blank)
            }
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_screenTimeoutChanged() =
        with(kosmos) {
            testScope.runTest {
                fakeSettings.putInt(Settings.System.SCREEN_OFF_TIMEOUT, SCREEN_TIMEOUT * 2)

                // Device is dreaming and on communal.
                updateDreaming(true)
                communalSceneInteractor.changeScene(CommunalScenes.Communal, "test")

                val scene by collectLastValue(communalSceneInteractor.currentScene)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                // Scene times out back to blank after the screen timeout.
                advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
                assertThat(scene).isEqualTo(CommunalScenes.Communal)

                advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
                assertThat(scene).isEqualTo(CommunalScenes.Blank)
                assertThat(uiEventLoggerFake.logs.first().eventId)
                    .isEqualTo(CommunalUiEvent.COMMUNAL_HUB_TIMEOUT.id)
                assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            }
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_withSceneContainer_whenDreaming_goesToBlank() =
        with(kosmos) {
            testScope.runTest {
                // Device is dreaming and on communal.
                updateDreaming(true)
                sceneInteractor.changeScene(Scenes.Communal, "test")

                val scene by collectLastValue(sceneInteractor.currentScene)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Scene times out back to blank after the screen timeout.
                advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
                assertThat(scene).isEqualTo(Scenes.Dream)
            }
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_withSceneContainer_notDreaming_staysOnCommunal() =
        with(kosmos) {
            testScope.runTest {
                // Device is not dreaming and on communal.
                updateDreaming(false)
                sceneInteractor.changeScene(Scenes.Communal, "test")

                // Scene stays as Communal
                advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
                val scene by collectLastValue(sceneInteractor.currentScene)
                assertThat(scene).isEqualTo(Scenes.Communal)
            }
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_withSceneContainer_dreamStopped_staysOnCommunal() =
        with(kosmos) {
            testScope.runTest {
                // Device is dreaming and on communal.
                updateDreaming(true)
                sceneInteractor.changeScene(Scenes.Communal, "test")

                val scene by collectLastValue(sceneInteractor.currentScene)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Wait a bit, but not long enough to timeout.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Dream stops, timeout is cancelled and device stays on hub, because the regular
                // screen timeout will take effect at this point.
                updateDreaming(false)
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(Scenes.Communal)
            }
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_withSceneContainer_dreamStartedHalfway_goesToCommunal() =
        with(kosmos) {
            testScope.runTest {
                // Device is on communal, but not dreaming.
                updateDreaming(false)
                sceneInteractor.changeScene(Scenes.Communal, "test")

                val scene by collectLastValue(sceneInteractor.currentScene)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Wait a bit, but not long enough to timeout, then start dreaming.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                updateDreaming(true)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Device times out after one screen timeout interval, dream doesn't reset timeout.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(Scenes.Dream)
            }
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_withSceneContainer_dreamAfterInitialTimeout_goesToBlank() =
        with(kosmos) {
            testScope.runTest {
                // Device is on communal.
                sceneInteractor.changeScene(Scenes.Communal, "test")

                // Device stays on the hub after the timeout since we're not dreaming.
                advanceTimeBy(SCREEN_TIMEOUT.milliseconds * 2)
                val scene by collectLastValue(sceneInteractor.currentScene)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Start dreaming.
                updateDreaming(true)
                advanceTimeBy(KeyguardInteractor.IS_ABLE_TO_DREAM_DELAY_MS)

                // Hub times out immediately.
                assertThat(scene).isEqualTo(Scenes.Dream)
            }
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_withSceneContainer_userActivityTriggered_resetsTimeout() =
        with(kosmos) {
            testScope.runTest {
                // Device is dreaming and on communal.
                updateDreaming(true)
                sceneInteractor.changeScene(Scenes.Communal, "test")

                val scene by collectLastValue(sceneInteractor.currentScene)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Wait a bit, but not long enough to timeout.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)

                // Send user interaction to reset timeout.
                communalInteractor.signalUserInteraction()

                // If user activity didn't reset timeout, we would have gone back to Blank by now.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Timeout happens one interval after the user interaction.
                advanceTimeBy((SCREEN_TIMEOUT / 2).milliseconds)
                assertThat(scene).isEqualTo(Scenes.Dream)
            }
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun hubTimeout_withSceneContainer_screenTimeoutChanged() =
        with(kosmos) {
            testScope.runTest {
                fakeSettings.putInt(Settings.System.SCREEN_OFF_TIMEOUT, SCREEN_TIMEOUT * 2)

                // Device is dreaming and on communal.
                updateDreaming(true)
                sceneInteractor.changeScene(Scenes.Communal, "test")

                val scene by collectLastValue(sceneInteractor.currentScene)
                assertThat(scene).isEqualTo(Scenes.Communal)

                // Scene times out back to blank after the screen timeout.
                advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
                assertThat(scene).isEqualTo(Scenes.Communal)

                advanceTimeBy(SCREEN_TIMEOUT.milliseconds)
                assertThat(scene).isEqualTo(Scenes.Dream)
                assertThat(uiEventLoggerFake.logs.first().eventId)
                    .isEqualTo(CommunalUiEvent.COMMUNAL_HUB_TIMEOUT.id)
                assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            }
        }

    private fun TestScope.updateDreaming(dreaming: Boolean) =
        with(kosmos) {
            fakeKeyguardRepository.setDreaming(dreaming)
            runCurrent()
        }
}
