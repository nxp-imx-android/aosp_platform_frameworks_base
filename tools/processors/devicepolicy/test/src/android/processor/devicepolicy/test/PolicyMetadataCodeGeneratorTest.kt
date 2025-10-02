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

package android.processor.devicepolicy.test

import android.processor.devicepolicy.PolicyMetadataCodeGenerator
import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadataList
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import com.squareup.javapoet.JavaFile
import org.junit.Test
import java.io.CharArrayWriter
import com.google.common.truth.Truth.assertThat

/**
 * Remove whitespace and empty lines to make string comparisons on code simpler for tests.
 */
private fun trimLines(string: String) =
    string.lines().map { it.trim() }.filter { !it.isEmpty() }.joinToString("\n")

class PolicyMetadataCodeGeneratorTest {
    private fun fillInFile(
        code: String, includes: String = """
        import java.util.ArrayList;
        import java.util.List;
        import java.util.Set;
    """
    ) = trimLines(
        """
        package android.app.admin.metadata;

        $includes

        /**
         * Generated class to load policy metadata
         */
        public class Policies {
            /**
             * Generated method that returns a list of all policy metadata
             */
            public static List<PolicyMetadata<?>> loadPolicyMetadata() {
                List<PolicyMetadata<?>> policies = new ArrayList<PolicyMetadata<?>>();
                $code
                return policies;
            }
        }
        """
    )

    private fun javaFileToString(file: JavaFile): String {
        val writer = CharArrayWriter()

        file.writeTo(writer)

        return trimLines(writer.toString())
    }

    private fun boolTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setName(name)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setBooleanMetadata(
                        TypeSpecificPolicyMetadata.BooleanPolicyMetadata.newBuilder()
                    )
            )

    @Test
    fun test_booleanPolicy_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                boolTestPolicy("test.package.MY_TEST_BOOL_POLICY")
                    .addAllAllowedScopes(listOf(
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER
                    ))
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                """
                policies.add(new BooleanPolicyMetadata(
                    /* id= */ test.package.MY_TEST_BOOL_POLICY,
                    /* allowedScopes= */ Set.of(
                        1,
                        3
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null
                ));
                """
            )
        )
    }

    @Test
    fun test_doubleBooleanPolicy_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                boolTestPolicy("test.package.MY_TEST_BOOL_POLICY")
                    .addAllAllowedScopes(listOf(
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER
                    ))
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .addPolicyMetadata(
                boolTestPolicy("test.package.MY_SECOND_TEST_BOOL_POLICY")
                    .addAllAllowedScopes(listOf(
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE,
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER
                    ))
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_PER_USER
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                """
                policies.add(new BooleanPolicyMetadata(
                    /* id= */ test.package.MY_TEST_BOOL_POLICY,
                    /* allowedScopes= */ Set.of(
                        1,
                        3
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null
                ));
                policies.add(new BooleanPolicyMetadata(
                    /* id= */ test.package.MY_SECOND_TEST_BOOL_POLICY,
                    /* allowedScopes= */ Set.of(
                        2,
                        3
                    ),
                    /* affectedResource= */ 2,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null
                ));
                """
            )
        )
    }

    private fun integerTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setName(name)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setIntegerMetadata(
                        TypeSpecificPolicyMetadata.IntegerPolicyMetadata.newBuilder()
                    )
            )

    @Test
    fun test_integerPolicy_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                integerTestPolicy("test.package.MY_TEST_INTEGER_POLICY")
                    .addAllAllowedScopes(listOf(
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE
                    ))
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                """
                policies.add(new IntegerPolicyMetadata(
                    /* id= */ test.package.MY_TEST_INTEGER_POLICY,
                    /* allowedScopes= */ Set.of(
                        2
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null
                ));
                """
            )
        )
    }

    private fun enumTestPolicy(name: String, allowedValues: Set<Int>): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setName(name)
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setEnumMetadata(
                        TypeSpecificPolicyMetadata.EnumPolicyMetadata.newBuilder()
                            .addAllValues(
                                allowedValues.map{
                                    TypeSpecificPolicyMetadata.EnumPolicyMetadata.EnumValue
                                        .newBuilder()
                                        .setIntValue(it)
                                        .build()
                                }
                            )
                    )
            )

    @Test
    fun test_enumPolicy_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                enumTestPolicy("test.package.MY_TEST_ENUM_POLICY", setOf(1, 5, 7))
                    .addAllAllowedScopes(listOf(
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE
                    ))
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile("""
                policies.add(new EnumPolicyMetadata(
                    /* id= */ test.package.MY_TEST_ENUM_POLICY,
                    /* allowedScopes= */ Set.of(
                        2
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedValues= */ Set.of(
                        1,
                        5,
                        7
                    )
                ));
                """
            )
        )
    }

    @Test
    fun test_permissions_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                boolTestPolicy("test.package.MY_TEST_BOOL_POLICY")
                    .addAllAllowedScopes(listOf(
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER
                    ))
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
                    .setRequiredPermission("test_permission")
                    .setRequiredCrossUserPermission("test_cross_permission")
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                """
                policies.add(new BooleanPolicyMetadata(
                    /* id= */ test.package.MY_TEST_BOOL_POLICY,
                    /* allowedScopes= */ Set.of(
                        1,
                        3
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ "test_permission",
                    /* requiredCrossUserPermission= */ "test_cross_permission"
                ));
                """
            )
        )
    }
}