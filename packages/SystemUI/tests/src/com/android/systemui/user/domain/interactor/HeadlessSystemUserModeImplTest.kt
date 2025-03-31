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
 *
 */
package com.android.systemui.user.domain.interactor

import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoSession

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeadlessSystemUserModeImplTest : SysuiTestCase() {

    private lateinit var mockitoSession: MockitoSession

    private val underTest = HeadlessSystemUserModeImpl()

    @Before
    fun startSession() {
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(UserManager::class.java)
                .startMocking()
    }

    @After
    fun closeSession() {
        mockitoSession.finishMocking()
    }

    @Test
    fun isHeadlessSystemUserMode_whenDeviceIsNotHsum_false() {
        mockIsHsum(false)

        assertWithMessage("HeadlessSystemUserMode.isHeadlessSystemUserMode()")
            .that(underTest.isHeadlessSystemUserMode())
            .isFalse()
    }

    @Test
    fun isHeadlessSystemUserMode_whenDeviceIsHsum_true() {
        mockIsHsum(true)

        assertWithMessage("HeadlessSystemUserMode.isHeadlessSystemUserMode()")
            .that(underTest.isHeadlessSystemUserMode())
            .isTrue()
    }

    private fun mockIsHsum(hsum: Boolean) {
        ExtendedMockito.doReturn(hsum).`when`(UserManager::isHeadlessSystemUserMode)
    }
}
