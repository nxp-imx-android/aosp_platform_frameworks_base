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

package com.android.systemui.shade.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffect
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModel
import com.android.systemui.testKosmos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SingleShadeNestedScrollLayoutTest : SysuiTestCase() {
    @get:Rule val rule = createComposeRule()

    private val kosmos = testKosmos()

    @Test
    fun scrimAtRest() =
        kosmos.runTest {
            setTestContent(scrimContentHeight = LayoutSize)

            // Initial layout placement
            assertNode(TAG_LAYOUT, width = LayoutSize, height = LayoutSize, top = 0.dp)
            // StatusBar is matching its size constraints and is placed on top
            assertNode(TAG_SB, width = LayoutSize, height = StatusBarHeight, top = 0.dp)
            // Headers are matching their size constraints and are placed below the StatusBar
            assertNode(TAG_HEADER, width = LayoutSize, height = HeaderHeight, top = StatusBarHeight)
            // The Scrim is filling the Layout size, and is sitting below the Headers
            assertScrimAtRest()
        }

    @Test
    fun scrimScrolledToTop() =
        kosmos.runTest {
            // Given a scrim tall enough to scroll, and is at rest
            setTestContent(scrimContentHeight = 1000.dp)
            assertScrimAtRest()

            // When swiped up
            swipeScrimUp()

            // Then it collapses
            assertScrimScrolledToTop()
        }

    @Test
    fun scrimScrolledUpAndDown() =
        kosmos.runTest {
            // Given a scrim tall enough to scroll, and is at rest
            setTestContent(scrimContentHeight = 1000.dp)

            // When swiped up
            swipeScrimUp()
            // Then it collapses
            assertScrimScrolledToTop()

            // When swiped down
            swipeScrimDown()
            // Then it returns to rest
            assertScrimAtRest()
        }

    // region Setup Helpers
    private fun setTestContent(scrimContentHeight: Dp) {
        rule.setContent {
            TestContentScope {
                TestLayout(
                    modifier =
                        Modifier.testTag(TAG_LAYOUT)
                            .width(LayoutSize)
                            .heightIn(min = 0.dp, max = LayoutSize),
                    statusBarHeader = {
                        Box(Modifier.testTag(TAG_SB).fillMaxWidth().height(StatusBarHeight))
                    },
                    mediaAndQqsHeader = {
                        Box(Modifier.testTag(TAG_HEADER).fillMaxWidth().height(HeaderHeight))
                    },
                    scrollableScrim = { onHeightChanged ->
                        // This box must be scrollable, for the parent's NestedScrollConnection
                        Box(
                            Modifier.testTag(TAG_SCRIM)
                                .fillMaxWidth()
                                .height(scrimContentHeight)
                                .onSizeChanged { onHeightChanged(it.height) }
                                .verticalScroll(rememberScrollState())
                        )
                    },
                )
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun ContentScope.TestLayout(
        modifier: Modifier = Modifier,
        statusBarHeader: @Composable () -> Unit,
        mediaAndQqsHeader: @Composable () -> Unit,
        scrollableScrim: @Composable (onContentHeightChanged: (Int) -> Unit) -> Unit,
    ) {
        SingleShadeNestedScrollLayout(
            modifier = modifier,
            shadeSession = rememberShadeSession(),
            viewModel = kosmos.notificationsPlaceholderViewModel,
            scrollState = rememberScrollState(),
            scrimOverScrollEffect = rememberOffsetOverscrollEffect(),
            jankMonitor = kosmos.interactionJankMonitor,
            statusBarHeader = statusBarHeader,
            mediaAndQqsHeader = mediaAndQqsHeader,
            scrollableScrim = scrollableScrim,
            cutoutInsetsProvider = { null },
        )
    }

    @Composable
    private fun rememberShadeSession() =
        object : SaveableSession, Session by Session(SessionStorage()) {
            @Composable
            override fun <T : Any> rememberSaveableSession(
                vararg inputs: Any?,
                saver: Saver<T, out Any>,
                key: String?,
                init: () -> T,
            ): T = rememberSession(key, inputs = inputs, init = init)
        }

    // endregion

    // region Interaction Helpers
    private fun swipeScrimUp() {
        rule.onNodeWithTag(TAG_SCRIM).performTouchInput { swipeUp(startY = centerY, endY = top) }
        rule.waitForIdle()
    }

    private fun swipeScrimDown() {
        rule.onNodeWithTag(TAG_SCRIM).performTouchInput {
            swipeDown(startY = centerY, endY = bottom)
        }
        rule.waitForIdle()
    }

    // endregion

    // region Assertion Helpers
    private fun assertScrimAtRest() {
        // Scrim at its default position at rest. Placed below the Status Bar AND the Headers
        rule
            .onNodeWithTag(TAG_SCRIM)
            .assertTopPositionInRootIsEqualTo(StatusBarHeight + HeaderHeight)
            // And fills the layout size
            .assertWidthIsEqualTo(LayoutSize)
            .assertHeightIsEqualTo(LayoutSize)
    }

    private fun assertScrimScrolledToTop() {
        // Scrim Scrolled all the way to top, covers the Headers, placed directly below Status Bar
        rule
            .onNodeWithTag(TAG_SCRIM)
            .assertTopPositionInRootIsEqualTo(StatusBarHeight)
            // And fills the layout size
            .assertWidthIsEqualTo(LayoutSize)
            .assertHeightIsEqualTo(LayoutSize)
    }

    @Suppress("SameParameterValue") // it's a test let's be explicit about asserted values
    private fun assertNode(tag: String, width: Dp, height: Dp, top: Dp) {
        rule
            .onNodeWithTag(tag)
            .assertWidthIsEqualTo(width)
            .assertHeightIsEqualTo(height)
            .assertTopPositionInRootIsEqualTo(top)
    }

    // endregion

    companion object {
        private val LayoutSize = 300.dp
        private val StatusBarHeight = 30.dp
        private val HeaderHeight = 70.dp

        private const val TAG_LAYOUT = "layout"
        private const val TAG_SB = "sb"
        private const val TAG_HEADER = "qqs"
        private const val TAG_SCRIM = "scrim"
    }
}
