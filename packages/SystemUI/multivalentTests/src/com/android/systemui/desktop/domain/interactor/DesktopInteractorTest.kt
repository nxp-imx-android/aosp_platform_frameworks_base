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

package com.android.systemui.desktop.domain.interactor

import android.content.res.mainResources
import android.content.testableContext
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var underTest: DesktopInteractor

    @Before
    fun setUp() {
        underTest = kosmos.desktopInteractor
    }

    @Test
    fun isDesktopFeatureSetEnabled_false() =
        kosmos.runTest {
            val isDesktopFeatureSetEnabled by collectLastValue(underTest.isDesktopFeatureSetEnabled)

            overrideConfig(R.bool.config_enableDesktopFeatureSet, false)

            assertThat(isDesktopFeatureSetEnabled).isFalse()
        }

    @Test
    fun isDesktopFeatureSetEnabled_true() =
        kosmos.runTest {
            val isDesktopFeatureSetEnabled by collectLastValue(underTest.isDesktopFeatureSetEnabled)

            overrideConfig(R.bool.config_enableDesktopFeatureSet, true)

            assertThat(isDesktopFeatureSetEnabled).isTrue()
        }

    @EnableFlags(Flags.FLAG_STATUS_BAR_FOR_DESKTOP)
    @EnableSceneContainer
    @Test
    fun desktopStatusBarEnabled_configEnabled_isNotificationShadeOnTopEndReturnsTrue() =
        kosmos.runTest {
            overrideConfig(R.bool.config_notificationShadeOnTopEnd, true)

            assertThat(createTestInstance().isNotificationShadeOnTopEnd).isTrue()
        }

    @EnableFlags(Flags.FLAG_STATUS_BAR_FOR_DESKTOP)
    @EnableSceneContainer
    @Test
    fun desktopStatusBarEnabled_configDisabled_isNotificationShadeOnTopEndReturnsFalse() =
        kosmos.runTest {
            overrideConfig(R.bool.config_notificationShadeOnTopEnd, false)

            assertThat(createTestInstance().isNotificationShadeOnTopEnd).isFalse()
        }

    @DisableFlags(Flags.FLAG_STATUS_BAR_FOR_DESKTOP)
    @Test
    fun desktopStatusBarDisabled_configEnabled_isNotificationShadeOnTopEndReturnsFalse() =
        kosmos.runTest {
            overrideConfig(R.bool.config_notificationShadeOnTopEnd, true)

            assertThat(createTestInstance().isNotificationShadeOnTopEnd).isFalse()
        }

    @DisableFlags(Flags.FLAG_STATUS_BAR_FOR_DESKTOP)
    @Test
    fun desktopStatusBarDisabled_configDisabled_isNotificationShadeOnTopEndReturnsFalse() =
        kosmos.runTest {
            overrideConfig(R.bool.config_notificationShadeOnTopEnd, false)

            assertThat(createTestInstance().isNotificationShadeOnTopEnd).isFalse()
        }

    private fun Kosmos.overrideConfig(configId: Int, value: Boolean) {
        testableContext.orCreateTestableResources.addOverride(configId, value)
    }

    private fun Kosmos.createTestInstance(): DesktopInteractor {
        return DesktopInteractor(
            resources = mainResources,
            scope = backgroundScope,
            configurationController = configurationController,
        )
    }
}
