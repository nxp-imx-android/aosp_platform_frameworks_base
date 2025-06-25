/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.os.MessageQueue;
import android.util.Log;

import com.android.ravenwood.common.RavenwoodCommonUtils;
import com.android.ravenwood.common.SneakyThrow;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Utilities for writing (bivalent) ravenwood tests.
 */
public class RavenwoodUtils {
    private RavenwoodUtils() {
    }

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /**
     * Load a JNI library respecting {@code java.library.path}
     * (which reflects {@code LD_LIBRARY_PATH}).
     *
     * <p>{@code libname} must be the library filename without:
     * - directory
     * - "lib" prefix
     * - and the ".so" extension
     *
     * <p>For example, in order to load "libmyjni.so", then pass "myjni".
     *
     * <p>This is basically the same thing as Java's {@link System#loadLibrary(String)},
     * but this API works slightly different on ART and on the desktop Java, namely
     * the desktop Java version uses a different entry point method name
     * {@code JNI_OnLoad_libname()} (note the included "libname")
     * while ART always seems to use {@code JNI_OnLoad()}.
     *
     * <p>This method provides the same behavior on both the device side and on Ravenwood --
     * it uses {@code JNI_OnLoad()} as the entry point name on both.
     */
    public static void loadJniLibrary(String libname) {
        RavenwoodCommonUtils.loadJniLibrary(libname);
    }

    private class MainHandlerHolder {
        static Handler sMainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Returns the main thread handler.
     */
    public static Handler getMainHandler() {
        return MainHandlerHolder.sMainHandler;
    }

    /**
     * Run a Callable on Handler and wait for it to complete.
     */
    @Nullable
    public static <T> T runOnHandlerSync(@NonNull Handler h, @NonNull Callable<T> c) {
        var result = new AtomicReference<T>();
        var thrown = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        h.post(() -> {
            try {
                result.set(c.call());
            } catch (Throwable th) {
                thrown.set(th);
            }
            latch.countDown();
        });
        try {
            latch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting on the Runnable", e);
        }
        var th = thrown.get();
        if (th != null) {
            SneakyThrow.sneakyThrow(th);
        }
        return result.get();
    }


    /**
     * Run a Runnable on Handler and wait for it to complete.
     */
    @Nullable
    public static void runOnHandlerSync(@NonNull Handler h, @NonNull Runnable r) {
        runOnHandlerSync(h, () -> {
            r.run();
            return null;
        });
    }

    /**
     * Run a Callable on main thread and wait for it to complete.
     */
    @Nullable
    public static <T> T runOnMainThreadSync(@NonNull Callable<T> c) {
        return runOnHandlerSync(getMainHandler(), c);
    }

    /**
     * Run a Runnable on main thread and wait for it to complete.
     */
    @Nullable
    public static void runOnMainThreadSync(@NonNull ThrowingRunnable r) {
        runOnHandlerSync(getMainHandler(), () -> {
            r.run();
            return null;
        });
    }

    /**
     * Set by {@link RavenwoodDriver} to run code before {@link #waitForLooperDone(Looper)}.
     */
    static volatile Runnable sPendingExceptionThrower = () -> {};

    /**
     * Wait for a looper to be idle.
     *
     * When running on Ravenwood, this will also throw the pending exception, if any.
     */
    public static void waitForLooperDone(Looper looper) {
        var idler = new Idler();
        looper.getQueue().addIdleHandler(idler);
        idler.waitForIdle();

        sPendingExceptionThrower.run();
    }

    /**
     * Wait for a looper to be idle.
     *
     * When running on Ravenwood, this will also throw the pending exception, if any.
     */
    public static void waitForMainLooperDone() {
        waitForLooperDone(Looper.getMainLooper());
    }

    private static class Idler implements MessageQueue.IdleHandler {
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public boolean queueIdle() {
            mLatch.countDown();
            return false; // One-shot idle handler returns true.
        }

        public boolean waitForIdle() {
            try {
                return mLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.w("Idler", "Interrupted");
                return false;
            }
        }
    }

    /**
     * Wrap the given {@link Supplier} to become memoized.
     *
     * The underlying {@link Supplier} will only be invoked once, and that result will be cached
     * and returned for any future requests.
     */
    static <T> Supplier<T> memoize(ThrowingSupplier<T> supplier) {
        return new Supplier<>() {
            private T mInstance;

            @Override
            public T get() {
                synchronized (this) {
                    if (mInstance == null) {
                        mInstance = create();
                    }
                    return mInstance;
                }
            }

            private T create() {
                try {
                    return supplier.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /** Used by {@link #memoize(ThrowingSupplier)}  */
    public interface ThrowingRunnable {
        /** run the code. */
        void run() throws Exception;
    }

    /** Used by {@link #memoize(ThrowingSupplier)}  */
    public interface ThrowingSupplier<T> {
        /** run the code. */
        T get() throws Exception;
    }
}
