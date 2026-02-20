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

import android.processor.devicepolicy.protos.FullyQualifiedFieldName
import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadataList
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata
import android.tools.policymetadata.Generator
import com.google.common.truth.Truth.assertThat
import com.squareup.javapoet.JavaFile
import java.io.CharArrayWriter
import org.junit.Test

/** Remove whitespace and empty lines to make string comparisons on code simpler for tests. */
private fun trimLines(string: String) =
    string.lines().map { it.trim() }.filter { !it.isEmpty() }.joinToString("\n")

class GeneratorTest {
    private fun fillInFile(
        code: String,
        includes: List<String> = listOf(),
        staticImports: List<String> = listOf(),
    ): String {
        var allIncludes =
            includes + listOf("java.util.ArrayList", "java.util.List", "java.util.Set")

        return trimLines(
            """
        package android.app.admin.metadata;

        ${
            staticImports.sorted().joinToString(
                separator = ";\nimport static ",
                prefix = "import static ",
                postfix = ";",
            )
        }
        ${
            allIncludes.sorted().joinToString(
                separator = ";\nimport ",
                prefix = "import ",
                postfix = ";",
            )
        }

        /**
         * Generated class that contains metadata on all known policies.
         *
         * @hide
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
    }

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
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    boolTestPolicy("test.package.PolicyContainer.MY_TEST_BOOL_POLICY")
                        .addAllAllowedScopes(
                            listOf(
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER,
                            )
                        )
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports = listOf("test.package.PolicyContainer.MY_TEST_BOOL_POLICY"),
                    code =
                        """
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
                """,
                )
            )
    }

    @Test
    fun test_doubleBooleanPolicy_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    boolTestPolicy("test.package.PolicyContainer.MY_TEST_BOOL_POLICY")
                        .addAllAllowedScopes(
                            listOf(
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER,
                            )
                        )
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .addPolicyMetadata(
                    boolTestPolicy("test.package.PolicyContainer.MY_SECOND_TEST_BOOL_POLICY")
                        .addAllAllowedScopes(
                            listOf(
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE,
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER,
                            )
                        )
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_PER_USER)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports =
                        listOf(
                            "test.package.PolicyContainer.MY_TEST_BOOL_POLICY",
                            "test.package.PolicyContainer.MY_SECOND_TEST_BOOL_POLICY",
                        ),
                    code =
                        """
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
                """,
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
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    integerTestPolicy("test.package.PolicyContainer.MY_TEST_INTEGER_POLICY")
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
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
            )
    }

    @Test
    fun test_integerPolicyWithMinMax_outputMatches() {
        val integerMetadata =
            TypeSpecificPolicyMetadata.IntegerPolicyMetadata.newBuilder()
                .setMinValue(-10)
                .setMaxValue(10)
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    integerTestPolicy("test.package.PolicyContainer.MY_TEST_INTEGER_POLICY")
                        .setTypeSpecificMetadata(
                            TypeSpecificPolicyMetadata.newBuilder()
                                .setIntegerMetadata(integerMetadata)
                        )
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
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
            )
    }

    private fun longTestPolicy(
        name: String,
        minValue: Long? = null,
        maxValue: Long? = null,
    ): PolicyMetadata.Builder {
        val longMetadata = TypeSpecificPolicyMetadata.LongPolicyMetadata.newBuilder()
        if (minValue != null) {
            longMetadata.setMinValue(minValue)
        }
        if (maxValue != null) {
            longMetadata.setMaxValue(maxValue)
        }

        return PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder().setLongMetadata(longMetadata)
            )
    }

    @Test
    fun test_longPolicy_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    longTestPolicy("test.package.PolicyContainer.MY_TEST_LONG_POLICY")
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports = listOf("test.package.PolicyContainer.MY_TEST_LONG_POLICY"),
                    code =
                        """
                policies.add(new LongPolicyMetadata(
                    /* id= */ MY_TEST_LONG_POLICY,
                    /* allowedScopes= */ Set.of(
                        2
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(),
                    /* minValue= */ Long.MIN_VALUE,
                    /* maxValue= */ Long.MAX_VALUE
                ));
                """,
                )
            )
    }

    @Test
    fun test_longPolicyWithMinMax_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    longTestPolicy(
                            "test.package.PolicyContainer.SIMPLE_LONG_POLICY_WITH_RANGE",
                            minValue = 10,
                            maxValue = 100,
                        )
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports =
                        listOf("test.package.PolicyContainer.SIMPLE_LONG_POLICY_WITH_RANGE"),
                    code =
                        """
                policies.add(new LongPolicyMetadata(
                    /* id= */ SIMPLE_LONG_POLICY_WITH_RANGE,
                    /* allowedScopes= */ Set.of(
                        2
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(),
                    /* minValue= */ 10L,
                    /* maxValue= */ 100L
                ));
                """,
                )
            )
    }

    private fun enumTestPolicy(
        name: String,
        allowedValues: Set<Int>,
        resolutionMechanism: EnumPolicyMetadata.ResolutionMechanism =
            EnumPolicyMetadata.ResolutionMechanism.newBuilder().setCustom(true).build(),
    ): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setEnumMetadata(
                        EnumPolicyMetadata.newBuilder()
                            .addAllValues(
                                allowedValues.map {
                                    EnumPolicyMetadata.EnumValue.newBuilder()
                                        .setIntValue(it)
                                        .build()
                                }
                            )
                            .setResolutionMechanism(resolutionMechanism)
                    )
            )

    @Test
    fun test_enumPolicy_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    enumTestPolicy(
                            "test.package.PolicyContainer.MY_TEST_ENUM_POLICY",
                            setOf(1, 5, 7),
                        )
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports = listOf("test.package.PolicyContainer.MY_TEST_ENUM_POLICY"),
                    code =
                        """
                policies.add(new EnumPolicyMetadata(
                    /* id= */ MY_TEST_ENUM_POLICY,
                    /* allowedScopes= */ Set.of(
                        2
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(),
                    /* resolutionMechanism= */ null,
                    /* allowedValues= */ Set.of(
                        1,
                        5,
                        7
                    )
                ));
                """,
                )
            )
    }

    @Test
    fun test_enumPolicyWithMostRestrictive_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    enumTestPolicy(
                            "test.package.PolicyContainer.MY_TEST_ENUM_POLICY",
                            setOf(1, 5, 7),
                            resolutionMechanism =
                                EnumPolicyMetadata.ResolutionMechanism.newBuilder()
                                    .setMostRestrictive(
                                        EnumPolicyMetadata.ResolutionMechanism.MostRestrictive
                                            .newBuilder()
                                            .addAllMostToLeastRestrictive(listOf(1, 7, 5))
                                    )
                                    .build(),
                        )
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports = listOf("test.package.PolicyContainer.MY_TEST_ENUM_POLICY"),
                    includes = listOf("java.lang.Integer"),
                    code =
                        """
                          policies.add(new EnumPolicyMetadata(
                              /* id= */ MY_TEST_ENUM_POLICY,
                              /* allowedScopes= */ Set.of(
                                  2
                              ),
                              /* affectedResource= */ 1,
                              /* requiredPermission= */ null,
                              /* requiredCrossUserPermission= */ null,
                              /* allowedDpcTypes= */ Set.of(),
                              /* resolutionMechanism= */ new ResolutionMechanismMetadata.MostRestrictive<Integer>(
                                  List.of(
                                      new Integer(1),
                                      new Integer(7),
                                      new Integer(5)
                                  )
                              ),
                              /* allowedValues= */ Set.of(
                                  1,
                                  5,
                                  7
                              )
                          ));
                        """,
                )
            )
    }

    private fun stringTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setStringMetadata(TypeSpecificPolicyMetadata.StringPolicyMetadata.newBuilder())
            )

    @Test
    fun test_stringPolicy_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    stringTestPolicy("test.package.PolicyContainer.MY_TEST_STRING_POLICY")
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports = listOf("test.package.PolicyContainer.MY_TEST_STRING_POLICY"),
                    code =
                        """
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
                """,
                )
            )
    }

    private fun listOfStringTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setListMetadata(
                        TypeSpecificPolicyMetadata.ListPolicyMetadata.newBuilder()
                            .setStringMetadata(
                                TypeSpecificPolicyMetadata.StringPolicyMetadata.newBuilder()
                            )
                    )
            )

    @Test
    fun test_listOfStringPolicy_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    listOfStringTestPolicy(
                            "test.package.PolicyContainer.MY_TEST_STRING_LIST_POLICY"
                        )
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    includes = listOf("android.app.admin.PolicyIdentifier", "java.lang.String"),
                    staticImports =
                        listOf("test.package.PolicyContainer.MY_TEST_STRING_LIST_POLICY"),
                    code =
                        """
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
                """,
                )
            )
    }

    private fun listOfIntegerTestPolicy(
        name: String,
        emptyListAllowed: Boolean,
    ): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setListMetadata(
                        TypeSpecificPolicyMetadata.ListPolicyMetadata.newBuilder()
                            .setIntegerMetadata(
                                TypeSpecificPolicyMetadata.IntegerPolicyMetadata.newBuilder()
                            )
                            .setEmptyListAllowed(emptyListAllowed)
                    )
            )

    @Test
    fun test_listOfIntegerPolicy_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    listOfIntegerTestPolicy(
                            "test.package.PolicyContainer.MY_TEST_INTEGER_LIST_POLICY",
                            emptyListAllowed = true,
                        )
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    includes = listOf("android.app.admin.PolicyIdentifier", "java.lang.Integer"),
                    staticImports =
                        listOf("test.package.PolicyContainer.MY_TEST_INTEGER_LIST_POLICY"),
                    code =
                        """
                policies.add(new ListPolicyMetadata<Integer>(
                    /* id= */ MY_TEST_INTEGER_LIST_POLICY,
                    /* elementMetadata= */ new IntegerPolicyMetadata(
                        /* id= */ new PolicyIdentifier<Integer>(MY_TEST_INTEGER_LIST_POLICY.getId() + "#elements"),
                        /* allowedScopes= */ Set.of(
                            2
                        ),
                        /* affectedResource= */ 1,
                        /* requiredPermission= */ null,
                        /* requiredCrossUserPermission= */ null,
                        /* allowedDpcTypes= */ Set.of(),
                        /* minValue= */ Integer.MIN_VALUE,
                        /* maxValue= */ Integer.MAX_VALUE
                    ),
                    /* emptyListAllowed= */ true
                ));
                """,
                )
            )
    }

    private fun listOfEnumTestPolicy(name: String, enumValues: Set<Int>): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setListMetadata(
                        TypeSpecificPolicyMetadata.ListPolicyMetadata.newBuilder()
                            .setEnumMetadata(
                                EnumPolicyMetadata.newBuilder()
                                    .addAllValues(
                                        enumValues.map {
                                            EnumPolicyMetadata.EnumValue.newBuilder()
                                                .setIntValue(it)
                                                .build()
                                        }
                                    )
                                    .setResolutionMechanism(
                                        EnumPolicyMetadata.ResolutionMechanism.newBuilder()
                                            .setCustom(true)
                                            .build()
                                    )
                            )
                    )
            )

    @Test
    fun test_listOfEnumPolicy_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    listOfEnumTestPolicy(
                            "test.package.PolicyContainer.MY_TEST_ENUM_LIST_POLICY",
                            enumValues = setOf(1, 9, 17),
                        )
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    includes = listOf("android.app.admin.PolicyIdentifier", "java.lang.Integer"),
                    staticImports = listOf("test.package.PolicyContainer.MY_TEST_ENUM_LIST_POLICY"),
                    code =
                        """
                policies.add(new ListPolicyMetadata<Integer>(
                    /* id= */ MY_TEST_ENUM_LIST_POLICY,
                    /* elementMetadata= */ new EnumPolicyMetadata(
                        /* id= */ new PolicyIdentifier<Integer>(MY_TEST_ENUM_LIST_POLICY.getId() + "#elements"),
                        /* allowedScopes= */ Set.of(
                            2
                        ),
                        /* affectedResource= */ 1,
                        /* requiredPermission= */ null,
                        /* requiredCrossUserPermission= */ null,
                        /* allowedDpcTypes= */ Set.of(),
                        /* resolutionMechanism= */ null,
                        /* allowedValues= */ Set.of(
                          1,
                          9,
                          17
                        )
                    ),
                    /* emptyListAllowed= */ false
                ));
                """,
                )
            )
    }

    @Test
    fun test_permissions_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    boolTestPolicy("test.package.PolicyContainer.MY_TEST_BOOL_POLICY")
                        .addAllAllowedScopes(
                            listOf(
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER,
                            )
                        )
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                        .setRequiredPermission("test_permission")
                        .setRequiredCrossUserPermission("test_cross_permission")
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports = listOf("test.package.PolicyContainer.MY_TEST_BOOL_POLICY"),
                    code =
                        """
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
                """,
                )
            )
    }

    @Test
    fun test_allowedDpcTypes_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    enumTestPolicy("test.package.MY_TEST_POLICY", setOf())
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_USER))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                        .addAllAllowedDpcTypes(
                            listOf(
                                PolicyMetadata.DpcType.DPC_TYPE_DEVICE_OWNER,
                                PolicyMetadata.DpcType
                                    .DPC_TYPE_MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE,
                            )
                        )
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports = listOf("test.package.MY_TEST_POLICY"),
                    code =
                        """
                policies.add(new EnumPolicyMetadata(
                    /* id= */ MY_TEST_POLICY,
                    /* allowedScopes= */ Set.of(
                        1
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(
                        1, // DEVICE_OWNER
                        5  // MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
                    ),
                    /* resolutionMechanism= */ null,
                    /* allowedValues= */ Set.of()
                ));
                """,
                )
            )
    }

    @Test
    fun test_allAllowedDpcTypes_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    enumTestPolicy("test.package.MY_TEST_POLICY", setOf())
                        .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_USER))
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                        .addAllAllowedDpcTypes(
                            listOf(
                                PolicyMetadata.DpcType.DPC_TYPE_DEVICE_OWNER,
                                PolicyMetadata.DpcType.DPC_TYPE_FINANCED_DEVICE_OWNER,
                                PolicyMetadata.DpcType
                                    .DPC_TYPE_MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                                PolicyMetadata.DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER0,
                                PolicyMetadata.DpcType
                                    .DPC_TYPE_MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE,
                                PolicyMetadata.DpcType
                                    .DPC_TYPE_UNAFFILIATED_FULL_USER_PROFILE_OWNER,
                                PolicyMetadata.DpcType.DPC_TYPE_AFFILIATED_FULL_USER_PROFILE_OWNER,
                            )
                        )
                )
                .build()

        val javaFile = Generator.generate(policyList)

        assertThat(javaFileToString(javaFile))
            .isEqualTo(
                fillInFile(
                    staticImports = listOf("test.package.MY_TEST_POLICY"),
                    code =
                        """
                policies.add(new EnumPolicyMetadata(
                    /* id= */ MY_TEST_POLICY,
                    /* allowedScopes= */ Set.of(
                        1
                    ),
                    /* affectedResource= */ 1,
                    /* requiredPermission= */ null,
                    /* requiredCrossUserPermission= */ null,
                    /* allowedDpcTypes= */ Set.of(
                        1, // DEVICE_OWNER
                        2, // FINANCED_DEVICE_OWNER
                        3, // MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE
                        4, // PROFILE_OWNER_ON_USER0
                        5, // MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
                        6, // UNAFFILIATED_FULL_USER_PROFILE_OWNER
                        7  // AFFILIATED_FULL_USER_PROFILE_OWNER
                    ),
                    /* resolutionMechanism= */ null,
                    /* allowedValues= */ Set.of()
                ));
                """,
                )
            )
    }
}
