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

package com.android.systemui.actioncorner.data.repository

import android.graphics.Rect
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION
import android.view.WindowMetrics
import android.view.layoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.actioncorner.data.model.ActionCornerRegion
import com.android.systemui.actioncorner.data.model.ActionCornerState.ActiveActionCorner
import com.android.systemui.actioncorner.data.model.ActionCornerState.InactiveActionCorner
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.domain.data.repository.multiDisplayCursorPositionRepository
import com.android.systemui.display.data.repository.fakeDisplayWindowPropertiesRepository
import com.android.systemui.display.shared.model.DisplayWindowProperties
import com.android.systemui.inputdevice.data.repository.FakePointerDeviceRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ActionCornerRepositoryTest : SysuiTestCase() {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.fakePointerRepository by Fixture { FakePointerDeviceRepository() }
    private val Kosmos.underTest by Fixture {
        ActionCornerRepositoryImpl(
            cursorPositionRepository,
            kosmos.fakeDisplayWindowPropertiesRepository,
            kosmos.fakePointerRepository,
            kosmos.backgroundScope,
        )
    }

    private val cursorPositionRepository = kosmos.multiDisplayCursorPositionRepository
    private val displayRepository = kosmos.fakeDisplayWindowPropertiesRepository
    private val windowManager: WindowManager = mock<WindowManager>()

    @Before
    fun setup() {
        whenever(windowManager.currentWindowMetrics).thenReturn(metrics)
        displayRepository.insert(createDisplayWindowProperties())
        kosmos.fakePointerRepository.setIsAnyPointerConnected(true)
    }

    @Test
    fun topLeftCursor_topLeftActionCornerEmitted() =
        kosmos.runTest {
            val model by collectLastValue(underTest.actionCornerState)
            cursorPositionRepository.addCursorPosition(display.topLeftCursorPos)
            assertThat(model)
                .isEqualTo(
                    ActiveActionCorner(
                        ActionCornerRegion.TOP_LEFT,
                        display.topLeftCursorPos.displayId,
                    )
                )
        }

    @Test
    fun outOfBoundTopLeftCursor_noActionCornerEmitted() =
        kosmos.runTest {
            val model by collectLastValue(underTest.actionCornerState)
            val actionCornerPos = display.topLeftCursorPos
            // Update x and y to make it just out of bound of action corner
            cursorPositionRepository.addCursorPosition(
                CursorPosition(
                    x = actionCornerPos.x + 1,
                    y = actionCornerPos.y + 1,
                    actionCornerPos.displayId,
                )
            )
            assertThat(model).isEqualTo(InactiveActionCorner)
        }

    @Test
    fun topRightCursor_topRightActionCornerEmitted() =
        kosmos.runTest {
            val model by collectLastValue(underTest.actionCornerState)
            val actionCornerPos = display.topRightCursorPos
            cursorPositionRepository.addCursorPosition(actionCornerPos)
            assertThat(model)
                .isEqualTo(
                    ActiveActionCorner(ActionCornerRegion.TOP_RIGHT, actionCornerPos.displayId)
                )
        }

    @Test
    fun outOfBoundTopRightCursor_noActionCornerEmitted() =
        kosmos.runTest {
            val model by collectLastValue(underTest.actionCornerState)
            val actionCornerPos = display.topRightCursorPos
            cursorPositionRepository.addCursorPosition(
                CursorPosition(
                    x = actionCornerPos.x - 1,
                    y = actionCornerPos.y + 1,
                    actionCornerPos.displayId,
                )
            )
            assertThat(model).isEqualTo(InactiveActionCorner)
        }

    @Test
    fun bottomLeftCursor_bottomLeftActionCornerEmitted() =
        kosmos.runTest {
            val model by collectLastValue(underTest.actionCornerState)
            val actionCornerPos = display.bottomLeftCursorPos
            cursorPositionRepository.addCursorPosition(actionCornerPos)
            assertThat(model)
                .isEqualTo(
                    ActiveActionCorner(ActionCornerRegion.BOTTOM_LEFT, actionCornerPos.displayId)
                )
        }

    @Test
    fun outOfBoundBottomLeftCursor_noActionCornerEmitted() =
        kosmos.runTest {
            val model by collectLastValue(underTest.actionCornerState)
            val actionCornerPos = display.bottomLeftCursorPos
            cursorPositionRepository.addCursorPosition(
                CursorPosition(
                    x = actionCornerPos.x + 1,
                    y = actionCornerPos.y - 1,
                    actionCornerPos.displayId,
                )
            )
            assertThat(model).isEqualTo(InactiveActionCorner)
        }

    @Test
    fun bottomRightCursor_bottomRightActionCornerEmitted() =
        kosmos.runTest {
            val model by collectLastValue(underTest.actionCornerState)
            val actionCornerPos = display.bottomRightCursorPos
            cursorPositionRepository.addCursorPosition(actionCornerPos)
            assertThat(model)
                .isEqualTo(
                    ActiveActionCorner(ActionCornerRegion.BOTTOM_RIGHT, actionCornerPos.displayId)
                )
        }

    @Test
    fun outOfBoundBottomRightCursor_noActionCornerEmitted() =
        kosmos.runTest {
            val model by collectLastValue(underTest.actionCornerState)
            val actionCornerPos = display.bottomRightCursorPos
            cursorPositionRepository.addCursorPosition(
                CursorPosition(
                    x = actionCornerPos.x - 1,
                    y = actionCornerPos.y - 1,
                    actionCornerPos.displayId,
                )
            )
            assertThat(model).isEqualTo(InactiveActionCorner)
        }

    @Test
    fun actionCornerCursor_moveOutOfBound_reEnterActionCorner_secondActiveActionCornerEmitted() =
        kosmos.runTest {
            // Filter out InactiveActionCorner for test readability
            val models by
                kosmos.collectValues(
                    underTest.actionCornerState.filter { it != InactiveActionCorner }
                )
            val actionCornerPos = display.bottomRightCursorPos
            cursorPositionRepository.addCursorPosition(actionCornerPos)
            cursorPositionRepository.addCursorPosition(
                CursorPosition(x = 1000f, y = 1000f, actionCornerPos.displayId)
            )
            cursorPositionRepository.addCursorPosition(actionCornerPos)

            val bottomRightModel =
                ActiveActionCorner(ActionCornerRegion.BOTTOM_RIGHT, actionCornerPos.displayId)
            assertThat(models).containsExactly(bottomRightModel, bottomRightModel).inOrder()
        }

    @Test
    fun actionCornerCursor_moveInsideSameCorner_OneActionCornerEmitted() =
        kosmos.runTest {
            val models by kosmos.collectValues(underTest.actionCornerState.drop(1))
            val actionCornerPos = display.bottomRightCursorPos
            cursorPositionRepository.addCursorPosition(actionCornerPos)
            // Move within the same corner
            cursorPositionRepository.addCursorPosition(
                CursorPosition(
                    actionCornerPos.x + 1,
                    actionCornerPos.y + 1,
                    actionCornerPos.displayId,
                )
            )
            cursorPositionRepository.addCursorPosition(
                CursorPosition(
                    actionCornerPos.x + 2,
                    actionCornerPos.y + 2,
                    actionCornerPos.displayId,
                )
            )

            assertThat(models.size).isEqualTo(1)
        }

    @Test
    fun activeActionCorner_pointerDeviceDisconnected_inactiveActionCorner() =
        kosmos.runTest {
            val actionCornerPos = display.bottomRightCursorPos
            cursorPositionRepository.addCursorPosition(actionCornerPos)

            fakePointerRepository.setIsAnyPointerConnected(false)

            val model by collectLastValue(underTest.actionCornerState)
            assertThat(model).isEqualTo(InactiveActionCorner)
        }

    private fun createDisplayWindowProperties() =
        DisplayWindowProperties(
            DEFAULT_DISPLAY,
            TYPE_BASE_APPLICATION,
            context,
            windowManager,
            kosmos.layoutInflater,
        )

    companion object {
        private val metrics = WindowMetrics(Rect(0, 0, 2560, 1600), mock<WindowInsets>(), 2f)
        private const val ACTION_CORNER_DP = 8f
        private val cornerSize = ACTION_CORNER_DP * metrics.density

        private val display =
            Display(
                // Place the cursor just inside the bound for testing, by putting it at the opposite
                // corner
                // e.g. below shows the cursor position it tests for bottom left corner
                //                       +-----+ <-- cursor position is placed here for testing
                //                       |     |
                // bottom left corner -> +-----+
                topLeftCursorPos = CursorPosition(cornerSize, cornerSize, DEFAULT_DISPLAY),
                topRightCursorPos =
                    CursorPosition(
                        metrics.bounds.width() - cornerSize,
                        cornerSize,
                        DEFAULT_DISPLAY,
                    ),
                bottomLeftCursorPos =
                    CursorPosition(
                        cornerSize,
                        metrics.bounds.height() - cornerSize,
                        DEFAULT_DISPLAY,
                    ),
                bottomRightCursorPos =
                    CursorPosition(
                        metrics.bounds.width() - cornerSize,
                        metrics.bounds.height() - cornerSize,
                        DEFAULT_DISPLAY,
                    ),
            )
    }

    private data class Display(
        val topLeftCursorPos: CursorPosition,
        val topRightCursorPos: CursorPosition,
        val bottomLeftCursorPos: CursorPosition,
        val bottomRightCursorPos: CursorPosition,
    )
}
