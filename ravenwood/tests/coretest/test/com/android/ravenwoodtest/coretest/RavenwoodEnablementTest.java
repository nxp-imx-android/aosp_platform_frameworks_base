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
package com.android.ravenwoodtest.coretest;

import static android.platform.test.ravenwood.RavenwoodEnablementChecker.REALLY_DISABLED_PATTERN;
import static android.platform.test.ravenwood.RavenwoodEnablementChecker.RUN_DISABLED_TESTS;

import static org.junit.Assert.fail;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnabledOnRavenwood;
import android.platform.test.annotations.NoRavenizer;
import android.platform.test.ravenwood.RavenwoodEnablementChecker;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTestBase;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@NoRavenizer
public class RavenwoodEnablementTest extends RavenwoodRunnerTestBase {

    private static final String ENABLEMENT_POLICY = """
            # Enable all tests by default
            RavenwoodCoreTest:* true  # inline comments should work

            # Disable only the method TestPolicy#testDisabled
            com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy#testDisabled false

            # Disable the entire TestPolicyDisableClass class
            com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicyDisableClass false
            """;

    @BeforeClass
    public static void beforeClass() {
        RavenwoodEnablementChecker.setTestEnablementPolicy(ENABLEMENT_POLICY);
    }

    @AfterClass
    public static void afterClass() {
        // Clear the test enablement policy
        RavenwoodEnablementChecker.setTestEnablementPolicy("");
    }

    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$NormalTest
    testStarted: testEnabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$NormalTest)
    testFinished: testEnabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$NormalTest)
    testStarted: testDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$NormalTest)
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: testDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$NormalTest)
    testSuiteFinished: com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$NormalTest
    testSuiteFinished: classes
    testRunFinished: 2,0,1,0
    """)
    // CHECKSTYLE:ON
    public static class NormalTest {
        @Test
        @DisabledOnRavenwood
        public void testDisabled() {
            fail("This should not run");
        }

        @Test
        public void testEnabled() {
        }
    }

    /**
     * Class-level disable should skip the entire class, even method-level overrides shouldn't work.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: ClassDisable(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$ClassDisable)
    testIgnored: ClassDisable(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$ClassDisable)
    testSuiteFinished: ClassDisable(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$ClassDisable)
    testSuiteFinished: classes
    testRunFinished: 0,0,0,1
    """)
    // CHECKSTYLE:ON
    @DisabledOnRavenwood
    public static class ClassDisable {
        @Test
        public void testDisabled() {
            fail("This should not run");
        }

        @Test
        @EnabledOnRavenwood
        public void testEnabled() {
            fail("This should not run");
        }
    }

    @DisabledOnRavenwood
    public static class ClassDisableBase {}

    /**
     * Class-level disable in parent class should skip the entire class.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: InheritDisable(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisable)
    testIgnored: InheritDisable(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisable)
    testSuiteFinished: InheritDisable(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisable)
    testSuiteFinished: classes
    testRunFinished: 0,0,0,1
    """)
    // CHECKSTYLE:ON
    public static class InheritDisable extends ClassDisableBase {
        @Test
        public void testDisabled() {
            fail("This should not run");
        }

        @Test
        @EnabledOnRavenwood
        public void testEnabled() {
            fail("This should not run");
        }
    }

    /**
     * Class-level enablement should override disable in parent class.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisableOverride
    testStarted: testEnabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisableOverride)
    testFinished: testEnabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisableOverride)
    testStarted: testDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisableOverride)
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: testDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisableOverride)
    testSuiteFinished: com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$InheritDisableOverride
    testSuiteFinished: classes
    testRunFinished: 2,0,1,0
    """)
    // CHECKSTYLE:ON
    @EnabledOnRavenwood
    public static class InheritDisableOverride {
        @Test
        @DisabledOnRavenwood
        public void testDisabled() {
            fail("This should not run");
        }

        @Test
        public void testEnabled() {
        }
    }

    /**
     * Test for "RAVENWOOD_RUN_DISABLED_TESTS".
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$RunDisabledTests
    testStarted: testEnabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$RunDisabledTests)
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: testEnabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$RunDisabledTests)
    testStarted: testDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$RunDisabledTests)
    testFinished: testDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$RunDisabledTests)
    testStarted: testReallyDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$RunDisabledTests)
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: testReallyDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$RunDisabledTests)
    testSuiteFinished: com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$RunDisabledTests
    testSuiteFinished: classes
    testRunFinished: 3,0,2,0
    """)
    // CHECKSTYLE:ON
    public static class RunDisabledTests {

        private static boolean sRunDisabledTests;
        private static Pattern sReallyDisabledPattern;

        @BeforeClass
        public static void beforeClass() {
            sRunDisabledTests = RUN_DISABLED_TESTS;
            sReallyDisabledPattern = REALLY_DISABLED_PATTERN;
            RUN_DISABLED_TESTS = true;
            REALLY_DISABLED_PATTERN = Pattern.compile("\\#testReallyDisabled$");
        }

        @AfterClass
        public static void afterClass() {
            RUN_DISABLED_TESTS = sRunDisabledTests;
            REALLY_DISABLED_PATTERN = sReallyDisabledPattern;
        }

        /**
         * This will be executed due to it being "disabled".
         */
        @Test
        @DisabledOnRavenwood
        public void testDisabled() {
        }

        /**
         * This will not be executed due to it being "enabled".
         */
        @Test
        public void testEnabled() {
            fail("This should not run");
        }

        /**
         * This will still not be executed due to the "really disabled" pattern.
         */
        @Test
        @DisabledOnRavenwood
        public void testReallyDisabled() {
            fail("This should not run");
        }
    }

    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy
    testStarted: testEnabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy)
    testFinished: testEnabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy)
    testStarted: testDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy)
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: testDisabled(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy)
    testStarted: testDisabledByAnnotation(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy)
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: testDisabledByAnnotation(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy)
    testSuiteFinished: com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicy
    testSuiteFinished: classes
    testRunFinished: 3,0,2,0
    """)
    // CHECKSTYLE:ON
    public static class TestPolicy {
        @Test
        public void testDisabled() {
            fail("This should be disabled by policy file");
        }

        @Test
        @DisabledOnRavenwood
        public void testDisabledByAnnotation() {
            fail("This should be disabled by policy file");
        }

        @Test
        public void testEnabled() {
        }
    }

    @RunWith(AndroidJUnit4.class)
    //CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: TestPolicyDisableClass(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicyDisableClass)
    testIgnored: TestPolicyDisableClass(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicyDisableClass)
    testSuiteFinished: TestPolicyDisableClass(com.android.ravenwoodtest.coretest.RavenwoodEnablementTest$TestPolicyDisableClass)
    testSuiteFinished: classes
    testRunFinished: 0,0,0,1
    """)
    //CHECKSTYLE:ON
    public static class TestPolicyDisableClass {
        @Test
        public void testDisabled() {
            fail("This should be disabled by policy file");
        }

        @Test
        public void testEnabled() {
            fail("This should be disabled by policy file");
        }
    }
}
