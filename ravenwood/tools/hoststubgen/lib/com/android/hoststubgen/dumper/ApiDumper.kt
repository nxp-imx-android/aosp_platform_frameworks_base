/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.hoststubgen.dumper

import com.android.hoststubgen.asm.CLASS_INITIALIZER_NAME
import com.android.hoststubgen.asm.CTOR_NAME
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.getClassNameFromFullClassName
import com.android.hoststubgen.asm.getPackageNameFromFullClassName
import com.android.hoststubgen.asm.toHumanReadableClassName
import com.android.hoststubgen.csvEscape
import com.android.hoststubgen.filters.FilterPolicy
import com.android.hoststubgen.filters.FilterPolicyWithReason
import com.android.hoststubgen.filters.OutputFilter
import com.android.hoststubgen.filters.StatsLabel
import com.android.hoststubgen.log
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.io.PrintWriter

/**
 * Dump all the API methods in [classes], with inherited methods, with their policies.
 */
class ApiDumper(
    val pw: PrintWriter,
    val classes: ClassNodes,
    val frameworkClasses: ClassNodes?,
    val filter: OutputFilter,
) {
    private data class MethodKey(
        val name: String,
        val descriptor: String,
    )

    private val javaStandardApiPolicy = FilterPolicy.Keep.withReason(
        "Java standard API",
        StatsLabel.Supported,
    )

    private val shownMethods = mutableSetOf<MethodKey>()

    /**
     * Do the dump.
     */
    fun dump() {
        pw.printf("PackageName,ClassName,FromSubclass,DeclareClass,MethodName,MethodDesc" +
                ",Supported,Policy,Reason,SupportedLabel\n")

        classes.forEach { classNode ->
            shownMethods.clear()
            dump(classNode, classNode)
        }
    }

    private fun dumpMethod(
        classPackage: String,
        className: String,
        isSuperClass: Boolean,
        methodClassName: String,
        methodName: String,
        methodDesc: String,
        classPolicy: FilterPolicyWithReason,
        methodPolicy: FilterPolicyWithReason,
    ) {
        if (methodPolicy.statsLabel == StatsLabel.Ignored) {
            return
        }
        // Label hack -- if the method is supported, but the class is boring, then the
        // method is boring too.
        var methodLabel = methodPolicy.statsLabel
        if (methodLabel == StatsLabel.SupportedButBoring
            && classPolicy.statsLabel == StatsLabel.SupportedButBoring) {
            methodLabel = classPolicy.statsLabel
        }

        pw.printf(
            "%s,%s,%d,%s,%s,%s,%d,%s,%s,%s\n",
            csvEscape(classPackage),
            csvEscape(className),
            if (isSuperClass) { 1 } else { 0 },
            csvEscape(methodClassName),
            csvEscape(methodName),
            csvEscape(methodDesc),
            methodLabel.statValue,
            methodPolicy.policy,
            csvEscape(methodPolicy.reason),
            methodLabel,
        )
    }

    private fun shownAlready(methodName: String, methodDesc: String): Boolean {
        val methodKey = MethodKey(methodName, methodDesc)

        if (shownMethods.contains(methodKey)) {
            return true
        }
        shownMethods.add(methodKey)
        return false
    }

    private fun dump(
        dumpClass: ClassNode,
        methodClass: ClassNode,
        ) {
        val classPolicy = filter.getPolicyForClass(dumpClass.name)
        if (classPolicy.statsLabel == StatsLabel.Ignored) {
            return
        }
        log.d("Class ${dumpClass.name} -- policy $classPolicy")

        val pkg = getPackageNameFromFullClassName(dumpClass.name).toHumanReadableClassName()
        val cls = getClassNameFromFullClassName(dumpClass.name).toHumanReadableClassName()

        val isSuperClass = dumpClass != methodClass

        methodClass.methods?.sortedWith(compareBy({ it.name }, { it.desc }))?.forEach { method ->

            // Don't print ctor's from super classes.
            if (isSuperClass) {
                if (CTOR_NAME == method.name || CLASS_INITIALIZER_NAME == method.name) {
                    return@forEach
                }
            }
            // If we already printed the method from a subclass, don't print it.
            if (shownAlready(method.name, method.desc)) {
                return@forEach
            }

            val methodPolicy = filter.getPolicyForMethod(methodClass.name, method.name, method.desc)

            // Let's skip "Remove" APIs. Ideally we want to print it, just to make the CSV
            // complete, we still need to hide methods substituted (== @RavenwoodReplace) methods
            // and for now we don't have an easy way to detect it.
            if (methodPolicy.policy == FilterPolicy.Remove) {
                return@forEach
            }

            val renameTo = filter.getRenameTo(methodClass.name, method.name, method.desc)

            dumpMethod(pkg, cls, isSuperClass, methodClass.name.toHumanReadableClassName(),
                renameTo ?: method.name, method.desc, classPolicy, methodPolicy)
       }

        // Dump super class methods.
        dumpSuper(dumpClass, methodClass.superName)

        // Dump interface methods (which may have default methods).
        methodClass.interfaces?.sorted()?.forEach { interfaceName ->
            dumpSuper(dumpClass, interfaceName)
        }
    }

    /**
     * Dump a given super class / interface.
     */
    private fun dumpSuper(
        dumpClass: ClassNode,
        methodClassName: String,
    ) {
        classes.findClass(methodClassName)?.let { methodClass ->
            dump(dumpClass, methodClass)
            return
        }
        frameworkClasses?.findClass(methodClassName)?.let { methodClass ->
            dump(dumpClass, methodClass)
            return
        }

        // Dump overriding methods from Java standard classes, except for the Object methods,
        // which are obvious.
        if (methodClassName.startsWith("java/") || methodClassName.startsWith("javax/")) {
            if (methodClassName != "java/lang/Object") {
               dumpStandardClass(dumpClass, methodClassName)
            }
            return
        }
        log.w("Super class or interface $methodClassName (used by ${dumpClass.name}) not found.")
    }

    /**
     * Dump methods from Java standard classes.
     */
    private fun dumpStandardClass(
        dumpClass: ClassNode,
        methodClassName: String,
    ) {
        val pkg = getPackageNameFromFullClassName(dumpClass.name).toHumanReadableClassName()
        val cls = getClassNameFromFullClassName(dumpClass.name).toHumanReadableClassName()

        val methodClassName = methodClassName.toHumanReadableClassName()

        try {
            val clazz = Class.forName(methodClassName)

            // Method.getMethods() returns only public methods, but with inherited ones.
            // Method.getDeclaredMethods() returns private methods too, but no inherited methods.
            //
            // Since we're only interested in public ones, just use getMethods().
            clazz.methods.forEach { method ->
                val methodName = method.name
                val methodDesc = Type.getMethodDescriptor(method)

                // If we already printed the method from a subclass, don't print it.
                if (shownAlready(methodName, methodDesc)) {
                    return@forEach
                }

                dumpMethod(pkg, cls, true, methodClassName,
                    methodName, methodDesc, javaStandardApiPolicy, javaStandardApiPolicy)
            }
        } catch (e: ClassNotFoundException) {
            log.w("JVM type $methodClassName (used by ${dumpClass.name}) not found.")
        }
    }
}
