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
package com.android.ravenwoodtest.runnercallbacktests;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.NoRavenizer;
import android.platform.test.ravenwood.RavenwoodRuntimeEnvironmentController;
import android.platform.test.ravenwood.RavenwoodUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;


@NoRavenizer // This class shouldn't be executed with RavenwoodAwareTestRunner.
public class RavenwoodRunnerExecutionTest extends RavenwoodRunnerTestBase {
    private static boolean sOrigTolerateUnhandledAsserts;
    private static boolean sOrigTolerateUnhandledExceptions;

    /** Save the TOLERATE_* flags and set them to false. */
    private static void initTolerateFlags() {
        sOrigTolerateUnhandledAsserts =
                RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_ASSERTS;
        sOrigTolerateUnhandledExceptions =
                RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_EXCEPTIONS;

        RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_ASSERTS = false;
        RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_EXCEPTIONS = false;
    }

    /** Restore the original TOLERATE_* flags. */
    private static void restoreTolerateFlags() {
        RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_ASSERTS =
                sOrigTolerateUnhandledAsserts;
        RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_EXCEPTIONS =
                sOrigTolerateUnhandledExceptions;
    }

    private static void ensureMainThreadAlive() {
        var value = new AtomicInteger(0);
        RavenwoodUtils.runOnMainThreadSync(() -> value.set(1));
        assertThat(value.get()).isEqualTo(1);
    }

    /**
     * Make sure TOLERATE_UNHANDLED_ASSERTS works.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadAssertionFailureTest
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadAssertionFailureTest)
    testFailure: Exception detected on thread Ravenwood:Main:  *** Continuing running the remaining tests ***
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadAssertionFailureTest)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadAssertionFailureTest
    testSuiteFinished: classes
    testRunFinished: 1,1,0,0
    """)
    // CHECKSTYLE:ON
    public static class MainThreadAssertionFailureTest {
        @BeforeClass
        public static void beforeClass() {
            initTolerateFlags();

            // Comment it out to test the false case.
            RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_ASSERTS = true;
        }

        @AfterClass
        public static void afterClass() {
            restoreTolerateFlags();
        }

        @Test
        public void test1() throws Exception {
            var h = new Handler(Looper.getMainLooper());
            h.post(() -> fail("failed on the man thread"));

            // If the flag isn't set to true, then the looper would be dead, so don't do it.
            if (RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_ASSERTS) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                ensureMainThreadAlive();
            } else {
                // waitForIdleSync() won't work, so just wait for a bit...
                Thread.sleep(5_000);
            }
        }
    }

    /**
     * Make sure TOLERATE_UNHANDLED_EXCEPTIONS works.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadRuntimeExceptionTest
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadRuntimeExceptionTest)
    testFailure: Exception detected on thread Ravenwood:Main:  *** Continuing running the remaining tests ***
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadRuntimeExceptionTest)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadRuntimeExceptionTest
    testSuiteFinished: classes
    testRunFinished: 1,1,0,0
    """)
    // CHECKSTYLE:ON
    public static class MainThreadRuntimeExceptionTest {
        @BeforeClass
        public static void beforeClass() {
            initTolerateFlags();

            // Comment it out to test the false case.
            RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_EXCEPTIONS = true;
        }

        @AfterClass
        public static void afterClass() {
            restoreTolerateFlags();
        }

        @Test
        public void test1() throws Exception {
            var h = new Handler(Looper.getMainLooper());
            h.post(() -> {
                throw new RuntimeException("exception on the man thread");
            });

            // If the flag isn't set to true, then the looper would be dead, so don't do it.
            if (RavenwoodRuntimeEnvironmentController.TOLERATE_UNHANDLED_EXCEPTIONS) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                ensureMainThreadAlive();
            } else {
                // waitForIdleSync() won't work, so just wait for a bit...
                Thread.sleep(5_000);
            }
        }
    }
}
