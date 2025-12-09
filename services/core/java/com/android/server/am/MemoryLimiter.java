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

package com.android.server.am;

import static android.text.TextUtils.formatSimple;

import android.app.ActivityManager;
import android.app.ActivityManager.ProcessState;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.Trace;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.tools.r8.keepanno.annotations.UsedByNative;

import java.util.Objects;

/**
 * This class monitors the amount of memory used by application processes.  Debug data is
 * collected if a process exceeds its limits, and the process may be killed.  The limits (and
 * action) vary by process category.  The java class sends process information into the native
 * layer where the limits are applied.  The native layer notifies the java layer when limits are
 * exceeded.
 *
 * An instance allocates native resources that are only released when the instance is closed.  This
 * behavior supports testing.  In production use, the instance attached to ActivityManagerService
 * lasts until the process exits, which releases all native resources back to the kernel.
 *
 * This class is not thread-safe.  Production code should call APIs while holding the AMS lock.
 * Test code may be single-threaded or must include its own lock.
 */
class MemoryLimiter implements AutoCloseable {
    // The standard logcat tag for this module.
    private static final String TAG = "MemoryLimiter";

    // The trace tag.
    private static final long TRACE_TAG = Trace.TRACE_TAG_ACTIVITY_MANAGER;

    // The trace track.  Keep this aligned with the track in the native layer.
    // LINT.IfChange(traceTrack)
    private static final String TRACE_TRACK = "MemoryLimiter";
    // LINT.ThenChange(/services/core/jni/com_android_server_am_MemoryLimiter.cpp:traceTrack)

    // The limits that this feature monitors.
    // LINT.IfChange(limitTypes)
    // A limit has been breached but which limit is unknown.
    static final int UNKNOWN_LIMIT_TYPE = 0;
    // The memory.high limit has been breached.
    static final int MEMORY_LIMIT_TYPE = 1;
    // LINT.ThenChange(/services/core/jni/com_android_server_am_MemoryLimiter.cpp:limitTypes)

    /**
     * A controller specializes the behavior of an individual MemoryLimiter.
     */
    interface Controller {
         /**
         * Returns true if this controller is enabled and actively managing memory limits.  This
         * can be overridden in test implementations to force the controller to be enabled or
         * disabled, regardless of the feature flag.
         */
        boolean isEnabled();

        // The pid or uid of the object has changed.  Push the update to the native layer.
        void setPidUid(int pid, int uid);

        // The process limit has changed.  Push the update to the native layer.
        void setLimit(int pid, int uid, Long limit);

        // Get the memory limit for the process state.
        Long getStateLimit(@ProcessState int newState);

        // The callback when an over-limit event occurs.
        void onLimitExceeded(int pid, int uid, int limit);

        // Close and release any resources.
        void close();
    }

    /**
     * The controller for the disabled state is a bunch of no-ops.
     */
    static class ControllerDisabled implements Controller {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void setPidUid(int pid, int uid) {
        }

        @Override
        public void setLimit(int pid, int uid, Long limit) {
        }

        @Override
        public Long getStateLimit(@ProcessState int newState) {
            return null;
        }

        @Override
        public void onLimitExceeded(int pid, int uid, int limit) {
        }

        @Override
        public void close() {
        }
    }

    /**
     * The controller for production use when the flag is enabled.  This uses a message queue to
     * handle process updates, which allows the updates to happen outside the AMS lock.
     */
    static class ControllerEnabled implements Controller {

        // A lock to guard the open flag.
        private final Object mLock = new Object();

        // Toggles to false once the Controller is closed.  This is also used as the lock for
        // synchronization.
        private boolean mOpen = true;

        // The native handler.
        private final long mNative;

        // The message queue that distributes calls into the native layer.
        private final Handler mQueue;

        // The opcode to start a process.
        private static final int MESSAGE_START = 0;

        // The opcode to configure a process.  The configuration data is in 'obj'.
        private static final int MESSAGE_CONFIG = 1;

        /**
         * In the constructor, create the native peer and the message queue that will handle all
         * requests directed to the native layer.
         */
        ControllerEnabled() {
            mNative = initLimiter(this);
            mQueue = new Handler(BackgroundThread.getHandler().getLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        synchronized (mLock) {
                            if (!mOpen) {
                                // Getting a message after the controller has been closed is
                                // unexpected but only happens during testing.  Silently ignore
                                // it.
                                return;
                            }
                            final int pid = msg.arg1;
                            final int uid = msg.arg2;
                            final int op = msg.what;
                            switch (op) {
                                case MESSAGE_START ->
                                        onProcessStarted(mNative, pid, uid);

                                case MESSAGE_CONFIG -> {
                                    if (msg.obj != null) {
                                        long limit = (Long) msg.obj;
                                        configureLimit(mNative, pid, uid, limit);
                                    }
                                }

                                default ->
                                        Slog.e(TAG, "invalid message: op=" + op);
                            }
                        }
                    }
                };
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        /**
         * Send a command.
         */
        private void sendCommand(int command, int pid, int uid, Object obj) {
            mQueue.sendMessage(mQueue.obtainMessage(command, pid, uid, obj));
        }

        @Override
        public void setPidUid(int pid, int uid) {
            sendCommand(MESSAGE_START, pid, uid, null);
        }

        @Override
        public void setLimit(int pid, int uid, Long limit) {
            Trace.asyncTraceForTrackBegin(TRACE_TAG, TRACE_TRACK, "newLimit", pid);
            sendCommand(MESSAGE_CONFIG, pid, uid, limit);
        }

        /**
         * Objects containing the app configuration parameters.  The only configuration parameter is
         * memory.high, captured in a Long.
         */

        // Configure maximum memory.
        private static final Long sMemoryMax = -1L;

        @Override
        public Long getStateLimit(@ProcessState int newState) {
            return switch (newState) {
                // Never try to configure a process that does not exist.
                case ActivityManager.PROCESS_STATE_NONEXISTENT -> null;

                // By configuring the maximum all the time, the MemoryLimiter does not have any
                // effect on the system.
                default -> sMemoryMax;
            };
        }

        @UsedByNative
        @Override
        public void onLimitExceeded(int pid, int uid, int limit) {
            Slog.w(TAG, formatSimple("limits exceeded: pid=%d uid=%d limit=%d", pid, uid, limit));
        }

        @Override
        public void close() {
            synchronized (mLock) {
                if (mOpen) {
                    mQueue.removeCallbacksAndMessages(null);
                    closeLimiter(mNative);
                    mOpen = false;
                }
            }
        }
    }

    /**
     * The controller for this processes created from this limiter.
     */
    private final Controller mController;

    /**
     * Initialize the native layer and any maps.  This eventually makes a native call and
     * therefore cannot be invoked before the native libraries are loaded.
     */
    @VisibleForTesting
    MemoryLimiter(Controller controller) {
        mController = controller;
    }

    /**
     * Construct the default memory limiter.
     */
    private static Controller getDefaultController() {
        if (!Flags.memoryLimiterEnable()) {
            // The feature is disabled.
            return new ControllerDisabled();
        } else if (Process.myUid() != Process.SYSTEM_UID) {
            // The feature is not running in a system process, which means this is a test.  The
            // feature must be enabled explicitly by the test method using the constructor that
            // takes a Controller.
            return new ControllerDisabled();
        } else {
            // The feature is enabled and this is system_server.
            return new ControllerEnabled();
        }
    }

    /**
     * Create the default controller, based on the feature flag and the enclosing process.
     */
    MemoryLimiter() {
        this(getDefaultController());
    }

    // The object that tracks the state of an individual process.  It is not static.  Methods in
    // this class are not thread-safe.  Normally, these are called inside the AMS lock.
    class Limiter {

        // The pid that this instance controls.
        private int mPid = 0;
        // The uid that this instance controls.
        private int mUid = 0;
        // The last limit assigned to the process.
        private Long mLimit = null;

        /**
         * Set the pid and uid of the instance.  The instance is created before the pid is known,
         * so both are set at this time, and the native layer is notified that the process has
         * started.
         *
         * This method should be called while holding the AMS lock.  The actual work happens on a
         * handler thread.
         */
        void setPidUid(int pid, int uid) {
            if (!mController.isEnabled()) return;

            mPid = pid;
            mUid = uid;

            if (mPid == 0 || mUid == 0 || mUid < Process.FIRST_APPLICATION_UID) {
                // A zero pid/zero uid is not valid for monitoring.  Do not forward any change to
                // the controller.  The pid may change in the future, so reset the last-known
                // limit.
                mPid = 0;
                mUid = 0;
                mLimit = null;
                return;
            }
            mController.setPidUid(pid, uid);
        }

        /**
         * React to a new procstate.  If the new procstate requires a profile change, notify the
         * controller.
         *
         * This method should be called while holding the AMS lock.  The actual work happens on a
         * handler thread.
         */
        void onProcStateUpdated(@ProcessState int newState) {
            if (!mController.isEnabled()) return;

            // The process is not running, so we cannot assign limits.
            if (mPid == 0) return;

            final Long newLimit = mController.getStateLimit(newState);
            if (newLimit != null && !Objects.equals(mLimit, newLimit)) {
                mLimit = newLimit;
                mController.setLimit(mPid, mUid, mLimit);
            }
        }
    }

    /**
     * Return a new Process object bound to this instance.
     */
    Limiter newLimiter() {
        return new Limiter();
    }

    /**
     * Close the instance.  This is idempotent.
     */
    @VisibleForTesting
    public void close() {
        mController.close();
    }

    /**
     * Native methods.
     */

    /**
     * Initialize the native layer and return a pointer to the native handler.  The controller
     * receives any over-limit events.
     */
    private static native long initLimiter(Controller controller);

    /**
     * Release the native handler.
     */
    private static native void closeLimiter(long servicePtr);

    /**
     * Inform the native layer that a process has started.  No profile is assigned to the process
     * but monitoring starts.  The function returns true on success.
     */
    private static native boolean onProcessStarted(long servicePtr, int pid, int uid);

    /**
     * Request that a process's memory.high be configured to limit.  Negative values for the limit
     * mean "maximum memory".  The function returns true on success.  If the process has not been
     * started, or the process has exited since the last start, the function returns false.
     */
    private static native boolean configureLimit(long servicePtr, int pid, int uid, long limit);
}
