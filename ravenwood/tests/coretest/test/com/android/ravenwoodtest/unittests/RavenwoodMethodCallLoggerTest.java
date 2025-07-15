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
package com.android.ravenwoodtest.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.platform.test.ravenwood.RavenwoodMethodCallLogger;
import android.util.Log;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;

/**
 * Unit tests for {@link RavenwoodMethodCallLogger}.
 *
 * Ideally we want to put it in the same package as {@link RavenwoodMethodCallLogger}
 * and use package-private for @VisibleForTesting methods, but that package would be exempted
 * from Ravenizer, which would be problematic, so we can't use that package.
 *
 * NOTE: the "EXPECTED" strings depend on various implementation details, so we may need to adjust
 * them as we update the implementation.
 */
public class RavenwoodMethodCallLoggerTest {
    private final RavenwoodMethodCallLoggerForTest mTarget = new RavenwoodMethodCallLoggerForTest();

    private static class RavenwoodMethodCallLoggerForTest extends RavenwoodMethodCallLogger {
        RavenwoodMethodCallLoggerForTest() {
            super(/* logAllMethods =*/ false);
        }

        // We always use a fixed TID.
        @Override
        public int getRawThreadId() {
            return 123;
        }

        /** This allows overriding the nest level. */
        public Integer mNestLevel = null;

        @Override
        public int getNestLevel() {
            return (mNestLevel != null) ? mNestLevel : super.getNestLevel();
        }
    }

    private void assertLogged(Class<?> clazz) {
        assertFalse(clazz + " should be logged", mTarget.shouldIgnoreClass(clazz));
    }

    private void assertNotLogged(Class<?> clazz) {
        assertTrue(clazz + " should not be logged", mTarget.shouldIgnoreClass(clazz));
    }

    @Test
    public void testShouldIgnoreClass() {
        assertNotLogged(android.util.Log.class);
        assertNotLogged(android.util.SparseArray.class);
        assertNotLogged(android.util.ArrayMap.class);
        assertNotLogged(android.app.EventLogTags.class);

        assertLogged(android.app.ActivityThread.class);
        assertLogged(android.content.Context.class);
    }

    /**
     * Unit test for buildMethodCallLogLine().
     */
    @Test
    public void testBuildLogLine() {
        Thread thread = new Thread("caller-thread");
        StringBuilder sb = new StringBuilder();

        // Note, buildMethodCallLogLine() doesn't check "mEnabled", so no need to call enable().

        // Here, we should only use public APIs that won't suddenly be removed
        mTarget.mNestLevel = 0;
        sb.append(mTarget.buildMethodCallLogLine(
                Context.class,
                "getPackageName",
                "()Ljava/lang/String;",
                thread));

        mTarget.mNestLevel = 1;
        sb.append(mTarget.buildMethodCallLogLine(
                Context.class,
                "getBasePackageName",
                "()Ljava/lang/String;",
                thread));

        // This shouldn't be logged, so the method should return null.
        mTarget.mNestLevel = 2;
        assertNull(mTarget.buildMethodCallLogLine(
                Log.class,
                "d",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                thread));

        // Called by an ignored method, so this shouldn't be logged either.
        mTarget.mNestLevel = 3;
        assertNull(mTarget.buildMethodCallLogLine(
                Context.class,
                "getBasePackageName",
                "()Ljava/lang/String;",
                thread));

        // This should be logged again.
        mTarget.mNestLevel = 1;
        sb.append(mTarget.buildMethodCallLogLine(
                Context.class,
                "getOpPackgeName",
                "()Ljava/lang/String;",
                thread));
        String expected = """
# [123: caller-thread]: [@ 0] android.content.Context.getPackageName()Ljava/lang/String;
# [123: caller-thread]: [@ 1]   android.content.Context.getBasePackageName()Ljava/lang/String;
# [123: caller-thread]: [@ 1]   android.content.Context.getOpPackgeName()Ljava/lang/String;
                """;
        assertThat(sb.toString().trim()).isEqualTo(expected.trim());
    }

    /**
     * More complete "end-to-end" test that exercises more of the target code.
     *
     * - It doesn't inject the nest level or the caller thread and let RavenwoodMethodCallLogger
     *   figure them out. (That means we can't adjust the nest level, and because
     *   RavenwoodMethodCallLogger gets the nest level from the stacktrace, all the log lines
     *   will get the same nest level.)
     */
    @Test
    public void testEndToEnd() throws Exception {
        // Create PrintStream to store the log output.
        var bos = new ByteArrayOutputStream();

        mTarget.enable(new PrintStream(bos));

        // Here, we should only use public APIs that won't suddenly be removed
        mTarget.onMethodCalled(
                Context.class,
                "getPackageName",
                "()Ljava/lang/String;");
        mTarget.onMethodCalled(
                Log.class,
                "d",
                "(Ljava/lang/String;Ljava/lang/String;)V");
        mTarget.onMethodCalled(
                Context.class,
                "getOpPackgeName",
                "()Ljava/lang/String;");

        // =================================================================
        // Check the log output.
        // Note, for implementation detail reasons, the nest levels show up as negative,
        // because of how we initialize the initial nest level.
        String expected = """
# [123: Ravenwood:Test]: [@-5] android.content.Context.getPackageName()Ljava/lang/String;
# [123: Ravenwood:Test]: [@-5] android.content.Context.getOpPackgeName()Ljava/lang/String;
                """;
        assertThat(bos.toString().trim()).isEqualTo(expected.trim());

        // =================================================================
        // Next, generate a policy file, and check the output again.
        File temp = File.createTempFile("policy-file", ".txt");
        mTarget.dumpAllCalledMethodsForFileInner(temp.getAbsolutePath(), null);

        var policy = Files.readString(temp.toPath());
        expected = """
class android.content.Context	keep
    method getOpPackgeName()Ljava/lang/String;	keep
    method getPackageName()Ljava/lang/String;	keep	# annotation(ThrowButSupported)

class android.util.Log	keep
    method d(Ljava/lang/String;Ljava/lang/String;)V	keep	# class-wide in android/util/Log [inner-reason: class-annotation]
                """;
        assertThat(policy.trim()).isEqualTo(expected.trim());

        // =================================================================
        // Next, we generate the policy file with a filter.
        mTarget.dumpAllCalledMethodsForFileInner(temp.getAbsolutePath(),
                "(ThrowButSupported|class-wide)");

        policy = Files.readString(temp.toPath());
        expected = """
class android.content.Context	keep
    method getPackageName()Ljava/lang/String;	keep	# annotation(ThrowButSupported)

class android.util.Log	keep
    method d(Ljava/lang/String;Ljava/lang/String;)V	keep	# class-wide in android/util/Log [inner-reason: class-annotation]
                """;
        assertThat(policy.trim()).isEqualTo(expected.trim());
    }
}
