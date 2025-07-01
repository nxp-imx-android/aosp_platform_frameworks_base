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

package com.android.server.signalcollector;

import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;
import static android.app.ActivityManager.UID_OBSERVER_PROCSTATE;
import static android.app.ActivityManager.UID_OBSERVER_GONE;

import static com.android.server.SystemService.PHASE_ACTIVITY_MANAGER_READY;

import android.app.ActivityManager;
import android.app.ActivityManager.ProcessState;
import android.app.IActivityManager;
import android.app.UidObserver;
import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;
import com.android.server.SystemService.BootPhase;

/**
 * Service for managing signal collectors and tracking process state for anomaly
 * detection.
 */
public final class SignalCollectorService extends SystemService {
    private static final String TAG = "SignalCollectorManagerService";

    private final Injector mInjector;

    // A map of uid to its {@link ProcessState}.
    @GuardedBy("mUidProcessState")
    private final SparseIntArray mUidProcessState = new SparseIntArray();
    private final SignalCollectorManagerInternalImpl mInternal =
        new SignalCollectorManagerInternalImpl();

    public SignalCollectorService(Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting
    SignalCollectorService(Context context, Injector injector) {
        super(context);
        mInjector = injector;
    }

    @Override
    public void onStart() {
        registerUidObserver();
        publishLocalService(SignalCollectorManagerInternal.class, getInternal());
    }

    private final UidObserver mUidObserver = new UidObserver() {
        @Override
        public void onUidGone(int uid, boolean disabled) {
            synchronized (mUidProcessState) {
                mUidProcessState.delete(uid);
            }
        }

        @Override
        public void onUidStateChanged(
            int uid, int processState, long procStateSeq, int capability) {
            synchronized (mUidProcessState) {
                mUidProcessState.put(uid, processState);
            }
        }
    };

    private void registerUidObserver() {
        try {
            mInjector.getActivityManager().registerUidObserver(
                mUidObserver,
                UID_OBSERVER_PROCSTATE | UID_OBSERVER_GONE,
                PROCESS_STATE_UNKNOWN,
                /* caller */ null);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to register UidObserver", e);
        }
    }

    @VisibleForTesting
    SignalCollectorManagerInternal getInternal() {
        return mInternal;
    }

    private final class SignalCollectorManagerInternalImpl extends SignalCollectorManagerInternal {
        @Override
        @ProcessState
        public int getProcessState(int uid) {
            synchronized (mUidProcessState) {
                return mUidProcessState.get(uid, PROCESS_STATE_UNKNOWN);
            }
        }
    }

    @VisibleForTesting
    static class Injector {
        IActivityManager getActivityManager() {
            return ActivityManager.getService();
        }
    }
}
