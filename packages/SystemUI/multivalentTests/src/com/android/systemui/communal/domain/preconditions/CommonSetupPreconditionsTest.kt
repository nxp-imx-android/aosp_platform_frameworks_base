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

package com.android.systemui.communal.domain.preconditions

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.power.data.repository.powerRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.statusbar.policy.deviceProvisionedController
import com.android.systemui.statusbar.policy.fakeDeviceProvisionedController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class CommonSetupPreconditionsTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            CommonSetupPreconditionsImpl(
                deviceProvisionedController = deviceProvisionedController,
                keyguardInteractor = keyguardInteractor,
                connectivityRepository = connectivityRepository,
                powerRepository = powerRepository,
                bgScope = testScope.backgroundScope,
            )
        }

    @Before
    fun setUp() {
        // Default valid state
        kosmos.fakeDeviceProvisionedController.deviceProvisioned = true
        kosmos.fakeDeviceProvisionedController.currentUser = 0
        kosmos.fakeDeviceProvisionedController.setUserSetup(0, true)
        kosmos.connectivityRepository.fake.setCarrierMergedConnected(validated = true)
        kosmos.keyguardInteractor.setDreaming(false)
        kosmos.fakePowerRepository.setInteractive(true)
    }

    @Test
    fun allMet_whenAllConditionsMet_isTrue() =
        kosmos.runTest {
            val allMet by collectLastValue(underTest.allMet)
            assertThat(allMet).isTrue()
        }

    @Test
    fun allMet_whenDeviceNotProvisioned_isFalse() =
        kosmos.runTest {
            fakeDeviceProvisionedController.deviceProvisioned = false
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenUserNotSetup_isFalse() =
        kosmos.runTest {
            fakeDeviceProvisionedController.setUserSetup(0, false)
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenNetworkNotValidated_isFalse() =
        kosmos.runTest {
            connectivityRepository.fake.setCarrierMergedConnected(validated = false)
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenDreaming_isFalse() =
        kosmos.runTest {
            keyguardInteractor.setDreaming(true)
            testScope.runCurrent()
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenNotInteractive_isFalse() =
        kosmos.runTest {
            fakePowerRepository.setInteractive(false)
            testScope.runCurrent()
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_networkDebounced() =
        kosmos.runTest {
            // Start with valid network
            connectivityRepository.fake.setCarrierMergedConnected(validated = true)
            val allMet by collectLastValue(underTest.allMet)

            // Let debounce settle
            testScope.advanceTimeBy(1000L)
            assertThat(allMet).isTrue()

            // Become invalid
            connectivityRepository.fake.setCarrierMergedConnected(validated = false)
            testScope.runCurrent()
            // Should be false immediately
            assertThat(allMet).isFalse()

            // Become valid again
            connectivityRepository.fake.setCarrierMergedConnected(validated = true)
            testScope.runCurrent()
            // Should still be false due to debounce
            assertThat(allMet).isFalse()

            // Advance past debounce
            testScope.advanceTimeBy(600L)
            assertThat(allMet).isTrue()
        }

    @Test
    fun listenerRemoved_whenFlowCancelled() =
        kosmos.runTest {
            val initialCallbackCount = kosmos.fakeDeviceProvisionedController.callbackCount

            // Launching the flow should cause a listener to be registered.
            val job = underTest.allMet.launchIn(testScope)
            testScope.runCurrent()

            // Verify a callback was added.
            assertThat(kosmos.fakeDeviceProvisionedController.callbackCount)
                .isEqualTo(initialCallbackCount + 1)

            // Cancelling the job should cause the listener to be removed.
            job.cancel()
            testScope.runCurrent()

            // Verify the callback was removed.
            assertThat(kosmos.fakeDeviceProvisionedController.callbackCount)
                .isEqualTo(initialCallbackCount)
        }
}
