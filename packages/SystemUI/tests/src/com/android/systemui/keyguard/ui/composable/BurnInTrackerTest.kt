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

package com.android.systemui.keyguard.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyguard.ui.viewmodel.BurnInMovementState
import com.android.systemui.keyguard.ui.viewmodel.aodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.keyguardClockViewModel
import com.android.systemui.testKosmos
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@Ignore("b/479248684 Re-enable after verifying test")
class BurnInTrackerTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    @get:Rule val composeTestRule = createComposeRule()
    private lateinit var underTest: BurnInTracker

    @Composable
    private fun BurnInTrackerUnderTest() {
        underTest =
            trackBurnInParameters(
                burnInViewModel = kosmos.aodBurnInViewModel,
                burnInMovementState = { BurnInMovementState(kosmos.aodBurnInViewModel) },
                clockViewModel = kosmos.keyguardClockViewModel,
            )
    }

    @Test
    fun smartspaceTopChange_triggersUpdateBurnInParams() {
        composeTestRule.setContent { BurnInTrackerUnderTest() }
        composeTestRule.waitForIdle()

        // Check it should be called once on initial composition
        verify(kosmos.aodBurnInViewModel, times(1)).updateBurnInParams(any())

        // WHEN: Update a dependency of 'params' (smartspaceTop)
        // This causes the 'params' object to be re-created in the remember block
        composeTestRule.runOnUiThread { underTest.onSmartspaceTopChanged(100f) }
        composeTestRule.waitForIdle()

        // THEN: The burn in params are updated
        verify(kosmos.aodBurnInViewModel, times(2)).updateBurnInParams(any())
    }

    @Test
    fun densityChange_triggersUpdateBurnInParams() {
        val densityState = mutableStateOf(Density(density = 1f))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides densityState.value) {}
            BurnInTrackerUnderTest()
        }
        composeTestRule.waitForIdle()

        // Check it should be called once on initial composition
        verify(kosmos.aodBurnInViewModel, times(1)).updateBurnInParams(any())

        // WHEN: Update a dependency of 'params' (density)
        // This causes the 'params' object to be re-created in the remember block
        composeTestRule.runOnUiThread { densityState.value = Density(density = 2f) }
        composeTestRule.waitForIdle()

        // THEN: The burn in params are updated
        verify(kosmos.aodBurnInViewModel, times(2)).updateBurnInParams(any())
    }
}
