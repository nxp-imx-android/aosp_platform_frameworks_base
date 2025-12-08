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

import android.app.admin.DevicePolicyManager.DEVICE_OWNER
import android.app.admin.DevicePolicyManager.FINANCED_DEVICE_OWNER
import android.app.admin.DevicePolicyManager.NOT_A_DPC
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.IntegerPolicyValue
import android.app.admin.NoArgsPolicyKey
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyKey
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

// Helper that can be used as a policy enforcer callback when creating a PolicyDefinition.
class NoOpPolicyEnforcerCallback<T> :
    QuadFunction<T, Context, Int, PolicyKey, CompletableFuture<Boolean>> {
    override fun apply(v: T, c: Context, i: Int, p: PolicyKey): CompletableFuture<Boolean> {
        return AndroidFuture.completedFuture(true)
    }
}

val anyCaller = CallerIdentity(111, 222, "callerPackage", null)
const val anyScope = POLICY_SCOPE_USER
val allScopes = setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)

// This class contains all `PolicyHandler` tests that are independent of the policy value type.
// Type specific tests (for enum vs string vs int vs ...) should go in the correct
// <Type>PolicyHandlerTest class.
@RunWith(AndroidJUnit4::class)
open class PolicyHandlerTest {

    // A sample policy that can be used in the tests.
    object Policy {
        val name = "thePolicy"
        const val VALUE_1 = 1
        val key = PolicyIdentifier<Int>(name)

        val metadata =
            EnumPolicyMetadata(
                key,
                /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
                /*affectedResource=*/ RESOURCE_PER_USER,
                /*requiredPermission=*/ "testPermission",
                /*requiredCrossUserPermission=*/ "testCrossUserPermission",
                /*allowedDpcTypes=*/ setOf(),
                /*allowedValues=*/ setOf(VALUE_1),
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

    data class EnforceArguments(val permission: String, val caller: CallerIdentity)

    private val permissionChecker = object : IPermissionChecker {
        val enforceCalls = mutableListOf<EnforceArguments>()

        override fun enforce(
            permission: String,
            caller: CallerIdentity
        ) {
            enforceCalls.add(EnforceArguments(permission, caller))
        }

        fun assertEnforcesPermissions(caller: CallerIdentity, vararg permission: String) {
            assertThat(enforceCalls).isEqualTo(
                permission.map {
                    EnforceArguments(it, caller)
                }
            )
        }
    }

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn permissionChecker
        }

    fun copyOf(
        source: EnumPolicyMetadata,
        id: PolicyIdentifier<Int>? = null,
        allowedScopes: Set<Int>? = null,
        affectedResource: Int? = null,
        requiredPermission: String? = null,
        requiredCrossUserPermission: String? = null,
        allowedDpcTypes: Set<Int>? = null,
    ) =
        EnumPolicyMetadata(
            id ?: source.id,
            allowedScopes ?: source.allowedScopes,
            affectedResource ?: source.affectedResource,
            requiredPermission ?: source.requiredPermission,
            requiredCrossUserPermission ?: source.requiredCrossUserPermission,
            allowedDpcTypes ?: source.allowedDpcTypes,
            source.allowedValues,
        )

    fun createHandler(
        key: PolicyIdentifier<Int> = Policy.key,
        metadata: PolicyMetadata<Int> = Policy.metadata,
        definition: PolicyDefinition<Int> = Policy.definition,
        delegate: PolicyHandler.Delegate = this.mockDelegate,
    ) = PolicyHandler<Int>(key, metadata, definition, delegate)

    @Test
    fun setPolicyUnchecked_shouldCallMethodsInOrder() {
        val methodCalls = mutableListOf<String>()
        val handler =
            object :
                PolicyHandler<Int>(Policy.key, Policy.metadata, Policy.definition, mockDelegate) {
                override fun convertValue(transportValue: PolicyValueTransport?): Int? {
                    methodCalls.add("convertValue")
                    return 5
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

                override fun getPolicyValue(caller: CallerIdentity, scope: Int): Int? {
                    methodCalls.add("getPolicyValue")
                    return null
                }
            }

        handler.setPolicyUnchecked(anyCaller, anyScope, Policy.anyTransportValue)

        assertThat(methodCalls)
            .isEqualTo(
                listOf("convertValue", "validateValue", "storePolicyValue")
            )
    }

    @Test
    fun setPolicyUnchecked_shouldValidateAllowedScope() {
        val allAllowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)
        val someDisallowedScopes = setOf(POLICY_SCOPE_USER, 111, 666)
        val metadata = copyOf(Policy.metadata, allowedScopes = allAllowedScopes)
        val handler = createHandler(metadata = metadata)

        // This should not throw exceptions
        for (scope in allAllowedScopes) {
            handler.setPolicyUnchecked(anyCaller, scope, Policy.anyTransportValue)
        }

        // This should throw exceptions
        for (scope in someDisallowedScopes) {
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(anyCaller, scope, Policy.anyTransportValue)
            }
        }
    }

    @Test
    fun setPolicyUnchecked_shouldStorePolicy() {
        val handler = createHandler(metadata = copyOf(Policy.metadata, allowedScopes = allScopes))
        val theCaller = anyCaller
        val theValue = Policy.VALUE_1
        val theKey = Policy.definition

        for (scope in allScopes) {
            handler.setPolicyUnchecked(theCaller, scope, PolicyValueTransport.integerField(theValue))

            verify(mockDelegate, times(1))
                .storePolicy(theCaller, theKey, scope, IntegerPolicyValue(theValue))
        }

        // There should be no calls to `clearPolicy`
        verify(mockDelegate, never()).clearPolicy<Int>(any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_shouldClearNullPolicy() {
        val handler =
            createHandler(
                metadata = copyOf(Policy.metadata, allowedScopes = allScopes),
                delegate = mockDelegate,
            )
        val theCaller = anyCaller
        val theKey = Policy.definition

        for (scope in allScopes) {
            handler.setPolicyUnchecked(theCaller, scope, null)

            verify(mockDelegate, times(1)).clearPolicy(theCaller, theKey, scope)
        }

        // There should be no calls to `storePolicy`
        verify(mockDelegate, never()).storePolicy<Int>(any(), any(), any(), any())
    }

    @Test
    fun checkPermissions_scopeUser_shouldCheckPermission() {
        val metadata =
            copyOf(
                Policy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_USER),
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "shouldNotBeChecked",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.checkPermissions(theCaller, POLICY_SCOPE_USER)

        permissionChecker.assertEnforcesPermissions(theCaller, "thePermission")
    }

    @Test
    fun checkPermissions_scopeGlobal_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            copyOf(
                Policy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "theCrossUserPermission",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.checkPermissions(theCaller, POLICY_SCOPE_DEVICE)

        permissionChecker.assertEnforcesPermissions(
            theCaller, "thePermission", "theCrossUserPermission"
        )
    }

    @Test
    fun checkPermissions_scopeParent_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            copyOf(
                Policy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_PARENT_USER),
                requiredPermission = "permission",
                requiredCrossUserPermission = "crossUserPermission",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.checkPermissions(theCaller, POLICY_SCOPE_PARENT_USER)

        permissionChecker.assertEnforcesPermissions(
            theCaller, "permission", "crossUserPermission"
        )
    }

    @Test
    fun checkPermissions_acceptedDpcTypes_shouldNotCheckPermissionIfDpcTypeIsAccepted() {
        val metadata =
            copyOf(
                Policy.metadata,
                requiredPermission = "thePermissionThatShallNotBeChecked",
                allowedDpcTypes = setOf(
                    DEVICE_OWNER,
                    MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
                ),
            )
        val handler = createHandler(metadata = metadata)

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEVICE_OWNER }

        handler.checkPermissions(anyCaller, anyScope)

        assertThat(permissionChecker.enforceCalls).isEmpty()
    }

    @Test
    fun checkPermissions_acceptedDpcTypes_shouldCheckPermissionIfDpcTypeIsNotAccepted() {
        val metadata =
            copyOf(
                Policy.metadata,
                requiredPermission = "thePermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEVICE_OWNER, MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn FINANCED_DEVICE_OWNER }

        handler.checkPermissions(theCaller, anyScope)

        permissionChecker.assertEnforcesPermissions(
            theCaller, "thePermissionThatShallBeChecked"
        )
    }

    @Test
    fun checkPermissions_acceptedDpcTypes_shouldStillCheckCrossUserPermissionIfDpcTypeIsAccepted() {
        val metadata =
            copyOf(
                Policy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEVICE_OWNER),
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEVICE_OWNER }

        handler.checkPermissions(theCaller, POLICY_SCOPE_DEVICE)

        permissionChecker.assertEnforcesPermissions(
            theCaller, "theCrossUserPermissionThatShallBeChecked"
        )
    }

    @Test
    fun getPolicyUnchecked_shouldCallMethodsInOrder() {
        val methodCalls = mutableListOf<String>()
        val handler =
            object :
                PolicyHandler<Int>(Policy.key, Policy.metadata, Policy.definition, mockDelegate) {
                override fun convertValue(transportValue: PolicyValueTransport?): Int? {
                    methodCalls.add("convertValue")
                    return 5
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

                override fun getPolicyValue(caller: CallerIdentity, scope: Int): Int? {
                    methodCalls.add("getPolicyValue")
                    return null
                }
            }

        handler.getPolicyUnchecked(anyCaller, anyScope)

        assertThat(methodCalls).isEqualTo(listOf("getPolicyValue"))
    }

    @Test
    fun getPolicyUnchecked_shouldAcceptAllowedScopes() {
        val allAllowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)
        val metadata = copyOf(Policy.metadata, allowedScopes = allAllowedScopes)
        val handler = createHandler(metadata = metadata)

        // This should not throw exceptions
        for (scope in allAllowedScopes) {
            handler.getPolicyUnchecked(anyCaller, scope)
        }
    }

    @Test
    fun getPolicyUnchecked_shouldRejectDisallowedScopes() {
        val allAllowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)
        val someDisallowedScopes = setOf(POLICY_SCOPE_USER, 111, 666)
        val metadata = copyOf(Policy.metadata, allowedScopes = allAllowedScopes)
        val handler = createHandler(metadata = metadata)

        for (scope in someDisallowedScopes) {
            assertFailsWith<IllegalArgumentException> { handler.getPolicyUnchecked(anyCaller, scope) }
        }
    }

    @Test
    fun checkPermissions_missingRequiredPermission_throwsException() {
        val metadata = EnumPolicyMetadata(
            Policy.key,
            /* allowedScopes= */ setOf(POLICY_SCOPE_USER),
            /* affectedResource= */ RESOURCE_PER_USER,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ "testCrossUserPermission",
            /* allowedDpcTypes= */ setOf(),
            /* allowedValues= */ setOf(Policy.VALUE_1),
        )

        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        val error = assertFailsWith<IllegalStateException> {
            handler.checkPermissions(theCaller, POLICY_SCOPE_USER)
        }

        assertThat(error).hasMessageThat().contains("no requiredPermission")
        assertThat(permissionChecker.enforceCalls).isEmpty()
    }

    @Test
    fun checkPermissions_missingRequiredCrossUserPermission_throwsException() {
        val metadata =
            EnumPolicyMetadata(
                Policy.key,
                /* allowedScopes= */ setOf(POLICY_SCOPE_DEVICE),
                /* affectedResource= */ RESOURCE_PER_USER,
                /* requiredPermission= */ "thePermission",
                /* requiredCrossUserPermission= */ null,
                /* allowedDpcTypes= */ setOf(),
                /* allowedValues= */ setOf(Policy.VALUE_1),
            )

        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.checkPermissions(theCaller, POLICY_SCOPE_DEVICE)

        permissionChecker.assertEnforcesPermissions(theCaller, "thePermission")
    }

    @Test
    fun getPolicyUnchecked_getStoredPolicy() {
        val handler = createHandler(metadata = copyOf(Policy.metadata, allowedScopes = allScopes))
        val theCaller = anyCaller
        val storedValue = Policy.VALUE_1
        val theKey = Policy.definition

        mockDelegate.stub { on { getPolicySetByAdmin<Int>(any(), any(), any()) } doReturn storedValue }

        for (scope in allScopes) {
            val returnedValue = handler.getPolicyUnchecked(theCaller, scope)

            assertThat(returnedValue).isNotNull()
            assertThat(returnedValue?.tag).isEqualTo(PolicyValueTransport.integerField)
            assertThat(returnedValue?.getIntegerField()).isEqualTo(storedValue)
            verify(mockDelegate, times(1)).getPolicySetByAdmin(theCaller, theKey, scope)
        }
    }

    @Test
    fun getPolicyUnchecked_shouldBeAbleToHandleUnsetPolicies() {
        val handler =
            createHandler(
                metadata = copyOf(Policy.metadata, allowedScopes = allScopes),
                delegate = mockDelegate,
            )
        val theCaller = anyCaller
        val theKey = Policy.definition

        mockDelegate.stub { on { getPolicySetByAdmin<Int>(any(), any(), any()) } doReturn null }

        for (scope in allScopes) {
            val returnedValue = handler.getPolicyUnchecked(theCaller, scope)

            assertThat(returnedValue).isNull()
            verify(mockDelegate, times(1)).getPolicySetByAdmin(theCaller, theKey, scope)
        }
    }
}
