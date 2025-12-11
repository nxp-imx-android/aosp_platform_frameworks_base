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

package com.android.protolog

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import java.util.EnumSet
import kotlin.math.min
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class ProtoLogFormatDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName !in listOf("v", "d", "i", "w", "e", "wtf")) {
                    return
                }
                val method: PsiMethod = node.resolve() ?: return
                if (
                    !context.evaluator.isMemberInSubClassOf(
                        method,
                        "com.android.internal.protolog.ProtoLog",
                        false,
                    )
                ) {
                    return
                }

                val arguments = node.valueArguments
                // ProtoLogGroup, FormatString, Varargs...
                if (arguments.size < 2) {
                    return
                }

                val formatStringArg = arguments[1]
                val formatArgs = arguments.subList(2, arguments.size)

                val formatString = formatStringArg.evaluate() as? String
                if (formatString == null) {
                    context.report(
                        ISSUE_NON_CONSTANT_FORMAT,
                        formatStringArg,
                        context.getLocation(formatStringArg),
                        "ProtoLog format string should be a compile-time constant.",
                    )
                    return
                }

                val specifiers = parseFormatString(formatString)
                val isValidFormatString = validateSpecifiers(specifiers, context, formatStringArg)

                if (isValidFormatString && formatArgs.isNotEmpty()) {
                    val nonContextContent = formatString
                        .replace("%(${SUPPORTED_SPECIFIERS.joinToString("|")})".toRegex(), "")
                    if (nonContextContent.isBlank()) {
                        context.report(
                            ISSUE_NO_TEXT_CONTEXT,
                            formatStringArg,
                            context.getLocation(formatStringArg),
                            "ProtoLog format string should contain some context, not just specifiers."
                        )
                    }
                }

                val validSpecifiers = specifiers.filter {
                    it.char in SUPPORTED_SPECIFIERS
                }

                if (validSpecifiers.size != formatArgs.size && isValidFormatString) {
                    context.report(
                        ISSUE_ARG_COUNT,
                        node,
                        context.getLocation(node),
                        "Incorrect argument count: format string expects ${specifiers.size} " +
                                "arguments but ${formatArgs.size} were provided.",
                    )
                }

                val checkCount = min(specifiers.size, formatArgs.size)
                for (j in 0 until checkCount) {
                    val specifier = specifiers[j]
                    val arg = formatArgs[j]
                    checkArgumentType(context, specifier, arg)

                    if (arg.evaluate() != null) {
                        context.report(
                            ISSUE_CONSTANT_ARGUMENT,
                            arg,
                            context.getLocation(arg),
                            "ProtoLog format string argument should not be a constant."
                        )
                    }
                }
            }
        }

    private fun validateSpecifiers(
        specs: List<Specifier>,
        context: JavaContext,
        formatStringArg: UExpression
    ): Boolean {
        var isValidFormatString = true
        specs.forEach { spec ->
            if (spec.char !in SUPPORTED_SPECIFIERS) {
                context.report(
                    ISSUE_INVALID_FORMAT_SPECIFIER,
                    formatStringArg,
                    context.getLocation(formatStringArg),
                    "Unsupported format specifier '%${spec.char ?: ""}'. " +
                            "Supported: " +
                            "[${SUPPORTED_SPECIFIERS.joinToString(", %", "%")}]. " +
                            "Use %% to escape."
                )
                isValidFormatString = false
            }
        }
        return isValidFormatString
    }

    private data class Specifier(val char: Char?)

    private fun parseFormatString(formatString: String): List<Specifier> {
        val specifiers = mutableListOf<Specifier>()
        var i = 0
        while (i < formatString.length) {
            if (formatString[i] == '%') {
                if (i + 1 < formatString.length) {
                    val nextChar = formatString[i + 1]
                    if (nextChar == '%') {
                        i += 2
                    } else {
                        specifiers.add(Specifier(nextChar))
                        i += 2
                    }
                } else {
                    specifiers.add(Specifier(null))
                    i++
                }
            } else {
                i++
            }
        }
        return specifiers
    }

    private fun checkArgumentType(context: JavaContext, specifier: Specifier, arg: UExpression) {
        val type = arg.getExpressionType() ?: return

        val specifier = specifier.char ?: return

        if (specifier !in SUPPORTED_SPECIFIERS) {
            // We are already validating specifier above,
            // and invalid specifiers don't have a defined type to check below
            return
        }

        val typeOk =
            when {
                isBoolean(type) -> specifier == 'b'
                isInteger(type) -> specifier == 'd'
                isFloat(type) -> specifier == 'f'
                else -> specifier == 's'
            }

        if (!typeOk) {
            val expectedType = when (specifier) {
                'b' -> "boolean"
                'd' -> "integer, long, short, or byte"
                'f' -> "float or double"
                's' -> "String"
                else -> "unknown"
            }

            context.report(
                ISSUE_ARG_TYPE,
                arg,
                context.getLocation(arg),
                "Incorrect argument type for format specifier '%$specifier': " +
                        "expected $expectedType but got ${type.presentableText}",
            )
        }
    }

    private fun isBoolean(type: PsiType): Boolean =
        type.equalsToText("boolean") || type.equalsToText("java.lang.Boolean")

    private fun isInteger(type: PsiType): Boolean =
        type.equalsToText("int") ||
                type.equalsToText("long") ||
                type.equalsToText("short") ||
                type.equalsToText("byte") ||
                type.equalsToText("java.lang.Integer") ||
                type.equalsToText("java.lang.Long") ||
                type.equalsToText("java.lang.Short") ||
                type.equalsToText("java.lang.Byte")

    private fun isFloat(type: PsiType): Boolean =
        type.equalsToText("float") ||
                type.equalsToText("double") ||
                type.equalsToText("java.lang.Float") ||
                type.equalsToText("java.lang.Double")

    companion object {
        // Matches a format specifier starting with %, followed by any non-whitespace characters.
        private val SUPPORTED_SPECIFIERS = arrayOf('b', 'd', 'f', 's')

        val ISSUE_INVALID_FORMAT_SPECIFIER: Issue =
            Issue.create(
                id = "ProtoLogInvalidFormatSpecifier",
                briefDescription = "ProtoLog format string contains an invalid specifier",
                explanation =
                    """
                    ProtoLog format strings only support a subset of format specifiers.
                    Supported specifiers are ${SUPPORTED_SPECIFIERS.joinToString(", %", "%")}
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_NON_CONSTANT_FORMAT: Issue =
            Issue.create(
                id = "ProtoLogNonConstantFormat",
                briefDescription = "ProtoLog format string must be a compile-time constant",
                explanation =
                    """
                    The format string argument to ProtoLog methods must be a string literal or a
                    compile-time constant. Using dynamically generated strings prevents
                    build-time optimizations and can lead to unexpected behavior.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_ARG_COUNT: Issue =
            Issue.create(
                id = "ProtoLogArgCount",
                briefDescription = "Incorrect number of arguments for ProtoLog format string",
                explanation =
                    """
                    The number of arguments passed to a ProtoLog method must match the number
                    of format specifiers in the format string.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_ARG_TYPE: Issue =
            Issue.create(
                id = "ProtoLogArgType",
                briefDescription = "Incorrect argument type for ProtoLog format specifier",
                explanation =
                    """
                    The type of arguments passed to ProtoLog methods must match the type
                    required by the format specifiers. For example, %d requires an integer
                    and %b requires a boolean.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_CONSTANT_ARGUMENT: Issue =
            Issue.create(
                id = "ProtoLogConstantArgument",
                briefDescription = "ProtoLog format string argument should not be a constant",
                explanation =
                    """
                    ProtoLog format string arguments should not be constants.
                    If the argument is constant, it should be directly included in the format string.
                    """,
                category = Category.PERFORMANCE,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val ISSUE_NO_TEXT_CONTEXT: Issue =
            Issue.create(
                id = "ProtoLogNoContext",
                briefDescription = "ProtoLog format string should have some context",
                explanation =
                    """
                    The format string should contain some text context, not just format specifiers.
                    Logging only the arguments without any explanation is discouraged.
                    """,
                category = Category.CORRECTNESS,
                priority = 4,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        ProtoLogFormatDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
