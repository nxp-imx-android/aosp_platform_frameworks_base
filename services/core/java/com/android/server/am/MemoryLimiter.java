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
import android.util.Slog;

import com.android.internal.annotations.Keep;
import com.android.internal.os.BackgroundThread;

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

    private static boolean enableMemoryLimiter() {
        return Flags.memoryLimiterEnable();
    }

    // The static profile list.  Order is important: the list is passed to the native layer and
    // profiles are later referenced by their index in this list.  The current (empty) list is
    // just a placeholder.
    private static final String[] sProfiles = new String[0];

    // The "no profile" profile.  This value is never a valid index into sProfiles.
    private static final int NO_PROFILE = -1;

    // Return the task profile that should be assigned to an app in the give state.  The return
    // value must be NO_PROFILE (there is no profile) or a valid index into sProfiles.
    private static int processStateToProfile(@ProcessState int processState) {
        switch (processState) {
            // Never try to configure a process that does not exist.
            case ActivityManager.PROCESS_STATE_NONEXISTENT:
                return NO_PROFILE;

            // This method is currently a stub, until actual profiles are defined.
            default:
                return NO_PROFILE;
        }
    }

    /**
     * The limiter queue uses existing Message fields to encode its data.  In all cases arg1 is
     * the pid and arg2 is the uid.  The operation is encoded in 'what':
     *  -1      means start the process
     *  n >= 0  means apply profile n to the process
     */

    // Start a process.
    private static final int MESSAGE_START = -1;

    // The first profile.
    private static final int MESSAGE_PROFILE_BEGIN = 0;

    // The last profile, plus 1.
    private static final int MESSAGE_PROFILE_END = sProfiles.length;

    // The message queue that distributes calls into the native layer.
    private static Handler sQueue;

    /**
     * Initialize the native layer and any maps.  This eventually makes a native call and
     * therefore cannot be invoked before the native libraries are loaded.
     */
    static void init() {
        sQueue = new Handler(BackgroundThread.getHandler().getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    final int pid = msg.arg1;
                    final int uid = msg.arg2;
                    final int op = msg.what;
                    if (op == MESSAGE_START) {
                        onProcessStarted(pid, uid);
                    } else if (op >= MESSAGE_PROFILE_BEGIN && op < MESSAGE_PROFILE_END) {
                        configureProfile(pid, uid, op);
                    } else {
                        Slog.e(TAG, "invalid message: op=" + op);
                    }

                }
            };
        initLimiter(sProfiles);
    }

    // The pid that this instance controls.
    private int mPid = 0;
    // The uid that this instance controls.
    private int mUid = 0;
    // The last profile assigned to the process.
    private int mProfile = NO_PROFILE;

    /**
     * Send a command.
     */
    private void sendCommand(int command) {
        sQueue.sendMessage(sQueue.obtainMessage(command, mPid, mUid));
    }

    /**
     * Set the pid and uid of the instance.  The instance is created before the pid is known, so
     * both are set at this time, and the native layer is notified that the process has started.
     */
    void setPidUid(int pid, int uid) {
        if (!enableMemoryLimiter()) return;

        mPid = pid;
        mUid = uid;

        if (mPid == 0 || mUid == 0) {
            // A zero pid/zero uid is not valid for monitoring.  Do not try to configure the
            // native layer.  The pid may change in the future, so reset the last-known profile.
            mPid = 0;
            mUid = 0;
            mProfile = NO_PROFILE;
            return;
        }
        sendCommand(MESSAGE_START);
    }

    /**
     * Record a new procstate.  If the new procstate requires a profile change, notify the native
     * layer.
     */
    void onProcStateUpdated(@ProcessState int newState) {
        if (!enableMemoryLimiter()) return;

        // The process is not running, so we cannot assign profiles.
        if (mPid == 0) return;

        final int newProfile = processStateToProfile(newState);
        if (newProfile != NO_PROFILE && mProfile != newProfile) {
            sendCommand(newProfile);
            mProfile = newProfile;
        }
    }

    /**
     * The callback from the native layer.
     */
    @Keep
    private static void onLimitExceeded(int pid, int uid, int limit) {
        Slog.w(TAG, formatSimple("limits exceeded: pid=%d uid=%d limit=%d", pid, uid, limit));
    }

    /**
     * Native method stubs.
     */

    /**
     * Initialize the native layer.  This configures the profiles that will be used and starts the
     * native monitoring thread.
     */
    private static void initLimiter(String[] profiles) {
    }

    /**
     * Inform the native layer that a process has started.  No profile is assigned to the process
     * but monitoring starts.  The function returns true on success.
     */
    private static boolean onProcessStarted(int pid, int uid) {
        return true;
    }

    /**
     * Request that a process be configured to the n'th profile.  The function returns true on
     * success.  If the process has not been started, or the process has exited since the last
     * start, the function returns false.
     */
    private static boolean configureProfile(int pid, int uid, int profile) {
        return true;
    }
}
