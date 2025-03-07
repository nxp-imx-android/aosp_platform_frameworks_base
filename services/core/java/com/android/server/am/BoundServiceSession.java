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

import android.app.IBinderSession;
import android.os.Handler;
import android.os.IBinder;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.IntPair;

import java.lang.ref.WeakReference;
import java.util.function.BiConsumer;

/**
 * An implementation of {@link IBinderSession} on top of a {@link ConnectionRecord} that
 * is used to facilitate important binder calls to a bound remote service hosted by a process that
 * is eligible to get frozen by {@link ProcessStateController}.
 */
public class BoundServiceSession implements IBinderSession {
    private static final String TAG = BoundServiceSession.class.getSimpleName();
    private static final int MAGIC_ID = 0xFBD_5E55;
    private static final String TRACE_TRACK = "bound_service_calls";

    // We don't hold a strong reference in case this object is held on for a long time after the
    // binding has gone away. This helps us easily avoid leaks and excess OomAdjuster updates
    // while remaining agnostic to binding state changes. This is also a convenient long-term choice
    // for when we enable a BinderProxy pointing to this from outside of the system process.
    private final WeakReference<ConnectionRecord> mConnection;
    private final BiConsumer<ConnectionRecord, Boolean> mProcessStateUpdater;
    private final Handler mBackgroundHandler;
    private final String mDebugName;

    @VisibleForTesting
    @GuardedBy("this")
    ArrayMap<String, Integer> mCountsByTag = null;

    @VisibleForTesting
    @GuardedBy("this")
    int mTotal = 0;

    BoundServiceSession(BiConsumer<ConnectionRecord, Boolean> processStateUpdater,
            WeakReference<ConnectionRecord> weakCr, String debugName) {
        mBackgroundHandler = BackgroundThread.getHandler();
        mProcessStateUpdater = processStateUpdater;
        mConnection = weakCr;
        mDebugName = debugName;
    }

    private static long getToken(int key) {
        return IntPair.of(MAGIC_ID, key);
    }

    private static boolean isValidToken(long token) {
        return IntPair.first(token) == MAGIC_ID;
    }

    private static int getKeyIndex(long token) {
        return IntPair.second(token);
    }

    @GuardedBy("this")
    private void maybePostProcessStateUpdate() {
        final ConnectionRecord strongCr = mConnection.get();
        if (strongCr == null) {
            Slog.d(TAG, "ConnectionRecord " + mDebugName
                    + " already gone. Possibly the service has unbound.");
            return;
        }
        mBackgroundHandler.post(() -> mProcessStateUpdater.accept(strongCr, mTotal > 0));
    }

    private void logTraceInstant(String message) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_ACTIVITY_MANAGER)) {
            Trace.instantForTrack(Trace.TRACE_TAG_ACTIVITY_MANAGER, TRACE_TRACK, mDebugName
                    + ": " + message);
        }
    }

    @GuardedBy("this")
    private void handleInvalidToken(String errorMessage) {
        // We cannot take any meaningful action. The bookkeeping got corrupt in the client and we
        // cannot tell for which tag. We'll just reset all counts to 0 and propagate the same to
        // the underlying ConnectionRecord. This also ensures that there are no shenanigans that
        // the remote app can perform with the given token to remain unfrozen.
        logTraceInstant(errorMessage);
        Slog.wtfStack(TAG, errorMessage);
        mCountsByTag.clear();
        if (mTotal != 0) {
            mTotal = 0;
            maybePostProcessStateUpdate();
        }
    }

    @Override
    public long binderTransactionStarting(String debugTag) {
        logTraceInstant("+" + debugTag);
        synchronized (this) {
            if (mCountsByTag == null) {
                mCountsByTag = new ArrayMap<>(4);
            }
            mCountsByTag.merge(debugTag, 1, (old, _unused) -> old + 1);
            mTotal++;
            if (mTotal == 1) {
                maybePostProcessStateUpdate();
            }
            return getToken(mCountsByTag.indexOfKey(debugTag));
        }
    }

    @Override
    public void binderTransactionCompleted(long token) {
        synchronized (this) {
            if (!isValidToken(token)) {
                handleInvalidToken("Invalid token " + Long.toHexString(token)
                        + " received in binderTransactionCompleted! Closing all transactions on "
                        + mDebugName);
                return;
            }
            final int keyIndex = getKeyIndex(token);
            if (mCountsByTag.size() <= keyIndex || mCountsByTag.valueAt(keyIndex) <= 0) {
                handleInvalidToken("Bad keyIndex " + keyIndex
                        + " received in binderTransactionCompleted! Closing all transactions on "
                        + mDebugName);
                return;
            }
            logTraceInstant("-" + mCountsByTag.keyAt(keyIndex));
            mCountsByTag.setValueAt(keyIndex, mCountsByTag.valueAt(keyIndex) - 1);
            mTotal--;
            if (mTotal == 0) {
                maybePostProcessStateUpdate();
            }
        }
    }

    void dump(IndentingPrintWriter ipw) {
        ipw.println("Bound service session: " + mDebugName);

        synchronized (this) {
            ipw.increaseIndent();
            ipw.print("Connection present: ");
            ipw.println((mConnection.get() != null) ? "Yes" : "No");
            ipw.print("Ongoing bound service calls: ");
            ipw.println(mTotal);

            if (mCountsByTag != null) {
                ipw.increaseIndent();
                for (int i = 0; i < mCountsByTag.size(); i++) {
                    ipw.print(mCountsByTag.keyAt(i), mCountsByTag.valueAt(i));
                }
                ipw.println();
                ipw.decreaseIndent();
            }

            ipw.decreaseIndent();
        }
    }

    @Override
    public IBinder asBinder() {
        // Only for use within system server.
        return null;
    }
}
