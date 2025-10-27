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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

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
                /*requiredPermission=*/ null,
                /*requiredCrossUserPermission=*/ null,
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

    val mockPermissionChecker: IPermissionChecker = mock<IPermissionChecker> {}
    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mockPermissionChecker
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
    fun setPolicy_shouldCallAllMethodsInOrder() {
        val methodCalls = mutableListOf<String>()
        val handler =
            object :
                PolicyHandler<Int>(Policy.key, Policy.metadata, Policy.definition, mockDelegate) {
                override fun convertValue(
                    caller: CallerIdentity,
                    transportValue: PolicyValueTransport?,
                ): Int? {
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
            }

        handler.setPolicy(anyCaller, anyScope, Policy.anyTransportValue)

        assertThat(methodCalls)
            .isEqualTo(
                listOf("convertValue", "checkPermissions", "validateValue", "storePolicyValue")
            )
    }

    @Test
    fun setPolicy_shouldValidateAllowedScope() {
        val allAllowedScopes = setOf(POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)
        val someDisallowedScopes = setOf(POLICY_SCOPE_USER, 111, 666)
        val metadata = copyOf(Policy.metadata, allowedScopes = allAllowedScopes)
        val handler = createHandler(metadata = metadata)

        // This should not throw exceptions
        for (scope in allAllowedScopes) {
            handler.setPolicy(anyCaller, scope, Policy.anyTransportValue)
        }

        // This should throw exceptions
        for (scope in someDisallowedScopes) {
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicy(anyCaller, scope, Policy.anyTransportValue)
            }
        }
    }

    @Test
    fun setPolicy_scopeUser_shouldCheckPermission() {
        val metadata =
            copyOf(
                Policy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_USER),
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "shouldNotBeChecked",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_USER, Policy.anyTransportValue)

        verify(mockPermissionChecker).enforce("thePermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_scopeGlobal_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            copyOf(
                Policy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermission",
                requiredCrossUserPermission = "theCrossUserPermission",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_DEVICE, Policy.anyTransportValue)

        verify(mockPermissionChecker).enforce("thePermission", theCaller)
        verify(mockPermissionChecker).enforce("theCrossUserPermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_scopeParent_shouldCheckPermissionAndCrossUserPermission() {
        val metadata =
            copyOf(
                Policy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_PARENT_USER),
                requiredPermission = "permission",
                requiredCrossUserPermission = "crossUserPermission",
            )
        val handler = createHandler(metadata = metadata)
        val theCaller = anyCaller

        handler.setPolicy(theCaller, POLICY_SCOPE_PARENT_USER, Policy.anyTransportValue)

        verify(mockPermissionChecker).enforce("permission", theCaller)
        verify(mockPermissionChecker).enforce("crossUserPermission", theCaller)
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_acceptedDpcTypes_shouldNotCheckPermissionIfDpcTypeIsAccepted() {
        val metadata =
            copyOf(
                Policy.metadata,
                requiredPermission = "thePermissionThatShallNotBeChecked",
                allowedDpcTypes = setOf(DEFAULT_DEVICE_OWNER, PROFILE_OWNER),
            )
        val handler = createHandler(metadata = metadata)

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEFAULT_DEVICE_OWNER }

        handler.setPolicy(anyCaller, anyScope, Policy.anyTransportValue)

        verify(mockPermissionChecker, never()).enforce(any(), any())
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_acceptedDpcTypes_shouldCheckPermissionIfDpcTypeIsNotAccepted() {
        val metadata =
            copyOf(
                Policy.metadata,
                requiredPermission = "thePermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEFAULT_DEVICE_OWNER, PROFILE_OWNER),
            )
        val handler = createHandler(metadata = metadata)

        mockDelegate.stub { on { getDpcType(any()) } doReturn FINANCED_DEVICE_OWNER }

        handler.setPolicy(anyCaller, anyScope, Policy.anyTransportValue)

        verify(mockPermissionChecker).enforce(eq("thePermissionThatShallBeChecked"), any())
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_acceptedDpcTypes_shouldStillCheckCrossUserPermissionIfDpcTypeIsAccepted() {
        val metadata =
            copyOf(
                Policy.metadata,
                allowedScopes = setOf(POLICY_SCOPE_DEVICE),
                requiredPermission = "thePermissionThatShallNotBeChecked",
                requiredCrossUserPermission = "theCrossUserPermissionThatShallBeChecked",
                allowedDpcTypes = setOf(DEFAULT_DEVICE_OWNER),
            )
        val handler = createHandler(metadata = metadata)

        mockDelegate.stub { on { getDpcType(any()) } doReturn DEFAULT_DEVICE_OWNER }

        handler.setPolicy(anyCaller, POLICY_SCOPE_DEVICE, Policy.anyTransportValue)

        verify(mockPermissionChecker, never())
            .enforce(eq("thePermissionThatShallNotBeChecked"), any())
        verify(mockPermissionChecker).enforce(eq("theCrossUserPermissionThatShallBeChecked"), any())
        verifyNoMoreInteractions(mockPermissionChecker)
    }

    @Test
    fun setPolicy_shouldStorePolicy() {
        val handler = createHandler(metadata = copyOf(Policy.metadata, allowedScopes = allScopes))
        val theCaller = anyCaller
        val theValue = Policy.VALUE_1
        val theKey = Policy.definition

        for (scope in allScopes) {
            handler.setPolicy(theCaller, scope, PolicyValueTransport.integerField(theValue))

            verify(mockDelegate, times(1))
                .storePolicy(theCaller, theKey, scope, IntegerPolicyValue(theValue))
        }

        // There should be no calls to `clearPolicy`
        verify(mockDelegate, never()).clearPolicy<Int>(any(), any(), any())
    }

    @Test
    fun setPolicy_shouldClearNullPolicy() {
        val handler =
            createHandler(
                metadata = copyOf(Policy.metadata, allowedScopes = allScopes),
                delegate = mockDelegate,
            )
        val theCaller = anyCaller
        val theKey = Policy.definition

        for (scope in allScopes) {
            handler.setPolicy(theCaller, scope, null)

            verify(mockDelegate, times(1)).clearPolicy(theCaller, theKey, scope)
        }

        // There should be no calls to `storePolicy`
        verify(mockDelegate, never()).storePolicy<Int>(any(), any(), any(), any())
    }
}
