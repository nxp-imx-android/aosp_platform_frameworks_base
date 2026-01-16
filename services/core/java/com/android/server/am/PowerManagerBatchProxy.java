/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static com.android.server.am.ActivityManagerDebugConfig.TAG_AM;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_WITH_CLASS_NAME;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManagerInternal;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.IntArray;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * A proxy for {@link PowerManagerInternal} that batches UID state change notifications
 * and executes them asynchronously.
 *
 * <p>When {@link #startUidChanges()} is called, the proxy enters a batching mode.
 * All subsequent UID state change notifications (e.g., {@link #uidActive}, {@link #uidIdle})
 * are buffered. When {@link #finishUidChanges()} is called, all buffered operations are posted
 * to a handler for asynchronous execution. Calls made outside of a
 * {@code startUidChanges/finishUidChanges} block are also executed asynchronously but are
 * flushed immediately.</p>
 *
 * <p>This class is thread-safe.</p>
 */
@RavenwoodKeepWholeClass
final class PowerManagerBatchProxy {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "PowerManagerBatchProxy" : TAG_AM;

    @IntDef(prefix = { "OP_" }, value = {
            OP_START_UID_CHANGES,
            OP_FINISH_UID_CHANGES,
            OP_UID_ACTIVE,
            OP_UID_IDLE,
            OP_UID_GONE,
            OP_UPDATE_UID_PROC_STATE,
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface PowerManagerOp {}

    /**
     * Operation codes for each {@link PowerManagerInternal} method.
     * Negative values are used to avoid collisions with integer arguments like UIDs or
     * process states.
     */
    private static final int OP_START_UID_CHANGES = -1;
    private static final int OP_FINISH_UID_CHANGES = -2;
    private static final int OP_UID_ACTIVE = -3;
    private static final int OP_UID_IDLE = -4;
    private static final int OP_UID_GONE = -5;
    private static final int OP_UPDATE_UID_PROC_STATE = -6;

    private final Runnable mFlushRunnable = this::handleFlush;
    private final @NonNull PowerManagerInternal mLocalPowerManager;
    private final @NonNull Handler mHandler;

    /** The staging queue for operations awaiting a flush. */
    @GuardedBy("this")
    private IntArray mStagingOps = new IntArray();
    /**
     * The queue used for flushing operations to {@link PowerManagerInternal}.
     * This queue is accessed exclusively on the handler thread and does not require a lock.
     */
    private IntArray mFlushingOps = new IntArray();

    /**
     * Whether we are currently batching operations (between startUidChanges and finishUidChanges).
     * If true, operations are queued but not flushed until finishUidChanges is called.
     * If false, each operation triggers an immediate flush.
     */
    @GuardedBy("this")
    private boolean mBatchingActive = false;

    PowerManagerBatchProxy(@NonNull PowerManagerInternal localPowerManager,
            @NonNull Looper looper) {
        mLocalPowerManager = localPowerManager;
        mHandler = new Handler(looper);
    }

    /**
     * Signals the beginning of a series of UID changes.
     * <p>Subsequent calls to UID state modification methods will be buffered until
     * {@link #finishUidChanges()} is called.</p>
     */
    void startUidChanges() {
        if (!Flags.batchPowerManagerCalls()) {
            mLocalPowerManager.startUidChanges();
            return;
        }

        synchronized (this) {
            mBatchingActive = true;
            mStagingOps.add(OP_START_UID_CHANGES);
        }
    }

    /**
     * Signals the end of a series of UID changes.
     * <p>All buffered operations since the corresponding {@link #startUidChanges()} call are
     * posted for asynchronous execution.</p>
     */
    void finishUidChanges() {
        if (!Flags.batchPowerManagerCalls()) {
            mLocalPowerManager.finishUidChanges();
            return;
        }

        synchronized (this) {
            mStagingOps.add(OP_FINISH_UID_CHANGES);
            mBatchingActive = false;
        }
        mHandler.post(mFlushRunnable);
    }

    /**
     * Notifies PowerManager that a UID has become active.
     * <p>This operation is buffered if called within a {@code start/finishUidChanges} block.</p>
     */
    void uidActive(int uid) {
        if (!Flags.batchPowerManagerCalls()) {
            mLocalPowerManager.uidActive(uid);
            return;
        }
        enqueueOp(OP_UID_ACTIVE, uid);
    }

    /**
     * Notifies PowerManager that a UID has become idle.
     * <p>This operation is buffered if called within a {@code start/finishUidChanges} block.</p>
     */
    void uidIdle(int uid) {
        if (!Flags.batchPowerManagerCalls()) {
            mLocalPowerManager.uidIdle(uid);
            return;
        }
        enqueueOp(OP_UID_IDLE, uid);
    }

    /**
     * Notifies PowerManager that a UID is no longer active.
     * <p>This operation is buffered if called within a {@code start/finishUidChanges} block.</p>
     */
    void uidGone(int uid) {
        if (!Flags.batchPowerManagerCalls()) {
            mLocalPowerManager.uidGone(uid);
            return;
        }
        enqueueOp(OP_UID_GONE, uid);
    }

    /**
     * Notifies PowerManager of a process state change for a UID.
     * <p>This operation is buffered if called within a {@code start/finishUidChanges} block.</p>
     */
    void updateUidProcState(int uid, int procState) {
        if (!Flags.batchPowerManagerCalls()) {
            mLocalPowerManager.updateUidProcState(uid, procState);
            return;
        }
        enqueueOp(OP_UPDATE_UID_PROC_STATE, uid, procState);
    }

    /** Enqueues an operation with one integer argument. */
    private void enqueueOp(@PowerManagerOp int op, int arg1) {
        final boolean batchingActive;
        synchronized (this) {
            mStagingOps.add(op);
            mStagingOps.add(arg1);
            batchingActive = mBatchingActive;
        }

        // Post the flush runnable outside the synchronized block to reduce the lock contention
        // while interacting with the Handler.
        if (!batchingActive) {
            mHandler.post(mFlushRunnable);
        }
    }

    /** Enqueues an operation with two integer arguments. */
    private void enqueueOp(@PowerManagerOp int op, int arg1, int arg2) {
        final boolean batchingActive;
        synchronized (this) {
            mStagingOps.add(op);
            mStagingOps.add(arg1);
            mStagingOps.add(arg2);
            batchingActive = mBatchingActive;
        }

        // Post the flush runnable outside the synchronized block to reduce the lock contention
        // while interacting with the Handler.
        if (!batchingActive) {
            mHandler.post(mFlushRunnable);
        }
    }

    /** Swaps the staging and flushing queues, then processes all staged operations. */
    private void handleFlush() {
        // mFlushingOps should always be empty here, as it's cleared at the end of this method
        // and only ever accessed on this handler's thread.
        if (mFlushingOps.size() > 0) {
            Slog.wtf(TAG, "mFlushingOps is not empty before swapping, dropping stale ops."
                    + " mFlushingOps.size=" + mFlushingOps.size()
                    + ", mStagingOps.size=" + mStagingOps.size());
            mFlushingOps.clear();
        }

        synchronized (this) {
            if (mStagingOps.size() == 0) {
                return;
            }

            // Swap the staging and flushing ops so we can process the queue outside
            // the synchronized block, minimizing lock contention.
            final IntArray temp = mStagingOps;
            mStagingOps = mFlushingOps;
            mFlushingOps = temp;
        }

        final int size = mFlushingOps.size();
        int i = 0;
        while (i < size) {
            final int op = mFlushingOps.get(i++);
            switch (op) {
                case OP_START_UID_CHANGES:
                    mLocalPowerManager.startUidChanges();
                    break;
                case OP_FINISH_UID_CHANGES:
                    mLocalPowerManager.finishUidChanges();
                    break;
                case OP_UID_ACTIVE:
                    mLocalPowerManager.uidActive(mFlushingOps.get(i++));
                    break;
                case OP_UID_IDLE:
                    mLocalPowerManager.uidIdle(mFlushingOps.get(i++));
                    break;
                case OP_UID_GONE:
                    mLocalPowerManager.uidGone(mFlushingOps.get(i++));
                    break;
                case OP_UPDATE_UID_PROC_STATE:
                    final int arg1 = mFlushingOps.get(i++);
                    final int arg2 = mFlushingOps.get(i++);
                    mLocalPowerManager.updateUidProcState(arg1, arg2);
                    break;
                default:
                    Slog.wtf(TAG, "Unknown PowerManagerBatchProxy op: " + op);
                    break;
            }
        }
        mFlushingOps.clear();
    }
}
