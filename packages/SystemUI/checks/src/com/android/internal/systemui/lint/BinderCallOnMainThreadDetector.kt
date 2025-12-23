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

package com.android.internal.systemui.lint

import com.android.internal.systemui.lint.BindServiceOnMainThreadDetector.Companion.containingMethodOrClassHasWorkerThreadAnnotation
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getContainingDeclaration

/**
 * A linter that warns when binder calls are being made not on a background thread.
 *
 * Please add additional binder calls to the list in [binderCalls]! This will help protect future
 * engineers from adding a binder call on the main thread without realizing it.
 */
class BinderCallOnMainThreadDetector : Detector(), SourceCodeScanner {
    // TODO: b/469073407 - Add more binder calls.
    private val binderCalls =
        listOf(
            "android.app.PendingIntent#getBroadcast",
            "android.app.PendingIntent#queryIntentComponents",
            "android.media.projection.MediaProjectionManager#stopActiveProjection",
        )

    private data class BinderCall(
        /** Package location of binder call, like "android.app". */
        val packageName: String,
        /** Class containing binder call, like "PendingIntent". */
        val className: String,
        /** Binder call method name, like "getBroadcast". */
        val methodName: String,
    )

    private val parsedBinderCalls: List<BinderCall> =
        binderCalls.map { binderCallString ->
            val classAndMethod = binderCallString.split("#")
            check(classAndMethod.size == 2)
            val fullyQualifiedClass = classAndMethod[0]
            val methodName = classAndMethod[1]

            val indexOfPackageClassSplit = fullyQualifiedClass.indexOfLast { it == '.' }
            val packageName = fullyQualifiedClass.substring(0, indexOfPackageClassSplit)
            val className =
                fullyQualifiedClass.substring(
                    indexOfPackageClassSplit + 1,
                    fullyQualifiedClass.length,
                )

            BinderCall(methodName = methodName, className = className, packageName = packageName)
        }

    override fun getApplicableMethodNames(): List<String> =
        parsedBinderCalls.map { it.methodName }.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // TODO: b/469073407 - Handle the same method name in a different class/package gracefully.
        val matchingBinderCall: BinderCall =
            parsedBinderCalls.find { it.methodName == method.name } ?: return

        val className = method.parent
        val classMatches = className is PsiClass && className.name == matchingBinderCall.className
        val packageMatches =
            context.evaluator.getPackage(method)?.qualifiedName == matchingBinderCall.packageName

        if (!classMatches || !packageMatches) {
            return
        }

        if (node.containingMethodOrClassHasWorkerThreadAnnotation(context)) {
            // This binder call is inside a method or class marked `@WorkerThread`, and that
            // annotation enforces that the method will only be invoked on the background thread.
            return
        }

        val containingBlock = findContainingBlock(node)
        if (containingBlock != null) {
            if (
                containingBlock.isLaunchedOnBackgroundScope() ||
                    containingBlock.isStartedWithBackgroundParameter()
            ) {
                // This binder call is correctly inside a block that explicitly runs in the
                // background.
                return
            }
        }

        if (isInStateFlowOnBackground(node)) {
            // This binder call is correctly in a state flow on the background.
            return
        }

        // TODO: b/469073407 - Don't warn for calls followed by `.flowOn(backgroundDispatcher)`.

        context.report(ISSUE, context.getNameLocation(node), message = "Binder call on main thread")
    }

    /**
     * Given a specific method call, find the block that contains it. For example:
     * ```
     * withContext(backgroundDispatcher) {
     *   manager.binderCall()
     * }
     * ```
     *
     * Calling this method with the `binderCall` node will return the call expression for
     * `withContext`.
     */
    private fun findContainingBlock(node: UCallExpression): UCallExpression? {
        var parent = node.uastParent
        while (parent != null && parent !is UCallExpression) {
            parent = parent.uastParent
        }
        return parent as UCallExpression?
    }

    /** Returns true if the given [node] is contained within a `.stateIn(backgroundScope)` block. */
    private fun isInStateFlowOnBackground(node: UCallExpression): Boolean {
        val containingDeclaration = node.getContainingDeclaration() ?: return false
        return containingDeclaration.text.contains(Regex("stateIn\\s*\\(\\s*(bg|background)"))
    }

    /**
     * Returns true if the call expression is received by the backgroundScope. Matches expressions
     * like `bgScope.launch {}` or `backgroundScope.launch {}`.
     */
    private fun UCallExpression.isLaunchedOnBackgroundScope(): Boolean {
        // For extension functions like CoroutineScope.launch, the CoroutineScope is the receiver.
        val receiverIdentifier: String? =
            when (val receiver = this.receiver) {
                is USimpleNameReferenceExpression -> receiver.identifier
                is UParenthesizedExpression -> receiver.expression.asRenderString()
                else -> null
            }
        return receiverIdentifier?.isBackgroundScopeIdentifier() == true
    }

    /**
     * Returns true if the call expression matches expressions that start a job in the background.
     * Matches expressions like `withContext(backgroundContext) {}` or `scope.launch(bgDispatcher)
     * {}`.
     */
    private fun UCallExpression.isStartedWithBackgroundParameter(): Boolean {
        if (this.methodName != null && this.methodName != "withContext") {
            return false
        }
        // For calls like `scope.launch(bgDispatcher)`, the method name is `null` so we can't
        // compare on method name and so we just check the first argument.
        // TODO: b/469073407 - Determine whether the method name for `launch` isn't getting
        // populated only in the linter test, or in production code as well.

        if (this.valueArguments.isEmpty()) {
            return false
        }

        val parameter = this.valueArguments[0]
        return parameter is USimpleNameReferenceExpression &&
            parameter.identifier.isBackgroundIdentifier()
    }

    /** Returns true if this string identifies a background context or background dispatcher. */
    private fun String.isBackgroundIdentifier(): Boolean {
        return this.matches(Regex(".*(bg|background|Bg|Background)(Context|Dispatcher)"))
    }

    /** Returns true if this string identifies a background scope. */
    private fun String.isBackgroundScopeIdentifier(): Boolean {
        return endsWith("bgScope", ignoreCase = true) ||
            endsWith("backgroundScope", ignoreCase = true)
    }

    companion object {
        @JvmStatic
        val ISSUE =
            Issue.create(
                id = "BinderCallOnMainThread",
                briefDescription = "Binder call on main thread",
                explanation =
                    """
                    Blocking IPC/binder calls must be done on a background thread to avoid heavy
                    computation on the main thread, because heavy computation on the main thread
                    can cause jank and frame drops. Switch work off the main thread using
                    `withContext(backgroundDispatcher)` instead.
                """,
                moreInfo =
                    "http://go/sysui-perf-dev-guide#moving-long-operations-to-background-thread",
                category = Category.PERFORMANCE,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        BinderCallOnMainThreadDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
