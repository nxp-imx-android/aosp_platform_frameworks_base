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

package android.processor.devicepolicy.test

import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.IntegerPolicyMetadata
import android.tools.policymetadata.Generator
import org.junit.Test

class IntegerGeneratorTest {
    private fun integerTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setIntegerMetadata(
                        TypeSpecificPolicyMetadata.IntegerPolicyMetadata.newBuilder()
                    )
            )

    @Test
    fun test_outputMatches() {
        val javaFile =
            Generator.generate(
                integerTestPolicy("test.package.PolicyContainer.MY_TEST_INTEGER_POLICY")
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_INTEGER_POLICY"),
            code =
                """
                  policies.add(new IntegerPolicyMetadata(
                      /* id= */ MY_TEST_INTEGER_POLICY,
                      /* allowedScopes= */ Set.of(
                          2
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* minValue= */ Integer.MIN_VALUE,
                      /* maxValue= */ Integer.MAX_VALUE
                  ));
                """,
        )
    }

    @Test
    fun test_withMinMax_outputMatches() {
        val integerMetadata =
            TypeSpecificPolicyMetadata.IntegerPolicyMetadata.newBuilder()
                .setMinValue(-10)
                .setMaxValue(10)
        val javaFile =
            Generator.generate(
                integerTestPolicy("test.package.PolicyContainer.MY_TEST_INTEGER_POLICY")
                    .setTypeSpecificMetadata(
                        TypeSpecificPolicyMetadata.newBuilder().setIntegerMetadata(integerMetadata)
                    )
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_INTEGER_POLICY"),
            code =
                """
                  policies.add(new IntegerPolicyMetadata(
                      /* id= */ MY_TEST_INTEGER_POLICY,
                      /* allowedScopes= */ Set.of(
                          2
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* minValue= */ -10,
                      /* maxValue= */ 10
                  ));
                """,
        )
    }
}
