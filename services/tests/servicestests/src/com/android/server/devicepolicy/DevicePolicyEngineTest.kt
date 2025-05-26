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

package com.android.server.devicepolicy

import android.app.admin.DevicePolicyManager
import android.app.admin.IntegerPolicyValue
import android.app.admin.PolicyUpdateResult
import android.app.admin.PolicyValue
import android.content.ComponentName
import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.util.test.BroadcastInterceptingContext
import com.android.role.RoleManagerLocal
import com.android.server.LocalManagerRegistry
import com.android.server.LocalServices
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DevicePolicyEngineTest {
    private val context =
        BroadcastInterceptingContext(InstrumentationRegistry.getInstrumentation().targetContext)

    private val deviceAdminServiceController = mock<DeviceAdminServiceController>()
    private val userManager = mock<UserManager>()
    private val policyPathProvider = mock<PolicyPathProvider>()

    private val lock = Any()
    private lateinit var devicePolicyEngine: DevicePolicyEngine

    @Before
    fun setUp() {
        resetPolicyFolder()
        LocalServices.removeServiceForTest(UserManager::class.java)
        LocalServices.addService(UserManager::class.java, userManager)
        resetDevicePolicyEngine()
    }

    @After
    fun tearDown() {
        LocalServices.removeServiceForTest(UserManager::class.java)
    }

    private fun resetPolicyFolder() {
        whenever(policyPathProvider.getDataSystemDirectory()).thenReturn(tmpDir.newFolder())
    }

    private fun resetDevicePolicyEngine() {
        devicePolicyEngine =
            DevicePolicyEngine(context, deviceAdminServiceController, lock, policyPathProvider)
        devicePolicyEngine.load()
    }

    // Helper functions for test setup. Only DO needed for now, but we will want to exptend this to
    // other admin types in the future.

    private fun <T> ensurePolicyIsSetLocally(
        policyDefinition: PolicyDefinition<T>,
        value: PolicyValue<T>,
    ) {
        val result =
            devicePolicyEngine.setLocalPolicy(
                policyDefinition,
                DEVICE_OWNER_ADMIN,
                value,
                DEVICE_OWNER_USER_ID,
            )
        assertThat(result.get()).isEqualTo(POLICY_SET)
    }

    private fun <T> ensurePolicyIsRemovedLocally(policyDefinition: PolicyDefinition<T>) {
        val result =
            devicePolicyEngine.removeLocalPolicy(
                policyDefinition,
                DEVICE_OWNER_ADMIN,
                DEVICE_OWNER_USER_ID,
            )
        assertThat(result.get()).isEqualTo(POLICY_CLEARED)
    }

    @Test
    fun setAndGetLocalPolicy_returnsCorrectPolicy() {
        ensurePolicyIsSetLocally(PASSWORD_COMPLEXITY_POLICY, HIGH_PASSWORD_COMPLEXITY)

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, DEVICE_OWNER_USER_ID)

        assertThat(resolvedPolicy).isEqualTo(HIGH_PASSWORD_COMPLEXITY.value)
    }

    @Test
    fun removeLocalPolicy_removesPolicyAndResolvesToNull() {
        ensurePolicyIsSetLocally(PASSWORD_COMPLEXITY_POLICY, HIGH_PASSWORD_COMPLEXITY)
        ensurePolicyIsRemovedLocally(PASSWORD_COMPLEXITY_POLICY)

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, DEVICE_OWNER_USER_ID)

        assertThat(resolvedPolicy).isNull()
    }

    @Test
    fun setLocalPolicy_restartDevicePolicyEngine_policyIsStillSet() {
        ensurePolicyIsSetLocally(PASSWORD_COMPLEXITY_POLICY, HIGH_PASSWORD_COMPLEXITY)
        resetDevicePolicyEngine()

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, DEVICE_OWNER_USER_ID)

        assertThat(resolvedPolicy).isEqualTo(HIGH_PASSWORD_COMPLEXITY.value)
    }

    @Test
    fun setLocalPolicy_restartDevicePolicyEngine_andRemovePolicyData_policyIsRemoved() {
        ensurePolicyIsSetLocally(PASSWORD_COMPLEXITY_POLICY, HIGH_PASSWORD_COMPLEXITY)
        resetPolicyFolder()
        resetDevicePolicyEngine()

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, DEVICE_OWNER_USER_ID)

        assertThat(resolvedPolicy).isNull()
    }

    companion object {
        private const val POLICY_SET = PolicyUpdateResult.RESULT_POLICY_SET
        private const val POLICY_CLEARED = PolicyUpdateResult.RESULT_POLICY_CLEARED

        private val PASSWORD_COMPLEXITY_POLICY = PolicyDefinition.PASSWORD_COMPLEXITY
        private val HIGH_PASSWORD_COMPLEXITY =
            IntegerPolicyValue(DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH)

        private const val DEVICE_OWNER_USER_ID = UserHandle.USER_SYSTEM
        private val DEVICE_OWNER_ADMIN =
            EnforcingAdmin.createEnterpriseEnforcingAdmin(
                ComponentName("packagename", "classname"),
                DEVICE_OWNER_USER_ID,
            )

        @ClassRule @JvmField val tmpDir = TemporaryFolder()

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            // TODO(b/420373209): Remove this once we have a better way to mock RoleManagerLocal.
            if (LocalManagerRegistry.getManager(RoleManagerLocal::class.java) == null) {
                LocalManagerRegistry.addManager(
                    RoleManagerLocal::class.java,
                    mock<RoleManagerLocal>(),
                )
            }
        }
    }
}
