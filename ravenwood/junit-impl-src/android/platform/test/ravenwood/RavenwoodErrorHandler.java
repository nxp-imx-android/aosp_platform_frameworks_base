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
import android.os.Message;
import android.util.Log;

import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.SneakyThrow;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;

import java.util.concurrent.atomic.AtomicReference;

public class RavenwoodErrorHandler {
    private static final String TAG = RavenwoodInternalUtils.TAG;

    /**
     * When enabled, detect uncaught exceptions from background threads.
     */
    static final boolean ENABLE_UNCAUGHT_EXCEPTION_DETECTION =
            !"0".equals(System.getenv("RAVENWOOD_ENABLE_UNCAUGHT_EXCEPTION_DETECTION"));

    /**
     * When enabled, uncaught Assertion exceptions from background threads are tolerated.
     */
    private static final boolean TOLERATE_UNHANDLED_ASSERTS =
            !"0".equals(System.getenv("RAVENWOOD_TOLERATE_UNHANDLED_ASSERTS"));

    /**
     * When enabled, all uncaught exceptions from background threads are tolerated.
     */
    private static final boolean TOLERATE_UNHANDLED_EXCEPTIONS =
            !"0".equals(System.getenv("RAVENWOOD_TOLERATE_UNHANDLED_EXCEPTIONS"));

    private static final boolean DIE_ON_UNCAUGHT_EXCEPTION = false;

    volatile static Description sCurrentDescription;

    private static class RecoverableUncaughtException extends Exception {
        private RecoverableUncaughtException(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String getMessage() {
            return super.getMessage() + " : " + getCause().getMessage();
        }

        static RecoverableUncaughtException create(Throwable th) {
            if (th instanceof RecoverableUncaughtException r) {
                return r;
            }
            var outer = new RecoverableUncaughtException(
                    "Uncaught exception detected on thread " + Thread.currentThread().getName()
                            + ": *** Continuing running the remaining tests ***", th);
            Log.e(TAG, outer.getMessage(), outer);
            return outer;
        }
    }

    /**
     * Return if an exception is benign and okay to continue running the remaining tests.
     */
    private static boolean isThrowableRecoverable(Throwable th) {
        if (th instanceof RecoverableUncaughtException) {
            return true;
        }
        if (TOLERATE_UNHANDLED_ASSERTS
                && (th instanceof AssertionError || th instanceof AssumptionViolatedException)) {
            return true;
        }
        return TOLERATE_UNHANDLED_EXCEPTIONS;
    }

    // Unhandled exception callbacks

    static class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread thread, Throwable inner) {
            if (isThrowableRecoverable(inner)) {
                setPendingRecoverableUncaughtException(inner);
                return;
            }
            setPendingUnrecoverableUncaughtException(thread, inner);
            RavenwoodDriver.doBugreport(thread, inner, DIE_ON_UNCAUGHT_EXCEPTION);
        }
    }

    /**
     * Called by {@link android.os.Handler_ravenwood#onBeforeEnqueue}
     */
    public static void onBeforeEnqueue(@NonNull Message msg) {
        // Check for pending exception, and throw it if any.
        maybeThrowPendingRecoverableUncaughtExceptionNoClear();
        // Track the msg poster in case an exception is thrown later during msg dispatch.
        RavenwoodMessageTracker.getInstance().trackMessagePoster(msg);
    }

    /**
     * Called by {@link android.os.Looper_ravenwood#dispatchMessage}
     */
    public static void dispatchMessage(Message msg) {
        // If there's already an exception caught and pending, don't run any more messages.
        if (hasPendingRecoverableUncaughtException()) {
            return;
        }
        try {
            msg.getTarget().dispatchMessage(msg);
        } catch (Throwable th) {
            var desc = String.format("Detected %s on looper thread %s", th.getClass().getName(),
                    Thread.currentThread());
            RavenwoodDriver.sRawStdErr.println(desc);

            // If it's a tracked message, attach the stacktrace where we posted it as a cause.
            RavenwoodMessageTracker.getInstance().injectPosterAsCause(th, msg);
            if (isThrowableRecoverable(th)) {
                setPendingRecoverableUncaughtException(th);
                return;
            }
            throw th;
        }
    }

    // Unrecoverable exceptions

    /**
     * It's an exception detected from a BG thread (which is not recoverable). Once
     * we detect one, we make the current and all subsequent tests failed.
     */
    private static final AtomicReference<Throwable> sUnrecoverableUncaughtException =
            new AtomicReference<>();

    private static void setPendingUnrecoverableUncaughtException(Thread thread, Throwable th) {
        var msg = String.format(
                "Uncaught exception detected on thread %s, test=%s:"
                        + " %s; Failing all subsequent tests. "
                        + "Run with `RAVENWOOD_TOLERATE_UNHANDLED_EXCEPTIONS=1 atest ...` to "
                        + "force run subsequent tests",
                thread, sCurrentDescription, RavenwoodInternalUtils.getStackTraceString(th));

        var outer = new Exception(msg, th);
        Log.e(TAG, outer.getMessage(), outer);

        sUnrecoverableUncaughtException.compareAndSet(null, outer);
    }

    public static void maybeThrowUnrecoverableUncaughtException() {
        var e = sUnrecoverableUncaughtException.get();
        if (e != null) {
            SneakyThrow.sneakyThrow(e);
        }
    }

    // Recoverable exceptions

    /**
     * This is a "recoverable" uncaught exception from a BG thread. When we detect one,
     * we just make the current test failed, but continue running the subsequent tests normally.
     */
    private static final AtomicReference<Throwable> sPendingRecoverableUncaughtException =
            new AtomicReference<>();

    private static void setPendingRecoverableUncaughtException(Throwable th) {
        sPendingRecoverableUncaughtException.compareAndSet(null,
                RecoverableUncaughtException.create(th));
    }

    private static boolean hasPendingRecoverableUncaughtException() {
        return sPendingRecoverableUncaughtException.get() != null;
    }

    private static void maybeThrowPendingRecoverableUncaughtException(boolean clear) {
        final Throwable pending;
        if (clear) {
            pending = sPendingRecoverableUncaughtException.getAndSet(null);
        } else {
            pending = sPendingRecoverableUncaughtException.get();
        }
        if (pending != null) {
            SneakyThrow.sneakyThrow(pending);
        }
    }

    public static void maybeThrowPendingRecoverableUncaughtExceptionAndClear() {
        maybeThrowPendingRecoverableUncaughtException(true);
    }

    public static void maybeThrowPendingRecoverableUncaughtExceptionNoClear() {
        maybeThrowPendingRecoverableUncaughtException(false);
    }
}
