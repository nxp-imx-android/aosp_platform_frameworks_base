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

import static android.os.Process.INVALID_PID;
import static android.os.Process.INVALID_UID;
import static android.os.Process.FIRST_APPLICATION_UID;
import static android.text.TextUtils.formatSimple;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManager.ProcessState;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.Trace;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.MemInfoReader;
import com.android.tools.r8.keepanno.annotations.UsedByNative;

import java.io.PrintWriter;
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
     * A convenience function that maps limit types to strings.
     */
    static String limitTypeToString(int type) {
        return switch (type) {
            case 0 -> "unknown";
            case 1 -> "memory.high";
            default -> "unexpected";
        };
    }

    /**
     * A controller specializes the behavior of an individual MemoryLimiter.
     */
    @UsedByNative
    interface Controller {
         /**
         * Returns true if this controller is enabled and actively managing memory limits.  This
         * can be overridden in test implementations to force the controller to be enabled or
         * disabled, regardless of the feature flag.
         */
        boolean isEnabled();

        // The pid or uid of the object has changed.  Push the update to the native layer.
        void setPidUid(int pid, int uid, @Nullable String pkg);

        // The process limit has changed.  Push the update to the native layer.
        void setLimit(int pid, int uid, Long limit);

        // Get the memory limit for the process state.
        Long getStateLimit(@ProcessState int newState);

        // The callback when an over-limit event occurs.
        @UsedByNative
        void onLimitExceeded(int pid, int uid, int limit, @Nullable String pkg);

        // Block or unblock the limiter from monitoring/configuring the UID.
        void ignoreUid(int uid, boolean ignore);

        // Close and release any resources.
        void close();

        // The controller status, for debug and reports.
        void dump(PrintWriter pw);
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
        public void setPidUid(int pid, int uid, @Nullable String pkg) {
        }

        @Override
        public void setLimit(int pid, int uid, Long limit) {
        }

        @Override
        public Long getStateLimit(@ProcessState int newState) {
            return null;
        }

        @Override
        public void onLimitExceeded(int pid, int uid, int limit, @Nullable String pkg) {
        }

        @Override
        public void ignoreUid(int uid, boolean ignore) {
        }

        @Override
        public void close() {
        }

        @Override
        public void dump(PrintWriter pw) {
            pw.println("disabled");
        }
    }

    /**
     * The controller for production use when the flag is enabled.  This uses a message queue to
     * handle process updates, which allows the updates to happen outside the AMS lock.  Since
     * everything happens in the message handler, no locks are required.
     */
    @UsedByNative
    static class ControllerEnabled implements Controller {

        // A lock for information that is shared between the status() method and the handler.  The
        // lock is only taken one of those two places.
        private final Object mLock = new Object();

        // The message queue that distributes calls into the native layer.
        private final Handler mQueue;

        // The opcode to start a process.
        private static final int MESSAGE_START = 0;

        // The opcode to configure a process.  The configuration data is in 'obj'.
        private static final int MESSAGE_CONFIG = 1;

        // The opcode to ignore a UID.  Whatever is in arg2 (the uid field) is ignored.  Pass a
        // negative value to ignore nothing (since real UIDs are non-negative).
        private static final int MESSAGE_IGNORE = 2;

        // The opcode to close the controller.
        private static final int MESSAGE_CLOSE = 3;

        // Well-known memory limits.
        private static final Long MAX_MEMORY = -1L;     // Maximum memory
        private final Long mMemoryVisible;
        private final Long mMemoryNotVisible;

        // The ignore list.  The code supports exactly one ignored uid.  The invalid uid never
        // matches a uid, so that value turns off ignoring.
        @GuardedBy("mLock")
        private int mIgnoredUid = INVALID_UID;

        /**
         * In the constructor, create the native peer and the message queue that will handle all
         * requests directed to the native layer.
         */
        ControllerEnabled() {
            mQueue = new Handler(BackgroundThread.getHandler().getLooper()) {
                    // Toggles to false once the Controller is closed.
                    private boolean mOpen = true;

                    // The native handler.
                    private final long mNative = initLimiter(ControllerEnabled.this);

                    @Override
                    public void handleMessage(Message msg) {
                        if (!mOpen) {
                            // Getting a message after the controller has been closed is
                            // unexpected but only happens during testing.  Silently ignore
                            // it.
                            return;
                        }
                        final int pid = msg.arg1;
                        final int uid = msg.arg2;
                        final int op = msg.what;
                        synchronized (mLock) {
                            switch (op) {
                                case MESSAGE_START -> {
                                    if (uid != mIgnoredUid) {
                                        String pkg = (String) msg.obj;
                                        onProcessStarted(mNative, pid, uid, pkg);
                                    }
                                }

                                case MESSAGE_CONFIG -> {
                                    if (msg.obj != null && uid != mIgnoredUid) {
                                        long limit = (Long) msg.obj;
                                        configureLimit(mNative, pid, uid, limit);
                                    }
                                }

                                case MESSAGE_IGNORE -> {
                                    // This message is only issued during testing.
                                    String oldValue = ignoredUid();
                                    mIgnoredUid = uid;
                                    Slog.i(TAG, "ignoring " + ignoredUid() + " was " + oldValue);
                                }

                                case MESSAGE_CLOSE -> {
                                    closeLimiter(mNative);
                                    mOpen = false;
                                }

                                default ->
                                        Slog.e(TAG, "invalid message: op=" + op);
                            }
                        }
                    }
                };

            if (Flags.memoryLimiterDefaultAppLimits()) {
                MemInfoReader memInfo = new MemInfoReader();
                memInfo.readMemInfo();
                long vmem = memInfo.getTotalSize();
                // Visible apps get 50% of memory.  Others get 25% of memory.
                mMemoryVisible = vmem / 2;
                mMemoryNotVisible = vmem / 4;
                final long meg = 1024 * 1024;
            } else {
                mMemoryVisible = MAX_MEMORY;
                mMemoryNotVisible = MAX_MEMORY;
            }
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
        public void setPidUid(int pid, int uid, @Nullable String pkg) {
            sendCommand(MESSAGE_START, pid, uid, pkg);
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

        @Override
        public Long getStateLimit(@ProcessState int newState) {
            // Never try to configure a process that does not exist or is cached.
            if (newState == ActivityManager.PROCESS_STATE_UNKNOWN
                    || newState == ActivityManager.PROCESS_STATE_NONEXISTENT) {
                return null;
            } else if (ActivityManager.isProcStateCached(newState)) {
                return null;
            } else if (ActivityManager.isProcStateJankPerceptible(newState)) {
                return mMemoryVisible;
            } else {
                return mMemoryNotVisible;
            }
        }

        @UsedByNative
        @Override
        public void onLimitExceeded(int pid, int uid, int limit, @Nullable String pkg) {
            Slog.i(TAG, formatSimple("limits exceeded: pid=%d uid=%d limit=%s pkg=%s",
                                pid, uid, limitTypeToString(limit), pkg));
        }

        @Override
        public void ignoreUid(int uid, boolean ignore) {
            sendCommand(MESSAGE_IGNORE, INVALID_PID, ignore ? uid : INVALID_UID, null);
        }

        @Override
        public void close() {
            sendCommand(MESSAGE_CLOSE, INVALID_PID, INVALID_UID, null);
        }

        // A simple function to string-ify the ignored UID.
        private String ignoredUid() {
            return (mIgnoredUid == INVALID_UID) ? "none" : Integer.toString(mIgnoredUid);
        }

        @Override
        public void dump(PrintWriter pw) {
            final long meg = 1024 * 1024;
            synchronized (mLock) {
                pw.format("enabled low=%dMB high=%dMB ignored=%s\n",
                        mMemoryNotVisible / meg, mMemoryVisible / meg, ignoredUid());
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

        // The package associated with this instance.
        private final String mPackage;

        // The pid that this instance controls.
        private int mPid = INVALID_PID;
        // The uid that this instance controls.
        private int mUid = INVALID_UID;
        // The last limit assigned to the process.
        private Long mLimit = null;

        /**
         * Construct the instance with a fixed package name.
         */
        Limiter(@Nullable String pkg) {
            mPackage = pkg;
        }

        /**
         * Return true if the process should be monitored and limited.
         */
        private boolean shouldMonitor() {
            return (mPid != INVALID_PID && mUid != INVALID_UID && mUid >= FIRST_APPLICATION_UID);
        }

        /**
         * Return true if the object is ready to manage a process.  The pid and uid must be valid
         * and the UID must belong to the application name space.  This method is called whenever
         * the pid or uid changes.
         */
        private void maybeStart() {
            if (!shouldMonitor()) return;
            mLimit = null;
            mController.setPidUid(mPid, mUid, mPackage);
        }

        /**
         * Set the UID.  If this is change from the previous pid/uid combination then start the
         * process.
         */
        void setUid(int uid) {
            if (!mController.isEnabled()) return;
            if (uid == mUid) {
                return;
            }
            mUid = uid;
            maybeStart();
        }


        /**
         * Set the pid and uid of the instance.  The instance is created before the pid is known,
         * so both are set at this time, and the native layer is notified that the process has
         * started.  The pid and uid are saved for reuse when the process memory limits is
         * changed.  The package name is passed to the native layer and is not retained by this
         * class.
         *
         * This method should be called while holding the AMS lock.  The actual work happens on a
         * handler thread.
         */
        void setPid(int pid) {
            if (!mController.isEnabled()) return;

            if (pid == mPid) {
                return;
            }
            mPid = pid;
            maybeStart();
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

            // Do not assign limits if the process should not be monitored.
            if (!shouldMonitor()) return;

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
    Limiter newLimiter(@Nullable String pkg) {
        return new Limiter(pkg);
    }

    /**
     * Close the instance.  This is idempotent.
     */
    @VisibleForTesting
    public void close() {
        mController.close();
    }

    /**
     * Return the operational status of the controller.
     */
    boolean isEnabled() {
        return mController.isEnabled();
    }

    /**
     * Display the status of the limiter.
     */
    void dump(PrintWriter pw) {
        mController.dump(pw);
    }

    /**
     * Block or unblock a UID.  This is used in feature testing, when it is important that the
     * limits are controlled by the test process and not by system_server.
     */
    @VisibleForTesting
    void ignoreUid(int uid, boolean ignore) {
        mController.ignoreUid(uid, ignore);
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
    private static native boolean onProcessStarted(long servicePtr, int pid, int uid,
            @Nullable String pkg);

    /**
     * Request that a process's memory.high be configured to limit.  Negative values for the limit
     * mean "maximum memory".  The function returns true on success.  If the process has not been
     * started, or the process has exited since the last start, the function returns false.
     */
    private static native boolean configureLimit(long servicePtr, int pid, int uid, long limit);
}
