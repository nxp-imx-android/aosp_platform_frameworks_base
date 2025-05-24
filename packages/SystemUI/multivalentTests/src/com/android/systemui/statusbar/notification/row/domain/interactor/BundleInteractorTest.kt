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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.statusbar.notification.row.domain.interactor

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.platform.test.annotations.EnableFlags
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.systemui.SysuiTestCase
import com.android.systemui.controls.dagger.ControlsComponentTest.Companion.eq
import com.android.systemui.controls.ui.ControlActionCoordinatorImplTest.Companion.any
import com.android.systemui.kosmos.testScope
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.statusbar.notification.row.data.model.AppData
import com.android.systemui.statusbar.notification.row.data.repository.BundleRepository
import com.android.systemui.statusbar.notification.row.data.repository.testBundleRepository
import com.android.systemui.statusbar.notification.row.domain.bundleInteractor
import com.android.systemui.statusbar.notification.row.icon.appIconProvider
import com.android.systemui.statusbar.notification.row.icon.mockAppIconProvider
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.motion.compose.runMonotonicClockTest

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class BundleInteractorTest : SysuiTestCase() {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private val testBundleRepository: BundleRepository = kosmos.testBundleRepository

    private lateinit var underTest: BundleInteractor

    @Before
    fun setUp() {
        kosmos.appIconProvider = kosmos.mockAppIconProvider
        underTest = kosmos.bundleInteractor
    }

    @Test
    fun previewIcons_callsFetchEnoughTimesForThreeNonNullResults() =
        testScope.runTest {
            // Arrange
            val appDataList =
                List(5) { index ->
                    mock<AppData> {
                        on { it.packageName }.thenReturn("com.example.app${index + 1}")
                    }
                }

            val drawable1: Drawable = ColorDrawable(android.graphics.Color.RED)
            val drawable2: Drawable = ColorDrawable(android.graphics.Color.GREEN)
            val drawable3: Drawable = ColorDrawable(android.graphics.Color.BLUE)
            val unusedDrawable: Drawable = ColorDrawable(android.graphics.Color.YELLOW)

            whenever(
                    kosmos.mockAppIconProvider.getOrFetchAppIcon(
                        any<String>(),
                        any<Context>(),
                        eq(false),
                        eq(false),
                    )
                )
                .thenReturn(drawable1)
                .thenReturn(null)
                .thenReturn(drawable2)
                .thenReturn(drawable3)
                .thenReturn(unusedDrawable)

            // Act
            testBundleRepository.appDataList.value = appDataList
            runCurrent()
            val result = underTest.previewIcons.first()

            // Assert
            verify(kosmos.mockAppIconProvider, times(4))
                .getOrFetchAppIcon(any<String>(), any<Context>(), eq(false), eq(false))

            assertThat(result).hasSize(3)
            assertThat(result).containsExactly(drawable1, drawable2, drawable3).inOrder()
        }

    @Test
    fun setExpansionState_sets_state_to_expanded() = runMonotonicClockTest {
        // Arrange
        underTest.composeScope = this
        underTest.state =
            MutableSceneTransitionLayoutState(
                BundleHeader.Scenes.Collapsed,
                MotionScheme.standard(),
            )
        assertThat(underTest.state?.currentScene).isEqualTo(BundleHeader.Scenes.Collapsed)

        // Act
        underTest.setExpansionState(true)

        // Assert
        assertThat(underTest.state?.currentScene).isEqualTo(BundleHeader.Scenes.Expanded)
    }

    @Test
    fun setExpansionState_sets_state_to_collapsed() = runMonotonicClockTest {
        // Arrange
        underTest.composeScope = this
        underTest.state =
            MutableSceneTransitionLayoutState(BundleHeader.Scenes.Expanded, MotionScheme.standard())
        assertThat(underTest.state?.currentScene).isEqualTo(BundleHeader.Scenes.Expanded)

        // Act
        underTest.setExpansionState(false)

        // Assert
        assertThat(underTest.state?.currentScene).isEqualTo(BundleHeader.Scenes.Collapsed)
    }
}
