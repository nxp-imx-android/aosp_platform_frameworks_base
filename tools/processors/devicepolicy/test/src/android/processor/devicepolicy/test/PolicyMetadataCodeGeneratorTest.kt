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
import android.processor.devicepolicy.protos.FullyQualifiedFieldName
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
        code: String,
        includes: String = """
            import java.util.ArrayList;
            import java.util.List;
            import java.util.Set;
        """,
        staticImports: List<String> = listOf()
    ) = trimLines(
        """
        package android.app.admin.metadata;

        ${
            staticImports.sorted().joinToString(
                separator = ";\nimport static ",
                prefix = "import static ",
                postfix = ";"
            )
        }
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

    private fun simpleNameToFieldName(name: String): FullyQualifiedFieldName {
        val fieldName = name.substringAfterLast(".")
        val rest = name.substringBeforeLast(".")
        val className = rest.substringAfterLast(".")
        val packageName = rest.substringBeforeLast(".")

        return FullyQualifiedFieldName.newBuilder()
            .setFieldName(fieldName)
            .setClassName(className)
            .setPackageName(packageName)
            .build()
    }

    private fun boolTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
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
                boolTestPolicy(
                    "test.package.PolicyContainer.MY_TEST_BOOL_POLICY"
                )
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER
                        )
                    )
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                staticImports = listOf("test.package.PolicyContainer.MY_TEST_BOOL_POLICY"),
                code = """
                policies.add(new BooleanPolicyMetadata(
                    /* id= */ MY_TEST_BOOL_POLICY,
                    /* allowedScopes= */ Set.of(
                        1,
                        3
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of()
                ));
                """
            )
        )
    }

    @Test
    fun test_doubleBooleanPolicy_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                boolTestPolicy(
                    "test.package.PolicyContainer.MY_TEST_BOOL_POLICY"
                )
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER
                        )
                    )
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .addPolicyMetadata(
                boolTestPolicy(
                    "test.package.PolicyContainer.MY_SECOND_TEST_BOOL_POLICY"
                )
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE,
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER
                        )
                    )
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_PER_USER
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                staticImports = listOf(
                    "test.package.PolicyContainer.MY_TEST_BOOL_POLICY",
                    "test.package.PolicyContainer.MY_SECOND_TEST_BOOL_POLICY"
                ),
                code = """
                policies.add(new BooleanPolicyMetadata(
                    /* id= */ MY_TEST_BOOL_POLICY,
                    /* allowedScopes= */ Set.of(
                        1,
                        3
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of()
                ));
                policies.add(new BooleanPolicyMetadata(
                    /* id= */ MY_SECOND_TEST_BOOL_POLICY,
                    /* allowedScopes= */ Set.of(
                        2,
                        3
                    ),
                    /* affectedResource= */ 2,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of()
                ));
                """
            )
        )
    }

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
    fun test_integerPolicy_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                integerTestPolicy(
                    "test.package.PolicyContainer.MY_TEST_INTEGER_POLICY"
                )
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE
                        )
                    )
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                staticImports = listOf("test.package.PolicyContainer.MY_TEST_INTEGER_POLICY"),
                code = """
                policies.add(new IntegerPolicyMetadata(
                    /* id= */ MY_TEST_INTEGER_POLICY,
                    /* allowedScopes= */ Set.of(
                        2
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of()
                ));
                """
            )
        )
    }

    private fun enumTestPolicy(name: String, allowedValues: Set<Int>): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setEnumMetadata(
                        TypeSpecificPolicyMetadata.EnumPolicyMetadata.newBuilder()
                            .addAllValues(
                                allowedValues.map {
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
                enumTestPolicy("test.package.PolicyContainer.MY_TEST_ENUM_POLICY", setOf(1, 5, 7))
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE
                        )
                    )
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                staticImports = listOf("test.package.PolicyContainer.MY_TEST_ENUM_POLICY"),
                code = """
                policies.add(new EnumPolicyMetadata(
                    /* id= */ MY_TEST_ENUM_POLICY,
                    /* allowedScopes= */ Set.of(
                        2
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(),
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

    private fun stringTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setStringMetadata(
                        TypeSpecificPolicyMetadata.StringPolicyMetadata.newBuilder()
                    )
            )

    @Test
    fun test_stringPolicy_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                stringTestPolicy(
                    "test.package.PolicyContainer.MY_TEST_STRING_POLICY"
                )
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE
                        )
                    )
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                staticImports = listOf("test.package.PolicyContainer.MY_TEST_STRING_POLICY"),
                code = """
                policies.add(new StringPolicyMetadata(
                    /* id= */ MY_TEST_STRING_POLICY,
                    /* allowedScopes= */ Set.of(
                        2
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(),
                    /* emptyStringAllowed= */ false
                ));
                """
            )
        )
    }

    private fun listOfStringTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setListOfStringMetadata(
                        TypeSpecificPolicyMetadata.ListOfStringPolicyMetadata.newBuilder()
                    )
            )

    @Test
    fun test_listOfStringPolicy_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                listOfStringTestPolicy(
                    "test.package.PolicyContainer.MY_TEST_STRING_LIST_POLICY"
                )
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE
                        )
                    )
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
                includes = """
                import android.app.admin.PolicyIdentifier;
                import java.lang.String;
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Set;
                """,
                staticImports = listOf("test.package.PolicyContainer.MY_TEST_STRING_LIST_POLICY"),
                code = """
                policies.add(new ListPolicyMetadata<String>(
                    /* id= */ MY_TEST_STRING_LIST_POLICY,
                    /* elementMetadata= */ new StringPolicyMetadata(
                        /* id= */ new PolicyIdentifier<String>(MY_TEST_STRING_LIST_POLICY.getId() + "#elements"),
                        /* allowedScopes= */ Set.of(
                            2
                        ),
                        /* affectedResource= */ 1,
                        /* requiredPermission= */ null,
                        /* requiredCrossUserPermission= */ null,
                        /* allowedDpcTypes= */ Set.of(),
                        /* emptyStringAllowed= */ false
                    ),
                    /* emptyListAllowed= */ false
                ));
                """
            )
        )
    }

    @Test
    fun test_permissions_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                boolTestPolicy(
                    "test.package.PolicyContainer.MY_TEST_BOOL_POLICY"
                )
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER
                        )
                    )
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
                staticImports = listOf("test.package.PolicyContainer.MY_TEST_BOOL_POLICY"),
                code = """
                policies.add(new BooleanPolicyMetadata(
                    /* id= */ MY_TEST_BOOL_POLICY,
                    /* allowedScopes= */ Set.of(
                        1,
                        3
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ "test_permission",
                    /* requiredCrossUserPermission= */ "test_cross_permission",
                    /* allowedDpcTypes= */ Set.of()
                ));
                """
            )
        )
    }

    @Test
    fun test_allowedDpcTypes_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                enumTestPolicy("test.package.MY_TEST_POLICY", setOf())
                    .addAllAllowedScopes(listOf(
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                    ))
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
                    .addAllAllowedDpcTypes(listOf(
                        PolicyMetadata.DpcType.DPC_TYPE_DEFAULT_DEVICE_OWNER,
                        PolicyMetadata.DpcType.DPC_TYPE_PROFILE_OWNER
                    ))
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
              staticImports = listOf("test.package.MY_TEST_POLICY"),
               code =  """
                policies.add(new EnumPolicyMetadata(
                    /* id= */ MY_TEST_POLICY,
                    /* allowedScopes= */ Set.of(
                        1
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(
                        1, // DEFAULT_DEVICE_OWNER
                        5  // PROFILE_OWNER
                    ),
                    /* allowedValues= */ Set.of()
                ));
                """
            )
        )
    }

    @Test
    fun test_allAllowedDpcTypes_outputMatches() {
        val policyList = PolicyMetadataList.newBuilder()
            .addPolicyMetadata(
                enumTestPolicy("test.package.MY_TEST_POLICY", setOf())
                    .addAllAllowedScopes(listOf(
                        PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                    ))
                    .setAffectedResource(
                        PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                    )
                    .addAllAllowedDpcTypes(listOf(
                        PolicyMetadata.DpcType.DPC_TYPE_DEFAULT_DEVICE_OWNER,
                        PolicyMetadata.DpcType.DPC_TYPE_FINANCED_DEVICE_OWNER,
                        PolicyMetadata.DpcType.DPC_TYPE_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                        PolicyMetadata.DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER_0,
                        PolicyMetadata.DpcType.DPC_TYPE_PROFILE_OWNER,
                        PolicyMetadata.DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER,
                        PolicyMetadata.DpcType.DPC_TYPE_AFFILIATED_PROFILE_OWNER_ON_USER,
                    ))
            )
            .build()

        val javaFile = PolicyMetadataCodeGenerator.generate(policyList)

        assertThat(javaFileToString(javaFile)).isEqualTo(
            fillInFile(
              staticImports = listOf("test.package.MY_TEST_POLICY"),
               code =  """
                policies.add(new EnumPolicyMetadata(
                    /* id= */ MY_TEST_POLICY,
                    /* allowedScopes= */ Set.of(
                        1
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(
                        1, // DEFAULT_DEVICE_OWNER
                        2, // FINANCED_DEVICE_OWNER
                        3, // PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE
                        4, // PROFILE_OWNER_ON_USER_0
                        5, // PROFILE_OWNER
                        6, // PROFILE_OWNER_ON_USER
                        7  // AFFILIATED_PROFILE_OWNER_ON_USER
                    ),
                    /* allowedValues= */ Set.of()
                ));
                """
            )
        )
    }
}