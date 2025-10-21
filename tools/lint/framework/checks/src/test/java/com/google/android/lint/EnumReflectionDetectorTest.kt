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

package com.google.android.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class EnumReflectionDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = EnumReflectionDetector()

    override fun getIssues(): List<Issue> =
        listOf(
            EnumReflectionDetector.ISSUE_GET_ENUM_CONSTANTS,
            EnumReflectionDetector.ISSUE_ENUM_VALUE_OF,
        )

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    private val enumStub: TestFile =
        java(
                """
            package test.pkg;
            public enum MyEnum {
                A, B, C
            }
            """
            )
            .indented()

    fun testGetEnumConstantsFixable() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    public class TestClass {
                        private void testMethod() {
                            MyEnum[] values = MyEnum.class.getEnumConstants();
                        }
                    }
                    """
                    )
                    .indented(),
                enumStub,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:4: Error: Reflection with Class.getEnumConstants() is discouraged; use MyEnum.values() instead. [ReflectiveEnumConstants]
                        MyEnum[] values = MyEnum.class.getEnumConstants();
                                                       ~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
                    .trimIndent()
            )
            .checkFix(
                null,
                after =
                    java(
                            """
                    package test.pkg;
                    public class TestClass {
                        private void testMethod() {
                            MyEnum[] values = MyEnum.values();
                        }
                    }
                    """
                        )
                        .indented(),
            )
    }

    fun testEnumValueOfFixable() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    public class TestClass {
                        private void testMethod() {
                            MyEnum a = Enum.valueOf(MyEnum.class, "A");
                        }
                    }
                    """
                    )
                    .indented(),
                enumStub,
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:4: Error: Reflection with Enum.valueOf() is discouraged; use MyEnum.valueOf() instead. [ReflectiveEnumValueOf]
                        MyEnum a = Enum.valueOf(MyEnum.class, "A");
                                        ~~~~~~~
                1 errors, 0 warnings
                """
                    .trimIndent()
            )
            .checkFix(
                null,
                after =
                    java(
                            """
                    package test.pkg;
                    public class TestClass {
                        private void testMethod() {
                            MyEnum a = MyEnum.valueOf("A");
                        }
                    }
                    """
                        )
                        .indented(),
            )
    }

    fun testGetEnumConstants() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    public class TestClass {
                        private void testMethod(Class<?> clazz) {
                            Object[] values = clazz.getEnumConstants();
                        }
                    }
                    """
                    )
                    .indented()
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:4: Error: Reflection with Class.getEnumConstants() is discouraged; use YourEnum.values() if possible. [ReflectiveEnumConstants]
                        Object[] values = clazz.getEnumConstants();
                                                ~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
                    .trimIndent()
            )
    }

    fun testEnumValueOf() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    public class TestClass {
                        private void testMethod(Class<Enum> clazz) {
                            Enum a = Enum.valueOf(clazz, "A");
                        }
                    }
                    """
                    )
                    .indented()
            )
            .run()
            .expect(
                """
                src/test/pkg/TestClass.java:4: Error: Reflection with Enum.valueOf() is discouraged; use YourEnum.valueOf() if possible. [ReflectiveEnumValueOf]
                        Enum a = Enum.valueOf(clazz, "A");
                                      ~~~~~~~
                1 errors, 0 warnings
                """
                    .trimIndent()
            )
    }

    fun testNoIssues() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    public class TestClass {
                        private void testMethod() {
                            MyEnum[] values = MyEnum.values();
                            MyEnum a = MyEnum.valueOf("A");
                        }
                    }
                    """
                    )
                    .indented(),
                enumStub,
            )
            .run()
            .expectClean()
    }
}
