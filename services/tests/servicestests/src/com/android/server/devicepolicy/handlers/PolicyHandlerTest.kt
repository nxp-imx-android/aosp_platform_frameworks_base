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

package com.android.server.devicepolicy.handlers

import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.EnumPolicyMetadata
import android.app.admin.metadata.PolicyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.devicepolicy.CallerIdentity
import com.android.server.devicepolicy.DevicePolicyManagerService.NOT_A_DPC
import com.android.server.devicepolicy.IPermissionChecker
import com.android.server.devicepolicy.handlers.PolicyHandlerTest.EnumPolicy.crossUserPermission
import com.android.server.devicepolicy.handlers.PolicyHandlerTest.EnumPolicy.permission
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * A {@link PolicyHandler} that doesn't store the resulting policy value in the {@link
 * DevicePolicyEngine} but instead stores it locally in {@link resultValue}.
 */
open class HandlerThatStoresValue<T>(
    key: PolicyIdentifier<T>,
    policyInformation: PolicyInformation<T>,
    policyMetadata: PolicyMetadata<T>,
    delegate: Delegate,
) : PolicyHandler<T>(key, policyInformation, policyMetadata, delegate) {
    var resultValue: T? = null

    override fun setPolicy(
        caller: CallerIdentity,
        scope: Int,
        transportValue: PolicyValueTransport?,
    ) {
        resultValue = null
        super.setPolicy(caller, scope, transportValue)
    }

    override fun storePolicyValue(caller: CallerIdentity, scope: Int, value: T?) {
        resultValue = value
    }
}

@RunWith(AndroidJUnit4::class)
class PolicyHandlerTest {

    // A sample enum policy that can be used in the tests.
    object EnumPolicy {
        val name = "theEnumPolicy"
        val permission = "thePermissionForTheEnumPolicy"
        val crossUserPermission = "theCrossUserPermissionForTheEnumPolicy"
        const val VALUE_1 = 1
        const val VALUE_2 = 2
        val key = PolicyIdentifier<Int>(name)
        val information =
            EnumPolicyInformation(
                key,
                permission,
                crossUserPermission,
            )
        val metadata =
            EnumPolicyMetadata(
                key,
                setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
                RESOURCE_PER_USER,
                permission,
                crossUserPermission,
                setOf(VALUE_1, VALUE_2),
            )
        val anyTransportValue: PolicyValueTransport = PolicyValueTransport.integerField(VALUE_1)
    }

    private val anyCaller = CallerIdentity(111, 222, "callerPackage", null)
    private val anyScope = POLICY_SCOPE_USER

    private val mockPermissionChecker = Mockito.mock(IPermissionChecker::class.java)

    private val delegate =
        object : PolicyHandler.Delegate {
            // The DPC type of the caller. Returned by getDpcType.
            var callerDpcType = NOT_A_DPC

            override fun getDpcType(caller: CallerIdentity): Int {
                return callerDpcType
            }

            override fun getPermissionChecker(): IPermissionChecker? {
                return mockPermissionChecker
            }
        }

    fun copyOf(
        source: EnumPolicyMetadata,
        id: PolicyIdentifier<Int>? = null,
        allowedScopes: Set<Int>? = null,
        affectedResource: Int? = null,
        allowedValues: Set<Int>? = null,
    ): EnumPolicyMetadata {
        return EnumPolicyMetadata(
            id ?: source.id,
            allowedScopes ?: source.allowedScopes,
            affectedResource ?: source.affectedResource,
            source.requiredPermission,
            source.requiredCrossUserPermission,
            allowedValues ?: source.allowedValues,
        )
    }

    fun copyOf(
        source: EnumPolicyInformation,
        key: PolicyIdentifier<Int>? = null,
        requiredPermission: String? = null,
        requiredCrossUserPermission: String? = null,
    ): EnumPolicyInformation {
        return EnumPolicyInformation(
            key ?: source.key,
            requiredPermission ?: source.requiredPermission,
            requiredCrossUserPermission ?: source.requiredCrossUserPermission,
        )
    }

    @Test
    fun setPolicy_shouldCallAllMethodsInOrder() {
        val methodCalls = mutableListOf<String>()
        val handler =
            object :
                PolicyHandler<Int>(
                    EnumPolicy.key,
                    EnumPolicy.information,
                    EnumPolicy.metadata,
                    delegate,
                ) {
                override fun convertValue(
                    caller: CallerIdentity,
                    transportValue: PolicyValueTransport?,
                ): Int? {
                    methodCalls.add("convertValue")
                    return EnumPolicy.VALUE_2
                }

                override fun checkPermissions(caller: CallerIdentity, scope: Int) {
                    methodCalls.add("checkPermissions")
                }

                override fun validateValue(caller: CallerIdentity, value: Int?) {
                    methodCalls.add("validateValue")
                }

                override fun storePolicyValue(caller: CallerIdentity, scope: Int, value: Int?) {
                    methodCalls.add("storePolicyValue")
                }
            }

        handler.setPolicy(anyCaller, anyScope, EnumPolicy.anyTransportValue)

        assertThat(methodCalls)
            .isEqualTo(
                listOf("convertValue", "checkPermissions", "validateValue", "storePolicyValue")
            )
    }

    @Test
    fun setPolicy_shouldValidateAllowedScope() {
        val allAllowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)
        val someDisallowedScopes = setOf(POLICY_SCOPE_USER, 111, 666)
        val metadata = copyOf(EnumPolicy.metadata, allowedScopes = allAllowedScopes)
        val handler = PolicyHandler<Int>(EnumPolicy.key, EnumPolicy.information, metadata, delegate)

        // This should not throw exceptions
        for (scope in allAllowedScopes) {
            handler.setPolicy(anyCaller, scope, EnumPolicy.anyTransportValue)
        }

        // This should throw exceptions
        for (scope in someDisallowedScopes) {
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicy(anyCaller, scope, EnumPolicy.anyTransportValue)
            }
        }
    }

    @Test
    fun setPolicy_scopeUser_shouldCheckPermission() {
        val policyInformation =
            copyOf(
                EnumPolicy.information,
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "shouldNotBeChecked",
            )
        val metadata = copyOf(EnumPolicy.metadata, allowedScopes = setOf(POLICY_SCOPE_USER))
        val handler =
            PolicyHandler<Int>(EnumPolicy.key, policyInformation, metadata, delegate)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_USER, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker).enforce("thePermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_scopeGlobal_shouldCheckPermissionAndCrossUserPermission() {
        val policyInformation =
            copyOf(
                EnumPolicy.information,
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "theCrossUserPermission",
            )
        val metadata = copyOf(EnumPolicy.metadata, allowedScopes = setOf(POLICY_SCOPE_DEVICE))
        val handler =
            PolicyHandler<Int>(EnumPolicy.key, policyInformation, metadata, delegate)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_DEVICE, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker).enforce("thePermission", theCaller)
        verify(mockPermissionChecker).enforce("theCrossUserPermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_scopeParent_shouldCheckPermissionAndCrossUserPermission() {
        val policyInformation =
            copyOf(
                EnumPolicy.information,
                requiredPermission = "permission",
                requiredCrossUserPermission = "crossUserPermission",
            )
        val metadata = copyOf(EnumPolicy.metadata, allowedScopes = setOf(POLICY_SCOPE_PARENT_USER))
        val handler =
            PolicyHandler<Int>(EnumPolicy.key, policyInformation, metadata, delegate)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_PARENT_USER, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker).enforce("permission", theCaller)
        verify(mockPermissionChecker).enforce("crossUserPermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_enum_shouldHandleValidValues() {
        val enumValues = setOf(123, 456, 789)
        val metadata = copyOf(EnumPolicy.metadata, allowedValues = enumValues)
        val handler =
            HandlerThatStoresValue<Int>(
                EnumPolicy.key,
                EnumPolicy.information,
                metadata,
                delegate,
            )

        for (enumValue in enumValues) {
            handler.setPolicy(anyCaller, anyScope, PolicyValueTransport.integerField(enumValue))

            assertThat(handler.resultValue).isEqualTo(enumValue)
        }
    }

    @Test
    fun setPolicy_enum_shouldHandleNull() {
        val handler =
            HandlerThatStoresValue<Int>(
                EnumPolicy.key,
                EnumPolicy.information,
                EnumPolicy.metadata,
                delegate,
            )
        handler.setPolicy(anyCaller, anyScope, null)
        assertThat(handler.resultValue).isEqualTo(null)
    }

    @Test
    fun setPolicy_enum_shouldThrowWhenValueIsOutOfRange() {
        val validEnumValues = setOf(555, 666)
        val metadata = copyOf(EnumPolicy.metadata, allowedValues = validEnumValues)

        val handler =
            PolicyHandler<Int>(EnumPolicy.key, EnumPolicy.information, metadata, delegate)

        val invalidEnumValues = setOf(0, 1, 554, 556, 665, 667, 1000)

        for (enumValue in invalidEnumValues) {
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicy(anyCaller, anyScope, PolicyValueTransport.integerField(enumValue))
            }
        }
    }

    @Test
    fun setPolicy_enum_shouldThrowWhenValueHasWrongType() {
        val handler =
            PolicyHandler<Int>(
                EnumPolicy.key,
                EnumPolicy.information,
                EnumPolicy.metadata,
                delegate,
            )

        assertFailsWith<IllegalArgumentException>("theEnumPolicy requires an Enum value $$$") {
            handler.setPolicy(anyCaller, anyScope, PolicyValueTransport.booleanField(true))
        }
    }
}
