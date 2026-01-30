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

package com.android.systemui.communal.domain.definition

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.commonSetupPreconditions
import com.android.systemui.communal.contextualSetupRepository
import com.android.systemui.communal.data.repository.SetupState
import com.android.systemui.communal.fake
import com.android.systemui.communal.uprightChargingTriggerRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.android.systemui.util.kotlin.SimpleFlowDumper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class UprightChargingSetupDefinitionTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val commonPreconditions = kosmos.commonSetupPreconditions.fake
    private val triggerRepository = kosmos.uprightChargingTriggerRepository.fake
    private val contextualSetupRepo = kosmos.contextualSetupRepository.fake
    private val underTest =
        UprightChargingSetupDefinition(
            commonConditions = commonPreconditions,
            triggerRepo = triggerRepository,
            contextualSetupRepo = contextualSetupRepo,
            flowDumper = SimpleFlowDumper(),
            target = SetupTarget.Activity(ComponentName("package", "class")),
        )

    @Test
    fun isReady_preconditionsNotMet_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            triggerRepository.setTriggered(true)

            // Start with preconditions being met.
            commonPreconditions.setAllMet(true)
            assertThat(isReady).isTrue()

            // When preconditions are no longer met, isReady is false.
            commonPreconditions.setAllMet(false)
            assertThat(isReady).isFalse()
        }

    @Test
    fun isReady_preconditionsMet_triggerNotFired_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)

            // Start with the trigger fired.
            triggerRepository.setTriggered(true)
            assertThat(isReady).isTrue()

            // When the trigger is no longer fired, isReady is false.
            triggerRepository.setTriggered(false)
            assertThat(isReady).isFalse()
        }

    @Test
    fun isReady_preconditionsMet_triggerFired_isTrue() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)
            triggerRepository.setTriggered(true)

            assertThat(isReady).isTrue()
        }

    @Test
    fun isReady_setupCompleted_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            // Start in a ready state.
            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)
            triggerRepository.setTriggered(true)
            assertThat(isReady).isTrue()

            // When setup is completed, isReady is false.
            contextualSetupRepo.setSetupState(underTest.id, SetupState.Completed)
            assertThat(isReady).isFalse()
        }

    @Test
    fun isReady_setupDismissed_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            // Start in a ready state.
            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)
            triggerRepository.setTriggered(true)
            assertThat(isReady).isTrue()

            // When setup is dismissed, isReady is false.
            contextualSetupRepo.setSetupState(underTest.id, SetupState.Dismissed)
            assertThat(isReady).isFalse()
        }

    @Test
    fun isReady_setupSnoozed_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            // Start in a ready state.
            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)
            triggerRepository.setTriggered(true)
            assertThat(isReady).isTrue()

            // When setup is snoozed, isReady is false.
            contextualSetupRepo.setSetupState(
                underTest.id,
                SetupState.Snoozed(expirationTimeMillis = 100L),
            )
            assertThat(isReady).isFalse()
        }
}
