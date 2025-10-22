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

import android.app.admin.DevicePolicyManager.DEFAULT_DEVICE_OWNER
import android.app.admin.DevicePolicyManager.FINANCED_DEVICE_OWNER
import android.app.admin.DevicePolicyManager.NOT_A_DPC
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.PROFILE_OWNER
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.IntegerPolicyValue
import android.app.admin.NoArgsPolicyKey
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyKey
import android.app.admin.PolicyValue
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.EnumPolicyMetadata
import android.app.admin.metadata.PolicyMetadata
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.internal.infra.AndroidFuture
import com.android.internal.util.function.QuadFunction
import com.android.server.devicepolicy.CallerIdentity
import com.android.server.devicepolicy.IPermissionChecker
import com.android.server.devicepolicy.IntegerPolicySerializer
import com.android.server.devicepolicy.MostRecent
import com.android.server.devicepolicy.PolicyDefinition
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * A {@link PolicyHandler} that doesn't store the resulting policy value in the {@link
 * DevicePolicyEngine} but instead stores it locally in {@link resultValue}.
 */
open class HandlerThatStoresValue<T>(
    key: PolicyIdentifier<T>,
    policyMetadata: PolicyMetadata<T>,
    policyDefinition: PolicyDefinition<T>,
    delegate: Delegate,
) : PolicyHandler<T>(key, policyMetadata, policyDefinition, delegate) {
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

// Helper that can be used as a policy enforcer callback when creating a PolicyDefinition.
private class NoOpPolicyEnforcerCallback<T> :
    QuadFunction<T, Context, Int, PolicyKey, CompletableFuture<Boolean>> {
    override fun apply(v: T, c: Context, i: Int, p: PolicyKey): CompletableFuture<Boolean> {
        return AndroidFuture.completedFuture(true)
    }
}

@RunWith(AndroidJUnit4::class)
class PolicyHandlerTest {

    // A sample enum policy that can be used in the tests.
    object EnumPolicy {
        val name = "theEnumPolicy"
        const val VALUE_1 = 1
        const val VALUE_2 = 2
        val key = PolicyIdentifier<Int>(name)
        val metadata =
            EnumPolicyMetadata(
                key,
                /*allowedScopes=*/setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
                /*affectedResource=*/RESOURCE_PER_USER,
                /*requiredPermission=*/null,
                /*requiredCrossUserPermission=*/null,
                /*allowedDpcTypes=*/setOf(),
                /*allowedValues=*/setOf(VALUE_1, VALUE_2),
            )
        val anyTransportValue: PolicyValueTransport = PolicyValueTransport.integerField(VALUE_1)

        // The policy definition used for storing the policy value in DevicePolicyEngine.
        val definition =
            PolicyDefinition<Int>(
                NoArgsPolicyKey(name),
                MostRecent<Int>(),
                NoOpPolicyEnforcerCallback<Int>(),
                IntegerPolicySerializer(),
            )
    }

    private val anyCaller = CallerIdentity(111, 222, "callerPackage", null)
    private val anyScope = POLICY_SCOPE_USER
    private val allScopes = setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)

    private val mockPermissionChecker = Mockito.mock(IPermissionChecker::class.java)

    /**
     * Helper interface to mock calls to {@link PolicyHandler.Delegate} to store policies.
     * Since the methods on the delegate are templated, accept the value as a <*>.
     */
    abstract class PolicyStorage {
        abstract fun storePolicy(
            caller: CallerIdentity,
            key: PolicyDefinition<*>,
            scope: Int,
            value: PolicyValue<*>,
        )

        abstract fun clearPolicy(caller: CallerIdentity, key: PolicyDefinition<*>, scope: Int)
    }

    private val mockPolicyStorage = Mockito.mock(PolicyStorage::class.java)

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

            override fun <T> storePolicy(
                caller: CallerIdentity,
                key: PolicyDefinition<T>,
                scope: Int,
                value: PolicyValue<T>,
            ) {
                mockPolicyStorage.storePolicy(caller, key, scope, value)
            }

            override fun <T> clearPolicy(
                caller: CallerIdentity,
                key: PolicyDefinition<T>,
                scope: Int,
            ) {
                mockPolicyStorage.clearPolicy(caller, key, scope)
            }
        }

    fun copyOf(
        source: EnumPolicyMetadata,
        id: PolicyIdentifier<Int>? = null,
        allowedScopes: Set<Int>? = null,
        affectedResource: Int? = null,
        requiredPermission: String? = null,
        requiredCrossUserPermission: String? = null,
        allowedDpcTypes: Set<Int>? = null,
        allowedValues: Set<Int>? = null,
    ): EnumPolicyMetadata {
        return EnumPolicyMetadata(
            id ?: source.id,
            allowedScopes ?: source.allowedScopes,
            affectedResource ?: source.affectedResource,
            requiredPermission ?: source.requiredPermission,
            requiredCrossUserPermission ?: source.requiredCrossUserPermission,
            allowedDpcTypes ?: source.allowedDpcTypes,
            allowedValues ?: source.allowedValues,
        )
    }

    fun createEnumHandler(
        key: PolicyIdentifier<Int> = EnumPolicy.key,
        metadata: EnumPolicyMetadata = EnumPolicy.metadata,
        definition: PolicyDefinition<Int> = EnumPolicy.definition,
        delegate: PolicyHandler.Delegate = this.delegate,
    ) = PolicyHandler<Int>(key, metadata, definition, delegate)

    @Test
    fun setPolicy_shouldCallAllMethodsInOrder() {
        val methodCalls = mutableListOf<String>()
        val handler =
            object :
                PolicyHandler<Int>(
                    EnumPolicy.key,
                    EnumPolicy.metadata,
                    EnumPolicy.definition,
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
        val handler = createEnumHandler(metadata = metadata)

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
        val metadata =
            copyOf(
                EnumPolicy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_USER),
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "shouldNotBeChecked",
            )
        val handler = createEnumHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_USER, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker).enforce("thePermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_scopeGlobal_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            copyOf(
                EnumPolicy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "theCrossUserPermission",
            )
        val handler = createEnumHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_DEVICE, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker).enforce("thePermission", theCaller)
        verify(mockPermissionChecker).enforce("theCrossUserPermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_scopeParent_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            copyOf(
                EnumPolicy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_PARENT_USER),
                requiredPermission = "permission",
                requiredCrossUserPermission = "crossUserPermission",
            )
        val handler = createEnumHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_PARENT_USER, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker).enforce("permission", theCaller)
        verify(mockPermissionChecker).enforce("crossUserPermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_acceptedDpcTypes_shouldNotCheckPermissionIfDpcTypeIsAccepted() {
        val metadata =
            copyOf(
                EnumPolicy.metadata,
                requiredPermission = "thePermissionThatShallNotBeChecked",
                allowedDpcTypes = setOf(DEFAULT_DEVICE_OWNER, PROFILE_OWNER)
            )
        val handler = createEnumHandler(metadata = metadata, delegate = delegate)
        delegate.callerDpcType = DEFAULT_DEVICE_OWNER

        handler.setPolicy(anyCaller, anyScope, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker, never()).enforce(any(), any())
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_acceptedDpcTypes_shouldCheckPermissionIfDpcTypeIsNotAccepted() {
        val metadata =
            copyOf(
                EnumPolicy.metadata,
                requiredPermission = "thePermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEFAULT_DEVICE_OWNER, PROFILE_OWNER)
            )
        val handler = createEnumHandler(metadata = metadata, delegate = delegate)
        delegate.callerDpcType = FINANCED_DEVICE_OWNER

        handler.setPolicy(anyCaller, anyScope, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker).enforce(eq("thePermissionThatShallBeChecked"), any())
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_acceptedDpcTypes_shouldStillCheckCrossUserPermissionIfDpcTypeIsAccepted() {
        val metadata =
            copyOf(
                EnumPolicy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEFAULT_DEVICE_OWNER)
            )
        val handler = createEnumHandler(metadata = metadata, delegate = delegate)
        delegate.callerDpcType = DEFAULT_DEVICE_OWNER
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_DEVICE, EnumPolicy.anyTransportValue)

        verify(mockPermissionChecker, never()).enforce(eq("thePermissionThatShallNotBeChecked"), any())
        verify(mockPermissionChecker).enforce("theCrossUserPermissionThatShallBeChecked", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_enum_shouldHandleValidValues() {
        val enumValues = setOf(123, 456, 789)
        val metadata = copyOf(EnumPolicy.metadata, allowedValues = enumValues)
        val handler =
            HandlerThatStoresValue<Int>(EnumPolicy.key, metadata, EnumPolicy.definition, delegate)

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
                EnumPolicy.metadata,
                EnumPolicy.definition,
                delegate,
            )
        handler.setPolicy(anyCaller, anyScope, null)
        assertThat(handler.resultValue).isEqualTo(null)
    }

    @Test
    fun setPolicy_enum_shouldThrowWhenValueIsOutOfRange() {
        val validEnumValues = setOf(555, 666)
        val metadata = copyOf(EnumPolicy.metadata, allowedValues = validEnumValues)
        val handler = createEnumHandler(metadata = metadata)

        val invalidEnumValues = setOf(0, 1, 554, 556, 665, 667, 1000)

        for (enumValue in invalidEnumValues) {
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicy(anyCaller, anyScope, PolicyValueTransport.integerField(enumValue))
            }
        }
    }

    @Test
    fun setPolicy_enum_shouldThrowWhenValueHasWrongType() {
        val handler = createEnumHandler()

        val exception = assertFailsWith<IllegalArgumentException> {
            handler.setPolicy(anyCaller, anyScope, PolicyValueTransport.booleanField(true))
        }

        assertThat(exception.message).contains("is not an integer")
    }

    @Test
    fun setPolicy_enum_shouldStorePolicy() {
        val handler =
            createEnumHandler(metadata = copyOf(EnumPolicy.metadata, allowedScopes = allScopes))
        val theCaller = anyCaller
        val theValue = EnumPolicy.VALUE_2
        val theKey = EnumPolicy.definition

        for (scope in allScopes) {
            handler.setPolicy(theCaller, scope, PolicyValueTransport.integerField(theValue))

            verify(mockPolicyStorage)
                .storePolicy(theCaller, theKey, scope, IntegerPolicyValue(theValue))
            verifyNoMoreInteractions(mockPolicyStorage)
        }
    }

    @Test
    fun setPolicy_enum_shouldClearNullPolicy() {
        val handler =
            createEnumHandler(metadata = copyOf(EnumPolicy.metadata, allowedScopes = allScopes))
        val theCaller = anyCaller
        val theKey = EnumPolicy.definition

        for (scope in allScopes) {
            handler.setPolicy(theCaller, scope, null)

            verify(mockPolicyStorage).clearPolicy(theCaller, theKey, scope)

            verifyNoMoreInteractions(mockPolicyStorage)
        }
    }
}
