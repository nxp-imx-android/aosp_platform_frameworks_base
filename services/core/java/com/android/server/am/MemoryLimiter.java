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
 * This class is not thread-safe.  Production code should call APIs while holding the AMS lock.
 * Test code may be single-threaded or must include its own lock.
 */
class MemoryLimiter {
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

    private static boolean enableMemoryLimiter() {
        return Flags.memoryLimiterEnable();
    }

    /**
     * Objects containing the app configuration parameters.  The only configuration parameter is
     * memory.high, captured in a Long.
     */

    // Configure maximum memory.
    private static final Long sMemoryMax = -1L;

    // Return the memory limit that should be assigned to an app in the give state.
    private static Long processStateToLimit(@ProcessState int processState) {
        return switch (processState) {
            // Never try to configure a process that does not exist.
            case ActivityManager.PROCESS_STATE_NONEXISTENT -> null;

            // This method is currently a stub, until actual limits are defined.
            default -> sMemoryMax;
        };
    }

    /**
     * The limiter queue uses existing Message fields to encode its data.  In all cases arg1 is
     * the pid and arg2 is the uid.  The operation is encoded in 'what':
     *  -1      means start the process
     *  n >= 0  means apply profile n to the process
     */

    // Start a process.
    private static final int MESSAGE_START = 0;

    // Configure a process.
    private static final int MESSAGE_CONFIG = 1;

    // The message queue that distributes calls into the native layer.
    private static Handler sQueue;

    /**
     * Initialize the native layer and any maps.  This eventually makes a native call and
     * therefore cannot be invoked before the native libraries are loaded.
     */
    static void init() {
        if (!enableMemoryLimiter()) return;

        // Initialize the native layer.
        initLimiter();

        sQueue = new Handler(BackgroundThread.getHandler().getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    final int pid = msg.arg1;
                    final int uid = msg.arg2;
                    final int op = msg.what;
                    switch (op) {
                        case MESSAGE_START:
                            onProcessStarted(pid, uid);
                            break;
                        case MESSAGE_CONFIG:
                            if (msg.obj != null) {
                                configureLimit(pid, uid, (Long) msg.obj);
                            }
                            break;
                        default:
                            Slog.e(TAG, "invalid message: op=" + op);
                            break;
                    }
                }
            };
    }

    // The pid that this instance controls.
    private int mPid = 0;
    // The uid that this instance controls.
    private int mUid = 0;
    // The last limit assigned to the process.
    private Long mLimit = null;

    /**
     * Send a command.
     */
    private void sendCommand(int command, Object obj) {
        sQueue.sendMessage(sQueue.obtainMessage(command, mPid, mUid, obj));
    }

    /**
     * Set the pid and uid of the instance.  The instance is created before the pid is known, so
     * both are set at this time, and the native layer is notified that the process has started.
     *
     * This method should be called while holding the AMS lock.  The actual work happens on a
     * handler thread.
     */
    void setPidUid(int pid, int uid) {
        if (!enableMemoryLimiter()) return;

        mPid = pid;
        mUid = uid;

        if (mPid == 0 || mUid == 0 || mUid < Process.FIRST_APPLICATION_UID) {
            // A zero pid/zero uid is not valid for monitoring.  Do not try to configure the native
            // layer.  Also, the feature does not monitor system processes.  The pid may change in
            // the future, so reset the last-known profile.
            mPid = 0;
            mUid = 0;
            mLimit = null;
            return;
        }
        sendCommand(MESSAGE_START, null);
    }

    /**
     * React to a new procstate.  If the new procstate requires a profile change, notify the
     * native layer.  A trace is started if there is a limit change.  The trace ends in the native
     * layer after the new limit has been applied.
     *
     * This method should be called while holding the AMS lock.  The actual work happens on a
     * handler thread.
     */
    void onProcStateUpdated(@ProcessState int newState) {
        if (!enableMemoryLimiter()) return;

        // The process is not running, so we cannot assign limits.
        if (mPid == 0) return;

        final Long newLimit = processStateToLimit(newState);
        if (newLimit != null && !Objects.equals(mLimit, newLimit)) {
            Trace.asyncTraceForTrackBegin(TRACE_TAG, TRACE_TRACK, "newLimit", mPid);
            sendCommand(MESSAGE_CONFIG, newLimit);
            mLimit = newLimit;
        }
    }

    /**
     * The callback from the native layer.
     */
    @UsedByNative
    private static void onLimitExceeded(int pid, int uid, int limit) {
        Slog.w(TAG, formatSimple("limits exceeded: pid=%d uid=%d limit=%d", pid, uid, limit));
    }

    /**
     * Native methods.
     */

    /**
     * Initialize the native layer.  This starts the native monitoring thread.  The method returns
     * the total ram available for processes; this value can be used to create dynamic limits.
     */
    private static native long initLimiter();

    /**
     * Inform the native layer that a process has started.  No profile is assigned to the process
     * but monitoring starts.  The function returns true on success.
     */
    private static native boolean onProcessStarted(int pid, int uid);

    /**
     * Request that a process's memory.high be configured to limit.  Negative values for the limit
     * mean "maximum memory".  The function returns true on success.  If the process has not been
     * started, or the process has exited since the last start, the function returns false.
     */
    private static native boolean configureLimit(int pid, int uid, long limit);
}
