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

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic

open class Processor(protected val processingEnv: ProcessingEnvironment) {
    /**
     * Given an element that represents a PolicyIdentifier field, get the type of the policy.
     */
    protected fun policyType(element: Element): TypeMirror {
        val elementType = element.asType() as DeclaredType

        if (elementType.typeArguments.size != 1) {
            printError(
                element, "Only expected 1 type parameter in $elementType"
            )

            throw IllegalArgumentException("Element $element is not a policy")
        }

        return elementType.typeArguments[0]
    }

    /**
     * Print an error and make compilation fail.
     */
    protected fun printError(element: Element, message: String) {
        processingEnv.messager.printMessage(
            Diagnostic.Kind.ERROR,
            message,
            element,
        )
    }
}