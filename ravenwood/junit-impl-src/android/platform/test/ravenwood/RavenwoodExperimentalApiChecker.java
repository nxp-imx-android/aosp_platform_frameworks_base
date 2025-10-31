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
package android.platform.test.ravenwood;

import com.android.internal.annotations.GuardedBy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class RavenwoodExperimentalApiChecker {
    private RavenwoodExperimentalApiChecker() {
    }

    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static boolean sInitialized;

    @GuardedBy("sLock")
    private static boolean sExperimentalApiEnabled;

    /**
     * A map with key = "method info", value = "number of times the method was called".
     */
    @GuardedBy("sLock")
    private static final Map<MethodInfo, IntRef> sStats = new HashMap<>();

    private record MethodInfo(Class<?> clazz, String name, String desc) {
        MethodInfo(StackWalker.StackFrame frame) {
            this(frame.getDeclaringClass(), frame.getMethodName(), frame.getDescriptor());
        }

        @Override
        public String toString() {
            return clazz.getName() + "#" + name + desc;
        }
    }

    private static class IntRef {
        int i = 0;
    }

    public static boolean isExperimentalApiEnabled() {
        synchronized (sLock) {
            if (!sInitialized) {
                sExperimentalApiEnabled = RavenwoodEnvironment.getInstance()
                        .getBoolEnvVar("RAVENWOOD_ENABLE_EXP_API");
                sInitialized = true;
            }
            return sExperimentalApiEnabled;
        }
    }

    /**
     * Check if experimental APIs are enabled, and if not, throws
     * {@link RavenwoodUnsupportedApiException}.
     */
    public static boolean onExperimentalApiCalled(Class<?> clazz, String method, String desc) {
        synchronized (sLock) {
            sStats.computeIfAbsent(new MethodInfo(clazz, method, desc), k -> new IntRef()).i += 1;
        }
        // Even when experimental APIs are disabled, we don't want to throw from <clinit>.
        // because that'd make the class unloadable. Instead, we return false to skip the rest of
        // the code.
        if ("<clinit>".equals(method)) {
            return isExperimentalApiEnabled();
        }
        onExperimentalApiCalledInner(2);
        return true;
    }

    /**
     * Check if experimental APIs are enabled, and if not, throws
     * {@link RavenwoodUnsupportedApiException}.
     *
     * @param skipStackTraces the number of stack frames to skip to traverse back to
     *                        the "actual" experimental API.
     */
    public static void onExperimentalApiCalled(int skipStackTraces) {
        var walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        var frame = walker.walk(s -> s.skip(skipStackTraces).findFirst().get());
        synchronized (sLock) {
            sStats.computeIfAbsent(new MethodInfo(frame), k -> new IntRef()).i += 1;
        }
        onExperimentalApiCalledInner(skipStackTraces + 1);
    }

    private static void onExperimentalApiCalledInner(int skipStackTraces) {
        if (!isExperimentalApiEnabled()) {
            throw new RavenwoodUnsupportedApiException().skipStackTracesForReason(skipStackTraces);
        }
    }

    /**
     * Print all experimental method call stats.
     */
    public static void dumpExperimentalApiUsage() {
        final String module = RavenwoodEnvironment.getInstance().getTestModuleName();
        final String file = "/tmp/ravenwood-experimental-api-" + module + "-stats.txt";
        try (PrintWriter stats = new PrintWriter(file)) {
            synchronized (sLock) {
                sStats.entrySet().stream()
                        .sorted((a, b) -> b.getValue().i - a.getValue().i)
                        .forEach(e -> stats.printf("%s %d\n", e.getKey(), e.getValue().i));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file=" + file, e);
        }
    }

    private static void ensureCoreTest() {
        if (RavenwoodEnvironment.getInstance().getTestModuleName().equals("RavenwoodCoreTest")) {
            // Okay
            return;
        }
        throw new IllegalStateException("This method is only for internal testing");
    }

    /**
     * Override {@link #sExperimentalApiEnabled}. Only use it in internal testing.
     */
    public static void setExperimentalApiEnabledOnlyForTesting(boolean experimentalApiEnabled) {
        ensureCoreTest();
        sExperimentalApiEnabled = experimentalApiEnabled;
    }
}
