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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Singleton;

import com.android.ravenwood.common.StackTrace;

import org.junit.internal.management.ManagementFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Various utility methods that can be used on the "impl" side.
 */
public class RavenwoodImplUtils {
    private static final String TAG = RavenwoodDriver.TAG;

    private RavenwoodImplUtils() {
    }

    /**
     * Base class for a simple key value cache. Subclass implements {@link #compute}.
     */
    public abstract static class MapCache<K, V> {
        private final Object mLock = new Object();

        private final HashMap<K, V> mCache = new HashMap<>();

        /**
         * Subclass implements it.
         *
         * This method may be called for the same key multiple times if access to the same
         * key happens on multiple threads at the same time.
         */
        protected abstract V compute(K key);

        /**
         * @return the value for a given key, using the cache.
         */
        public V get(K key) {
            synchronized (mLock) {
                var cached = mCache.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            V value = compute(key);
            synchronized (mLock) {
                mCache.put(key, value);
            }
            return value;
        }
    }

    /**
     * Helper method to build a {@link StackTrace} object.
     *
     * @param message exception message.
     * @param stackTraceStartClass if set, we remove the stacktrace up to this class.
     * @param removeMatchingFrameToo whether to remove the matching class too.
     */
    public static StackTrace getStackTrace(
            @Nullable String message,
            @Nullable Class<?> stackTraceStartClass,
            boolean removeMatchingFrameToo
    ) {
        // If we're in the middle of a message dispatch, chaing the message poster trace too.
        // TODO: I'd be great if we could show Executor.execute() call too.
        var poster = RavenwoodMessageTracker.getInstance().getCurrentMessagePoster();

        var ret = new StackTrace(message, poster);

        if (stackTraceStartClass != null) {
            ret.removeStackTraceUntil(
                    StackTrace.classPredicate(stackTraceStartClass),
                    removeMatchingFrameToo);
        }

        return ret;
    }

    /**
     * @return comment line arguments to the current JVM.
     */
    public static List<String> getJvmArguments() {
        // Note, we use the wrapper in JUnit4, not the actual class (
        // java.lang.management.ManagementFactory), because we can't see the later at the build
        // because this source file is compiled for the device target, where ManagementFactory
        // doesn't exist.
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    private static volatile Boolean sIsDebuggerAttached;

    /**
     * @return if the debugger is attached.
     */
    public static boolean isDebuggerAttached() {
        if (sIsDebuggerAttached == null) {
            var ret = false;
            for (String arg : getJvmArguments()) {
                if (arg.startsWith("-agentlib:jdwp=")) {
                    ret = true;
                    break;
                }
            }
            sIsDebuggerAttached = ret;
        }
        return sIsDebuggerAttached;
    }

    /**
     * Async handler on the main thread. Used to call a dump() method without getting blocked
     * by sync barriers.
     */
    private static final Singleton<Handler> sMainAsyncHandler = new Singleton<>() {
        @Override
        protected Handler create() {
            return new Handler(Looper.getMainLooper(), null, true);
        }
    };

    /**
     * Use this to call a dump() method on the main thread. If the calling method is _not_
     * the main thread, we can apply a timeout. But if the calling thread is the main thread,
     * the timeout won't apply.
     */
    public static String callDumpOnMainThreadWithTimeout(
            @NonNull Consumer<PrintStream> dumper,
            int timeoutMillis) {
        var bst = new ByteArrayOutputStream(1024 * 4);
        var pst = new PrintStream(bst, /* autoflush= */ true);
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                dumper.accept(pst);
            } else {
                RavenwoodUtils.runOnHandlerSync(sMainAsyncHandler.get(), () -> {
                    dumper.accept(pst);
                    return null;
                }, timeoutMillis);
            }
        } catch (Throwable th) {
            var msg = "Exception detected while running dump: " + th.getMessage();
            Log.w(TAG, msg, th);
            RavenwoodErrorHandler.onWarningDetected(msg);
        }
        return bst.toString();
    }
}
