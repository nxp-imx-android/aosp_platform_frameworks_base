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

package com.android.server.devicepolicy.handlers

import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.PolicyIdentifier
import android.app.admin.metadata.PolicyMetadata
import android.app.admin.metadata.StringPolicyMetadata
import com.android.server.devicepolicy.MostRecent
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.times

@RunWith(TestParameterInjector::class)
open class PolicyDefinitionFactoryTest {

    // A sample policy that can be used in the tests.
    object Policy {
        val name = "thePolicy"
        val key = PolicyIdentifier<String>(name)

        val metadata =
            StringPolicyMetadata(
                key,
                /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
                /*affectedResource=*/ RESOURCE_PER_USER,
                /*requiredPermission=*/ "testPermission",
                /*requiredCrossUserPermission=*/ "testCrossUserPermission",
                /*allowedDpcTypes=*/ setOf(),
                /*emptyStringAllowed=*/ true,
            )
    }

    fun copyOf(
        source: StringPolicyMetadata,
        id: PolicyIdentifier<String>? = null,
        allowedScopes: Set<Int>? = null,
        affectedResource: Int? = null,
        requiredPermission: String? = null,
        requiredCrossUserPermission: String? = null,
        allowedDpcTypes: Set<Int>? = null,
    ) =
        StringPolicyMetadata(
            id ?: source.id,
            allowedScopes ?: source.allowedScopes,
            affectedResource ?: source.affectedResource,
            requiredPermission ?: source.requiredPermission,
            requiredCrossUserPermission ?: source.requiredCrossUserPermission,
            allowedDpcTypes ?: source.allowedDpcTypes,
            source.isEmptyStringAllowed,
        )

    fun createPolicyDefinition(metadata: PolicyMetadata<String>) =
        PolicyDefinitionFactory.createPrePopulatedBuilder(metadata)
            // At the time of writing resolution mechanism can not be modelled in the annotations.
            .setResolutionMechanism(MostRecent<String>())
            .build()

    enum class PolicyDefinitionGlobalFlagTestCase(
        val allowedScopes: Set<Int>,
        val expectedValue: Boolean,
    ) {
        SCOPE_DEVICE(allowedScopes = setOf(POLICY_SCOPE_DEVICE), expectedValue = true),
        SCOPE_USER(allowedScopes = setOf(POLICY_SCOPE_USER), expectedValue = false),
        SCOPE_PARENT_USER(allowedScopes = setOf(POLICY_SCOPE_PARENT_USER), expectedValue = false),
        SCOPE_DEVICE_AND_USER(
            allowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_USER),
            expectedValue = false,
        ),
        SCOPE_DEVICE_AND_PARENT_USER(
            allowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER),
            expectedValue = false,
        ),
    }

    @Test
    fun getPolicyDefinition_globalOnlyFlag(
        @TestParameter testCase: PolicyDefinitionGlobalFlagTestCase
    ) {
        val metadata = copyOf(Policy.metadata, allowedScopes = testCase.allowedScopes)

        val policyDefinition = createPolicyDefinition(metadata)

        assertThat(policyDefinition.isGlobalOnlyPolicy()).isEqualTo(testCase.expectedValue)
    }

    enum class PolicyDefinitionLocalFlagTestCase(
        val allowedScopes: Set<Int>,
        val expectedValue: Boolean,
    ) {
        SCOPE_DEVICE(allowedScopes = setOf(POLICY_SCOPE_DEVICE), expectedValue = false),
        SCOPE_USER(allowedScopes = setOf(POLICY_SCOPE_USER), expectedValue = true),
        SCOPE_PARENT_USER(allowedScopes = setOf(POLICY_SCOPE_PARENT_USER), expectedValue = true),
        SCOPE_DEVICE_AND_USER(
            allowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_USER),
            expectedValue = false,
        ),
        SCOPE_DEVICE_AND_PARENT_USER(
            allowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER),
            expectedValue = false,
        ),
        SCOPE_USER_AND_PARENT_USER(
            allowedScopes = setOf(POLICY_SCOPE_USER, POLICY_SCOPE_PARENT_USER),
            expectedValue = true,
        ),
        SCOPE_ALL(allowedScopes = allScopes, expectedValue = false),
    }

    @Test
    fun getPolicyDefinition_localOnlyFlag(
        @TestParameter testCase: PolicyDefinitionLocalFlagTestCase
    ) {
        val metadata = copyOf(Policy.metadata, allowedScopes = testCase.allowedScopes)

        val policyDefinition = createPolicyDefinition(metadata)

        assertThat(policyDefinition.isLocalOnlyPolicy()).isEqualTo(testCase.expectedValue)
    }

    @Test
    fun getPolicyDefinition_key() {

        val policyDefinition =
            createPolicyDefinition(
                copyOf(Policy.metadata, id = PolicyIdentifier<String>("THE_POLICY_NAME"))
            )

        assertThat(policyDefinition.getPolicyKey().getIdentifier()).isEqualTo("THE_POLICY_NAME")
    }
}
