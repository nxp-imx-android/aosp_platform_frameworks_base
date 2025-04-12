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

package com.android.systemfeatures

import com.android.tools.metalava.model.text.ApiFile
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import java.io.File
import javax.lang.model.element.Modifier

/*
 * Simple Java code generator that takes as input a list of Metalava API files and generates an
 * accessory class that maps from PackageManager system feature variable values to their
 * declared PackageManager variable names. This is needed for host tooling that cannot depend
 * directly on the base framework lib/srcs.
 *
 * <pre>
 * package com.android.systemfeatures;
 * public final class SystemFeaturesLookup {
 *     // Gets the declared system feature var name from its string value.
 *     // Example: "android.software.print" -> "FEATURE_PRINTING"
 *     public static String getDeclaredFeatureVarNameFromValue(String featureVarValue);
 * }
 * </pre>
 */
object SystemFeaturesLookupGenerator {

    /** Main entrypoint for system feature constant lookup codegen. */
    @JvmStatic
    fun main(args: Array<String>) {
        generate(args.asIterable(), System.out)
    }

    /**
     * Simple API entrypoint for system feature lookup codegen.
     *
     * Given a list of Metalava API files, pipes a generated SystemFeaturesLookup class into output.
     */
    @JvmStatic
    fun generate(apiFilePaths: Iterable<String>, output: Appendable) {
        val featuresMap = parse(apiFilePaths)

        val stringClassName = ClassName.get(String::class.java)

        val featureLookupMethod =
            MethodSpec.methodBuilder("getDeclaredFeatureVarNameFromValue")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(ClassName.get("android.annotation", "Nullable"))
                .addJavadoc("Gets the declared system feature var name from its string value.")
                .addJavadoc("\n\nExample: \"android.software.print\" -> \"FEATURE_PRINTING\"")
                .addJavadoc("\n\n@hide")
                .returns(stringClassName)
                .addParameter(stringClassName, "featureVarValue")
                .beginControlFlow("switch (featureVarValue)")
                .apply {
                    featuresMap.forEach { (key, value) ->
                        addStatement("case \$S: return \$S", key, value)
                    }
                }
                .addStatement("default: return null")
                .endControlFlow()
                .build()

        val outputClassName = ClassName.get("com.android.systemfeatures", "SystemFeaturesLookup")
        val systemFeaturesApiLookupClass =
            TypeSpec.classBuilder(outputClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(featureLookupMethod)
                .addJavadoc("@hide")
                .build()

        JavaFile.builder(outputClassName.packageName(), systemFeaturesApiLookupClass)
            .indent("    ")
            .skipJavaLangImports(true)
            .addFileComment("This file is auto-generated. DO NOT MODIFY.\n")
            .build()
            .writeTo(output)
    }

    /**
     * Given a list of Metalava API files, extracts a mapping from all @SdkConstantType.FEATURE
     * PackageManager values to their declared variable names, e.g.,
     * - "android.hardware.type.automotive" -> "FEATURE_AUTOMOTIVE"
     * - "android.software.print" -> "FEATURE_PRINTING"
     */
    @JvmStatic
    fun parse(apiFilePaths: Iterable<String>): Map<String, String> {
        return ApiFile.parseApi(apiFilePaths.map(::File))
            .findClass("android.content.pm.PackageManager")
            ?.fields()
            ?.filter { field ->
                field.type().isString() &&
                    field.modifiers.isStatic() &&
                    field.modifiers.isFinal() &&
                    field.name().startsWith("FEATURE_") &&
                    field.legacyInitialValue() != null
            }
            ?.associateBy({ it.legacyInitialValue()!!.toString() }, { it.name() }) ?: emptyMap()
    }
}
