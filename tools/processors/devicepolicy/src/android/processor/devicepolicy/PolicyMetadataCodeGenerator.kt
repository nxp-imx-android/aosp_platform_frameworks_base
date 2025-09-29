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

package android.processor.devicepolicy

import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadataList
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.WildcardTypeName
import javax.lang.model.element.Modifier

private const val METADATA_PACKAGE = "android.app.admin.metadata"

/**
 * Generates Policies.java which contains metadata on all known policies.
 *
 * The output will look something like this:
 * <pre>
 * {@code
 * public class Policies {
 *     public static List<PolicyMetadata<?>> loadPolicyMetadata() {
 *         List<PolicyMetadata<?>> policies = new ArrayList<PolicyMetadata<?>>();
 *         policies.add(new BooleanPolicyMetadata(android.app.admin.PolicyIdentifier.SIMPLE_BOOLEAN_POLICY));
 *         policies.add(new IntegerPolicyMetadata(android.app.admin.PolicyIdentifier.SIMPLE_INTEGER_POLICY));
 *         return policies;
 *     }
 * }
 * }
 * </pre>
 */
object PolicyMetadataCodeGenerator {
    fun generate(policies: PolicyMetadataList): JavaFile {
        val policiesClass =
            TypeSpec.classBuilder("Policies")
                .addModifiers(Modifier.PUBLIC)
                .addMethod(generateLoadPolicyMetadata(policies))
                .addJavadoc(
                    "Generated class to load policy metadata"
                )
                .build()

        val javaFile =
            JavaFile.builder(METADATA_PACKAGE, policiesClass)
                .indent("    ")
                .build()

        return javaFile
    }

    private fun generateLoadPolicyMetadata(policies: PolicyMetadataList): MethodSpec {
        val loadPolicyMetadataMethod =
            MethodSpec.methodBuilder("loadPolicyMetadata")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(listOfPolicyMetadataType)
                .addStatement(
                    "\$T policies = new \$T()", listOfPolicyMetadataType, arrayListOfPolicyMetadataType
                ).addJavadoc(
                    "Generated method that returns a list of all policy metadata"
                )

        for (policy in policies.policyMetadataList) {
            loadPolicyMetadataMethod.addCode(generatePolicyAdder(policy))
        }

        loadPolicyMetadataMethod.addStatement("return policies")

        return loadPolicyMetadataMethod.build()
    }

    private fun generatePolicyAdder(
        policy: PolicyMetadata
    ): CodeBlock = when (policy.typeSpecificMetadata.typeMetadataCase) {
        TypeSpecificPolicyMetadata.TypeMetadataCase.ENUM_METADATA -> generateEnumPolicyAdder(
            policy
        )

        TypeSpecificPolicyMetadata.TypeMetadataCase.BOOLEAN_METADATA -> generateBooleanPolicyAdder(
            policy
        )

        TypeSpecificPolicyMetadata.TypeMetadataCase.INTEGER_METADATA -> generateIntegerPolicyAdder(
            policy
        )

        TypeSpecificPolicyMetadata.TypeMetadataCase.TYPEMETADATA_NOT_SET -> throw IllegalArgumentException(
            "Type specific metadata unset"
        )
    }

    private fun generateSetBuilder(values: List<Int>): CodeBlock {
        if (values.isEmpty()) {
            return CodeBlock.builder().add(
                "\$T.of()", setType
            ).build()
        }
        val builder = CodeBlock.builder()

        builder.add(
            "\$T.of(\n", setType
        ).indent()

        for (value in values.dropLast(1)) {
            builder.add("\$L,\n", value)
        }
        builder.add("\$L\n", values.last())

        builder.unindent().add(")")

        return builder.build()
    }

    private fun CodeBlock.Builder.addPolicyArguments(policy: PolicyMetadata): CodeBlock.Builder {
        add("/* id= */ \$L,\n", policy.name)
        add(
            "/* allowedScopes= */ \$L,\n",
            generateSetBuilder(policy.allowedScopesList.map { it.number })
        )
        add("/* affectedResource= */ \$L,\n", policy.affectedResource.number)

        if (policy.requiredPermission.isEmpty()) {
            add("/* requiredPermission= */ null,\n")
        } else {
            add("/* requiredPermission= */ \$S,\n", policy.requiredPermission)
        }

        if (policy.requiredCrossUserPermission.isEmpty()) {
            add("/* requiredCrossUserPermission= */ null")
        } else {
            add("/* requiredCrossUserPermission= */ \$S", policy.requiredCrossUserPermission)
        }

        return this
    }

    private fun genericPolicyAdder(policy: PolicyMetadata, type: ClassName) =
        CodeBlock.builder()
            .add("policies.add(new \$T(\n", type)
            .indent()
            .addPolicyArguments(policy)
            .add("\n")
            .unindent()
            .addStatement("))")
            .build()

    private val enumPolicyMetadataType: ClassName =
        ClassName.get(METADATA_PACKAGE, "EnumPolicyMetadata")

    private fun generateEnumPolicyAdder(policy: PolicyMetadata) =
        CodeBlock.builder()
            .add("policies.add(new \$T(\n", enumPolicyMetadataType)
            .indent()
            .addPolicyArguments(policy)
            .add(",\n")
            .add("/* allowedValues= */ \$L\n",
                generateSetBuilder(
                    policy.typeSpecificMetadata.enumMetadata.valuesList.map { it.intValue }
                )
            )
            .unindent()
            .addStatement("))")
            .build()

    private val booleanPolicyMetadataType =
        ClassName.get(METADATA_PACKAGE, "BooleanPolicyMetadata")

    private fun generateBooleanPolicyAdder(policy: PolicyMetadata): CodeBlock =
        genericPolicyAdder(policy, booleanPolicyMetadataType)

    private val integerPolicyMetadataType =
        ClassName.get(METADATA_PACKAGE, "IntegerPolicyMetadata")

    private fun generateIntegerPolicyAdder(policy: PolicyMetadata) =
        genericPolicyAdder(policy, integerPolicyMetadataType)

    private val setType = ClassName.get(Set::class.java)
    private val listType = ClassName.get(List::class.java)
    private val arrayListType = ClassName.get(ArrayList::class.java)
    private val policyMetadataType = ParameterizedTypeName.get(
        ClassName.get(METADATA_PACKAGE, "PolicyMetadata"),
        WildcardTypeName.subtypeOf(ClassName.get(Object::class.java))
    )
    private val listOfPolicyMetadataType = ParameterizedTypeName.get(
        listType, policyMetadataType
    )
    private val arrayListOfPolicyMetadataType = ParameterizedTypeName.get(
        arrayListType, policyMetadataType
    )
}