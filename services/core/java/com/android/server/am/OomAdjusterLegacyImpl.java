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

import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_EMPTY;

import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_UID_OBSERVERS;
import static com.android.server.am.ActivityManagerService.TAG_UID_OBSERVERS;
import static com.android.server.am.ProcessList.UNKNOWN_ADJ;

import android.annotation.NonNull;
import android.app.ActivityManagerInternal.OomAdjReason;
import android.os.Trace;
import android.util.Slog;

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

    /**
     * Update OomAdj for all processes within the given list (could be partial), or the whole LRU
     * list if the given list is null; when it's partial update, each process's client proc won't
     * get evaluated recursively here.
     *
     * <p>Note: If the given {@code processes} is not null, the expectation to it is, the caller
     * must have called {@link collectReachableProcessesLocked} on it.
     */
    @GuardedBy({"mService", "mProcLock"})
    private void updateOomAdjInnerLSP(@OomAdjReason int oomAdjReason, final ProcessRecord topApp,
            ArrayList<ProcessRecord> processes, ActiveUids uids, boolean potentialCycles,
            boolean startProfiling) {
        final boolean fullUpdate = processes == null;
        final ArrayList<ProcessRecord> activeProcesses = fullUpdate
                ? mProcessList.getLruProcessesLOSP() : processes;
        ActiveUids activeUids = uids;
        if (activeUids == null) {
            final int numUids = mActiveUids.size();
            activeUids = mTmpUidRecords;
            activeUids.clear();
            for (int i = 0; i < numUids; i++) {
                UidRecord uidRec = mActiveUids.valueAt(i);
                activeUids.put(uidRec.getUid(), uidRec);
            }
        }

        mLastReason = oomAdjReason;
        if (startProfiling) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, oomAdjReasonToString(oomAdjReason));
        }
        final long now = mInjector.getUptimeMillis();
        final long nowElapsed = mInjector.getElapsedRealtimeMillis();
        final long oldTime = now - mConstants.mMaxEmptyTimeMillis;
        final int numProc = activeProcesses.size();

        mAdjSeq++;
        if (fullUpdate) {
            mNewNumServiceProcs = 0;
            mNewNumAServiceProcs = 0;
        }

        // Reset state in all uid records.
        resetUidRecordsLsp(activeUids);

        boolean retryCycles = false;
        boolean computeClients = fullUpdate || potentialCycles;

        // need to reset cycle state before calling computeOomAdjLSP because of service conns
        for (int i = numProc - 1; i >= 0; i--) {
            ProcessRecord app = activeProcesses.get(i);
            final ProcessStateRecord state = app.mState;
            state.setReachable(false);
            // No need to compute again it has been evaluated in previous iteration
            if (state.getAdjSeq() != mAdjSeq) {
                state.setContainsCycle(false);
                state.setCurRawProcState(PROCESS_STATE_CACHED_EMPTY);
                state.setCurRawAdj(UNKNOWN_ADJ);
                state.setSetCapability(PROCESS_CAPABILITY_NONE);
                state.resetCachedInfo();
                state.setCurBoundByNonBgRestrictedApp(false);
            }
        }
        mProcessesInCycle.clear();
        for (int i = numProc - 1; i >= 0; i--) {
            ProcessRecord app = activeProcesses.get(i);
            final ProcessStateRecord state = app.mState;
            if (!app.isKilledByAm() && app.getThread() != null) {
                state.setProcStateChanged(false);
                app.mOptRecord.setLastOomAdjChangeReason(oomAdjReason);
                // It won't enter cycle if not computing clients.
                computeOomAdjLSP(app, UNKNOWN_ADJ, topApp, fullUpdate, now, false,
                        computeClients, oomAdjReason, true);
                // if any app encountered a cycle, we need to perform an additional loop later
                retryCycles |= state.containsCycle();
                // Keep the completedAdjSeq to up to date.
                state.setCompletedAdjSeq(mAdjSeq);
            }
        }

        if (mCacheOomRanker.useOomReranking()) {
            mCacheOomRanker.reRankLruCachedAppsLSP(mProcessList.getLruProcessesLSP(),
                    mProcessList.getLruProcessServiceStartLOSP());
        }

        if (computeClients) { // There won't be cycles if we didn't compute clients above.
            // Cycle strategy:
            // - Retry computing any process that has encountered a cycle.
            // - Continue retrying until no process was promoted.
            // - Iterate from least important to most important.
            int cycleCount = 0;
            while (retryCycles && cycleCount < 10) {
                cycleCount++;
                retryCycles = false;

                for (int i = 0; i < numProc; i++) {
                    ProcessRecord app = activeProcesses.get(i);
                    final ProcessStateRecord state = app.mState;
                    if (!app.isKilledByAm() && app.getThread() != null && state.containsCycle()) {
                        state.decAdjSeq();
                        state.decCompletedAdjSeq();
                    }
                }

                for (int i = 0; i < numProc; i++) {
                    ProcessRecord app = activeProcesses.get(i);
                    final ProcessStateRecord state = app.mState;
                    if (!app.isKilledByAm() && app.getThread() != null && state.containsCycle()) {
                        if (computeOomAdjLSP(app, UNKNOWN_ADJ, topApp, true, now,
                                true, true, oomAdjReason, true)) {
                            retryCycles = true;
                        }
                    }
                }
            }
        }
        mProcessesInCycle.clear();

        applyLruAdjust(mProcessList.getLruProcessesLOSP());

        postUpdateOomAdjInnerLSP(oomAdjReason, activeUids, now, nowElapsed, oldTime, true);

        if (startProfiling) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    private void resetUidRecordsLsp(@NonNull ActiveUids activeUids) {
        // Reset state in all uid records.
        for (int  i = activeUids.size() - 1; i >= 0; i--) {
            final UidRecord uidRec = activeUids.valueAt(i);
            if (DEBUG_UID_OBSERVERS) {
                Slog.i(TAG_UID_OBSERVERS, "Starting update of " + uidRec);
            }
            uidRec.reset();
        }
    }
}
