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
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.type.DeclaredType

/**
 * Process elements with @PolicyDefinition.
 */
class PolicyAnnotationProcessor(processingEnv: ProcessingEnvironment) : Processor(processingEnv) {
    private companion object {
        const val POLICY_IDENTIFIER = "android.app.admin.PolicyIdentifier"
    }

    val booleanProcessor = BooleanProcessor(processingEnv)
    val enumProcessor = EnumProcessor(processingEnv)

    val policyIdentifierElem =
        processingEnv.elementUtils.getTypeElement(POLICY_IDENTIFIER) ?: throw IllegalStateException(
            "Could not find $POLICY_IDENTIFIER"
        )

    /** Represents a android.app.admin.PolicyIdentifier<T> */
    val policyIdentifierType = policyIdentifierElem.asType()
        ?: throw IllegalStateException("Could not get type of $POLICY_IDENTIFIER")

    /** Represents a android.app.admin.PolicyIdentifier<?> */
    val genericPolicyIdentifierType = processingEnv.typeUtils.getDeclaredType(
        policyIdentifierElem, processingEnv.typeUtils.getWildcardType(null, null)
    ) ?: throw IllegalStateException("Could not get generic type of $POLICY_IDENTIFIER")

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into useful data.
     *
     * @return All data about the policy or null on error and reports the error to the user.
     */
    fun process(element: Element): Policy? {
        element.getAnnotation(PolicyDefinition::class.java)
            ?: throw IllegalArgumentException("Element $element does not have the @PolicyDefinition annotation")

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
                element, "Only expected 1 type parameter in $elementType"
            )
            return null
        }

        val policyType = policyType(element)

        // Temporary check until the API is rolled out. Later other module should be able to use @PolicyDefinition.
        if (!processingEnv.typeUtils.isAssignable(enclosingType, genericPolicyIdentifierType)) {
            printError(
                element,
                "@PolicyDefinition can only be applied to fields in $policyIdentifierType, it was applied to a field in $enclosingType."
            )
        }

        val allMetadata = listOfNotNull(
            booleanProcessor.process(element), enumProcessor.process(element)
        )

        if (allMetadata.isEmpty()) {
            printError(
                element, "@PolicyDefinition has no type specific definition."
            )
            return null
        }

        if (allMetadata.size > 1) {
            printError(
                element, "@PolicyDefinition must only have one type specific annotation."
            )
            return null
        }

        val name = "$enclosingType.$element"
        val documentation = processingEnv.elementUtils.getDocComment(element)
        val type = policyType.toString()
        val metadata = allMetadata.first()

        return Policy(name, type, documentation, metadata)
    }
}

data class Policy(
    val name: String, val type: String, val documentation: String, val metadata: PolicyMetadata
) {
    fun dump(writer: JsonWriter) {
        writer.apply {
            beginObject()

            name("name")
            value(name)

            name("type")
            value(type)

            name("documentation")
            value(documentation)

            metadata.dump(writer)

            endObject()
        }
    }
}

abstract class PolicyMetadata() {
    abstract fun dump(writer: JsonWriter)
}

fun dumpJSON(writer: JsonWriter, items: List<Policy>) {
    writer.beginArray()

    items.forEachIndexed { index, policy ->
        policy.dump(writer)
    }

    writer.endArray()
}