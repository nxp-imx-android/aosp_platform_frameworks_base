/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.MotionTest
import android.testing.TestableLooper.RunWithLooper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.animation.scene.Key
import com.android.compose.animation.scene.isElement
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.keyguard.dagger.KeyguardStatusBarViewComponent
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_NOTIFICATION_SHADE_BLUR
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.authController
import com.android.systemui.communal.data.repository.communalSettingsRepository
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.ui.compose.Communal
import com.android.systemui.communal.ui.compose.CommunalScene
import com.android.systemui.communal.ui.compose.section.AmbientStatusBarSection
import com.android.systemui.communal.ui.viewmodel.CommunalUserActionsViewModel
import com.android.systemui.communal.ui.viewmodel.ambientStatusBarSection
import com.android.systemui.communal.ui.viewmodel.communalContent
import com.android.systemui.communal.ui.viewmodel.communalUserActionsViewModel
import com.android.systemui.communal.ui.viewmodel.communalViewModel
import com.android.systemui.communal.util.communalColors
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardClockInteractorWithImpl
import com.android.systemui.keyguard.ui.composable.LockscreenContent
import com.android.systemui.keyguard.ui.composable.LockscreenScene
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElementFactoryImpl
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElements
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.ambientIndicationAreaProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.clockRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.defaultClockFaceLayout
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.keyguardStatusBarViewComponentFactory
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockIconElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenLowerRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenRootElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenUpperRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.settingsMenuElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.statusBarElementProvider
import com.android.systemui.keyguard.ui.viewmodel.LockscreenUserActionsViewModel
import com.android.systemui.keyguard.ui.viewmodel.keyguardClockViewModelWithImpl
import com.android.systemui.keyguard.ui.viewmodel.lockscreenBehindScrimViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.lockscreenContentViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.lockscreenFrontScrimViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.lockscreenUserActionsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.log.LogBuffer
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesShadeStateViewModelFactory
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement
import com.android.systemui.qs.ui.composable.QuickSettingsScene
import com.android.systemui.qs.ui.viewmodel.quickSettingsSceneContentViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsUserActionsViewModelFactory
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.sceneContainerTransitions
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.KeyguardStatusBarViewController
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.motion.compose.ComposeFeatureCaptures
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.golden.FeatureCapture
import platform.test.motion.golden.TimeSeriesCaptureScope
import platform.test.motion.golden.asDataPoint
import platform.test.motion.golden.feature
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

@RunWith(AndroidJUnit4::class)
@MotionTest
@LargeTest
@RunWithLooper
@EnableSceneContainer
@EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
class LockScreenTransitionTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val deviceSpec = DeviceEmulationSpec(Phone)
    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    private val lockscreenUserActionsViewModelFactory =
        object : LockscreenUserActionsViewModel.Factory {
            override fun create() = kosmos.lockscreenUserActionsViewModel
        }

    private val notificationScrollView = kosmos.notificationScrollView

    private val keyguardStatusBarViewComponentFactory: KeyguardStatusBarViewComponent.Factory =
        kosmos.keyguardStatusBarViewComponentFactory

    private val keyguardStatusBarViewComponent: KeyguardStatusBarViewComponent =
        mock<KeyguardStatusBarViewComponent>()
    private val keyguardStatusBarViewController: KeyguardStatusBarViewController =
        mock<KeyguardStatusBarViewController>()

    @Before
    fun setup() {
        whenever(keyguardStatusBarViewComponentFactory.build(any(), any()))
            .thenReturn(keyguardStatusBarViewComponent)
        whenever(keyguardStatusBarViewComponent.keyguardStatusBarViewController)
            .thenReturn(keyguardStatusBarViewController)
        whenever(kosmos.authController.scaleFactor).thenReturn(1.5f)
        whenever(kosmos.communalSettingsRepository.getV2FlagEnabled()).thenReturn(true)
    }

    private val lockScreenContent =
        with(kosmos) {
            LockscreenContent(
                viewModelFactory = lockscreenContentViewModelFactory,
                lockscreenFrontScrimViewModelFactory = lockscreenFrontScrimViewModelFactory,
                lockscreenBehindScrimViewModelFactory = lockscreenBehindScrimViewModelFactory,
                lockscreenElements =
                    LockscreenElements(
                        builder =
                            object : LockscreenElementFactoryImpl.Builder {
                                override fun create(
                                    elements: Map<Key, BaseLockscreenElement>
                                ): LockscreenElementFactoryImpl {
                                    return LockscreenElementFactoryImpl(
                                        elements = elements,
                                        blueprintLog =
                                            LogBuffer(
                                                "Test",
                                                10,
                                                com.android.systemui.util.mockito.mock(),
                                            ),
                                    )
                                }
                            },
                        keyguardClockViewModel = keyguardClockViewModelWithImpl,
                        elementProviders = {
                            setOf(
                                lockscreenRootElementProvider,
                                statusBarElementProvider,
                                lockscreenUpperRegionElementProvider,
                                lockIconElementProvider,
                                ambientIndicationAreaProvider,
                                lockscreenLowerRegionElementProvider,
                                settingsMenuElementProvider,
                                clockRegionElementProvider,
                                defaultClockFaceLayout,
                            )
                        },
                        blueprintLog =
                            LogBuffer(
                                "LockscreenBuffer",
                                10,
                                com.android.systemui.util.mockito.mock(),
                            ),
                    ),
                clockInteractor = keyguardClockInteractorWithImpl,
                interactionJankMonitor = interactionJankMonitor,
            )
        }

    private val shadeSession: SaveableSession =
        object : SaveableSession, Session by Session(SessionStorage()) {
            @Composable
            override fun <T : Any> rememberSaveableSession(
                vararg inputs: Any?,
                saver: Saver<T, out Any>,
                key: String?,
                init: () -> T,
            ): T = rememberSession(key, inputs = inputs, init = init)
        }
    private val communalActionsViewModelFactory =
        object : CommunalUserActionsViewModel.Factory {
            override fun create(): CommunalUserActionsViewModel {
                return kosmos.communalUserActionsViewModel
            }
        }
    private val communalScene =
        CommunalScene(
            contentViewModel = kosmos.communalViewModel,
            actionsViewModelFactory = communalActionsViewModelFactory,
            communalColors = kosmos.communalColors,
            communalContent = kosmos.communalContent,
            ambientStatusBarSection = mock<AmbientStatusBarSection>(),
        )
    private val quickSettingScene =
        QuickSettingsScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { notificationScrollView },
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            actionsViewModelFactory = kosmos.quickSettingsUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.quickSettingsSceneContentViewModelFactory,
            notificationRulesShadeStateViewModelFactory =
                kosmos.notificationRulesShadeStateViewModelFactory,
            jankMonitor = kosmos.interactionJankMonitor,
        )

    private val lockscreenScene =
        LockscreenScene(
            actionsViewModelFactory = lockscreenUserActionsViewModelFactory,
            lockscreenContent = { lockScreenContent },
        )

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    fun swipeLeftFromLockscreenToCommunalScene_recordingLockscreenAlpha() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.communalSettingsInteractor.setSuppressionReasons(emptyList())
            kosmos.fakeKeyguardRepository.setKeyguardShowing(true)
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true

            val motion =
                recordMotion(
                    content = { SceneContainerUnderTest() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionState.isIdle()
                                    }
                                }
                            ) {
                                performTouchInputAsync(onRoot()) { swipeLeft(startX = centerX) }

                                awaitCondition {
                                    kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                }
                            },
                            recordAfter = false,
                            recordBefore = false,
                        ) {
                            featureFloat(LockscreenContent.LockscreenContentMotionTestKeys.Alpha)
                            feature(
                                isElement(Communal.Elements.Grid),
                                ComposeFeatureCaptures.x,
                                useUnmergedTree = true,
                            )
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    fun recordLockscreenAlpha_duringSwipeDownToQS() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.populateQuickSettings(14)
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true

            val motion =
                recordMotion(
                    content = { SceneContainerUnderTest() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                    }
                                }
                            ) {

                                // perform swipe down gesture from top
                                performTouchInputAsync(onRoot()) {
                                    swipeDown(endY = centerY, durationMillis = 300)
                                }
                            }
                        ) {
                            featureFloat(LockscreenContent.LockscreenContentMotionTestKeys.Alpha)
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Composable
    private fun SceneContainerUnderTest() {
        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                val vm =
                    rememberViewModel("HomeScreenShadeTest") {
                        kosmos.sceneContainerViewModelFactory.create {}
                    }
                ObserveReadsRoot {
                    SceneContainer(
                        viewModel = vm,
                        sceneByKey =
                            mapOf(
                                Scenes.Lockscreen to lockscreenScene,
                                Scenes.QuickSettings to quickSettingScene,
                                Scenes.Communal to communalScene,
                            ),
                        initialSceneKey = Scenes.Lockscreen,
                        transitionsBuilder = kosmos.sceneContainerTransitions,
                        overlayByKey = mapOf(),
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                        onTransitionStart = { _, _ -> },
                        onSnap = {},
                    )
                }
            }
        }
    }

    private companion object {
        fun TimeSeriesCaptureScope<SemanticsNodeInteractionsProvider>.featureFloat(
            motionTestValueKey: MotionTestValueKey<Float>
        ) {
            feature(
                motionTestValueKey = motionTestValueKey,
                capture =
                    FeatureCapture(motionTestValueKey.semanticsPropertyKey.name) {
                        it.asDataPoint()
                    },
                name = motionTestValueKey.semanticsPropertyKey.name,
            )
        }
    }
}
