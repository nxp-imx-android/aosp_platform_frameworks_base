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

import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.activityStarter
import com.android.systemui.testKosmos
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamSetupViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { dreamSetupViewModel }

    @Test
    fun onEvent_dismiss_doesNotCrash() =
        kosmos.runTest {
            // b/475511585 - Implement the business logic for the "Dismiss" event & update this test
            // accordingly.
            underTest.onEvent(DreamSetupEvent.Dismiss)

            verify(activityStarter, never())
                .postStartActivityDismissingKeyguard(any(), any(), any())
        }

    @Test
    fun onEvent_notNow_doesNotCrash() =
        kosmos.runTest {
            // b/475511585 - Implement the business logic for the "Not Now" event & update this test
            // accordingly.
            underTest.onEvent(DreamSetupEvent.NotNow)

            verify(activityStarter, never())
                .postStartActivityDismissingKeyguard(any(), any(), any())
        }

    @Test
    fun onEvent_setUp_startsDreamSettings() =
        kosmos.runTest {
            underTest.onEvent(DreamSetupEvent.SetUp)

            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat { intent ->
                        intent?.action == Settings.ACTION_DREAM_SETTINGS &&
                            intent.flags ==
                                (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                    eq(0),
                    isNull(),
                )
        }
}
