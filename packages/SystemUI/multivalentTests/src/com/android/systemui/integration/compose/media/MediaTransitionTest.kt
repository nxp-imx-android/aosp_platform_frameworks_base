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
package com.android.systemui.media

import android.platform.test.annotations.DisableFlags
import android.testing.TestableLooper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.integration.SystemUiIntegrationTest
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.data.repository.fakeActiveMedia
import com.android.systemui.media.remedia.data.repository.fakeResumableMedia
import com.android.systemui.media.remedia.data.repository.setFakeCurrentMedia
import com.android.systemui.media.remedia.data.repository.setHasMedia
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesParentViewModelFactory
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.ui.composable.QuickSettingsScene
import com.android.systemui.qs.ui.viewmodel.quickSettingsSceneContentViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsUserActionsViewModelFactory
import com.android.systemui.scene.sceneContainerTransitions
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.scene.ui.view.sceneTransitionLatencyMonitor
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.ui.composable.ShadeScene
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.shade.ui.viewmodel.shadeSceneContentViewModelFactory
import com.android.systemui.shade.ui.viewmodel.shadeUserActionsViewModelFactory
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
@DisableFlags(Flags.FLAG_DUAL_SHADE)
@SystemUiIntegrationTest
class MediaTransitionTest : SysuiTestCase() {
    @get:Rule val composeTestRule = createComposeRule()

    private val kosmos = testKosmos()
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

    private val shadeScene =
        ShadeScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { kosmos.notificationScrollView },
            actionsViewModelFactory = kosmos.shadeUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.shadeSceneContentViewModelFactory,
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            notificationRulesParentViewModelFactory =
                kosmos.notificationRulesParentViewModelFactory,
            jankMonitor = kosmos.interactionJankMonitor,
        )

    private val quickSettingsScene =
        QuickSettingsScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { kosmos.notificationScrollView },
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            actionsViewModelFactory = kosmos.quickSettingsUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.quickSettingsSceneContentViewModelFactory,
            notificationRulesParentViewModelFactory =
                kosmos.notificationRulesParentViewModelFactory,
            jankMonitor = kosmos.interactionJankMonitor,
        )

    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    @Test
    fun transitFromShadeToQuickSettings() {
        kosmos.runTest {
            setFakeCurrentMedia(listOf(kosmos.fakeActiveMedia))
            usingMediaInComposeFragment = true
            enableSingleShade()
            setHasMedia(true)

            composeTestRule.setContent { shadeSceneToQuickSettingsSceneContainer() }
            runCurrent()
            composeTestRule.waitForIdle()

            // Verify that the UMO show on shade.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertIsDisplayed()

            composeTestRule.swipeDownFromTopCenter()
            runCurrent()

            // Verify that the UMO show on quick settings.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertIsDisplayed()
        }
    }

    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    @Test
    fun resumableMediaPersistsInQuickSettings() =
        kosmos.runTest {
            setFakeCurrentMedia(listOf(kosmos.fakeResumableMedia))
            usingMediaInComposeFragment = true
            enableSingleShade()

            composeTestRule.setContent { shadeSceneToQuickSettingsSceneContainer() }
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(EXPECTED_RESUMABLE_UMO_CONTENT_DESC, substring = true)
                .assertIsNotDisplayed()

            composeTestRule.swipeDownFromTopCenter()
            runCurrent()

            composeTestRule
                .onNodeWithContentDescription(EXPECTED_RESUMABLE_UMO_CONTENT_DESC, substring = true)
                .assertIsDisplayed()
        }

    @Composable
    private fun shadeSceneToQuickSettingsSceneContainer() {
        val transitionState =
            MutableStateFlow<ObservableTransitionState>(
                ObservableTransitionState.Idle(Scenes.Shade)
            )

        PlatformTheme {
            ObserveReadsRoot {
                WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                    val vm =
                        rememberViewModel("MediaTransitionTest") {
                            kosmos.sceneContainerViewModelFactory
                                .create() {}
                                .apply { setTransitionState(transitionState = transitionState) }
                        }

                    SceneContainer(
                        viewModel = vm,
                        sceneByKey =
                            mapOf(
                                Scenes.Shade to shadeScene,
                                Scenes.QuickSettings to quickSettingsScene,
                            ),
                        initialSceneKey = Scenes.Shade,
                        transitionsBuilder = kosmos.sceneContainerTransitions,
                        overlayByKey = mapOf(),
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                        sceneTransitionLatencyMonitor = kosmos.sceneTransitionLatencyMonitor,
                        onTransitionStart = { _, _ -> },
                        onSnap = {},
                    )
                }
            }
        }
    }

    private companion object {
        const val EXPECTED_UMO_CONTENT_DESC = "Fake_Music_Player"
        const val EXPECTED_RESUMABLE_UMO_CONTENT_DESC = "Fake_Podcast_Player"
    }
}
