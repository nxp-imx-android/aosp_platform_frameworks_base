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

import static com.android.ravenwoodtest.coretest.RavenwoodMainThreadTest.assertHasMessageWasPostedHereStackTraceAsCause;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.NoRavenizer;
import android.platform.test.ravenwood.RavenwoodErrorHandler;
import android.platform.test.ravenwood.RavenwoodUnsupportedApiException;
import android.platform.test.ravenwood.RavenwoodUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;


@NoRavenizer // This class shouldn't be executed with RavenwoodAwareTestRunner.
public class RavenwoodRunnerExecutionTest extends RavenwoodRunnerTestBase {
    /**
     * Test around exceptions on handler threads.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadExceptionAndwaitForMainLooperDoneTest
    testStarted: testMainThreadException(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadExceptionAndwaitForMainLooperDoneTest)
    testFailure: Uncaught exception detected on thread Ravenwood:Main. *** Continue running remaining tests ***
    testFinished: testMainThreadException(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadExceptionAndwaitForMainLooperDoneTest)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadExceptionAndwaitForMainLooperDoneTest
    testSuiteFinished: classes
    testRunFinished: 1,1,0,0
    """)
    // CHECKSTYLE:ON
    public static class MainThreadExceptionAndwaitForMainLooperDoneTest {

        @Test
        public void testMainThreadException() throws Exception {
            var h = new Handler(Looper.getMainLooper());
            h.post(() -> {
                throw new RuntimeException("Intentional exception on the main thread!");
            });

            // This will wait for the looper idle, and checks for a pending exception and throws
            // if any. So the remaining code shouldn't be executed.
            try {
                RavenwoodUtils.waitForMainLooperDone();
                RavenwoodErrorHandler.maybeThrowPendingRecoverableUncaughtException();
            } catch (Throwable th) {
                // Ensure that the exception has MessageWasPostedHereStackTrace as a "cause".
                assertHasMessageWasPostedHereStackTraceAsCause(th, "testMainThreadException");

                throw th;
            }

            fail("Shouldn't reach here");
        }
    }

    /**
     * Test around exceptions on handler threads.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadExceptionAndPostTest
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadExceptionAndPostTest)
    testFailure: Uncaught exception detected on thread Ravenwood:Main. *** Continue running remaining tests ***
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadExceptionAndPostTest)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$MainThreadExceptionAndPostTest
    testSuiteFinished: classes
    testRunFinished: 1,1,0,0
    """)
    // CHECKSTYLE:ON
    @org.junit.Ignore // TODO(b/459868484) This test is flaky. Fix it and remove this line.
    public static class MainThreadExceptionAndPostTest {
        @Test
        public void test1() throws Exception {
            var h = new Handler(Looper.getMainLooper());
            h.post(() -> {
                throw new RuntimeException("Intentional exception on the main thread!");
            });
            // Because the above exception happens first, this message shouldn't be
            // executed, because before Looper executes each message, we check for a pending
            // exception and prevents running farther messages.
            h.post(() -> {
                setError(new RuntimeException("Shouldn't reach here"));
            });
            RavenwoodUtils.waitForMainLooperDone();
            RavenwoodErrorHandler.maybeThrowPendingRecoverableUncaughtException();
        }
    }

    /**
     * Assertion failure on a worker thread.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$BgThreadFailureTest
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$BgThreadFailureTest)
    testFailure: Uncaught exception detected on thread Thread-1. *** Continue running remaining tests ***
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$BgThreadFailureTest)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$BgThreadFailureTest
    testSuiteFinished: classes
    testRunFinished: 1,1,0,0
    """)
    // CHECKSTYLE:ON
    public static class BgThreadFailureTest {
        @Test
        public void test1() throws Exception {
            var t = new Thread(() -> {
                fail("Expected Exception");
            });
            t.start();
            t.join();

            try {
                var e = RavenwoodErrorHandler.getPendingRecoverableUncaughtException();
                assertThat(e).isNotNull();
                assertThat(e.getCause()).isNotNull();
                assertThat(e.getCause().getMessage()).isEqualTo("Expected Exception");
            } catch (Throwable th) {
                setError(th);
            }
        }
    }

    /**
     * RavenwoodUnsupportedApiException on a worker thread.
     */
    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$BgThreadUnsupportedApiTest
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$BgThreadUnsupportedApiTest)
    testFailure: Uncaught exception detected on thread Thread-0. *** Continue running remaining tests ***
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$BgThreadUnsupportedApiTest)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerExecutionTest$BgThreadUnsupportedApiTest
    testSuiteFinished: classes
    testRunFinished: 1,1,0,0
    """)
    // CHECKSTYLE:ON
    public static class BgThreadUnsupportedApiTest {
        @Test
        public void test1() throws Exception {
            var t = new Thread(() -> {
                throw new RavenwoodUnsupportedApiException("[test] ");
            });
            t.start();
            t.join();

            try {
                var e = RavenwoodErrorHandler.getPendingRecoverableUncaughtException();
                assertThat(e).isNotNull();
                assertThat(e.getCause()).isNotNull();
                assertThat(e.getCause()).isInstanceOf(RavenwoodUnsupportedApiException.class);
            } catch (Throwable th) {
                setError(th);
            }
        }
    }
}
