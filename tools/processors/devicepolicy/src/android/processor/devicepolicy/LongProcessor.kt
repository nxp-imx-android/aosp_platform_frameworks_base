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

package android.processor.devicepolicy

import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Process elements with @LongPolicyDefinition.
 *
 * Since this annotation holds no data and we don't export any type-specific information, this only
 * contains type-specific checks.
 */
class LongProcessor(processingEnv: ProcessingEnvironment) :
    PolicyProcessor<LongPolicyDefinition>(processingEnv) {
    private companion object {
        const val SIMPLE_TYPE_LONG = "java.lang.Long"
    }

    /** Represents a built-in Long */
    val longType: TypeMirror = processingEnv.elementUtils.getTypeElement(SIMPLE_TYPE_LONG).asType()

    /**
     * Process an {@link Element} representing a {@link android.app.admin.PolicyIdentifier} into
     * useful data.
     *
     * @return null on error, {@link LongPolicyMetadata} otherwise.
     */
    override fun processMetadata(
        element: Element
    ): Pair<TypeSpecificPolicyMetadata, PolicyDefinition>? {
        val longDefinition =
            element.getAnnotation(LongPolicyDefinition::class.java)
                ?: throw IllegalStateException(
                    "Processor should only be called on elements with @LongPolicyDefinition"
                )

        val actualType = policyType(element)
        if (!processingEnv.typeUtils.isSameType(actualType, longType)) {
            printError(
                element,
                "@LongPolicyDefinition can only be applied to policies of type $longType, but got $actualType.",
            )
        }

        val longMetadataBuilder =
            TypeSpecificPolicyMetadata.LongPolicyMetadata.newBuilder()
        if (longDefinition.minValue != Long.MIN_VALUE) {
            longMetadataBuilder.setMinValue(longDefinition.minValue)
        }
        if (longDefinition.maxValue != Long.MAX_VALUE) {
            longMetadataBuilder.setMaxValue(longDefinition.maxValue)
        }

        val typeSpecificMetadata =
            TypeSpecificPolicyMetadata.newBuilder()
                .setLongMetadata(longMetadataBuilder)
                .build()

        return Pair(typeSpecificMetadata, longDefinition.base)
    }

    override fun annotationClass(): Class<LongPolicyDefinition> {
        return LongPolicyDefinition::class.java
    }
}
