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

package com.android.systemui.screencapture.record.largescreen.data.repository

import android.content.pm.UserInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeScreenCaptureParametersRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private lateinit var underTest: LargeScreenCaptureParametersRepository

    @Before
    fun setUp() {
        kosmos.fakeUserRepository.setUserInfos(listOf(PRIMARY_USER, ANOTHER_USER))
        underTest = kosmos.largeScreenCaptureParametersRepository
    }

    @Test
    fun customSaveLocationUriString_initialValueIsEmpty() =
        kosmos.runTest {
            val initialValue by collectLastValue(underTest.customSaveLocationUriString)
            assertThat(initialValue).isEmpty()
        }

    @Test
    fun updateCustomSaveLocation_flowEmitsUpdatedValue() =
        kosmos.runTest {
            val latestValue by collectLastValue(underTest.customSaveLocationUriString)
            val testUri = "content://media/external/downloads/123"

            assertThat(latestValue).isEmpty()

            underTest.updateCustomSaveLocationUriString(testUri)

            assertThat(latestValue).isEqualTo(testUri)
        }

    @Test
    fun updateCustomSaveLocation_clearsValue_whenEmptyStringIsPassed() =
        kosmos.runTest {
            val latestValue by collectLastValue(underTest.customSaveLocationUriString)
            val initialUri = "content://media/external/downloads/456"

            underTest.updateCustomSaveLocationUriString(initialUri)
            assertThat(latestValue).isEqualTo(initialUri)

            underTest.updateCustomSaveLocationUriString("")

            assertThat(latestValue).isEmpty()
        }

    @Test
    fun customSaveLocationUriString_isScopedPerUser() =
        kosmos.runTest {
            val latestValue by collectLastValue(underTest.customSaveLocationUriString)

            fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            assertThat(latestValue).isEmpty()
            underTest.updateCustomSaveLocationUriString(PRIMARY_USER_URI)
            assertThat(latestValue).isEqualTo(PRIMARY_USER_URI)

            fakeUserRepository.setSelectedUserInfo(ANOTHER_USER)
            assertThat(latestValue).isEmpty()
            underTest.updateCustomSaveLocationUriString(ANOTHER_USER_URI)
            assertThat(latestValue).isEqualTo(ANOTHER_USER_URI)

            fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            assertThat(latestValue).isEqualTo(PRIMARY_USER_URI)
        }

    companion object {
        private val PRIMARY_USER =
            UserInfo(/* id= */ 0, /* name= */ "primary user", /* flags= */ UserInfo.FLAG_PRIMARY)

        private val ANOTHER_USER = UserInfo(/* id= */ 1, /* name= */ "another user", /* flags= */ 0)

        private const val PRIMARY_USER_URI = "content://primary_user_uri"
        private const val ANOTHER_USER_URI = "content://another_user_uri"
    }
}
