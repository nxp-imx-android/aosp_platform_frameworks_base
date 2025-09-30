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

package com.android.systemui.screencapture.record.largescreen.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeScreenCaptureParametersInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private lateinit var underTest: LargeScreenCaptureParametersInteractor

    @Before
    fun setUp() {
        underTest = kosmos.largeScreenCaptureParametersInteractor
    }

    @Test
    fun customSaveLocationUriString_initialValue_isEmpty() =
        kosmos.runTest {
            val initialValue by collectLastValue(underTest.customSaveLocationUriString)
            assertThat(initialValue).isEmpty()
        }

    @Test
    fun setCustomSaveLocation_updatesValue() =
        kosmos.runTest {
            val latestValue by collectLastValue(underTest.customSaveLocationUriString)
            val testUri = "content://media/external/downloads/123"

            assertThat(latestValue).isEmpty()

            underTest.setCustomSaveLocation(testUri)
            runCurrent()

            assertThat(latestValue).isEqualTo(testUri)
        }

    @Test
    fun setCustomSaveLocation_clearsValue_whenEmptyString() =
        kosmos.runTest {
            val latestValue by collectLastValue(underTest.customSaveLocationUriString)
            val initialUri = "content://media/external/downloads/456"

            underTest.setCustomSaveLocation(initialUri)
            runCurrent()
            assertThat(latestValue).isEqualTo(initialUri)

            underTest.setCustomSaveLocation("")
            runCurrent()

            assertThat(latestValue).isEmpty()
        }
}
