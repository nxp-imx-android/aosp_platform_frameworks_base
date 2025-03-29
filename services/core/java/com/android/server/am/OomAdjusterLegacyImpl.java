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

import static com.android.server.am.ProcessList.UNKNOWN_ADJ;

import android.annotation.NonNull;
import android.app.ActivityManagerInternal.OomAdjReason;
import android.os.Trace;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ServiceThread;

import java.util.ArrayList;

public class OomAdjusterLegacyImpl extends OomAdjuster {

    OomAdjusterLegacyImpl(ActivityManagerService service, ProcessList processList,
            ActiveUids activeUids, ServiceThread adjusterThread, GlobalState globalState,
            CachedAppOptimizer cachedAppOptimizer, Injector injector) {
        super(service, processList, activeUids, adjusterThread, globalState, cachedAppOptimizer,
                injector);
    }

    @GuardedBy("mService")
    @Override
    void onProcessEndLocked(@NonNull ProcessRecord app) {
        // Empty, the OomAdjusterModernImpl will have an implementation.
    }

    /**
     * Called when the process state is changed outside of the OomAdjuster.
     */
    @GuardedBy("mService")
    @Override
    void onProcessStateChanged(@NonNull ProcessRecord app, int prevProcState) {
        // Empty, the OomAdjusterModernImpl will have an implementation.
    }

    /**
     * Called when the oom adj is changed outside of the OomAdjuster.
     */
    @GuardedBy("mService")
    @Override
    void onProcessOomAdjChanged(@NonNull ProcessRecord app, int prevAdj) {
        // Empty, the OomAdjusterModernImpl will have an implementation.
    }

    @VisibleForTesting
    @Override
    void resetInternal() {
        // Empty, the OomAdjusterModernImpl will have an implementation.
    }

    @GuardedBy("mService")
    @Override
    protected int getInitialAdj(@NonNull ProcessRecord app) {
        return app.mState.getCurAdj();
    }

    @GuardedBy("mService")
    @Override
    protected int getInitialProcState(@NonNull ProcessRecord app) {
        return app.mState.getCurProcState();
    }

    @GuardedBy("mService")
    @Override
    protected int getInitialCapability(@NonNull ProcessRecord app) {
        return app.mState.getCurCapability();
    }

    @GuardedBy("mService")
    @Override
    protected boolean getInitialIsCurBoundByNonBgRestrictedApp(@NonNull ProcessRecord app) {
        // The caller will set the initial value in this implementation.
        return app.mState.isCurBoundByNonBgRestrictedApp();
    }

    @Override
    protected void performUpdateOomAdjLSP(@OomAdjReason int oomAdjReason) {
        final ProcessRecord topApp = mService.getTopApp();
        mProcessStateCurTop = mService.mAtmInternal.getTopProcessState();
        // Clear any pending ones because we are doing a full update now.
        mPendingProcessSet.clear();
        mService.mAppProfiler.mHasPreviousProcess = mService.mAppProfiler.mHasHomeProcess = false;
        updateOomAdjInnerLSP(oomAdjReason, topApp, null, null, true, true);
    }

    @GuardedBy({"mService", "mProcLock"})
    @Override
    protected boolean performUpdateOomAdjLSP(ProcessRecord app, @OomAdjReason int oomAdjReason) {
        final ProcessRecord topApp = mService.getTopApp();

        mLastReason = oomAdjReason;
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, oomAdjReasonToString(oomAdjReason));

        final ProcessStateRecord state = app.mState;

        // Next to find out all its reachable processes
        ArrayList<ProcessRecord> processes = mTmpProcessList;
        ActiveUids uids = mTmpUidRecords;
        mPendingProcessSet.add(app);
        mProcessStateCurTop = enqueuePendingTopAppIfNecessaryLSP();

        boolean containsCycle = collectReachableProcessesLocked(mPendingProcessSet,
                processes, uids);

        // Clear the pending set as they should've been included in 'processes'.
        mPendingProcessSet.clear();

        int size = processes.size();
        if (size > 0) {
            // Update these reachable processes
            updateOomAdjInnerLSP(oomAdjReason, topApp, processes, uids, containsCycle, false);
        } else if (state.getCurRawAdj() == UNKNOWN_ADJ) {
            // In case the app goes from non-cached to cached but it doesn't have other reachable
            // processes, its adj could be still unknown as of now, assign one.
            processes.add(app);
            applyLruAdjust(processes);
            applyOomAdjLSP(app, false, mInjector.getUptimeMillis(),
                    mInjector.getElapsedRealtimeMillis(), oomAdjReason);
        }
        mTmpProcessList.clear();
        mService.clearPendingTopAppLocked();
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        return true;
    }

    @GuardedBy("mService")
    @Override
    protected void performUpdateOomAdjPendingTargetsLocked(@OomAdjReason int oomAdjReason) {
        final ProcessRecord topApp = mService.getTopApp();

        mLastReason = oomAdjReason;
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, oomAdjReasonToString(oomAdjReason));
        mProcessStateCurTop = enqueuePendingTopAppIfNecessaryLSP();

        final ArrayList<ProcessRecord> processes = mTmpProcessList;
        final ActiveUids uids = mTmpUidRecords;
        collectReachableProcessesLocked(mPendingProcessSet, processes, uids);
        mPendingProcessSet.clear();
        synchronized (mProcLock) {
            updateOomAdjInnerLSP(oomAdjReason, topApp, processes, uids, true, false);
        }
        processes.clear();
        mService.clearPendingTopAppLocked();

        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
    }
}
