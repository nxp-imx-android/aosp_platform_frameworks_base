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

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest = kosmos.desktopInteractor

    @Test
    fun isDesktopFeatureSetEnabled_false() =
        kosmos.runTest {
            val isDesktopFeatureSetEnabled by collectLastValue(underTest.isDesktopFeatureSetEnabled)

            testableContext.orCreateTestableResources.addOverride(
                R.bool.config_enableDesktopFeatureSet,
                false,
            )

            assertThat(isDesktopFeatureSetEnabled).isFalse()
        }

    @Test
    fun isDesktopFeatureSetEnabled_true() =
        kosmos.runTest {
            val isDesktopFeatureSetEnabled by collectLastValue(underTest.isDesktopFeatureSetEnabled)

            testableContext.orCreateTestableResources.addOverride(
                R.bool.config_enableDesktopFeatureSet,
                true,
            )

            assertThat(isDesktopFeatureSetEnabled).isTrue()
        }
}
