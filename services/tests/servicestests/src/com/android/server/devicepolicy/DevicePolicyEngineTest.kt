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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class DevicePolicyEngineTest {
    private val context =
        BroadcastInterceptingContext(InstrumentationRegistry.getInstrumentation().targetContext)

    private val deviceAdminServiceController = mock<DeviceAdminServiceController>()
    private val userManager = mock<UserManager>()

    private val lock = Any()
    private lateinit var devicePolicyEngine: DevicePolicyEngine

    @Before
    fun setUp() {
        LocalServices.removeServiceForTest(UserManager::class.java)
        LocalServices.addService(UserManager::class.java, userManager)
        devicePolicyEngine = DevicePolicyEngine(context, deviceAdminServiceController, lock)

        if (LocalManagerRegistry.getManager(RoleManagerLocal::class.java) == null) {
            LocalManagerRegistry.addManager(RoleManagerLocal::class.java, mock<RoleManagerLocal>())
        }
    }

    @Test
    fun setAndGetLocalPolicy_returnsCorrectPolicy() {
        val result =
            devicePolicyEngine.setLocalPolicy(
                PASSWORD_COMPLEXITY_POLICY,
                DEVICE_OWNER_ADMIN,
                IntegerPolicyValue(HIGH_PASSWORD_COMPLEXITY),
                DEVICE_OWNER_USER_ID,
            )
        assertThat(result.get()).isEqualTo(POLICY_SET)

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, DEVICE_OWNER_USER_ID)

        assertThat(resolvedPolicy).isEqualTo(HIGH_PASSWORD_COMPLEXITY)
    }

    @Test
    fun removeLocalPolicy_removesPolicyAndResolvesToNull() {
        val result =
            devicePolicyEngine.setLocalPolicy(
                PASSWORD_COMPLEXITY_POLICY,
                DEVICE_OWNER_ADMIN,
                IntegerPolicyValue(HIGH_PASSWORD_COMPLEXITY),
                DEVICE_OWNER_USER_ID,
            )
        assertThat(result.get()).isEqualTo(POLICY_SET)
        val removeResult =
            devicePolicyEngine.removeLocalPolicy(
                PASSWORD_COMPLEXITY_POLICY,
                DEVICE_OWNER_ADMIN,
                DEVICE_OWNER_USER_ID,
            )
        assertThat(removeResult.get()).isEqualTo(POLICY_CLEARED)

        val resolvedPolicy =
            devicePolicyEngine.getResolvedPolicy(PASSWORD_COMPLEXITY_POLICY, DEVICE_OWNER_USER_ID)

        assertThat(resolvedPolicy).isNull()
    }

    private companion object {
        const val POLICY_SET = PolicyUpdateResult.RESULT_POLICY_SET
        const val POLICY_CLEARED = PolicyUpdateResult.RESULT_POLICY_CLEARED

        const val HIGH_PASSWORD_COMPLEXITY = DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH

        const val DEVICE_OWNER_USER_ID = UserHandle.USER_SYSTEM

        val PASSWORD_COMPLEXITY_POLICY = PolicyDefinition.PASSWORD_COMPLEXITY

        val DEVICE_OWNER_ADMIN =
            EnforcingAdmin.createEnterpriseEnforcingAdmin(
                ComponentName("packagename", "classname"),
                DEVICE_OWNER_USER_ID,
            )
    }
}
