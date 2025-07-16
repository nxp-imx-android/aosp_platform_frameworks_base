/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.platform.test.ravenwood;

import static com.android.ravenwood.common.RavenwoodInternalUtils.RAVENWOOD_VERBOSE_LOGGING;

import android.util.Log;

import com.android.ravenwood.common.RavenwoodInternalUtils;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collect test result stats and write them into a CSV file containing the test results.
 *
 * The output file is created as `/tmp/Ravenwood-stats_[TEST-MODULE=NAME]_[TIMESTAMP].csv`.
 * A symlink to the latest result will be created as
 * `/tmp/Ravenwood-stats_[TEST-MODULE=NAME]_latest.csv`.
 *
 * Also responsible for dumping all called methods in the form of policy file, by calling
 * {@link RavenwoodMethodCallLogger#dumpAllCalledMethodsInner()}, if the method call log is enabled.
 */
public class RavenwoodTestStats {
    private static final String TAG = RavenwoodInternalUtils.TAG;
    private static final String HEADER =
            "ClassOrMethod,Module,Class,OuterClass,Method,Passed,Failed,Skipped,DurationMillis";

    private static RavenwoodTestStats sInstance;

    /**
     * @return a singleton instance.
     */
    public static RavenwoodTestStats getInstance() {
        if (sInstance == null) {
            sInstance = new RavenwoodTestStats();
        }
        return sInstance;
    }

    /**
     * Represents a test result.
     */
    public enum Result {
        Passed,
        Failed,
        Skipped,
    }

    public static class Outcome {
        public final Result result;
        public final Duration duration;

        public Outcome(Result result, Duration duration) {
            this.result = result;
            this.duration = duration;
        }

        /** @return 1 if {@link #result} is "passed". */
        public int passedCount() {
            return result == Result.Passed ? 1 : 0;
        }

        /** @return 1 if {@link #result} is "failed". */
        public int failedCount() {
            return result == Result.Failed ? 1 : 0;
        }

        /** @return 1 if {@link #result} is "skipped". */
        public int skippedCount() {
            return result == Result.Skipped ? 1 : 0;
        }
    }

    private final File mOutputFile;
    private final File mOutputSymlinkFile;
    private final PrintWriter mOutputWriter;
    private final String mTestModuleName;

    public final Map<String, Map<String, Outcome>> mStats = new LinkedHashMap<>();

    /** Ctor */
    public RavenwoodTestStats() {
        mTestModuleName = guessTestModuleName();

        var basename = "Ravenwood-stats_" + mTestModuleName + "_";

        // Get the current time
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

        var tmpdir = System.getProperty("java.io.tmpdir");
        mOutputFile = new File(tmpdir, basename + now.format(fmt) + ".csv");

        try {
            mOutputWriter = new PrintWriter(mOutputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create logfile. File=" + mOutputFile, e);
        }

        // Create the "latest" symlink.
        Path symlink = Paths.get(tmpdir, basename + "latest.csv");
        try {
            Files.deleteIfExists(symlink);
            Files.createSymbolicLink(symlink, Paths.get(mOutputFile.getName()));

        } catch (IOException e) {
            throw new RuntimeException("Failed to create logfile. File=" + mOutputFile, e);
        }
        mOutputSymlinkFile = symlink.toFile();

        Log.i(TAG, "Test result stats file: " + mOutputSymlinkFile);

        // Print the header.
        mOutputWriter.println(HEADER);
        mOutputWriter.flush();
    }

    private String guessTestModuleName() {
        // Assume the current directory name is the test module name.
        File cwd;
        try {
            cwd = new File(".").getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get the current directory", e);
        }
        return cwd.getName();
    }

    private void addResult(String className, String methodName,
            Result result, Duration duration) {
        mStats.compute(className, (className_, value) -> {
            if (value == null) {
                value = new LinkedHashMap<>();
            }
            // If the result is already set, don't overwrite it.
            if (!value.containsKey(methodName)) {
                value.put(methodName, new Outcome(result, duration));
            }
            return value;
        });
    }

    /**
     * Call it when a test method is finished.
     */
    private void onTestFinished(String className,
            String testName,
            Result result,
            Duration duration) {
        addResult(className, testName, result, duration);
    }

    /**
     * Dump all the results and clear it.
     */
    private void dumpAllAndClear() {
        for (var entry : mStats.entrySet()) {
            var className = entry.getKey();
            var outcomes = entry.getValue();

            int passed = 0;
            int skipped = 0;
            int failed = 0;
            Duration totalDuration = Duration.ZERO;

            var methods = outcomes.keySet().stream().sorted().toList();

            for (var method : methods) {
                var outcome = outcomes.get(method);

                // Per-method status, with "m".
                mOutputWriter.printf("m,%s,%s,%s,%s,%d,%d,%d,%d\n",
                        mTestModuleName, className, getOuterClassName(className), method,
                        outcome.passedCount(), outcome.failedCount(), outcome.skippedCount(),
                        outcome.duration.toMillis());

                passed += outcome.passedCount();
                skipped += outcome.skippedCount();
                failed += outcome.failedCount();

                totalDuration = totalDuration.plus(outcome.duration);
            }

            // Per-class status, with "c".
            mOutputWriter.printf("c,%s,%s,%s,%s,%d,%d,%d,%d\n",
                    mTestModuleName, className, getOuterClassName(className), "-",
                    passed, failed, skipped, totalDuration.toMillis());
        }
        mOutputWriter.flush();
        mStats.clear();
        Log.i(TAG, "Added result to stats file: " + mOutputSymlinkFile);
    }

    private static String getOuterClassName(String className) {
        // Just delete the '$', because I'm not sure if the className we get here is actaully a
        // valid class name that does exist. (it might have a parameter name, etc?)
        int p = className.indexOf('$');
        if (p < 0) {
            return className;
        }
        return className.substring(0, p);
    }

    private static void createCalledMethodPolicyFile() {
        // Ideally we want to call it only once, when the very last test class finishes,
        // but we don't know which test class is last or not, so let's just do the dump
        // after every test class.
        RavenwoodMethodCallLogger.getInstance().dumpAllCalledMethods();
    }

    public void attachToRunNotifier(RunNotifier notifier) {
        notifier.addListener(mRunListener);
    }

    private final RunListener mRunListener = new RunListener() {
        private Instant mStartTime;

        @Override
        public void testSuiteStarted(Description description) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "testSuiteStarted: " + description);
            }
        }

        @Override
        public void testSuiteFinished(Description description) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "testSuiteFinished: " + description);
            }
        }

        @Override
        public void testRunStarted(Description description) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "testRunStarted: " + description);
            }
        }

        @Override
        public void testRunFinished(org.junit.runner.Result result) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "testRunFinished: " + result);
            }

            createCalledMethodPolicyFile();
            dumpAllAndClear();
        }

        @Override
        public void testStarted(Description description) {
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, "  testStarted: " + description);
            }
            mStartTime = Instant.now();
        }

        private void addResult(
                String className,
                String methodName,
                Result result,
                String logMessage,
                Object messageExtra) {
            var endTime = Instant.now();
            if (RAVENWOOD_VERBOSE_LOGGING) {
                Log.d(TAG, logMessage + messageExtra);
            }

            onTestFinished(className, methodName, result, Duration.between(mStartTime, endTime));
        }

        @Override
        public void testFinished(Description description) {
            // Note: testFinished() is always called, even in failure cases and another callback
            // (e.g. testFailure) has already called. But we just call it anyway because if
            // we already recorded a result to the same metho, we won't overwrite it.
            addResult(description.getClassName(),
                    description.getMethodName(),
                    Result.Passed,
                    "  testFinished: ",
                    description);
        }

        @Override
        public void testFailure(Failure failure) {
            var description = failure.getDescription();
            addResult(description.getClassName(),
                    description.getMethodName(),
                    Result.Failed,
                    "  testFailure: ",
                    failure);
        }

        @Override
        public void testAssumptionFailure(Failure failure) {
            var description = failure.getDescription();
            addResult(description.getClassName(),
                    description.getMethodName(),
                    Result.Skipped,
                    "  testAssumptionFailure: ",
                    failure);
        }

        @Override
        public void testIgnored(Description description) {
            addResult(description.getClassName(),
                    description.getMethodName(),
                    Result.Skipped,
                    "  testIgnored: ",
                    description);
        }
    };
}
