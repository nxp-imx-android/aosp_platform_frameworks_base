/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.dreams.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.testKosmos
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamSetupViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest: DreamSetupViewModel by lazy { kosmos.dreamSetupViewModel }

    @Test
    fun onEvent_dismiss_doesNotCrash() {
        // The onEvent method currently only logs. This test ensures it can be called without error
        // and serves as a placeholder for future business logic verification.
        underTest.onEvent(DreamSetupEvent.Dismiss)
    }

    @Test
    fun onEvent_notNow_doesNotCrash() {
        // The onEvent method currently only logs. This test ensures it can be called without error
        // and serves as a placeholder for future business logic verification.
        underTest.onEvent(DreamSetupEvent.NotNow)
    }

    @Test
    fun onEvent_setUp_doesNotCrash() {
        // The onEvent method currently only logs. This test ensures it can be called without error
        // and serves as a placeholder for future business logic verification.
        underTest.onEvent(DreamSetupEvent.SetUp)
    }
}
