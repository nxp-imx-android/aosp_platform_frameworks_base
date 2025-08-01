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

import com.android.json.stream.JsonWriter
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.FilerException
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.StandardLocation

/**
 * PolicyProcessor processes all {@link PolicyDefinition} instances:
 * <ol>
 *     <li> Verify that the policies are well formed. </li>
 *     <li> Exports the data for consumption by other tools. </li>
 * </ol>
 *
 * Currently the data exported contains:
 * <ul>
 *     <li> The name of the field. </li>
 *     <li> The type of the policy. </li>
 *     <li> The JavaDoc for the policy. </li>
 * </ul>
 *
 * Data is exported to `policies.json`.
 */
class PolicyProcessor : AbstractProcessor() {
    companion object {
        const val POLICY_IDENTIFIER = "android.app.admin.PolicyIdentifier"
        const val SIMPLE_TYPE_BOOLEAN = "java.lang.Boolean"
    }

    /** Represents a android.app.admin.PolicyIdentifier<T> */
    lateinit var policyIdentifierType: TypeMirror

    /** Represents a android.app.admin.PolicyIdentifier<?> */
    lateinit var genericPolicyIdentifierType: TypeMirror

    /** Represents a built-in Boolean */
    lateinit var booleanType: TypeMirror

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    // Define what the annotation we care about are for compiler optimization
    override fun getSupportedAnnotationTypes() = LinkedHashSet<String>().apply {
        add(PolicyDefinition::class.java.name)
    }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        val policyIdentifierElem =
            processingEnv.elementUtils.getTypeElement(POLICY_IDENTIFIER)
                ?: throw IllegalStateException("Could not find $POLICY_IDENTIFIER")

        policyIdentifierType =
            policyIdentifierElem.asType()
                ?: throw IllegalStateException("Could not get type of $POLICY_IDENTIFIER")

        genericPolicyIdentifierType = processingEnv.typeUtils.getDeclaredType(
            policyIdentifierElem, processingEnv.typeUtils.getWildcardType(null, null)
        )

        booleanType = processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_BOOLEAN).asType()
    }

    override fun process(
        annotations: MutableSet<out TypeElement>, roundEnvironment: RoundEnvironment
    ): Boolean {
        val policies =
            roundEnvironment.getElementsAnnotatedWith(PolicyDefinition::class.java).mapNotNull {
                extractPolicy(it)
            }

        try {
            writePolicies(roundEnvironment, policies)
        } catch (e: FilerException) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "Policies already written, not overwriting: $e",
            )

            return false
        }

        return false
    }

    fun writePolicies(roundEnvironment: RoundEnvironment, policies: List<Policy>) {
        val writer = createWriter(roundEnvironment)
        writer.setIndent("  ")

        writer.beginObject()
        writer.name("policies")
        dumpJSON(writer, policies)
        writer.endObject()

        writer.close()
    }

    fun createWriter(roundEnvironment: RoundEnvironment): JsonWriter {
        return JsonWriter(
            processingEnv.filer.createResource(
                StandardLocation.SOURCE_OUTPUT, "android.processor.devicepolicy", "policies.json"
            ).openWriter()
        )
    }

    fun extractPolicy(element: Element): Policy? {
        if (element.kind != ElementKind.FIELD) {
            printError(element, "@PolicyDefinition can only be applied to fields")
            return null
        }

        val elementType = element.asType() as DeclaredType
        val enclosingType = element.enclosingElement.asType()

        if (!processingEnv.typeUtils.isAssignable(elementType, genericPolicyIdentifierType)) {
            printError(
                element,
                "@PolicyDefinition can only be applied to $policyIdentifierType, it was applied to $elementType."
            )
            return null
        }

        if (elementType.typeArguments.size != 1) {
            printError(
                element,
                "Only expected 1 type parameter in $elementType"
            )
            return null
        }

        val policyType = elementType.typeArguments[0]

        // Temporary check until the API is rolled out. Later other module should be able to use @PolicyDefinition.
        if (!processingEnv.typeUtils.isAssignable(enclosingType, genericPolicyIdentifierType)) {
            printError(
                element,
                "@PolicyDefinition can only be applied to fields in $policyIdentifierType, it was applied to a field in $enclosingType."
            )
        }

        val annotation = element.getAnnotation(PolicyDefinition::class.java)

        var metadata: PolicyMetadata? = null

        val booleanPolicyMetadata = element.getAnnotation(BooleanPolicyDefinition::class.java)
        if (booleanPolicyMetadata != null) {
            if (!processingEnv.typeUtils.isSameType(policyType, booleanType)) {
                printError(
                    element,
                    "booleanValue in @PolicyDefinition can only be applied to policies of type $booleanType."
                )
            }

            metadata = BooleanPolicyMetadata()
        }

        if (metadata == null) {
            printError(
                element,
                "@PolicyDefinition has no type specific definition."
            )
            return null
        }

        val name = "$enclosingType.$element"
        val documentation = processingEnv.elementUtils.getDocComment(element)
        val type = policyType.toString()

        return Policy(name, type, documentation, metadata)
    }

    /**
     * Print an error and make compilation fail.
     */
    fun printError(element: Element, message: String) {
        processingEnv.messager.printMessage(
            Diagnostic.Kind.ERROR,
            message,
            element,
        )
    }
}