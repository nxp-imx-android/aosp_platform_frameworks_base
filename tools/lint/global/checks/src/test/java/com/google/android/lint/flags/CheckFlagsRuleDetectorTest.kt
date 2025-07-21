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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CheckFlagsRuleDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = CheckFlagsRuleDetector()

    override fun getIssues(): List<Issue> =
        listOf(CheckFlagsRuleDetector.ISSUE_MISSING_CHECK_FLAGS_RULE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testRequiresFlagsEnabled_methodAnnotation_noRule_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import android.platform.test.annotations.RequiresFlagsEnabled;

                    public class ExampleUnitTest {
                        @RequiresFlagsEnabled("my_flag")
                        public void testMethod() {}
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(CheckFlagsRuleDetector.ISSUE_MISSING_CHECK_FLAGS_RULE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:4: Error: Class ExampleUnitTest uses @RequiresFlagsEnabled or @RequiresFlagsDisabled and must have a CheckFlagsRule. [MissingCheckFlagsRule]
                public class ExampleUnitTest {
                             ~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testRequiresFlagsEnabled_classAnnotation_noRule_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import android.platform.test.annotations.RequiresFlagsEnabled;

                    @RequiresFlagsEnabled("my_flag")
                    public class ExampleUnitTest {

                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(CheckFlagsRuleDetector.ISSUE_MISSING_CHECK_FLAGS_RULE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Error: Class ExampleUnitTest uses @RequiresFlagsEnabled or @RequiresFlagsDisabled and must have a CheckFlagsRule. [MissingCheckFlagsRule]
                public class ExampleUnitTest {
                             ~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testRequiresFlagsDisabled_methodAnnotation_noRule_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import android.platform.test.annotations.RequiresFlagsDisabled;

                    public class ExampleUnitTest {
                        @RequiresFlagsDisabled("my_flag")
                        public void testMethod() {}
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(CheckFlagsRuleDetector.ISSUE_MISSING_CHECK_FLAGS_RULE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:4: Error: Class ExampleUnitTest uses @RequiresFlagsEnabled or @RequiresFlagsDisabled and must have a CheckFlagsRule. [MissingCheckFlagsRule]
                public class ExampleUnitTest {
                             ~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testRequiresFlagsDisabled_classAnnotation_noRule_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import android.platform.test.annotations.RequiresFlagsDisabled;

                    @RequiresFlagsDisabled("my_flag")
                    public class ExampleUnitTest {

                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(CheckFlagsRuleDetector.ISSUE_MISSING_CHECK_FLAGS_RULE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Error: Class ExampleUnitTest uses @RequiresFlagsEnabled or @RequiresFlagsDisabled and must have a CheckFlagsRule. [MissingCheckFlagsRule]
                public class ExampleUnitTest {
                             ~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testRequiresFlagsEnabled_methodAnnotation_withRule_passes() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import android.platform.test.annotations.RequiresFlagsEnabled;
                    import android.platform.test.flag.junit.CheckFlagsRule;
                    import org.junit.Rule;

                    public class ExampleUnitTest {
                        @Rule
                        public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule();

                        @RequiresFlagsEnabled("my_flag")
                        public void testMethod() {}
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testRequiresFlagsEnabled_classAnnotation_withRule_passes() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import android.platform.test.annotations.RequiresFlagsEnabled;
                    import android.platform.test.flag.junit.CheckFlagsRule;
                    import org.junit.Rule;

                    @RequiresFlagsEnabled("my_flag")
                    public class ExampleUnitTest {
                        @Rule
                        public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule();

                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testRequiresFlagsDisabled_methodAnnotation_withRule_passes() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import android.platform.test.annotations.RequiresFlagsDisabled;
                    import android.platform.test.flag.junit.CheckFlagsRule;
                    import org.junit.Rule;

                    public class ExampleUnitTest {
                        @Rule
                        public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule();

                        @RequiresFlagsDisabled("my_flag")
                        public void testMethod() {}
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testRequiresFlagsDisabled_classAnnotation_withRule_passes() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import android.platform.test.annotations.RequiresFlagsDisabled;
                    import android.platform.test.flag.junit.CheckFlagsRule;
                    import org.junit.Rule;

                    @RequiresFlagsDisabled("my_flag")
                    public class ExampleUnitTest {
                        @Rule
                        public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule();
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testNoAnnotations_passes() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;

                    public class ExampleUnitTest {}
                    """
                    )
                    .indented(),
                *stubs,
            )
            .run()
            .expectClean()
    }

    @Test
    fun testUnrelatedAnnotation_noRule_passes() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import org.junit.Test;

                    public class ExampleUnitTest {
                        @Test
                        public void testMethod() {}
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .run()
            .expectClean()
    }

    private val requiresFlagsEnabledStub =
        java(
                """
            package android.platform.test.annotations;
            public @interface RequiresFlagsEnabled {
                String[] value();
            }
            """
            )
            .indented()

    private val requiresFlagsDisabledStub =
        java(
                """
            package android.platform.test.annotations;
            public @interface RequiresFlagsDisabled {
                String[] value();
            }
            """
            )
            .indented()

    private val checkFlagsRuleStub =
        java(
                """
            package android.platform.test.flag.junit;
            public class CheckFlagsRule {}
            """
            )
            .indented()

    private val ruleStub =
        java(
                """
            package org.junit;
            public @interface Rule {}
            """
            )
            .indented()

    private val testAnnotationStub =
        java(
                """
            package org.junit;
            public @interface Test {}
            """
            )
            .indented()

    private val stubs =
        arrayOf(
            requiresFlagsEnabledStub,
            requiresFlagsDisabledStub,
            checkFlagsRuleStub,
            ruleStub,
            testAnnotationStub,
        )
}
