/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen.filters

import com.android.hoststubgen.addNonNullElement
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.toHumanReadableClassName
import com.android.hoststubgen.asm.toHumanReadableMethodName
import com.android.hoststubgen.asm.toJvmClassName
import com.android.hoststubgen.log
import com.android.hoststubgen.utils.Trie

// TODO: Validate all input names.

class InMemoryOutputFilter(
    private val classes: ClassNodes,
    fallback: OutputFilter,
) : DelegatingFilter(fallback) {
    private val mPolicies = mutableMapOf<String, FilterPolicyWithReason>()
    private val mRenames = mutableMapOf<String, String>()
    private val mRedirectionClasses = mutableMapOf<String, String>()
    private val mClassLoadHooks = mutableMapOf<String, String>()
    private val mMethodCallReplaceSpecs = mutableListOf<MethodCallReplaceSpec>()
    private val mTypeRenameSpecs = mutableListOf<TypeRenameSpec>()
    private val mPackagePolicies = PackagePolicyTrie()

    // We want to pick the most specific filter for a package name.
    // Since any package with a matching prefix is a valid match, we can use a prefix tree
    // to help us find the nearest matching filter.
    private class PackagePolicyTrie : Trie<String, String, FilterPolicyWithReason>() {
        // Split package name into individual component
        override fun splitToComponents(key: String): Iterator<String> {
            return key.split('.').iterator()
        }
    }

    private fun getPackageKey(packageName: String): String {
        return packageName.toHumanReadableClassName()
    }

    private fun getPackageKeyFromClass(className: String): String {
        val clazz = className.toHumanReadableClassName()
        val idx = clazz.lastIndexOf('.')
        return if (idx >= 0) clazz.substring(0, idx) else ""
    }

    private fun getClassKey(className: String): String {
        return className.toHumanReadableClassName()
    }

    private fun getFieldKey(className: String, fieldName: String): String {
        return getClassKey(className) + "." + fieldName
    }

    private fun getMethodKey(className: String, methodName: String, signature: String): String {
        return getClassKey(className) + "." + methodName + ";" + signature
    }

    private fun checkClass(className: String) {
        if (classes.findClass(className) == null) {
            log.w("Unknown class $className")
        }
    }

    private fun checkField(className: String, fieldName: String) {
        if (classes.findField(className, fieldName) == null) {
            log.w("Unknown field $className.$fieldName")
        }
    }

    private fun checkMethod(
        className: String,
        methodName: String,
        descriptor: String
    ) {
        if (descriptor == "*") {
            return
        }
        if (classes.findMethod(className, methodName, descriptor) == null) {
            log.w("Unknown method $className.$methodName$descriptor")
        }
    }

    // Add a "post-processing" step that applies to all policies
    private inline fun processPolicy(
        currentPolicy: FilterPolicyWithReason?,
        fallback: () -> FilterPolicyWithReason
    ): FilterPolicyWithReason {
        // If there's no policy set in our current filter, just use fallback.
        val policy = currentPolicy ?: return fallback()

        // It's possible that getting policy from inner filters may throw.
        // If that's the case, then we don't apply additional post-processing.
        val innerPolicy = runCatching(fallback).getOrNull() ?: return policy

        // Note, because policies in this filter are defined in the policy file, it takes precedence
        // over annotations. However, if an item has both a text policy and inner (lower-priority)
        // policies such as an annotation-based policy and if they're the same, we use the inner
        // policy's "reason" instead. This allows us to differentiate "APIs that are enabled with an
        // annotation" from "APIs that are enabled by a text policy (which are usually only used
        // during development)".
        if (policy.policy == innerPolicy.policy) {
            return innerPolicy
        }

        // If the current policy is experimental, but inner policy is considered "supported",
        // then we should not override the inner policy.
        if (policy.policy == FilterPolicy.Experimental && innerPolicy.policy.isSupported) {
            return innerPolicy
        }

        return policy
    }

    override fun getPolicyForClass(className: String): FilterPolicyWithReason {
        val policy = mPolicies[getClassKey(className)]
            ?: mPackagePolicies[getPackageKeyFromClass(className)]
        return processPolicy(policy) { super.getPolicyForClass(className) }
    }

    fun setPolicyForClass(className: String, policy: FilterPolicyWithReason) {
        checkClass(className)
        mPolicies[getClassKey(className)] = policy
    }

    fun setPolicyForPackage(packageName: String, policy: FilterPolicyWithReason) {
        mPackagePolicies[getPackageKey(packageName)] = policy
    }

    override fun getPolicyForField(className: String, fieldName: String): FilterPolicyWithReason {
        return processPolicy(mPolicies[getFieldKey(className, fieldName)]) {
            super.getPolicyForField(className, fieldName)
        }
    }

    fun setPolicyForField(className: String, fieldName: String, policy: FilterPolicyWithReason) {
        checkField(className, fieldName)
        mPolicies[getFieldKey(className, fieldName)] = policy
    }

    override fun getPolicyForMethod(
        className: String,
        methodName: String,
        descriptor: String,
    ): FilterPolicyWithReason {
        val policy = mPolicies[getMethodKey(className, methodName, descriptor)]
            ?: mPolicies[getMethodKey(className, methodName, "*")]

        return processPolicy(policy) {
            super.getPolicyForMethod(className, methodName, descriptor)
        }
    }

    fun setPolicyForMethod(
        className: String,
        methodName: String,
        descriptor: String,
        policy: FilterPolicyWithReason,
    ) {
        checkMethod(className, methodName, descriptor)
        mPolicies[getMethodKey(className, methodName, descriptor)] = policy
    }

    override fun getRenameTo(className: String, methodName: String, descriptor: String): String? {
        return mRenames[getMethodKey(className, methodName, descriptor)]
            ?: mRenames[getMethodKey(className, methodName, "*")]
            ?: super.getRenameTo(className, methodName, descriptor)
    }

    fun setRenameTo(className: String, methodName: String, descriptor: String, toName: String) {
        checkMethod(className, methodName, descriptor)
        checkMethod(className, toName, descriptor)
        mRenames[getMethodKey(className, methodName, descriptor)] = toName
    }

    override fun getRedirectionClass(className: String): String? {
        return mRedirectionClasses[getClassKey(className)]
            ?: super.getRedirectionClass(className)
    }

    fun setRedirectionClass(from: String, to: String) {
        checkClass(from)

        // Redirection classes may be provided from other jars, so we can't do this check.
        // ensureClassExists(to)
        mRedirectionClasses[getClassKey(from)] = to.toJvmClassName()
    }

    override fun getClassLoadHooks(className: String): List<String> {
        return addNonNullElement(
            super.getClassLoadHooks(className),
            mClassLoadHooks[getClassKey(className)]
        )
    }

    fun setClassLoadHook(className: String, methodName: String) {
        mClassLoadHooks[getClassKey(className)] = methodName.toHumanReadableMethodName()
    }

    override fun hasAnyMethodCallReplace(): Boolean {
        return mMethodCallReplaceSpecs.isNotEmpty() || super.hasAnyMethodCallReplace()
    }

    override fun getMethodCallReplaceTo(
        className: String,
        methodName: String,
        descriptor: String,
    ): MethodReplaceTarget? {
        // Maybe use 'Tri' if we end up having too many replacements.
        mMethodCallReplaceSpecs.forEach {
            if (className == it.fromClass &&
                methodName == it.fromMethod
            ) {
                if (it.fromDescriptor == "*" || descriptor == it.fromDescriptor) {
                    return MethodReplaceTarget(it.toClass, it.toMethod)
                }
            }
        }
        return super.getMethodCallReplaceTo(className, methodName, descriptor)
    }

    fun setMethodCallReplaceSpec(spec: MethodCallReplaceSpec) {
        mMethodCallReplaceSpecs.add(spec)
    }

    override fun remapType(className: String): String? {
        mTypeRenameSpecs.forEach {
            if (it.typeInternalNamePattern.matcher(className).matches()) {
                return it.typeInternalNamePrefix + className
            }
        }
        return super.remapType(className)
    }

    fun setRemapTypeSpec(spec: TypeRenameSpec) {
        mTypeRenameSpecs.add(spec)
    }
}
