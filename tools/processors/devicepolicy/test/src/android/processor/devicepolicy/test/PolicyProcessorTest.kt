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

package android.processor.devicepolicy.test

import android.processor.devicepolicy.PolicyProcessor
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.testing.compile.Compilation
import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import java.io.IOException
import javax.tools.StandardLocation.SOURCE_OUTPUT
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail

import org.junit.Test
import javax.tools.JavaFileObject

class PolicyProcessorTest {
    private val mCompilerWithoutProcessor = Compiler.javac()
    private val mCompiler = Compiler.javac().withProcessors(PolicyProcessor())

    private companion object {
        const val RESOURCE_ROOT = "test/resources/android/processor/devicepolicy/test"

        const val POLICY_IDENTIFIER = "$RESOURCE_ROOT/PolicyIdentifier"
        const val POLICY_IDENTIFIER_JAVA = "$POLICY_IDENTIFIER.java"
        const val POLICY_IDENTIFIER_TEXTPROTO = "$POLICY_IDENTIFIER.textproto"

        /**
         * Comes from the actual IntDef.java in the source, located in a different folder.
         */
        const val INT_DEF_JAVA = "android/annotation/IntDef.java"

        /**
         * Build path for the output.
         */
        const val POLICIES_TEXTPROTO_LOCATION = "android/processor/devicepolicy/policies.textproto"

        fun loadTextResource(path: String): String {
            try {
                val url = Resources.getResource(path)
                assertNotNull(String.format("Resource file not found: %s", path), url)
                return Resources.toString(url, Charsets.UTF_8)
            } catch (e: IOException) {
                fail(e.message)
                return ""
            }
        }

        fun buildPolicyIdentifier(policies: String): JavaFileObject = JavaFileObjects.forSourceLines(
            "android.app.admin.PolicyIdentifier",
            """
                package android.app.admin;

                import android.annotation.IntDef;
                import android.processor.devicepolicy.BooleanPolicyDefinition;
                import android.processor.devicepolicy.EnumPolicyDefinition;
                import android.processor.devicepolicy.IntegerPolicyDefinition;
                import android.processor.devicepolicy.PolicyDefinition;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                public final class PolicyIdentifier<T> {
                    // We don't actually do anything with this.
                    public PolicyIdentifier(String id) {}
                    
                    $policies
                }
            """.trimIndent())
    }

    @Test
    fun test_policyIdentifierFake_generates() {
        val expectedOutput = loadTextResource(POLICY_IDENTIFIER_TEXTPROTO)

        val compilation: Compilation =
            mCompiler.compile(
                JavaFileObjects.forResource(POLICY_IDENTIFIER_JAVA),
                JavaFileObjects.forResource(INT_DEF_JAVA)
            )

        assertThat(compilation).succeeded()
        assertThat(compilation).generatedFile(SOURCE_OUTPUT,
            POLICIES_TEXTPROTO_LOCATION).contentsAsUtf8String().isEqualTo(expectedOutput)
    }

    @Test
    fun test_other_class_failsToCompile() {
        val otherClass = JavaFileObjects.forSourceLines(
            "android.app.admin.OtherClass",
            """
                package android.app.admin;

                import android.processor.devicepolicy.BooleanPolicyDefinition;
                import android.processor.devicepolicy.PolicyDefinition;

                public final class OtherClass {
                    public OtherClass(String id) {}

                    private static final String TEST_POLICY_1_KEY = "test_policy_1_key";

                    /**
                     * Test policy 1
                     */
                    @BooleanPolicyDefinition(
                            base = @PolicyDefinition(
                                    allowedScopes = {0},
                                    affectedResource = 1
                            )
                    )
                    public static final PolicyIdentifier<Boolean> TEST_POLICY_1 = new PolicyIdentifier<>(
                            TEST_POLICY_1_KEY);
                }
            """.trimIndent())
        val policyIdentifier = JavaFileObjects.forResource(POLICY_IDENTIFIER_JAVA)

        val compilation: Compilation = mCompiler.compile(otherClass, policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(otherClass, policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("@PolicyDefinition can only be applied to fields in android.app.admin.PolicyIdentifier")
    }

    @Test
    fun test_invalidType_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
        """
            private static final String TEST_POLICY_1_KEY = "test_policy_1_key";
            /**
             * Test policy 1
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = {1, 2},
                            affectedResource = 1
                    )
            )
            public static final PolicyIdentifier<Integer> TEST_POLICY_1 =
                new PolicyIdentifier<>(TEST_POLICY_1_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("booleanValue in @PolicyDefinition can only be applied to policies of type java.lang.Boolean")
    }

    @Test
    fun test_directPolicyDefinition_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            private static final String TEST_POLICY_1_KEY = "test_policy_1_key";
            /**
             * Test policy 1
             */
            @PolicyDefinition(
                    allowedScopes = {1, 2},
                    affectedResource = 1
            )
            public static final PolicyIdentifier<Boolean>
                    TEST_POLICY_1 = new PolicyIdentifier<>(
                    TEST_POLICY_1_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("@PolicyDefinition should not be applied to any element")
    }

    @Test
    fun test_missingDocumentation_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            private static final String TEST_POLICY_1_KEY = "test_policy_1_key";
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = {2, 3},
                            affectedResource = 1
                    )
            )
            public static final PolicyIdentifier<Boolean> TEST_POLICY_1 = new PolicyIdentifier<>(
                    TEST_POLICY_1_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("Missing JavaDoc")
    }

    @Test
    fun test_emptyScope_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            private static final String EMPTY_SCOPE_KEY = "empty_scope_key";
            /**
             * Empty allowedScopes should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = {},
                            affectedResource = 2
                    )
            )
            public static final PolicyIdentifier<Boolean> EMPTY_SCOPE_POLICY = new PolicyIdentifier<>(
                    EMPTY_SCOPE_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("allowedScopes must not be empty")
    }

    @Test
    fun test_invalidScopeValue_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            private static final String INVALID_SCOPE_KEY = "invalid_scope_key";
            /**
             * Invalid scope should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = {100},
                            affectedResource = 2
                    )
            )
            public static final PolicyIdentifier<Boolean> EMPTY_SCOPE_POLICY = new PolicyIdentifier<>(
                    INVALID_SCOPE_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("allowedScopes contains an unknown value")
    }

    @Test
    fun test_undefinedScope_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            private static final String UNDEFINED_SCOPE_KEY = "undefined_scope_key";
            /**
             * Unspecified (0) scope should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = {0},
                            affectedResource = 2
                    )
            )
            public static final PolicyIdentifier<Boolean> UNDEFINED_SCOPE = new PolicyIdentifier<>(
                    UNDEFINED_SCOPE_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("allowedScopes contains an unknown value")
    }

    @Test
    fun test_invalidAffectedResource_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            private static final String INVALID_AFFECTED_RESOURCE_KEY = "invalid_affected_resource";
            /**
             * Invalid resource should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = {1},
                            affectedResource = 100
                    )
            )
            public static final PolicyIdentifier<Boolean> INVALID_AFFECTED_RESOURCE_POLICY = new PolicyIdentifier<>(
                    INVALID_AFFECTED_RESOURCE_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("affectedResource is set to an unknown value")
    }

    @Test
    fun test_undefinedAffectedResource_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            private static final String UNSPECIFIED_AFFECTED_RESOURCE_KEY = "unknown_affected_resource";
            /**
             * Unspecified (0) resource should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = {1},
                            affectedResource = 0
                    )
            )
            public static final PolicyIdentifier<Boolean> UNSPECIFIED_AFFECTED_RESOURCE_POLICY = new PolicyIdentifier<>(
                    UNSPECIFIED_AFFECTED_RESOURCE_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("affectedResource is set to an unknown value")
    }
}