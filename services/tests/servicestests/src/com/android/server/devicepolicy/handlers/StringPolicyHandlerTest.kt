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

import android.app.admin.DevicePolicyManager.NOT_A_DPC
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.NoArgsPolicyKey
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.StringPolicyValue
import android.app.admin.metadata.StringPolicyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.devicepolicy.IPermissionChecker
import com.android.server.devicepolicy.MostRecent
import com.android.server.devicepolicy.PolicyDefinition
import com.android.server.devicepolicy.StringPolicySerializer
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class StringPolicyHandlerTest {

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mock<IPermissionChecker> {}
        }

    // A sample string policy that can be used in the tests.
    object Policy {
        val name = "theStringPolicy"
        val key = PolicyIdentifier<String>(name)
        val metadata =
            StringPolicyMetadata(
                key,
                /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
                /*affectedResource=*/ RESOURCE_PER_USER,
                /*requiredPermission=*/ "testPermission",
                /*requiredCrossUserPermission=*/ "testCrossUserPermission",
                /*allowedDpcTypes=*/ setOf(),
                /*emptyStringAllowed=*/ false,
            )
        val anyTransportValue: PolicyValueTransport =
            PolicyValueTransport.stringField("a string value")

        // The policy definition used for storing the policy value in DevicePolicyEngine.
        val definition =
            PolicyDefinition<String>(
                NoArgsPolicyKey(name),
                MostRecent<String>(),
                NoOpPolicyEnforcerCallback<String>(),
                StringPolicySerializer(),
            )
    }

    fun createHandler(
        key: PolicyIdentifier<String> = Policy.key,
        metadata: StringPolicyMetadata = Policy.metadata,
        definition: PolicyDefinition<String> = Policy.definition,
        delegate: PolicyHandler.Delegate = this.mockDelegate,
    ) = PolicyHandler<String>(key, metadata, definition, delegate)

    fun copyOf(source: StringPolicyMetadata, emptyStringAllowed: Boolean? = null) =
        StringPolicyMetadata(
            source.id,
            source.allowedScopes,
            source.affectedResource,
            source.requiredPermission,
            source.requiredCrossUserPermission,
            source.allowedDpcTypes,
            emptyStringAllowed ?: source.isEmptyStringAllowed,
        )

    @Test
    fun setPolicyUnchecked_shouldAcceptNull() {
        val definition = Policy.definition
        val handler = createHandler(definition = definition)

        handler.setPolicyUnchecked(anyCaller, anyScope, null)

        verify(mockDelegate).clearPolicy(anyCaller, definition, anyScope)
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

        assertThat(exception.message).contains("is not a string")
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptNonEmptyStrings() {
        val handler = createHandler()

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.stringField("It's a string"),
        )

        verify(mockDelegate)
            .storePolicy(anyCaller, Policy.definition, anyScope, StringPolicyValue("It's a string"))
    }

    @Test
    fun setPolicyUnchecked_shouldRejectEmptyString() {
        val metadataBlockingEmptyString = copyOf(Policy.metadata, emptyStringAllowed = false)
        val handler = createHandler(metadata = metadataBlockingEmptyString)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.stringField(""),
                )
            }
        assertThat(exception.message)
            .contains("Empty string is not allowed for policy ${Policy.key}")
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptEmptyString() {
        val metadataAllowingEmptyString = copyOf(Policy.metadata, emptyStringAllowed = true)
        val handler = createHandler(metadata = metadataAllowingEmptyString)

        handler.setPolicyUnchecked(anyCaller, anyScope, PolicyValueTransport.stringField(""))

        verify(mockDelegate)
            .storePolicy(anyCaller, Policy.definition, anyScope, StringPolicyValue(""))
    }
}
