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

package com.android.systemui.screencapture.data.repository

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureDeviceStateRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val underTest by lazy { kosmos.screenCaptureDeviceStateRepository }

    @Test
    fun isLargeScreenReturnsConfigValue() =
        kosmos.runTest {
            val isLargeScreen by collectLastValue(underTest.isLargeScreen)

            testableContext.orCreateTestableResources.addOverride(
                R.bool.config_enableLargeScreenScreencapture,
                true,
            )
            configurationController.onConfigurationChanged(testableContext.resources.configuration)
            assertThat(isLargeScreen).isTrue()

            testableContext.orCreateTestableResources.addOverride(
                R.bool.config_enableLargeScreenScreencapture,
                false,
            )
            configurationController.onConfigurationChanged(testableContext.resources.configuration)
            assertThat(isLargeScreen).isFalse()
        }
}
