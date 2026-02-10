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

package com.android.systemui.shade.ui.motion

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.MotionTest
import android.testing.TestableLooper.RunWithLooper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.data.repository.keyguardRepository
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.notifications.intelligence.rules.ui.composable.notificationRulesScreen
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesScreenViewModelFactory
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesShadeStateViewModelFactory
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.sceneContainerTransitions
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.GoneScene
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.scene.ui.viewmodel.GoneUserActionsViewModel
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.ui.composable.ShadeScene
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.shade.ui.viewmodel.shadeSceneContentViewModelFactory
import com.android.systemui.shade.ui.viewmodel.shadeUserActionsViewModelFactory
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeFeatureCaptures.size
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

@RunWith(AndroidJUnit4::class)
@MotionTest
@LargeTest
@RunWithLooper
@EnableSceneContainer
class ShadeSceneToGoneSceneTransitionTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val deviceSpec = DeviceEmulationSpec(Phone)
    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    private val shadeSession =
        object : SaveableSession, Session by Session(SessionStorage()) {
            @Composable
            override fun <T : Any> rememberSaveableSession(
                vararg inputs: Any?,
                saver: Saver<T, out Any>,
                key: String?,
                init: () -> T,
            ): T = rememberSession(key, inputs = inputs, init = init)
        }

    private val goneScene =
        GoneScene(
            notificationStackScrollView = { kosmos.notificationScrollView },
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            viewModelFactory =
                object : GoneUserActionsViewModel.Factory {
                    override fun create(): GoneUserActionsViewModel {
                        return GoneUserActionsViewModel(kosmos.shadeModeInteractor)
                    }
                },
        )

    @Before
    fun setup() {
        // Disabled keyguard. Otherwise during the transition leads to Lockscreen.
        kosmos.keyguardRepository.setKeyguardEnabled(false)
    }

    private val shadeScene =
        ShadeScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { kosmos.notificationScrollView },
            actionsViewModelFactory = kosmos.shadeUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.shadeSceneContentViewModelFactory,
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            notificationRulesShadeStateViewModelFactory =
                kosmos.notificationRulesShadeStateViewModelFactory,
            notificationRulesScreenViewModelFactory =
                kosmos.notificationRulesScreenViewModelFactory,
            notificationRulesScreen = kosmos.notificationRulesScreen,
            jankMonitor = kosmos.interactionJankMonitor,
        )

    val transitionState =
        MutableStateFlow<ObservableTransitionState>(ObservableTransitionState.Idle(Scenes.Shade))

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    fun swipeUpFromShadeToGoneScene_recordingQQSPanelSize() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.usingMediaInComposeFragment = true
            kosmos.populateQuickSettings(tileCount = 7)

            val motion =
                recordMotion(
                    content = { ShadeToGoneSceneContainer(transitionState = transitionState) },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                    }
                                }
                            ) {
                                performTouchInputAsync(onRoot()) {
                                    swipe(
                                        start = Offset(x = centerX, y = bottom),
                                        end = Offset(x = centerX, y = top),
                                        durationMillis = 500,
                                    )
                                }
                            }
                        ) {
                            feature(
                                hasTestTag(resIdToTestTag("quick_qs_panel")),
                                size,
                                "quick_qs_panel_size",
                                useUnmergedTree = true,
                            )
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Composable
    private fun ShadeToGoneSceneContainer(transitionState: Flow<ObservableTransitionState>?) {
        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                val vm =
                    rememberViewModel("HomeScreenShadeTest") {
                        kosmos.sceneContainerViewModelFactory
                            .create() {}
                            .apply { setTransitionState(transitionState = transitionState) }
                    }

                ObserveReadsRoot {
                    SceneContainer(
                        viewModel = vm,
                        sceneByKey = mapOf(Scenes.Gone to goneScene, Scenes.Shade to shadeScene),
                        initialSceneKey = Scenes.Shade,
                        transitionsBuilder = kosmos.sceneContainerTransitions,
                        overlayByKey = mapOf(),
                        onTransitionStart = { _, _ -> },
                        onSnap = {},
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                    )
                }
            }
        }
    }
}
