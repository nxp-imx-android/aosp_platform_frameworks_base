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

import android.platform.test.annotations.EnableFlags
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.systemui.SysuiTestCase
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.statusbar.notification.row.domain.bundleInteractor
import com.android.systemui.statusbar.notification.row.icon.appIconProvider
import com.android.systemui.statusbar.notification.row.icon.mockAppIconProvider
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import platform.test.motion.compose.runMonotonicClockTest

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class BundleInteractorTest : SysuiTestCase() {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    private val kosmos = testKosmos()

    private lateinit var underTest: BundleInteractor

    @Before
    fun setUp() {
        kosmos.appIconProvider = kosmos.mockAppIconProvider
        underTest = kosmos.bundleInteractor
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
