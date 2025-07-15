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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.hoststubgen.hosthelper.HostTestUtils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ravenwood.RavenwoodRuntimeNative;
import com.android.ravenwood.common.SneakyThrow;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Provides a method call hook that prints almost all (see below) the framework methods being
 * called with indentation.
 *
 * Enable this method call logging by adding the following lines to the options file.
 * (frameworks/base/ravenwood/texts/ravenwood-standard-options.txt)

   --default-method-call-hook
     android.platform.test.ravenwood.RavenwoodMethodCallLogger.logMethodCall

 *
 * We don't log methods that are trivial, uninteresting, or would be too noisy.
 * e.g. we don't want to log any logging related methods or collection APIs.
 *
 * This class also dumps all the called method names in the
 * {@link #CALLED_METHOD_POLICY_FILE} file in the form of a policy file.
 * Optionally, if $RAVENWOOD_METHOD_DUMP_REASON_FILTER is defined, the method policy dump
 * will only contain methods with filter reasons matching it as a regex.
 */
public class RavenwoodMethodCallLogger {
    private static final String TAG = "RavenwoodMethodCallLogger";

    private static final boolean LOG_ALL_METHODS = "1".equals(
            System.getenv("RAVENWOOD_METHOD_LOG_NO_FILTER"));

    /** The policy file is created with this filename. */
    private static final String CALLED_METHOD_POLICY_FILE = "/tmp/ravenwood-called-methods.txt";

    /**
     * If set, we filter methods by applying this regex on the HostStubGen "filter reason"
     * when generating the policy file.
     */
    private static final String CALLED_METHOD_DUMP_REASON_FILTER_RE = System.getenv(
            "RAVENWOOD_METHOD_DUMP_REASON_FILTER");

    /** It's a singleton, except we create different instances for unit tests. */
    @VisibleForTesting
    public RavenwoodMethodCallLogger(boolean logAllMethods) {
        mLogAllMethods = logAllMethods;
    }

    /** Singleton instance */
    private static final RavenwoodMethodCallLogger sInstance =
            new RavenwoodMethodCallLogger(LOG_ALL_METHODS);

    /**
     * @return the singleton instance.
     */
    public static RavenwoodMethodCallLogger getInstance() {
        return sInstance;
    }

    /** Entry point for HostStubGen generated code, which needs to be static.*/
    public static void logMethodCall(
            Class<?> methodClass,
            String methodName,
            String methodDesc
    ) {
        getInstance().onMethodCalled(methodClass, methodName, methodDesc);
    }


    /** We don't want to log anything before ravenwood is initialized. This flag controls it.*/
    private volatile boolean mEnabled = false;

    private volatile PrintStream mOut = System.out;

    private final boolean mLogAllMethods;

    private static class MethodDesc {
        public final String name;
        public final String desc;
        private String mReason;

        public MethodDesc(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        public void setReason(String reason) {
            mReason = reason;
        }

        public String getReason() {
            return mReason;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MethodDesc that)) return false;
            return Objects.equals(name, that.name) && Objects.equals(desc, that.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, desc);
        }
    }

    /** Stores all called methods. */
    @GuardedBy("sAllMethods")
    private final Map<Class<?>, Set<MethodDesc>> mAllMethods = new HashMap<>();

    /** Return the current thread's call nest level. */
    @VisibleForTesting
    public int getNestLevel() {
        var st = Thread.currentThread().getStackTrace();
        return st.length;
    }

    /** Information about the current thread. */
    private class ThreadInfo {
        /**
         * We save the current thread's nest call level here and use that as the initial level.
         * We do it because otherwise the nest level would be too deep by the time test
         * starts.
         */
        public final int mInitialNestLevel = getNestLevel();

        /**
         * A nest level where shouldLog() returned false.
         * Once it's set, we ignore all calls deeper than this.
         */
        public int mDisabledNestLevel = Integer.MAX_VALUE;
    }

    private final ThreadLocal<ThreadInfo> mThreadInfo = ThreadLocal.withInitial(ThreadInfo::new);

    /** Classes that should be logged. Uses a map for fast lookup. */
    private static final HashSet<Class<?>> sIgnoreClasses = new HashSet<>();
    static {
        // The following classes are not interesting...
        sIgnoreClasses.add(android.util.Log.class);
        sIgnoreClasses.add(android.util.Slog.class);
        sIgnoreClasses.add(android.util.EventLog.class);
        sIgnoreClasses.add(android.util.TimingsTraceLog.class);

        sIgnoreClasses.add(android.util.SparseArray.class);
        sIgnoreClasses.add(android.util.SparseIntArray.class);
        sIgnoreClasses.add(android.util.SparseLongArray.class);
        sIgnoreClasses.add(android.util.SparseBooleanArray.class);
        sIgnoreClasses.add(android.util.SparseDoubleArray.class);
        sIgnoreClasses.add(android.util.SparseSetArray.class);
        sIgnoreClasses.add(android.util.SparseArrayMap.class);
        sIgnoreClasses.add(android.util.LongSparseArray.class);
        sIgnoreClasses.add(android.util.LongSparseLongArray.class);
        sIgnoreClasses.add(android.util.LongArray.class);

        sIgnoreClasses.add(android.text.FontConfig.class);

        sIgnoreClasses.add(android.os.SystemClock.class);
        sIgnoreClasses.add(android.os.Trace.class);
        sIgnoreClasses.add(android.os.LocaleList.class);
        sIgnoreClasses.add(android.os.Build.class);
        sIgnoreClasses.add(android.os.SystemProperties.class);
        sIgnoreClasses.add(android.os.UserHandle.class);
        sIgnoreClasses.add(android.os.MessageQueue.class);

        sIgnoreClasses.add(com.android.internal.util.Preconditions.class);

        sIgnoreClasses.add(android.graphics.FontListParser.class);
        sIgnoreClasses.add(android.graphics.ColorSpace.class);

        sIgnoreClasses.add(android.graphics.fonts.FontStyle.class);
        sIgnoreClasses.add(android.graphics.fonts.FontVariationAxis.class);

        sIgnoreClasses.add(com.android.internal.compat.CompatibilityChangeInfo.class);
        sIgnoreClasses.add(com.android.internal.os.LoggingPrintStream.class);

        sIgnoreClasses.add(android.os.ThreadLocalWorkSource.class);

        // Following classes *may* be interesting for some purposes, but the initialization is
        // too noisy...
        sIgnoreClasses.add(android.graphics.fonts.SystemFonts.class);
    }

    /**
     * Return if a class should be ignored. Uses {link #sIgnoreCladsses}, but
     * we ignore more classes.
     */
    @VisibleForTesting
    public boolean shouldIgnoreClass(Class<?> clazz) {
        if (mLogAllMethods) {
            return false;
        }
        if (sIgnoreClasses.contains(clazz)) {
            return true;
        }
        // Let's also ignore collection-ish classes in android.util.
        if (java.util.Collection.class.isAssignableFrom(clazz)
                || java.util.Map.class.isAssignableFrom(clazz)
                || java.util.Iterator.class.isAssignableFrom(clazz)
        ) {
            if ("android.util".equals(clazz.getPackageName())) {
                return true;
            }
            return false;
        }

        switch (clazz.getSimpleName()) {
            case "EventLogTags":
                return true;
        }

        // Following are classes that can't be referred to here directly.
        // e.g. AndroidPrintStream is package-private, so we can't use its "class" here.
        switch (clazz.getName()) {
            case "com.android.internal.os.AndroidPrintStream":
                return true;
        }
        if (clazz.getPackageName().startsWith("repackaged.services.com.android.server.compat")) {
            return true;
        }
        return false;
    }

    private boolean shouldLog(
            Class<?> methodClass,
            String methodName,
            @SuppressWarnings("UnusedVariable") String methodDescriptor
    ) {
        // Should we ignore this class?
        if (shouldIgnoreClass(methodClass)) {
            return false;
        }
        // Is it a nested class in a class that should be ignored?
        var host = methodClass.getNestHost();
        if (host != methodClass && shouldIgnoreClass(host)) {
            return false;
        }

        var className = methodClass.getName();

        // Ad-hoc ignore list. They'd be too noisy.
        if ("create".equals(methodName)
                // We may apply jarjar, so use endsWith().
                && className.endsWith("com.android.server.compat.CompatConfig")) {
            return false;
        }

        var pkg = methodClass.getPackageName();
        if (pkg.startsWith("android.icu")) {
            return false;
        }

        return true;
    }

    /**
     * Call this to enable logging.
     */
    public void enable(@NonNull PrintStream out) {
        mEnabled = true;
        mOut = Objects.requireNonNull(out);

        // It's called from the test thread (Java's main thread). Because we're already
        // in deep nest calls, we initialize the initial nest level here.
        mThreadInfo.get();
    }

    /** Called when a method is called. */
    public void onMethodCalled(
            @NonNull Class<?> methodClass,
            @NonNull String methodName,
            @NonNull String methodDesc
    ) {
        if (!mEnabled) {
            return;
        }
        synchronized (mAllMethods) {
            var set = mAllMethods.computeIfAbsent(methodClass, (k) -> new HashSet<>());
            set.add(new MethodDesc(methodName, methodDesc));
        }
        var log = buildMethodCallLogLine(methodClass, methodName, methodDesc,
                Thread.currentThread());
        if (log != null) {
            mOut.print(log);
        }
    }

    /** Inner method exposed for testing. */
    @VisibleForTesting
    @Nullable
    public String buildMethodCallLogLine(
            @NonNull Class<?> methodClass,
            @NonNull String methodName,
            @NonNull String methodDesc,
            @NonNull Thread mThread
    ) {
        final var ti = mThreadInfo.get();
        final int nestLevel = getNestLevel() - ti.mInitialNestLevel;

        // Once shouldLog() returns false, we just ignore all deeper calls.
        if (ti.mDisabledNestLevel < nestLevel) {
            return null; // Still ignore.
        }
        final boolean shouldLog = shouldLog(methodClass, methodName, methodDesc);

        if (!shouldLog) {
            ti.mDisabledNestLevel = nestLevel;
            return null;
        }
        ti.mDisabledNestLevel = Integer.MAX_VALUE;

        var sb = new StringBuilder();
        sb.append("# [");
        sb.append(getRawThreadId());
        sb.append(": ");
        sb.append(mThread.getName());
        sb.append("]: ");
        sb.append("[@");
        sb.append(String.format("%2d", nestLevel));
        sb.append("] ");
        for (int i = 0; i < nestLevel; i++) {
            sb.append("  ");
        }
        sb.append(methodClass.getName() + "." + methodName + methodDesc);
        sb.append('\n');
        return sb.toString();
    }

    /** To be overridden for unit tests */
    @VisibleForTesting
    public int getRawThreadId() {
        return RavenwoodRuntimeNative.gettid();
    }

    /**
     * Print all called methods in the form of "policy" file.
     */
    public void dumpAllCalledMethods() {
        dumpAllCalledMethodsForFileInner(
                CALLED_METHOD_POLICY_FILE, CALLED_METHOD_DUMP_REASON_FILTER_RE);
    }

    /**
     * Print all called methods in the form of "policy" file.
     */
    @VisibleForTesting
    public void dumpAllCalledMethodsForFileInner(@NonNull String filename,
            @Nullable String reasonFilterRegex) {
        Supplier<OutputStream> opener = () -> {
            try {
                return new FileOutputStream(filename);
            } catch (FileNotFoundException e) {
                SneakyThrow.sneakyThrow(e);
                return null;
            }
        };
        dumpAllCalledMethodsInner(opener, reasonFilterRegex, filename);
    }

    /** Inner method exposed for testing. */
    @VisibleForTesting
    public void dumpAllCalledMethodsInner(@NonNull Supplier<OutputStream> opener,
            @Nullable String resonFilterRegex, @NonNull String outputFileNameForLogging) {
        if (!mEnabled) {
            return;
        }

        synchronized (mAllMethods) {
            if (mAllMethods.isEmpty()) {
                return;
            }
            // "Filter reason" filter.
            final Predicate<String> reasonFilter;
            if (resonFilterRegex == null || resonFilterRegex.isEmpty()) {
                reasonFilter = (reason) -> true;
            } else {
                var pat = Pattern.compile(resonFilterRegex);

                reasonFilter = (reason) -> reason != null && pat.matcher(reason).find();
            }

            var classCount = 0;
            var methodCount = 0;
            try (PrintWriter wr = new PrintWriter(new BufferedOutputStream(opener.get()))) {
                for (var clazz : mAllMethods.keySet().stream()
                        .sorted(Comparator.comparing(Class::getName))
                        .toList()) {
                    classCount++;

                    var classMethods = mAllMethods.get(clazz);
                    // Set the reasons.
                    for (var m : classMethods) {
                        m.setReason(getMethodFilterReason(clazz, m.name, m.desc));
                    }

                    var methods = mAllMethods.get(clazz).stream()
                            .filter(m -> reasonFilter.test(m.getReason()))
                            .sorted(Comparator.comparing((MethodDesc a) -> a.name)
                                    .thenComparing(a -> a.desc))
                            .toList();

                    if (methods.isEmpty()) {
                        continue;
                    }

                    wr.print("class ");
                    wr.print(clazz.getName());
                    wr.print("\tkeep");
                    wr.println();
                    for (var method : methods) {
                        methodCount++;

                        wr.print("    method ");
                        wr.print(method.name);
                        wr.print(method.desc);
                        wr.print("\tkeep");

                        var reason = method.getReason();
                        if (reason != null && !reason.isEmpty()) {
                            wr.print("\t# ");
                            wr.print(reason);
                        }

                        wr.println();
                    }
                    wr.println();
                }
                Log.i(TAG, String.format("Wrote called methods to %s (%d classes, %d methods)",
                        outputFileNameForLogging, classCount, methodCount));
            } catch (Exception e) {
                Log.w(TAG, "Exception while dumping called methods", e);
            }
        }
    }

    /**
     * Find a specified method, and find its "reason" from the HostStubGen annotation.
     */
    @Nullable
    private static String getMethodFilterReason(
            @NonNull Class<?> clazz,
            @NonNull String methodName,
            @NonNull String methodDesc) {
        // Special case: If the method is "<clinit>", we can't get annotations from it,
        // so let's just use the class's reason instead.
        if ("<clinit>".equals(methodName)) {
            return HostTestUtils.getHostStubGenAnnotationReason(clazz);
        }

        // Find the method, and extract the reason from the annotation, if any.
        var m = RavenwoodAsmUtils.getMethodOrNull(clazz, methodName, methodDesc);
        if (m == null) {
            return null;
        }
        return HostTestUtils.getHostStubGenAnnotationReason(m);
    }
}
