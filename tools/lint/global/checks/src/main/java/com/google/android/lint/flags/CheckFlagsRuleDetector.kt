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

package com.google.android.lint.flags

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import java.util.EnumSet
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

/**
 * Lint detector to ensure that any class that uses test flag annotations (@RequiresFlagsEnabled
 * or @RequiresFlagsDisabled) also includes a `CheckFlagsRule`.
 *
 * This check is necessary to ensure that the test flags are properly handled by the test runner.
 */
class CheckFlagsRuleDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (!hasRequiresFlagsAnnotation(node)) {
                    return
                }

                val hasCheckFlagsRule =
                    node.fields.any { field ->
                        field.type.canonicalText ==
                            "android.platform.test.flag.junit.CheckFlagsRule"
                    }

                if (!hasCheckFlagsRule) {
                    context.report(
                        ISSUE_MISSING_CHECK_FLAGS_RULE,
                        node,
                        context.getNameLocation(node),
                        "Class `${node.name}` uses @RequiresFlagsEnabled or" +
                            " @RequiresFlagsDisabled and must have a CheckFlagsRule.",
                    )
                }
            }
        }

    private fun hasRequiresFlagsAnnotation(node: UClass): Boolean {
        if (node.hasRequiresFlagsAnnotation()) {
            return true
        }

        return node.methods.any { method -> method.hasRequiresFlagsAnnotation() }
    }

    private fun UAnnotated.hasRequiresFlagsAnnotation(): Boolean {
        return findAnnotation("android.platform.test.annotations.RequiresFlagsEnabled") != null ||
            findAnnotation("android.platform.test.annotations.RequiresFlagsDisabled") != null
    }

    companion object {
        private const val EXPLANATION =
            """
            The `@RequiresFlagsEnabled` and `@RequiresFlagsDisabled` annotations
            require a `CheckFlagsRule` to be present in the test class.

            This rule is responsible for checking the state of the specified feature flags and
            skipping the test if the conditions are not met. Without this rule,
            the annotations have no effect, and the test may run in an incorrect environment.

            To fix this, add a `CheckFlagsRule` field annotated with `@Rule` to your test class:
            ```java
            import android.platform.test.flag.junit.CheckFlagsRule;
            import org.junit.Rule;

            public class YourTestClass {
                @Rule
                public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule();

                // ... your test methods
            }
            ```
            """
        val ISSUE_MISSING_CHECK_FLAGS_RULE =
            Issue.create(
                id = "MissingCheckFlagsRule",
                briefDescription = "Missing CheckFlagsRule",
                explanation = EXPLANATION,
                category = Category.CORRECTNESS,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        CheckFlagsRuleDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
