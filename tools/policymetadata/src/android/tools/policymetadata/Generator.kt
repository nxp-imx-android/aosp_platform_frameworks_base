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

package android.tools.policymetadata

import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadataList
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata.ResolutionMechanism as EnumResolutionMechanismProto
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.IntegerPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.ListPolicyMetadata.ListElementMetadataCase
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.LongPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.StringPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.TypeMetadataCase
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
object Generator {
    /**
     * Add the copyright header to keep the linter happy. We have a golden copy of the generated
     * file checked in.
     */
    fun addLicense(writer: Appendable) {
        writer.append(
            """
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
            """
                .trimIndent()
        )
        writer.append("\n\n")
    }

    fun generate(policies: PolicyMetadataList): JavaFile {
        val policiesClass =
            TypeSpec.classBuilder("Policies")
                .addModifiers(Modifier.PUBLIC)
                .addMethod(generateLoadPolicyMetadata(policies))
                .addJavadoc(
                    """
                    Generated class that contains metadata on all known policies.

                    @hide
                    """
                        .trimIndent()
                )
                .build()

        val javaFile =
            JavaFile.builder(METADATA_PACKAGE, policiesClass)
                .indent("    ")
                .addPolicyIdentifierImports(policies)
                .build()

        return javaFile
    }

    private fun JavaFile.Builder.addPolicyIdentifierImports(
        policies: PolicyMetadataList
    ): JavaFile.Builder {
        policies.policyMetadataList
            .map {
                ClassName.get(it.identifier.packageName, it.identifier.className) to
                    it.identifier.fieldName
            }
            .groupBy({ it.first }, { it.second })
            .forEach { (className, fieldNames) ->
                addStaticImport(className, *fieldNames.toTypedArray())
            }

        return this
    }

    private fun generateLoadPolicyMetadata(policies: PolicyMetadataList): MethodSpec {
        val loadPolicyMetadataMethod =
            MethodSpec.methodBuilder("loadPolicyMetadata")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(listOfPolicyMetadataType)
                .addStatement(
                    "\$T policies = new \$T()",
                    listOfPolicyMetadataType,
                    arrayListOfPolicyMetadataType,
                )
                .addJavadoc("Generated method that returns a list of all policy metadata")

        for (policy in policies.policyMetadataList) {
            loadPolicyMetadataMethod.addCode(generatePolicyAdder(policy))
        }

        loadPolicyMetadataMethod.addStatement("return policies")

        return loadPolicyMetadataMethod.build()
    }

    // Returns a CodeBlock containing `policies.add(new MyTypePolicyMetadata(<snip arguments>));`
    private fun generatePolicyAdder(policy: PolicyMetadata): CodeBlock =
        CodeBlock.builder()
            .add("policies.add(")
            .add(generatePolicyMetadata(policy))
            .addStatement(")")
            .build()

    // Returns a CodeBlock containing `new MyTypePolicyMetadata(....)`
    private fun generatePolicyMetadata(policy: PolicyMetadata): CodeBlock =
        when (policy.typeSpecificMetadata.typeMetadataCase) {
            TypeMetadataCase.ENUM_METADATA -> generateEnumPolicyMetadata(policy)
            TypeMetadataCase.BOOLEAN_METADATA -> generateBooleanPolicyMetadata(policy)
            TypeMetadataCase.INTEGER_METADATA -> generateIntegerPolicyMetadata(policy)
            TypeMetadataCase.LONG_METADATA -> generateLongPolicyMetadata(policy)
            TypeMetadataCase.STRING_METADATA -> generateStringPolicyMetadata(policy)
            TypeMetadataCase.LIST_METADATA -> generateListPolicyMetadata(policy)
            TypeMetadataCase.TYPEMETADATA_NOT_SET ->
                throw IllegalArgumentException("Type specific metadata unset")
        }

    private fun generateSetBuilder(values: List<Int>): CodeBlock {
        if (values.isEmpty()) {
            return CodeBlock.builder().add("\$T.of()", setType).build()
        }
        val builder = CodeBlock.builder()

        builder.add("\$T.of(\n", setType).indent()

        for (value in values.dropLast(1)) {
            builder.add("\$L,\n", value)
        }
        builder.add("\$L\n", values.last())

        builder.unindent().add(")")

        return builder.build()
    }

    private fun generateTaggedSetBuilder(values: List<Pair<Int, String>>): CodeBlock {
        if (values.isEmpty()) {
            return CodeBlock.builder().add("\$T.of()", setType).build()
        }
        val builder = CodeBlock.builder()

        builder.add("\$T.of(\n", setType).indent()

        for ((value, tag) in values.dropLast(1)) {
            builder.add("\$L, // \$L\n", value, tag)
        }
        builder.add("\$L  // \$L\n", values.last().first, values.last().second)

        builder.unindent().add(")")

        return builder.build()
    }

    private fun PolicyMetadata.getPolicyIdCodeBlock() =
        CodeBlock.of("\$L", this.identifier.fieldName.substringAfterLast("."))

    private fun CodeBlock.Builder.addPolicyId(name: String) =
        this.add("/* id= */ \$L,\n", name.substringAfterLast("."))

    private fun CodeBlock.Builder.addPolicyId(nameGenerator: CodeBlock) =
        this.add("/* id= */ \$L,\n", nameGenerator)

    private fun CodeBlock.Builder.addPolicyInformation(policy: PolicyMetadata): CodeBlock.Builder {
        add(
            "/* allowedScopes= */ \$L,\n",
            generateSetBuilder(policy.allowedScopesList.map { it.number }),
        )
        add("/* affectedResource= */ \$L,\n", policy.affectedResource.number)

        if (policy.requiredPermission.isEmpty()) {
            add("/* requiredPermission= */ null,\n")
        } else {
            add("/* requiredPermission= */ \$S,\n", policy.requiredPermission)
        }

        if (policy.requiredCrossUserPermission.isEmpty()) {
            add("/* requiredCrossUserPermission= */ null,\n")
        } else {
            add("/* requiredCrossUserPermission= */ \$S,\n", policy.requiredCrossUserPermission)
        }
        add(
            "/* allowedDpcTypes= */ \$L",
            generateTaggedSetBuilder(
                policy.allowedDpcTypesList.map {
                    // Remove the DPC_TYPE_ prefix that is present in the proto but
                    // not in the IntDef.
                    it.number to it.name.removePrefix("DPC_TYPE_")
                }
            ),
        )

        return this
    }

    private fun CodeBlock.Builder.addPolicyArguments(
        policy: PolicyMetadata,
        policyId: CodeBlock = policy.getPolicyIdCodeBlock(),
    ): CodeBlock.Builder = this.addPolicyId(policyId).addPolicyInformation(policy)

    private val enumPolicyMetadataType: ClassName =
        ClassName.get(METADATA_PACKAGE, "EnumPolicyMetadata")

    // Returns a CodeBlock containing `new EnumPolicyMetadata(<...>)`
    private fun generateEnumPolicyMetadata(
        policy: PolicyMetadata,
        enumMetadata: EnumPolicyMetadata = policy.typeSpecificMetadata.enumMetadata,
        policyId: CodeBlock = policy.getPolicyIdCodeBlock(),
    ) =
        CodeBlock.builder()
            .add("new \$T(\n", enumPolicyMetadataType)
            .indent()
            .addPolicyArguments(policy, policyId)
            .add(",\n")
            .add(
                "/* resolutionMechanism= */ \$L,\n",
                generateEnumResolutionMechanism(enumMetadata.resolutionMechanism),
            )
            .add(
                "/* allowedValues= */ \$L\n",
                generateSetBuilder(enumMetadata.valuesList.map { it.intValue }),
            )
            .unindent()
            .add(")")
            .build()

    private val booleanPolicyMetadataType = ClassName.get(METADATA_PACKAGE, "BooleanPolicyMetadata")

    // Returns a CodeBlock containing `new BooleanPolicyMetadata(<...>)`
    private fun generateBooleanPolicyMetadata(
        policy: PolicyMetadata,
        policyId: CodeBlock = policy.getPolicyIdCodeBlock(),
    ): CodeBlock =
        CodeBlock.builder()
            .add("new \$T(\n", booleanPolicyMetadataType)
            .indent()
            .addPolicyArguments(policy, policyId)
            .add("\n")
            .unindent()
            .add(")")
            .build()

    private val integerPolicyMetadataType = ClassName.get(METADATA_PACKAGE, "IntegerPolicyMetadata")

    private fun generateIntegerPolicyMetadata(
        policy: PolicyMetadata,
        policyId: CodeBlock = policy.getPolicyIdCodeBlock(),
        integerMetadata: IntegerPolicyMetadata = policy.typeSpecificMetadata.integerMetadata,
    ): CodeBlock {
        val builder =
            CodeBlock.builder()
                .add("new \$T(\n", integerPolicyMetadataType)
                .indent()
                .addPolicyArguments(policy, policyId)
                .add(",\n")
        val minValue =
            if (integerMetadata.hasMinValue()) {
                CodeBlock.of("\$L", integerMetadata.minValue)
            } else {
                CodeBlock.of("Integer.MIN_VALUE")
            }
        val maxValue =
            if (integerMetadata.hasMaxValue()) {
                CodeBlock.of("\$L", integerMetadata.maxValue)
            } else {
                CodeBlock.of("Integer.MAX_VALUE")
            }
        builder.add("/* minValue= */ \$L,\n", minValue)
        builder.add("/* maxValue= */ \$L", maxValue)
        builder.add("\n")
        builder.unindent().add(")")
        return builder.build()
    }

    private val longPolicyMetadataType = ClassName.get(METADATA_PACKAGE, "LongPolicyMetadata")

    private fun generateLongPolicyMetadata(
        policy: PolicyMetadata,
        policyId: CodeBlock = policy.getPolicyIdCodeBlock(),
        longMetadata: LongPolicyMetadata = policy.typeSpecificMetadata.longMetadata,
    ): CodeBlock {
        val builder =
            CodeBlock.builder()
                .add("new \$T(\n", longPolicyMetadataType)
                .indent()
                .addPolicyArguments(policy, policyId)
                .add(",\n")
        val minValue =
            if (longMetadata.hasMinValue()) {
                CodeBlock.of("\$LL", longMetadata.minValue)
            } else {
                CodeBlock.of("Long.MIN_VALUE")
            }
        val maxValue =
            if (longMetadata.hasMaxValue()) {
                CodeBlock.of("\$LL", longMetadata.maxValue)
            } else {
                CodeBlock.of("Long.MAX_VALUE")
            }
        builder.add("/* minValue= */ \$L,\n", minValue)
        builder.add("/* maxValue= */ \$L", maxValue)
        builder.add("\n")
        builder.unindent().add(")")
        return builder.build()
    }

    private val stringPolicyMetadataType = ClassName.get(METADATA_PACKAGE, "StringPolicyMetadata")

    private fun CodeBlock.Builder.addStringMetadataInformation(
        stringMetadata: StringPolicyMetadata
    ) =
        this.add("/* emptyStringAllowed= */ \$L,\n", stringMetadata.emptyStringAllowed)
            .add(
                "/* unprintableCharactersAllowed= */ \$L",
                stringMetadata.unprintableCharactersAllowed,
            )

    // Returns a CodeBlock containing `new StringPolicyMetadata(<policy-id>, ....)` .
    private fun generateStringPolicyMetadata(
        policy: PolicyMetadata,
        stringMetadata: StringPolicyMetadata = policy.typeSpecificMetadata.stringMetadata,
        policyId: CodeBlock = policy.getPolicyIdCodeBlock(),
    ) =
        CodeBlock.builder()
            .add("new \$T(\n", stringPolicyMetadataType)
            .indent()
            .addPolicyArguments(policy, policyId)
            .add(",\n")
            .addStringMetadataInformation(stringMetadata)
            .add("\n")
            .unindent()
            .add(")")
            .build()

    private val listPolicyMetadataType = ClassName.get(METADATA_PACKAGE, "ListPolicyMetadata")

    // Returns a CodeBlock containing `new ListPolicyMetadata<TYPE>(....)`
    private fun generateListPolicyMetadata(policy: PolicyMetadata): CodeBlock {
        val elementType = getListElementType(policy)
        val elementMetadata = generateListPolicyElementMetadata(policy)
        return CodeBlock.builder()
            .add("new \$T(\n", ParameterizedTypeName.get(listPolicyMetadataType, elementType))
            .indent()
            .addPolicyId(policy.identifier.fieldName)
            .add("/* elementMetadata= */ ")
            .add(elementMetadata)
            .add(",\n")
            .add(
                "/* emptyListAllowed= */ \$L\n",
                policy.typeSpecificMetadata.listMetadata.emptyListAllowed,
            )
            .unindent()
            .add(")")
            .build()
    }

    // Returns a CodeBlock containing the policy metadata for a list element
    //    new ElementTypePolicyMetadata(<element-policy-id>, ...)
    private fun generateListPolicyElementMetadata(policy: PolicyMetadata): CodeBlock {
        val policyId = policy.generateListElementPolicyId(getListElementType(policy))
        return when (policy.typeSpecificMetadata.listMetadata.listElementMetadataCase) {
            ListElementMetadataCase.ENUM_METADATA ->
                generateEnumPolicyMetadata(
                    policy,
                    policy.typeSpecificMetadata.listMetadata.enumMetadata,
                    policyId,
                )
            ListElementMetadataCase.INTEGER_METADATA ->
                generateIntegerPolicyMetadata(
                    policy,
                    policyId,
                    policy.typeSpecificMetadata.listMetadata.integerMetadata,
                )
            ListElementMetadataCase.STRING_METADATA ->
                generateStringPolicyMetadata(
                    policy,
                    policy.typeSpecificMetadata.listMetadata.stringMetadata,
                    policyId,
                )
            ListElementMetadataCase.LISTELEMENTMETADATA_NOT_SET ->
                throw IllegalArgumentException("List Element type specific metadata unset")
        }
    }

    private fun getListElementType(policy: PolicyMetadata): ClassName =
        when (policy.typeSpecificMetadata.listMetadata.listElementMetadataCase) {
            ListElementMetadataCase.ENUM_METADATA -> ClassName.get(Integer::class.javaObjectType)
            ListElementMetadataCase.INTEGER_METADATA -> ClassName.get(Integer::class.javaObjectType)
            ListElementMetadataCase.STRING_METADATA -> ClassName.get(String::class.java)
            ListElementMetadataCase.LISTELEMENTMETADATA_NOT_SET ->
                throw IllegalArgumentException("List Element type specific metadata unset")
        }

    private fun PolicyMetadata.generateListElementPolicyId(elementType: ClassName) =
        CodeBlock.builder()
            .add(
                "new \$T(\$L.getId() + \$S)",
                ParameterizedTypeName.get(policyIdentifierType, elementType),
                this.identifier.fieldName,
                "#elements",
            )
            .build()

    private fun generateEnumResolutionMechanism(proto: EnumResolutionMechanismProto): CodeBlock {
        return when (proto.mechanismCase) {
            EnumResolutionMechanismProto.MechanismCase.MOST_RESTRICTIVE ->
                generateMostRestrictiveResolutionMechanism(
                    proto.mostRestrictive.mostToLeastRestrictiveList
                )
            EnumResolutionMechanismProto.MechanismCase.CUSTOM -> CodeBlock.of("null")
            EnumResolutionMechanismProto.MechanismCase.MECHANISM_NOT_SET ->
                throw IllegalArgumentException("Resolution mechanism not set")
        }
    }

    private fun generateMostRestrictiveResolutionMechanism(values: List<Int>): CodeBlock {
        val parameterizedMostRestrictiveType =
            ParameterizedTypeName.get(mostRestrictiveType, integerType)

        val policyValues = values.map { CodeBlock.of("new \$T(\$L)", integerType, it) }

        return CodeBlock.builder()
            .add("new \$T(\n", parameterizedMostRestrictiveType)
            .indent()
            .add("\$T.of(\n", listType)
            .indent()
            .add(CodeBlock.join(policyValues, ",\n"))
            .add("\n")
            .unindent()
            .add(")\n")
            .unindent()
            .add(")")
            .build()
    }

    private val integerType = ClassName.get(Integer::class.javaObjectType)
    private val setType = ClassName.get(Set::class.java)
    private val listType = ClassName.get(List::class.java)
    private val arrayListType = ClassName.get(ArrayList::class.java)
    private val policyMetadataType =
        ParameterizedTypeName.get(
            ClassName.get(METADATA_PACKAGE, "PolicyMetadata"),
            WildcardTypeName.subtypeOf(ClassName.get(Object::class.java)),
        )
    private val listOfPolicyMetadataType = ParameterizedTypeName.get(listType, policyMetadataType)
    private val arrayListOfPolicyMetadataType =
        ParameterizedTypeName.get(arrayListType, policyMetadataType)

    private val policyIdentifierType = ClassName.get("android.app.admin", "PolicyIdentifier")
    private val mostRestrictiveType =
        ClassName.get(
            "android.app.admin.metadata",
            "ResolutionMechanismMetadata",
            "MostRestrictive",
        )
}
