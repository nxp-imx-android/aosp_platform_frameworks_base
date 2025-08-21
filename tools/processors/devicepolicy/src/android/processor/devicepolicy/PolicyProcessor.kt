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
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewArrayTree
import com.sun.source.util.SimpleTreeVisitor
import com.sun.source.util.Trees
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.FilerException
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.StandardLocation
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.first

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
 *     <li> For enums: all options and their documentation. </li>
 * </ul>
 *
 * Data is exported to `policies.json`.
 */
class PolicyProcessor : AbstractProcessor() {
    companion object {
        const val POLICY_IDENTIFIER = "android.app.admin.PolicyIdentifier"
        const val SIMPLE_TYPE_BOOLEAN = "java.lang.Boolean"
        const val SIMPLE_TYPE_INTEGER = "java.lang.Integer"

        /**
         * Find the first value matching a predicate on the key.
         */
        private fun <K, V> Map<K, V>.firstValue(filter: (K) -> Boolean): V {
            return entries.first { (key, _) -> filter(key) }.value
        }
    }

    /** Represents a android.app.admin.PolicyIdentifier<T> */
    lateinit var policyIdentifierType: TypeMirror

    /** Represents a android.app.admin.PolicyIdentifier<?> */
    lateinit var genericPolicyIdentifierType: TypeMirror

    /** Represents a built-in Boolean */
    lateinit var booleanType: TypeMirror

    /** Represents a built-in Integer */
    lateinit var integerType: TypeMirror

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    // Define what the annotation we care about are for compiler optimization
    override fun getSupportedAnnotationTypes() = LinkedHashSet<String>().apply {
        add(PolicyDefinition::class.java.name)
    }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        val policyIdentifierElem = processingEnv.elementUtils.getTypeElement(POLICY_IDENTIFIER)
            ?: throw IllegalStateException("Could not find $POLICY_IDENTIFIER")

        policyIdentifierType = policyIdentifierElem.asType()
            ?: throw IllegalStateException("Could not get type of $POLICY_IDENTIFIER")

        genericPolicyIdentifierType = processingEnv.typeUtils.getDeclaredType(
            policyIdentifierElem, processingEnv.typeUtils.getWildcardType(null, null)
        )

        booleanType = processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_BOOLEAN).asType()
        integerType = processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_INTEGER).asType()
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
                element, "Only expected 1 type parameter in $elementType"
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

        element.getAnnotation(PolicyDefinition::class.java)

        val allMetadata = listOfNotNull(
            extractBooleanMetadata(element, policyType), extractEnumMetadata(element, policyType)
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

    private fun extractBooleanMetadata(
        element: Element, policyType: TypeMirror
    ): BooleanPolicyMetadata? {
        element.getAnnotation(BooleanPolicyDefinition::class.java) ?: return null

        if (!processingEnv.typeUtils.isSameType(policyType, booleanType)) {
            printError(
                element,
                "booleanValue in @PolicyDefinition can only be applied to policies of type $booleanType."
            )
        }

        return BooleanPolicyMetadata()
    }

    private fun extractEnumMetadata(element: Element, policyType: TypeMirror): EnumPolicyMetadata? {
        val enumPolicyAnnotation =
            element.getAnnotation(EnumPolicyDefinition::class.java) ?: return null

        if (!processingEnv.typeUtils.isSameType(policyType, integerType)) {
            printError(
                element,
                "@EnumPolicyDefinition can only be applied to policies of type $integerType."
            )
        }

        val intDefElement = getIntDefElement(element)

        val intDefClass = processingEnv.elementUtils.getTypeElement("android.annotation.IntDef")
        val annotationMirror =
            intDefElement.annotationMirrors.firstOrNull { it.annotationType.asElement() == intDefClass }

        if (annotationMirror == null) {
            printError(
                element, "@EnumPolicyDefinition.intDef must be the interface marked with @IntDef."
            )

            return null
        }

        val enumName = intDefElement.qualifiedName.toString()
        val enumDoc = processingEnv.elementUtils.getDocComment(intDefElement)

        val entries = getIntDefIdentifiers(annotationMirror, intDefElement)

        return EnumPolicyMetadata(
            enumPolicyAnnotation.defaultValue, enumName, enumDoc, entries
        )
    }

    /**
     * Given a policy definition element finds the type element representing the IntDef definition
     * Same as `processingEnv.elementUtils.getTypeElement(enumPolicyMetadata.intDef.qualifiedName)`,
     * but we have to use type mirrors.
     */
    private fun getIntDefElement(element: Element): TypeElement {
        val am = element.annotationMirrors.first {
            it.annotationType.toString() == EnumPolicyDefinition::class.java.name
        }
        val av = am.elementValues.firstValue { key ->
            key.simpleName.toString() == "intDef"
        }
        val mirror = av.value as TypeMirror
        return processingEnv.typeUtils.asElement(mirror) as TypeElement
    }

    private fun getIntDefIdentifiers(
        annotationMirror: AnnotationMirror, intDefElement: TypeElement
    ): List<EnumEntryMetadata> {
        val annotationValue: AnnotationValue =
            annotationMirror.elementValues.firstValue { key ->
                key.simpleName.contentEquals("value")
            }

        // Walk the AST as we want the actual identifiers passed to @IntDef.
        val trees = Trees.instance(processingEnv)
        val tree = trees.getTree(intDefElement, annotationMirror, annotationValue)

        val identifiers = ArrayList<String>()
        tree.accept(IdentifierVisitor(), identifiers)

        @Suppress("UNCHECKED_CAST") val values = annotationValue.value as List<AnnotationValue>

        val documentations = identifiers.map { identifier ->
            val identifierElement = intDefElement.enclosingElement.enclosedElements.find {
                it.simpleName.toString() == identifier
            }

            processingEnv.elementUtils.getDocComment(identifierElement)
        }

        return identifiers.mapIndexed { i, identifier ->
            EnumEntryMetadata(identifier, values[i].value as Int, documentations[i])
        }
    }

    private class IdentifierVisitor : SimpleTreeVisitor<Void, ArrayList<String>>() {
        override fun visitNewArray(node: NewArrayTree, identifiers: ArrayList<String>): Void? {
            for (initializer in node.initializers) {
                initializer.accept(this, identifiers)
            }

            return null
        }

        /**
         * Called when the identifier used in IntDef is a member of the class.
         */
        override fun visitMemberSelect(
            node: MemberSelectTree, identifiers: ArrayList<String>
        ): Void? {
            identifiers.add(node.identifier.toString())

            return null
        }

        /**
         * Called when the identifier in IntDef is an arbitrary identifier pointing outside the
         * current class.
         */
        override fun visitIdentifier(node: IdentifierTree, identifiers: ArrayList<String>): Void? {
            identifiers.add(node.name.toString())

            return null
        }
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