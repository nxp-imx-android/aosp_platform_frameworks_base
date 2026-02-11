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

import android.app.admin.BooleanPolicyValue
import android.app.admin.DevicePolicyManager.NOT_A_DPC
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.RESOURCE_DEVICE_WIDE
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.NoArgsPolicyKey
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.EnumPolicyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.devicepolicy.BooleanPolicySerializer
import com.android.server.devicepolicy.IPermissionChecker
import com.android.server.devicepolicy.MostRecent
import com.android.server.devicepolicy.PolicyDefinition
import com.google.common.truth.Truth.assertThat
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

@RunWith(AndroidJUnit4::class)
open class EnumStoredAsBooleanPolicyHandlerTest {

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mock<IPermissionChecker> {}
        }

    // A sample policy that can be used in the tests.
    object Policy {
        val name = "thePolicy"
        const val VALUE_TRUE = 12
        const val VALUE_FALSE = 24
        val key = PolicyIdentifier<Int>(name)

        val metadata =
            EnumPolicyMetadata(
                key,
                /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
                /*affectedResource=*/ RESOURCE_PER_USER,
                /*requiredPermission=*/ "testPermission",
                /*requiredCrossUserPermission=*/ "testCrossUserPermission",
                /*allowedDpcTypes=*/ setOf(),
                /*allowedValues=*/ setOf(VALUE_TRUE, VALUE_FALSE),
            )
        // The policy definition used for storing the policy value in DevicePolicyEngine.
        val definition =
            PolicyDefinition<Boolean>(
                NoArgsPolicyKey(name),
                MostRecent<Boolean>(),
                NoOpPolicyEnforcerCallback<Boolean>(),
                BooleanPolicySerializer(),
            )
    }

    fun createHandler(
        metadata: EnumPolicyMetadata = Policy.metadata
    ): EnumStoredAsBooleanPolicyHandler {
        val handler =
            EnumStoredAsBooleanPolicyHandler(
                Policy.key,
                Policy.definition,
                /*trueValue=*/ Policy.VALUE_TRUE,
                /*falseValue=*/ Policy.VALUE_FALSE,
                metadata,
            )
        handler.setDelegate(mockDelegate)
        return handler
    }

    fun copyOf(metadata: EnumPolicyMetadata, affectedResource: Int) =
        EnumPolicyMetadata(
            metadata.id,
            metadata.allowedValues,
            affectedResource,
            metadata.requiredPermission,
            metadata.requiredCrossUserPermission,
            metadata.allowedDpcTypes,
            metadata.allowedValues,
        )

    @Test
    fun setPolicyUnchecked_shouldAcceptNull() {
        val handler = createHandler()

        handler.setPolicyUnchecked(anyCaller, anyScope, null)

        verify(mockDelegate).clearPolicy(anyCaller, Policy.definition, anyScope)
    }

    @Test
    fun setPolicyUnchecked_shouldRejectValueOfWrongType() {
        val handler = createHandler()

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.booleanField(true),
                )
            }

        assertThat(exception.message).contains("is not an integer")
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptValueTrue() {
        val handler = createHandler()

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.integerField(Policy.VALUE_TRUE),
        )

        verify(mockDelegate, times(1))
            .storePolicy(any(), any(), any(), eq(BooleanPolicyValue(true)))

        verify(mockDelegate, never()).clearPolicy<Boolean>(any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptValueFalse() {
        val handler = createHandler()

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.integerField(Policy.VALUE_FALSE),
        )

        verify(mockDelegate, times(1))
            .storePolicy(any(), any(), any(), eq(BooleanPolicyValue(false)))

        verify(mockDelegate, never()).clearPolicy<Boolean>(any(), any(), any())
    }

    @Test
    fun setPolicyUnchecked_shouldRejectValuesOutOfRange() {
        val handler = createHandler()

        val invalidEnumValues = setOf(0, 1, 554, 556, 665, 667, 1000)

        for (enumValue in invalidEnumValues) {
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.integerField(enumValue),
                )
            }
        }
    }

    @Test
    fun getPolicyUnchecked_getValueTrue() {
        val handler = createHandler()

        mockDelegate.stub { on { getPolicySetByAdmin<Boolean>(any(), any(), any()) } doReturn true }

        val returnedValue = handler.getPolicyUnchecked(anyCaller, anyScope)

        assertThat(returnedValue).isNotNull()
        assertThat(returnedValue?.tag).isEqualTo(PolicyValueTransport.integerField)
        assertThat(returnedValue?.getIntegerField()).isEqualTo(Policy.VALUE_TRUE)
    }

    @Test
    fun getPolicyUnchecked_getValueFalse() {
        val handler = createHandler()

        mockDelegate.stub {
            on { getPolicySetByAdmin<Boolean>(any(), any(), any()) } doReturn false
        }

        val returnedValue = handler.getPolicyUnchecked(anyCaller, anyScope)

        assertThat(returnedValue).isNotNull()
        assertThat(returnedValue?.tag).isEqualTo(PolicyValueTransport.integerField)
        assertThat(returnedValue?.getIntegerField()).isEqualTo(Policy.VALUE_FALSE)
    }

    @Test
    fun getPolicyUnchecked_getUnsetValue() {
        val handler = createHandler()

        mockDelegate.stub { on { getPolicySetByAdmin<Boolean>(any(), any(), any()) } doReturn null }

        val returnedValue = handler.getPolicyUnchecked(anyCaller, anyScope)

        assertThat(returnedValue).isNull()
    }

    @Test
    fun getResolvedPerUserPolicyUnchecked() {
        val metadata = copyOf(Policy.metadata, affectedResource = RESOURCE_PER_USER)
        val handler = createHandler(metadata = metadata)
        val anyUserId = 123

        mockDelegate.stub { on { getResolvedPerUserPolicy<Boolean>(any(), any()) } doReturn true }

        val returnedValue = handler.getResolvedPerUserPolicyUnchecked(anyUserId)

        assertThat(returnedValue).isNotNull()
        assertThat(returnedValue?.tag).isEqualTo(PolicyValueTransport.integerField)
        assertThat(returnedValue?.getIntegerField()).isEqualTo(Policy.VALUE_TRUE)
    }

    @Test
    fun getResolvedDeviceWidePolicyUnchecked() {
        val metadata = copyOf(Policy.metadata, affectedResource = RESOURCE_DEVICE_WIDE)
        val handler = createHandler(metadata = metadata)

        mockDelegate.stub { on { getResolvedDeviceWidePolicy<Boolean>(any()) } doReturn false }

        val returnedValue = handler.getResolvedDeviceWidePolicyUnchecked()

        assertThat(returnedValue).isNotNull()
        assertThat(returnedValue?.tag).isEqualTo(PolicyValueTransport.integerField)
        assertThat(returnedValue?.getIntegerField()).isEqualTo(Policy.VALUE_FALSE)
    }
}
