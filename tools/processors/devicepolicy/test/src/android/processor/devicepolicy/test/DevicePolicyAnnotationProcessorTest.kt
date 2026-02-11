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

import android.processor.devicepolicy.DevicePolicyAnnotationProcessor
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
import org.junit.Ignore
import org.junit.Test
import javax.tools.JavaFileObject

class DevicePolicyAnnotationProcessorTest {
    private val mCompilerWithoutProcessor = Compiler.javac()
    private val mCompiler = Compiler.javac().withProcessors(DevicePolicyAnnotationProcessor())

    private companion object {
        const val RESOURCE_ROOT = "test/resources/android/processor/devicepolicy/test"

        const val POLICY_IDENTIFIER_JAVA = "$RESOURCE_ROOT/TestPolicyIdentifier.java"
        const val POLICY_IDENTIFIER_LOCATION = "android/app/admin/PolicyIdentifier"
        const val POLICY_IDENTIFIER_TEXTPROTO = "$RESOURCE_ROOT/ExpectedPolicyIdentifier.textproto"

        // Can be used by tests that do not care about the allowedDpcTypes field.
        const val ALLOWED_DPC_TYPES_SNIPPET = """
            allowedDpcTypes = @AllowedDpcTypes(
                    deviceOwner = DISALLOWED,
                    financedDeviceOwner = DISALLOWED,
                    managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                    managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                    profileOwnerOnUser0 = DISALLOWED,
                    unaffiliatedFullUserProfileOwner = DISALLOWED,
                    affiliatedFullUserProfileOwner = DISALLOWED)
        """

        val METADATA_FILES_JAVA = setOf(
            "PolicyMetadata",
            "BooleanPolicyMetadata",
            "IntegerPolicyMetadata",
            "LongPolicyMetadata",
            "EnumPolicyMetadata",
            "StringPolicyMetadata",
            "ListPolicyMetadata",
        )
            .map {
                "android/app/admin/metadata/$it.java"
            }
            .toSet()

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

                import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                import android.annotation.IntDef;
                import android.processor.devicepolicy.AllowedDpcTypes;
                import android.processor.devicepolicy.BooleanPolicyDefinition;
                import android.processor.devicepolicy.EnumPolicyDefinition;
                import android.processor.devicepolicy.IntegerPolicyDefinition;
                import android.processor.devicepolicy.ListOfStringPolicyDefinition;
                import android.processor.devicepolicy.LongPolicyDefinition;
                import android.processor.devicepolicy.PolicyDefinition;
                import android.processor.devicepolicy.StringPolicyDefinition;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.util.List;

                public final class PolicyIdentifier<T> {
                    // Allow using the constants without having to build DevicePolicyManager
                    // in our tests.
                    public static final int POLICY_SCOPE_USER = 1;
                    public static final int POLICY_SCOPE_DEVICE = 2;
                    public static final int POLICY_SCOPE_PARENT_USER = 3;
                    public static final int RESOURCE_DEVICE_WIDE = 1;
                    public static final int RESOURCE_PER_USER = 2;

                    // We don't actually do anything with this.
                    public PolicyIdentifier(String id) {}

                    $policies
                }
            """.trimIndent())
    }

    @Test
    fun test_policyIdentifierFake_generates() {
        val expectedOutput = loadTextResource(POLICY_IDENTIFIER_TEXTPROTO)

        val metadataSources = METADATA_FILES_JAVA.map {
            JavaFileObjects.forResource(it)
        }.toTypedArray()

        val compilation: Compilation =
            mCompiler.compile(
                JavaFileObjects.forSourceString(
                    POLICY_IDENTIFIER_LOCATION,
                    loadTextResource(POLICY_IDENTIFIER_JAVA)
                ),
                JavaFileObjects.forResource(INT_DEF_JAVA),
                *metadataSources
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

                import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                import android.processor.devicepolicy.AllowedDpcTypes;
                import android.processor.devicepolicy.BooleanPolicyDefinition;
                import android.processor.devicepolicy.PolicyDefinition;

                public final class OtherClass {
                    public OtherClass(String id) {}

                    /**
                     * Policies can only be defined in PolicyIdentifier.
                     */
                    @BooleanPolicyDefinition(
                            base = @PolicyDefinition(
                                    allowedScopes = { 1 },
                                    affectedResource = 1,
                                    $ALLOWED_DPC_TYPES_SNIPPET
                            )
                    )
                    public static final PolicyIdentifier<Boolean> LOST_POLICY =
                        new PolicyIdentifier<>("LOST_POLICY");
                }
            """.trimIndent())
        val policyIdentifier = JavaFileObjects.forSourceString(
            POLICY_IDENTIFIER_LOCATION,
            loadTextResource(POLICY_IDENTIFIER_JAVA)
        )

        val compilation: Compilation = mCompiler.compile(otherClass, policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(otherClass, policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("@PolicyDefinition can only be applied to fields in android.app.admin.PolicyIdentifier")
    }

    @Test
    fun test_invalidType_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
        """
            /**
             * Type of metadata and identifier must match.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { POLICY_SCOPE_DEVICE },
                            affectedResource = RESOURCE_DEVICE_WIDE,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Integer> INVALID_TYPE =
                new PolicyIdentifier<>("INVALID_TYPE");
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
            /**
             * Don't use @PolicyDefinition
             */
            @PolicyDefinition(
                    allowedScopes = { POLICY_SCOPE_DEVICE },
                    affectedResource = RESOURCE_DEVICE_WIDE,
                    $ALLOWED_DPC_TYPES_SNIPPET
            )
            public static final PolicyIdentifier<Boolean> INVALID_ANNOTATION =
                new PolicyIdentifier<>("INVALID_ANNOTATION");
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("@PolicyDefinition can not be applied to any element, use a type-specific annotation such as @EnumPolicyDefinition instead")
    }

    @Test
    fun test_missingDocumentation_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { POLICY_SCOPE_DEVICE },
                            affectedResource = RESOURCE_DEVICE_WIDE,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> MISSING_DOCS =
                new PolicyIdentifier<>("MISSING_DOCS");
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
            /**
             * Empty allowedScopes should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = {},
                            affectedResource = RESOURCE_PER_USER,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> EMPTY_SCOPE_POLICY =
                new PolicyIdentifier<>("EMPTY_SCOPE");
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
            /**
             * Invalid scope should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { 100 },
                            affectedResource = RESOURCE_PER_USER,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> EMPTY_SCOPE_POLICY =
                new PolicyIdentifier<>("INVALID_SCOPE");
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
            /**
             * Unspecified (0) scope should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { 0 },
                            affectedResource = RESOURCE_PER_USER,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> UNDEFINED_SCOPE =
                new PolicyIdentifier<>("UNDEFINED_SCOPE");
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
            /**
             * Invalid resource should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { POLICY_SCOPE_USER },
                            affectedResource = 100,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> INVALID_AFFECTED_RESOURCE_POLICY =
                new PolicyIdentifier<>("INVALID_AFFECTED_RESOURCE");
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
            /**
             * Unspecified (0) resource should fail.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { POLICY_SCOPE_USER },
                            affectedResource = 0,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> UNSPECIFIED_AFFECTED_RESOURCE_POLICY =
                new PolicyIdentifier<>("UNSPECIFIED_AFFECTED_RESOURCE");
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("affectedResource is set to an unknown value")
    }

    @Test
    fun test_invalidCrossUserPermission_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            /**
             * requiredCrossUserPermission only allows one of the 3 permissions.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { POLICY_SCOPE_USER },
                            affectedResource = RESOURCE_DEVICE_WIDE,
                            requiredCrossUserPermission = "my.custom.PERMISSION",
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> INVALID_PERMISSION =
                new PolicyIdentifier<>("INVALID_PERMISSION");
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("requiredCrossUserPermission was set to")
    }

    @Test
    fun test_missingModifiers_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            /**
             * field must be public, static and final.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { POLICY_SCOPE_USER },
                            affectedResource = RESOURCE_DEVICE_WIDE,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            private PolicyIdentifier<Boolean> MISSING_MODIFIERS =
                new PolicyIdentifier<>("MISSING_MODIFIERS");
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("Field must be static")
        assertThat(compilation).hadErrorContaining("Field must be public")
        assertThat(compilation).hadErrorContaining("Field must be final")
    }

    @Test
    fun test_invalidInitializer_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            private static final String INVALID_INITIALIZER_KEY = "INVALID_INITIALIZER";
            /**
             * Initializer must use a literal String.
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { POLICY_SCOPE_USER },
                            affectedResource = RESOURCE_DEVICE_WIDE,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> INVALID_INITIALIZER =
                new PolicyIdentifier<>(INVALID_INITIALIZER_KEY);
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("the argument to the constructor is not a literal")
    }

    @Test
    fun test_wrongKey_failsToCompile() {
        val policyIdentifier = buildPolicyIdentifier(
            """
            /**
             * Initializer and keys must match .
             */
            @BooleanPolicyDefinition(
                    base = @PolicyDefinition(
                            allowedScopes = { POLICY_SCOPE_USER },
                            affectedResource = RESOURCE_DEVICE_WIDE,
                            $ALLOWED_DPC_TYPES_SNIPPET
                    )
            )
            public static final PolicyIdentifier<Boolean> INVALID_KEY_POLICY =
                new PolicyIdentifier<>("WRONG_KEY_POLICY");
            """.trimIndent()
        )

        val compilation: Compilation = mCompiler.compile(policyIdentifier)

        assertThat(mCompilerWithoutProcessor.compile(policyIdentifier)).succeeded()
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("the argument to the constructor should be \"INVALID_KEY_POLICY\"")
    }
}
