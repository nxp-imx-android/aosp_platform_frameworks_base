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

import android.app.admin.BooleanPolicyValue
import android.app.admin.IntegerPolicyValue
import android.app.admin.PackageSetPolicyValue
import android.app.admin.PolicyValue
import android.content.ComponentName
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResolutionMechanismTest {

    @Test
    fun resolve_flagUnion() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Int>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, INT_POLICY_A as PolicyValue<Int>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, INT_POLICY_B as PolicyValue<Int>)

        val resolvedPolicy = FlagUnion().resolve(adminPolicies)

        assert(resolvedPolicy != null)
        assert(resolvedPolicy?.resolvedPolicyValue == INT_POLICY_AB)
        assertThat(resolvedPolicy?.contributingAdmins)
            .containsExactly(SYSTEM_ADMIN, DEVICE_OWNER_ADMIN)
    }

    @Test
    fun resolve_mostRecent() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Integer>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, INT_POLICY_A as PolicyValue<Integer>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, INT_POLICY_B as PolicyValue<Integer>)

        val resolvedPolicy = MostRecent<Integer>().resolve(adminPolicies)

        assert(resolvedPolicy != null)
        assert(resolvedPolicy?.resolvedPolicyValue == INT_POLICY_B)
        assertThat(resolvedPolicy?.contributingAdmins).containsExactly(DEVICE_OWNER_ADMIN)
    }

    @Test
    fun resolve_mostRestrictive() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Boolean>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, BooleanPolicyValue(false) as PolicyValue<Boolean>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, BooleanPolicyValue(true) as PolicyValue<Boolean>)

        val resolvedPolicy =
            MostRestrictive<Boolean>(listOf(BooleanPolicyValue(false), BooleanPolicyValue(true)))
                .resolve(adminPolicies)

        assert(resolvedPolicy != null)
        resolvedPolicy?.resolvedPolicyValue?.value?.let { assertFalse(it) }
        assertThat(resolvedPolicy?.contributingAdmins).containsExactly(SYSTEM_ADMIN)
    }

    @Test
    fun resolve_mostRestrictive_multipleContributingAdmins() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Boolean>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, BooleanPolicyValue(false) as PolicyValue<Boolean>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, BooleanPolicyValue(true) as PolicyValue<Boolean>)
        adminPolicies.put(DEVICE_ADMIN, BooleanPolicyValue(false) as PolicyValue<Boolean>)

        val resolvedPolicy =
            MostRestrictive(listOf(BooleanPolicyValue(false), BooleanPolicyValue(true)))
                .resolve(adminPolicies)

        assert(resolvedPolicy != null)
        resolvedPolicy?.resolvedPolicyValue?.value?.let { assertFalse(it) }
        assertThat(resolvedPolicy?.contributingAdmins)
            .containsExactly(SYSTEM_ADMIN, DEVICE_ADMIN)
    }

    @Test
    fun resolve_stringSetIntersection() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Set<String>>> = LinkedHashMap()
        adminPolicies.put(
            SYSTEM_ADMIN,
            PackageSetPolicyValue(setOf("package1", "package2")) as PolicyValue<Set<String>>,
        )
        adminPolicies.put(
            DEVICE_OWNER_ADMIN,
            PackageSetPolicyValue(setOf("package1")) as PolicyValue<Set<String>>,
        )

        val resolvedPolicy = StringSetIntersection().resolve(adminPolicies)

        assert(resolvedPolicy != null)
        assertThat(resolvedPolicy?.resolvedPolicyValue?.value).containsExactly("package1")
        assertThat(resolvedPolicy?.contributingAdmins)
            .containsExactly(SYSTEM_ADMIN, DEVICE_OWNER_ADMIN)
    }

    @Test
    fun resolve_packageSetUnion() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Set<String>>> = LinkedHashMap()
        adminPolicies.put(
            SYSTEM_ADMIN,
            PackageSetPolicyValue(setOf("package1", "package2")) as PolicyValue<Set<String>>,
        )
        adminPolicies.put(
            DEVICE_OWNER_ADMIN,
            PackageSetPolicyValue(setOf("package1", "package3")) as PolicyValue<Set<String>>,
        )

        val resolvedPolicy = PackageSetUnion().resolve(adminPolicies)

        assert(resolvedPolicy != null)
        assertThat(resolvedPolicy?.resolvedPolicyValue?.value)
            .containsExactly("package1", "package2", "package3")
        assertThat(resolvedPolicy?.contributingAdmins)
            .containsExactly(SYSTEM_ADMIN, DEVICE_OWNER_ADMIN)
    }

    @Test
    fun resolve_topPriority() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Integer>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, INT_POLICY_A as PolicyValue<Integer>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, INT_POLICY_B as PolicyValue<Integer>)

        val resolvedPolicy =
            TopPriority<Integer>(listOf(EnforcingAdmin.DPC_AUTHORITY)).resolve(adminPolicies)

        assert(resolvedPolicy != null)
        assert(resolvedPolicy?.resolvedPolicyValue == INT_POLICY_B)
        assertThat(resolvedPolicy?.contributingAdmins).containsExactly(DEVICE_OWNER_ADMIN)
    }

    companion object {
        private const val SYSTEM_USER_ID = UserHandle.USER_SYSTEM
        private val SYSTEM_ADMIN = EnforcingAdmin.createSystemEnforcingAdmin("system_entity")
        private val DEVICE_OWNER_ADMIN =
            EnforcingAdmin.createEnterpriseEnforcingAdmin(
                ComponentName("packagename", "classname"),
                SYSTEM_USER_ID,
            )
        private val DEVICE_ADMIN =
            EnforcingAdmin.createDeviceAdminEnforcingAdmin(
                ComponentName("packagename", "classname"),
                SYSTEM_USER_ID,
            )

        private val INT_POLICY_A = IntegerPolicyValue(1 shl 7)
        private val INT_POLICY_B = IntegerPolicyValue(1 shl 8)
        private val INT_POLICY_AB = IntegerPolicyValue(1 shl 7 or 1 shl 8)
    }
}
