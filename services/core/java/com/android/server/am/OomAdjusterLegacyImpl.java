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

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_BFSL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_TOP;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_ACTIVITY;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_EMPTY;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_RECENT;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
import static android.app.ActivityManager.PROCESS_STATE_LAST_ACTIVITY;
import static android.app.ActivityManager.PROCESS_STATE_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.app.ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND;
import static android.content.Context.BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static android.media.audio.Flags.roForegroundAudioControl;

import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BACKUP;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_OOM_ADJ_REASON;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_UID_OBSERVERS;
import static com.android.server.am.ActivityManagerService.TAG_BACKUP;
import static com.android.server.am.ActivityManagerService.TAG_OOM_ADJ;
import static com.android.server.am.ActivityManagerService.TAG_UID_OBSERVERS;
import static com.android.server.am.PlatformCompatCache.CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY;
import static com.android.server.am.PlatformCompatCache.CACHED_COMPAT_CHANGE_PROCESS_CAPABILITY;
import static com.android.server.am.ProcessCachedOptimizerRecord.SHOULD_NOT_FREEZE_REASON_BINDER_ALLOW_OOM_MANAGEMENT;
import static com.android.server.am.ProcessList.BACKUP_APP_ADJ;
import static com.android.server.am.ProcessList.CACHED_APP_MAX_ADJ;
import static com.android.server.am.ProcessList.CACHED_APP_MIN_ADJ;
import static com.android.server.am.ProcessList.FOREGROUND_APP_ADJ;
import static com.android.server.am.ProcessList.HEAVY_WEIGHT_APP_ADJ;
import static com.android.server.am.ProcessList.HOME_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_LOW_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_MEDIUM_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ;
import static com.android.server.am.ProcessList.PERSISTENT_SERVICE_ADJ;
import static com.android.server.am.ProcessList.PREVIOUS_APP_ADJ;
import static com.android.server.am.ProcessList.SCHED_GROUP_BACKGROUND;
import static com.android.server.am.ProcessList.SCHED_GROUP_DEFAULT;
import static com.android.server.am.ProcessList.SCHED_GROUP_RESTRICTED;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP_BOUND;
import static com.android.server.am.ProcessList.SERVICE_ADJ;
import static com.android.server.am.ProcessList.SERVICE_B_ADJ;
import static com.android.server.am.ProcessList.UNKNOWN_ADJ;
import static com.android.server.am.ProcessList.VISIBLE_APP_ADJ;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal.OomAdjReason;
import android.content.Context;
import android.os.IBinder;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ServiceThread;
import com.android.server.wm.ActivityServiceConnectionsHolder;

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

    @GuardedBy({"mService", "mProcLock"})
    private boolean computeOomAdjLSP(ProcessRecord app, int cachedAdj,
            ProcessRecord topApp, boolean doingAll, long now, boolean cycleReEval,
            boolean computeClients, int oomAdjReason, boolean couldRecurse) {
        final ProcessStateRecord state = app.mState;
        if (couldRecurse) {
            if (mAdjSeq == state.getAdjSeq()) {
                if (state.getAdjSeq() == state.getCompletedAdjSeq()) {
                    // This adjustment has already been computed successfully.
                    return false;
                } else {
                    // The process is being computed, so there is a cycle. We cannot
                    // rely on this process's state.
                    state.setContainsCycle(true);
                    mProcessesInCycle.add(app);

                    return false;
                }
            }
        }

        int prevAppAdj = app.mState.getCurAdj();
        int prevProcState = app.mState.getCurProcState();
        int prevCapability = app.mState.getCurCapability();

        // Remove any follow up update this process might have. It will be rescheduled if still
        // needed.
        app.mState.setFollowupUpdateUptimeMs(NO_FOLLOW_UP_TIME);

        if (app.getThread() == null) {
            state.setAdjSeq(mAdjSeq);
            state.setCurrentSchedulingGroup(SCHED_GROUP_BACKGROUND);
            state.setCurProcState(PROCESS_STATE_CACHED_EMPTY);
            state.setCurRawProcState(PROCESS_STATE_CACHED_EMPTY);
            state.setCurAdj(CACHED_APP_MAX_ADJ);
            state.setCurRawAdj(CACHED_APP_MAX_ADJ);
            state.setCompletedAdjSeq(state.getAdjSeq());
            state.setCurCapability(PROCESS_CAPABILITY_NONE);
            return false;
        }

        state.setAdjTypeCode(ActivityManager.RunningAppProcessInfo.REASON_UNKNOWN);
        state.setAdjSource(null);
        state.setAdjTarget(null);
        if (!couldRecurse || !cycleReEval) {
            // Don't reset this flag when doing cycles re-evaluation.
            state.setNoKillOnBgRestrictedAndIdle(false);
            // If this UID is currently allowlisted, it should not be frozen.
            final UidRecord uidRec = app.getUidRecord();
            app.mOptRecord.setShouldNotFreeze(uidRec != null && uidRec.isCurAllowListed(),
                    ProcessCachedOptimizerRecord.SHOULD_NOT_FREEZE_REASON_UID_ALLOWLISTED, mAdjSeq);
        }

        final int appUid = app.info.uid;
        final int logUid = mService.mCurOomAdjUid;

        final ProcessServiceRecord psr = app.mServices;

        if (state.getMaxAdj() <= FOREGROUND_APP_ADJ) {
            // The max adjustment doesn't allow this app to be anything
            // below foreground, so it is not worth doing work for it.
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making fixed: " + app);
            }
            state.setAdjType("fixed");
            state.setAdjSeq(mAdjSeq);
            state.setCurRawAdj(state.getMaxAdj());
            state.setHasForegroundActivities(false);
            state.setCurrentSchedulingGroup(SCHED_GROUP_DEFAULT);
            state.setCurCapability(PROCESS_CAPABILITY_ALL); // BFSL allowed
            state.setCurProcState(ActivityManager.PROCESS_STATE_PERSISTENT);
            // System processes can do UI, and when they do we want to have
            // them trim their memory after the user leaves the UI.  To
            // facilitate this, here we need to determine whether or not it
            // is currently showing UI.
            state.setSystemNoUi(true);
            if (app == topApp) {
                state.setSystemNoUi(false);
                state.setCurrentSchedulingGroup(SCHED_GROUP_TOP_APP);
                state.setAdjType("pers-top-activity");
            } else if (state.hasTopUi()) {
                // sched group/proc state adjustment is below
                state.setSystemNoUi(false);
                state.setAdjType("pers-top-ui");
            } else if (state.getCachedHasVisibleActivities()) {
                state.setSystemNoUi(false);
            }
            if (!state.isSystemNoUi()) {
                if (isScreenOnOrAnimatingLocked(state)) {
                    // screen on or animating, promote UI
                    state.setCurProcState(ActivityManager.PROCESS_STATE_PERSISTENT_UI);
                    state.setCurrentSchedulingGroup(SCHED_GROUP_TOP_APP);
                } else if (!app.getWindowProcessController().isShowingUiWhileDozing()) {
                    // screen off, restrict UI scheduling
                    state.setCurProcState(PROCESS_STATE_BOUND_FOREGROUND_SERVICE);
                    state.setCurrentSchedulingGroup(SCHED_GROUP_RESTRICTED);
                }
            }
            state.setCurRawProcState(state.getCurProcState());
            state.setCurAdj(state.getMaxAdj());
            state.setCompletedAdjSeq(state.getAdjSeq());
            // if curAdj is less than prevAppAdj, then this process was promoted
            return state.getCurAdj() < prevAppAdj || state.getCurProcState() < prevProcState;
        }

        state.setSystemNoUi(false);

        final int PROCESS_STATE_CUR_TOP = mProcessStateCurTop;

        // Determine the importance of the process, starting with most
        // important to least, and assign an appropriate OOM adjustment.
        int adj;
        int schedGroup;
        int procState;
        int capability = cycleReEval ? app.mState.getCurCapability() : PROCESS_CAPABILITY_NONE;

        boolean hasVisibleActivities = false;
        if (app == topApp && PROCESS_STATE_CUR_TOP == PROCESS_STATE_TOP) {
            // The last app on the list is the foreground app.
            adj = FOREGROUND_APP_ADJ;
            if (mService.mAtmInternal.useTopSchedGroupForTopProcess()) {
                schedGroup = SCHED_GROUP_TOP_APP;
                state.setAdjType("top-activity");
            } else {
                // Demote the scheduling group to avoid CPU contention if there is another more
                // important process which also uses top-app, such as if SystemUI is animating.
                schedGroup = SCHED_GROUP_DEFAULT;
                state.setAdjType("intermediate-top-activity");
            }
            hasVisibleActivities = true;
            procState = PROCESS_STATE_TOP;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making top: " + app);
            }
        } else if (state.isRunningRemoteAnimation()) {
            adj = VISIBLE_APP_ADJ;
            schedGroup = SCHED_GROUP_TOP_APP;
            state.setAdjType("running-remote-anim");
            procState = PROCESS_STATE_CUR_TOP;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making running remote anim: " + app);
            }
        } else if (app.getActiveInstrumentation() != null) {
            // Don't want to kill running instrumentation.
            adj = FOREGROUND_APP_ADJ;
            schedGroup = SCHED_GROUP_DEFAULT;
            state.setAdjType("instrumentation");
            procState = PROCESS_STATE_FOREGROUND_SERVICE;
            capability |= PROCESS_CAPABILITY_BFSL;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making instrumentation: " + app);
            }
        } else if (state.getCachedIsReceivingBroadcast(mTmpSchedGroup)) {
            // An app that is currently receiving a broadcast also
            // counts as being in the foreground for OOM killer purposes.
            // It's placed in a sched group based on the nature of the
            // broadcast as reflected by which queue it's active in.
            adj = FOREGROUND_APP_ADJ;
            schedGroup = mTmpSchedGroup[0];
            state.setAdjType("broadcast");
            procState = ActivityManager.PROCESS_STATE_RECEIVER;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making broadcast: " + app);
            }
        } else if (psr.numberOfExecutingServices() > 0) {
            // An app that is currently executing a service callback also
            // counts as being in the foreground.
            adj = FOREGROUND_APP_ADJ;
            schedGroup = psr.shouldExecServicesFg()
                    ? SCHED_GROUP_DEFAULT : SCHED_GROUP_BACKGROUND;
            state.setAdjType("exec-service");
            procState = PROCESS_STATE_SERVICE;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making exec-service: " + app);
            }
        } else if (app == topApp) {
            adj = FOREGROUND_APP_ADJ;
            schedGroup = SCHED_GROUP_BACKGROUND;
            state.setAdjType("top-sleeping");
            procState = PROCESS_STATE_CUR_TOP;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making top (sleeping): " + app);
            }
        } else {
            // As far as we know the process is empty.  We may change our mind later.
            schedGroup = SCHED_GROUP_BACKGROUND;
            // At this point we don't actually know the adjustment.  Use the cached adj
            // value that the caller wants us to.
            adj = cachedAdj;
            procState = PROCESS_STATE_CACHED_EMPTY;
            if (!couldRecurse || !state.containsCycle()) {
                state.setAdjType("cch-empty");
            }
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making empty: " + app);
            }
        }

        // Examine all non-top activities.
        boolean foregroundActivities = app == topApp;
        if (!foregroundActivities && state.getCachedHasActivities()) {
            state.computeOomAdjFromActivitiesIfNecessary(mTmpComputeOomAdjWindowCallback,
                    adj, foregroundActivities, hasVisibleActivities, procState, schedGroup,
                    appUid, logUid, PROCESS_STATE_CUR_TOP);

            adj = state.getCachedAdj();
            foregroundActivities = state.getCachedForegroundActivities();
            hasVisibleActivities = state.getCachedHasVisibleActivities();
            procState = state.getCachedProcState();
            schedGroup = state.getCachedSchedGroup();
            state.setAdjType(state.getCachedAdjType());
        }

        if (procState > PROCESS_STATE_CACHED_RECENT && state.getCachedHasRecentTasks()) {
            procState = PROCESS_STATE_CACHED_RECENT;
            state.setAdjType("cch-rec");
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to cached recent: " + app);
            }
        }

        int capabilityFromFGS = 0; // capability from foreground service.

        final boolean hasForegroundServices = psr.hasForegroundServices();
        final boolean hasNonShortForegroundServices = psr.hasNonShortForegroundServices();
        final boolean hasShortForegroundServices = hasForegroundServices
                && !psr.areAllShortForegroundServicesProcstateTimedOut(now);

        // Adjust for FGS or "has-overlay-ui".
        if (adj > PERCEPTIBLE_APP_ADJ
                || procState > PROCESS_STATE_FOREGROUND_SERVICE) {
            String adjType = null;
            int newAdj = 0;
            int newProcState = 0;

            if (hasForegroundServices && hasNonShortForegroundServices) {
                // For regular (non-short) FGS.
                adjType = "fg-service";
                newAdj = PERCEPTIBLE_APP_ADJ;
                newProcState = PROCESS_STATE_FOREGROUND_SERVICE;
                capabilityFromFGS |= PROCESS_CAPABILITY_BFSL;

            } else if (hasShortForegroundServices) {

                // For short FGS.
                adjType = "fg-service-short";

                // We use MEDIUM_APP_ADJ + 1 so we can tell apart EJ
                // (which uses MEDIUM_APP_ADJ + 1)
                // from short-FGS.
                // (We use +1 and +2, not +0 and +1, to be consistent with the following
                // RECENT_FOREGROUND_APP_ADJ tweak)
                newAdj = PERCEPTIBLE_MEDIUM_APP_ADJ + 1;

                // We give the FGS procstate, but not PROCESS_CAPABILITY_BFSL, so
                // short-fgs can't start FGS from the background.
                newProcState = PROCESS_STATE_FOREGROUND_SERVICE;

            } else if (state.hasOverlayUi()) {
                adjType = "has-overlay-ui";
                newAdj = PERCEPTIBLE_APP_ADJ;
                newProcState = PROCESS_STATE_IMPORTANT_FOREGROUND;
            }

            if (adjType != null) {
                adj = newAdj;
                procState = newProcState;
                state.setAdjType(adjType);
                schedGroup = SCHED_GROUP_DEFAULT;

                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to " + adjType + ": "
                            + app + " ");
                }
            }
        }

        // If the app was recently in the foreground and moved to a foreground service status,
        // allow it to get a higher rank in memory for some time, compared to other foreground
        // services so that it can finish performing any persistence/processing of in-memory state.
        if (psr.hasForegroundServices() && adj > PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ
                && (state.getLastTopTime() + mConstants.TOP_TO_FGS_GRACE_DURATION > now
                || state.getSetProcState() <= PROCESS_STATE_TOP)) {
            if (psr.hasNonShortForegroundServices()) {
                adj = PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ;
                state.setAdjType("fg-service-act");
            } else {
                // For short-service FGS, we +1 the value, so we'll be able to detect it in
                // various dashboards.
                adj = PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 1;
                state.setAdjType("fg-service-short-act");
            }
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to recent fg: " + app);
            }
            maybeSetProcessFollowUpUpdateLocked(app,
                    state.getLastTopTime() + mConstants.TOP_TO_FGS_GRACE_DURATION, now);
        }

        // If the app was recently in the foreground and has expedited jobs running,
        // allow it to get a higher rank in memory for some time, compared to other EJS and even
        // foreground services so that it can finish performing any persistence/processing of
        // in-memory state.
        if (psr.hasTopStartedAlmostPerceptibleServices()
                && (adj > PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 2)
                && (state.getLastTopTime()
                + mConstants.TOP_TO_ALMOST_PERCEPTIBLE_GRACE_DURATION > now
                || state.getSetProcState() <= PROCESS_STATE_TOP)) {
            // For EJ, we +2 the value, so we'll be able to detect it in
            // various dashboards.
            adj = PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 2;
            // This shall henceforth be called the "EJ" exemption, despite utilizing the
            // ALMOST_PERCEPTIBLE flag to work.
            state.setAdjType("top-ej-act");
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to recent fg for EJ: " + app);
            }
            maybeSetProcessFollowUpUpdateLocked(app,
                    state.getLastTopTime() + mConstants.TOP_TO_ALMOST_PERCEPTIBLE_GRACE_DURATION,
                    now);
        }

        if (adj > PERCEPTIBLE_APP_ADJ
                || procState > PROCESS_STATE_TRANSIENT_BACKGROUND) {
            if (state.getForcingToImportant() != null) {
                // This is currently used for toasts...  they are not interactive, and
                // we don't want them to cause the app to become fully foreground (and
                // thus out of background check), so we yes the best background level we can.
                adj = PERCEPTIBLE_APP_ADJ;
                procState = PROCESS_STATE_TRANSIENT_BACKGROUND;
                state.setAdjType("force-imp");
                state.setAdjSource(state.getForcingToImportant());
                schedGroup = SCHED_GROUP_DEFAULT;
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to force imp: " + app);
                }
            }
        }

        if (state.getCachedIsHeavyWeight()) {
            if (adj > HEAVY_WEIGHT_APP_ADJ) {
                // We don't want to kill the current heavy-weight process.
                adj = HEAVY_WEIGHT_APP_ADJ;
                schedGroup = SCHED_GROUP_BACKGROUND;
                state.setAdjType("heavy");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to heavy: " + app);
                }
            }
            if (procState > ActivityManager.PROCESS_STATE_HEAVY_WEIGHT) {
                procState = ActivityManager.PROCESS_STATE_HEAVY_WEIGHT;
                state.setAdjType("heavy");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to heavy: " + app);
                }
            }
        }

        if (state.getCachedIsHomeProcess()) {
            if (adj > HOME_APP_ADJ) {
                // This process is hosting what we currently consider to be the
                // home app, so we don't want to let it go into the background.
                adj = HOME_APP_ADJ;
                schedGroup = SCHED_GROUP_BACKGROUND;
                state.setAdjType("home");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to home: " + app);
                }
            }
            if (procState > ActivityManager.PROCESS_STATE_HOME) {
                procState = ActivityManager.PROCESS_STATE_HOME;
                state.setAdjType("home");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to home: " + app);
                }
            }
        }
        if (state.getCachedIsPreviousProcess() && state.getCachedHasActivities()) {
            // This was the previous process that showed UI to the user.  We want to
            // try to keep it around more aggressively, to give a good experience
            // around switching between two apps. However, we don't want to keep the
            // process in this privileged state indefinitely. Eventually, allow the
            // app to be demoted to cached.
            if (procState >= PROCESS_STATE_LAST_ACTIVITY
                    && state.getSetProcState() == PROCESS_STATE_LAST_ACTIVITY
                    && (state.getLastStateTime() + mConstants.MAX_PREVIOUS_TIME) <= now) {
                procState = PROCESS_STATE_LAST_ACTIVITY;
                schedGroup = SCHED_GROUP_BACKGROUND;
                state.setAdjType("previous-expired");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Expire prev adj: " + app);
                }
            } else {
                if (adj > PREVIOUS_APP_ADJ) {
                    adj = PREVIOUS_APP_ADJ;
                    schedGroup = SCHED_GROUP_BACKGROUND;
                    state.setAdjType("previous");
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to prev: " + app);
                    }
                }
                if (procState > PROCESS_STATE_LAST_ACTIVITY) {
                    procState = PROCESS_STATE_LAST_ACTIVITY;
                    state.setAdjType("previous");
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to prev: " + app);
                    }
                }
                final long lastStateTime;
                if (state.getSetProcState() == PROCESS_STATE_LAST_ACTIVITY) {
                    lastStateTime = state.getLastStateTime();
                } else {
                    lastStateTime = now;
                }
                maybeSetProcessFollowUpUpdateLocked(app,
                        lastStateTime + mConstants.MAX_PREVIOUS_TIME, now);
            }
        }

        if (false) {
            Slog.i(TAG, "OOM " + app + ": initial adj=" + adj
                    + " reason=" + state.getAdjType());
        }

        // By default, we use the computed adjustment.  It may be changed if
        // there are applications dependent on our services or providers, but
        // this gives us a baseline and makes sure we don't get into an
        // infinite recursion. If we're re-evaluating due to cycles, use the previously computed
        // values.
        if (cycleReEval) {
            procState = Math.min(procState, state.getCurRawProcState());
            adj = Math.min(adj, state.getCurRawAdj());
            schedGroup = Math.max(schedGroup, state.getCurrentSchedulingGroup());
        }
        state.setCurRawAdj(adj);
        state.setCurRawProcState(procState);

        state.setHasStartedServices(false);
        state.setAdjSeq(mAdjSeq);

        if (isBackupProcess(app)) {
            // If possible we want to avoid killing apps while they're being backed up
            if (adj > BACKUP_APP_ADJ) {
                if (DEBUG_BACKUP) Slog.v(TAG_BACKUP, "oom BACKUP_APP_ADJ for " + app);
                adj = BACKUP_APP_ADJ;
                if (procState > PROCESS_STATE_TRANSIENT_BACKGROUND) {
                    procState = PROCESS_STATE_TRANSIENT_BACKGROUND;
                }
                state.setAdjType("backup");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to backup: " + app);
                }
            }
            if (procState > ActivityManager.PROCESS_STATE_BACKUP) {
                procState = ActivityManager.PROCESS_STATE_BACKUP;
                state.setAdjType("backup");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to backup: " + app);
                }
            }
        }

        // The caller will set the initial value in this implementation.
        state.setCurBoundByNonBgRestrictedApp(app.mState.isCurBoundByNonBgRestrictedApp());

        state.setScheduleLikeTopApp(false);

        for (int is = psr.numberOfRunningServices() - 1;
                is >= 0 && (adj > FOREGROUND_APP_ADJ
                        || schedGroup == SCHED_GROUP_BACKGROUND
                        || procState > PROCESS_STATE_TOP);
                is--) {
            ServiceRecord s = psr.getRunningServiceAt(is);
            if (s.startRequested) {
                state.setHasStartedServices(true);
                if (procState > PROCESS_STATE_SERVICE) {
                    procState = PROCESS_STATE_SERVICE;
                    state.setAdjType("started-services");
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise procstate to started service: " + app);
                    }
                }
                if (!s.mKeepWarming && state.hasShownUi() && !state.getCachedIsHomeProcess()) {
                    // If this process has shown some UI, let it immediately
                    // go to the LRU list because it may be pretty heavy with
                    // UI stuff.  We'll tag it with a label just to help
                    // debug and understand what is going on.
                    if (adj > SERVICE_ADJ) {
                        state.setAdjType("cch-started-ui-services");
                    }
                } else {
                    if (s.mKeepWarming
                            || now < (s.lastActivity + mConstants.MAX_SERVICE_INACTIVITY)) {
                        // This service has seen some activity within
                        // recent memory, so we will keep its process ahead
                        // of the background processes. This does not apply
                        // to the SDK sandbox process since it should never
                        // be more important than its corresponding app.
                        if (!app.isSdkSandbox && adj > SERVICE_ADJ) {
                            adj = SERVICE_ADJ;
                            state.setAdjType("started-services");
                            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                                reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                        "Raise adj to started service: " + app);
                            }
                            maybeSetProcessFollowUpUpdateLocked(app,
                                    s.lastActivity + mConstants.MAX_SERVICE_INACTIVITY, now);
                        }
                    }
                    // If we have let the service slide into the background
                    // state, still have some text describing what it is doing
                    // even though the service no longer has an impact.
                    if (adj > SERVICE_ADJ) {
                        state.setAdjType("cch-started-services");
                    }
                }
            }

            if (s.isForeground) {
                final int fgsType = s.foregroundServiceType;
                if (s.isFgsAllowedWiu_forCapabilities()) {
                    capabilityFromFGS |=
                            (fgsType & FOREGROUND_SERVICE_TYPE_LOCATION)
                                    != 0 ? PROCESS_CAPABILITY_FOREGROUND_LOCATION : 0;

                    if (roForegroundAudioControl()) { // flag check
                        // TODO(b/335373208) - revisit restriction of FOREGROUND_AUDIO_CONTROL
                        //  when it can be limited to specific FGS types
                        capabilityFromFGS |= PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
                    }

                    final boolean enabled = state.getCachedCompatChange(
                            CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY);
                    if (enabled) {
                        capabilityFromFGS |=
                                (fgsType & FOREGROUND_SERVICE_TYPE_CAMERA)
                                        != 0 ? PROCESS_CAPABILITY_FOREGROUND_CAMERA : 0;
                        capabilityFromFGS |=
                                (fgsType & FOREGROUND_SERVICE_TYPE_MICROPHONE)
                                        != 0 ? PROCESS_CAPABILITY_FOREGROUND_MICROPHONE : 0;
                    } else {
                        capabilityFromFGS |= PROCESS_CAPABILITY_FOREGROUND_CAMERA
                                | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
                    }
                }
            }

            if (!couldRecurse) {
                // We're entering recursive functions below, if we're told it's not a recursive
                // loop, abort here.
                continue;
            }


            state.setCurRawAdj(adj);
            state.setCurRawProcState(procState);
            state.setCurrentSchedulingGroup(schedGroup);
            state.setCurCapability(capability);

            ArrayMap<IBinder, ArrayList<ConnectionRecord>> serviceConnections = s.getConnections();
            for (int conni = serviceConnections.size() - 1;
                    conni >= 0 && (adj > FOREGROUND_APP_ADJ
                            || schedGroup == SCHED_GROUP_BACKGROUND
                            || procState > PROCESS_STATE_TOP);
                    conni--) {
                ArrayList<ConnectionRecord> clist = serviceConnections.valueAt(conni);
                for (int i = 0;
                        i < clist.size() && (adj > FOREGROUND_APP_ADJ
                                || schedGroup == SCHED_GROUP_BACKGROUND
                                || procState > PROCESS_STATE_TOP);
                        i++) {
                    // XXX should compute this based on the max of
                    // all connected clients.
                    ConnectionRecord cr = clist.get(i);
                    if (cr.binding.client == app) {
                        // Binding to oneself is not interesting.
                        continue;
                    }

                    computeServiceHostOomAdjLSP(cr, app, cr.binding.client, now, topApp, doingAll,
                            cycleReEval, computeClients, oomAdjReason, cachedAdj, true, false);

                    adj = state.getCurRawAdj();
                    procState = state.getCurRawProcState();
                    schedGroup = state.getCurrentSchedulingGroup();
                    capability = state.getCurCapability();
                }
            }
        }

        final ProcessProviderRecord ppr = app.mProviders;
        for (int provi = ppr.numberOfProviders() - 1;
                provi >= 0 && (adj > FOREGROUND_APP_ADJ
                        || schedGroup == SCHED_GROUP_BACKGROUND
                        || procState > PROCESS_STATE_TOP);
                provi--) {
            ContentProviderRecord cpr = ppr.getProviderAt(provi);
            if (couldRecurse) {
                // We're entering recursive functions below.
                state.setCurRawAdj(adj);
                state.setCurRawProcState(procState);
                state.setCurrentSchedulingGroup(schedGroup);
                state.setCurCapability(capability);

                for (int i = cpr.connections.size() - 1;
                        i >= 0 && (adj > FOREGROUND_APP_ADJ
                                || schedGroup == SCHED_GROUP_BACKGROUND
                                || procState > PROCESS_STATE_TOP);
                        i--) {
                    ContentProviderConnection conn = cpr.connections.get(i);
                    ProcessRecord client = conn.client;
                    computeProviderHostOomAdjLSP(conn, app, client, now, topApp, doingAll,
                            cycleReEval, computeClients, oomAdjReason, cachedAdj, true, false);

                    adj = state.getCurRawAdj();
                    procState = state.getCurRawProcState();
                    schedGroup = state.getCurrentSchedulingGroup();
                    capability = state.getCurCapability();
                }
            }
            // If the provider has external (non-framework) process
            // dependencies, ensure that its adjustment is at least
            // FOREGROUND_APP_ADJ.
            if (cpr.hasExternalProcessHandles()) {
                if (adj > FOREGROUND_APP_ADJ) {
                    adj = FOREGROUND_APP_ADJ;
                    state.setCurRawAdj(adj);
                    schedGroup = SCHED_GROUP_DEFAULT;
                    state.setAdjType("ext-provider");
                    state.setAdjTarget(cpr.name);
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise adj to external provider: " + app);
                    }
                }
                if (procState > PROCESS_STATE_IMPORTANT_FOREGROUND) {
                    procState = PROCESS_STATE_IMPORTANT_FOREGROUND;
                    state.setCurRawProcState(procState);
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise procstate to external provider: " + app);
                    }
                }
            }
        }

        if ((ppr.getLastProviderTime() + mConstants.CONTENT_PROVIDER_RETAIN_TIME) > now) {
            if (adj > PREVIOUS_APP_ADJ) {
                adj = PREVIOUS_APP_ADJ;
                schedGroup = SCHED_GROUP_BACKGROUND;
                state.setAdjType("recent-provider");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ,
                            "Raise adj to recent provider: " + app);
                }
                maybeSetProcessFollowUpUpdateLocked(app,
                        ppr.getLastProviderTime() + mConstants.CONTENT_PROVIDER_RETAIN_TIME, now);
            }
            if (procState > PROCESS_STATE_LAST_ACTIVITY) {
                procState = PROCESS_STATE_LAST_ACTIVITY;
                state.setAdjType("recent-provider");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ,
                            "Raise procstate to recent provider: " + app);
                }
                maybeSetProcessFollowUpUpdateLocked(app,
                        ppr.getLastProviderTime() + mConstants.CONTENT_PROVIDER_RETAIN_TIME, now);
            }
        }

        if (procState >= PROCESS_STATE_CACHED_EMPTY) {
            if (psr.hasClientActivities()) {
                // This is a cached process, but with client activities.  Mark it so.
                procState = PROCESS_STATE_CACHED_ACTIVITY_CLIENT;
                state.setAdjType("cch-client-act");
            } else if (psr.isTreatedLikeActivity()) {
                // This is a cached process, but somebody wants us to treat it like it has
                // an activity, okay!
                procState = PROCESS_STATE_CACHED_ACTIVITY;
                state.setAdjType("cch-as-act");
            }
        }

        if (adj == SERVICE_ADJ) {
            if (doingAll && !cycleReEval) {
                state.setServiceB(mNewNumAServiceProcs > (mNumServiceProcs / 3));
                mNewNumServiceProcs++;
                if (!state.isServiceB()) {
                    // This service isn't far enough down on the LRU list to
                    // normally be a B service, but if we are low on RAM and it
                    // is large we want to force it down since we would prefer to
                    // keep launcher over it.
                    long lastPssOrRss = mService.mAppProfiler.isProfilingPss()
                            ? app.mProfile.getLastPss() : app.mProfile.getLastRss();

                    // RSS is larger than PSS, but the RSS/PSS ratio varies per-process based on how
                    // many shared pages a process uses. The threshold is increased if the flag for
                    // reading RSS instead of PSS is enabled.
                    //
                    // TODO(b/296454553): Tune the second value so that the relative number of
                    // service B is similar before/after this flag is enabled.
                    double thresholdModifier = mService.mAppProfiler.isProfilingPss()
                            ? 1 : mConstants.PSS_TO_RSS_THRESHOLD_MODIFIER;
                    double cachedRestoreThreshold =
                            mProcessList.getCachedRestoreThresholdKb() * thresholdModifier;

                    if (!isLastMemoryLevelNormal() && lastPssOrRss >= cachedRestoreThreshold) {
                        state.setServiceHighRam(true);
                        state.setServiceB(true);
                        //Slog.i(TAG, "ADJ " + app + " high ram!");
                    } else {
                        mNewNumAServiceProcs++;
                        //Slog.i(TAG, "ADJ " + app + " not high ram!");
                    }
                } else {
                    state.setServiceHighRam(false);
                }
            }
            if (state.isServiceB()) {
                adj = SERVICE_B_ADJ;
            }
        }

        // apply capability from FGS.
        if (psr.hasForegroundServices()) {
            capability |= capabilityFromFGS;
        }

        capability |= getDefaultCapability(app, procState);
        capability |= getCpuCapability(app, now, foregroundActivities);
        capability |= getImplicitCpuCapability(app, adj);

        // Procstates below BFGS should never have this capability.
        if (procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            capability &= ~PROCESS_CAPABILITY_BFSL;
        }

        if (app.isPendingFinishAttach()) {
            // If the app is still starting up. We reset the computations to the
            // hardcoded values in setAttachingProcessStatesLSP. This ensures that the app keeps
            // hard-coded default 'startup' oom scores while starting up. When it finishes startup,
            // we'll recompute oom scores based on it's actual hosted components.
            setAttachingProcessStatesLSP(app);
            state.setAdjSeq(mAdjSeq);
            state.setCompletedAdjSeq(state.getAdjSeq());
            return false;
        }

        // Do final modification to adj.  Everything we do between here and applying
        // the final setAdj must be done in this function, because we will also use
        // it when computing the final cached adj later.  Note that we don't need to
        // worry about this for max adj above, since max adj will always be used to
        // keep it out of the cached values.
        state.setCurCapability(capability);
        state.updateLastInvisibleTime(hasVisibleActivities);
        state.setHasForegroundActivities(foregroundActivities);
        state.setCompletedAdjSeq(mAdjSeq);

        schedGroup = setIntermediateAdjLSP(app, adj, schedGroup);
        setIntermediateProcStateLSP(app, procState);
        setIntermediateSchedGroupLSP(state, schedGroup);

        // if curAdj or curProcState improved, then this process was promoted
        return state.getCurAdj() < prevAppAdj || state.getCurProcState() < prevProcState
                || state.getCurCapability() != prevCapability;
    }

    @GuardedBy({"mService", "mProcLock"})
    @Override
    public boolean computeServiceHostOomAdjLSP(ConnectionRecord cr, ProcessRecord app,
            ProcessRecord client, long now, ProcessRecord topApp, boolean doingAll,
            boolean cycleReEval, boolean computeClients, int oomAdjReason, int cachedAdj,
            boolean couldRecurse, boolean dryRun) {
        if (app.isPendingFinishAttach()) {
            // We've set the attaching process state in the computeInitialOomAdjLSP. Skip it here.
            return false;
        }

        final ProcessStateRecord state = app.mState;
        ProcessStateRecord cstate = client.mState;
        boolean updated = false;

        if (couldRecurse) {
            if (app.isSdkSandbox && cr.binding.attributedClient != null) {
                // For SDK sandboxes, use the attributed client (eg the app that
                // requested the sandbox)
                client = cr.binding.attributedClient;
                cstate = client.mState;
            }
            if (computeClients) {
                computeOomAdjLSP(client, cachedAdj, topApp, doingAll, now, cycleReEval, true,
                        oomAdjReason, true);
            } else {
                cstate.setCurRawAdj(cstate.getCurAdj());
                cstate.setCurRawProcState(cstate.getCurProcState());
            }
        }

        int clientAdj = cstate.getCurRawAdj();
        int clientProcState = cstate.getCurRawProcState();

        final boolean clientIsSystem = clientProcState < PROCESS_STATE_TOP;

        int adj = state.getCurRawAdj();
        int procState = state.getCurRawProcState();
        int schedGroup = state.getCurrentSchedulingGroup();
        int capability = state.getCurCapability();

        final int prevRawAdj = adj;
        final int prevProcState = procState;
        final int prevSchedGroup = schedGroup;
        final int prevCapability = capability;

        final int appUid = app.info.uid;
        final int logUid = mService.mCurOomAdjUid;

        if (!dryRun) {
            state.setCurBoundByNonBgRestrictedApp(state.isCurBoundByNonBgRestrictedApp()
                    || cstate.isCurBoundByNonBgRestrictedApp()
                    || clientProcState <= PROCESS_STATE_BOUND_TOP
                    || (clientProcState == PROCESS_STATE_FOREGROUND_SERVICE
                    && !cstate.isBackgroundRestricted()));
        }

        if (client.mOptRecord.shouldNotFreeze()) {
            // Propagate the shouldNotFreeze flag down the bindings.
            if (app.mOptRecord.setShouldNotFreeze(true, dryRun,
                    app.mOptRecord.shouldNotFreezeReason()
                            | client.mOptRecord.shouldNotFreezeReason(), mAdjSeq)) {
                if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
                    // Do nothing, capability updated check will handle the dryrun output.
                } else {
                    // Bail out early, as we only care about the return value for a dryrun.
                    return true;
                }
            }
        }

        boolean trackedProcState = false;

        // We always propagate PROCESS_CAPABILITY_BFSL over bindings here,
        // but, right before actually setting it to the process,
        // we check the final procstate, and remove it if the procsate is below BFGS.
        capability |= getBfslCapabilityFromClient(client);

        capability |= getCpuCapabilityFromClient(cr, client);

        if (cr.notHasFlag(Context.BIND_WAIVE_PRIORITY)) {
            if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES)) {
                capability |= cstate.getCurCapability();
            }

            // If an app has network capability by default
            // (by having procstate <= BFGS), then the apps it binds to will get
            // elevated to a high enough procstate anyway to get network unless they
            // request otherwise, so don't propagate the network capability by default
            // in this case unless they explicitly request it.
            if ((cstate.getCurCapability()
                    & PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK) != 0) {
                if (clientProcState <= PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
                    // This is used to grant network access to Expedited Jobs.
                    if (cr.hasFlag(Context.BIND_BYPASS_POWER_NETWORK_RESTRICTIONS)) {
                        capability |= PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
                    }
                } else {
                    capability |= PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
                }
            }
            if ((cstate.getCurCapability()
                    & PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK) != 0) {
                if (clientProcState <= PROCESS_STATE_IMPORTANT_FOREGROUND) {
                    // This is used to grant network access to User Initiated Jobs.
                    if (cr.hasFlag(Context.BIND_BYPASS_USER_NETWORK_RESTRICTIONS)) {
                        capability |= PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;
                    }
                }
            }

            // Sandbox should be able to control audio only when bound client
            // has this capability.
            if ((cstate.getCurCapability()
                    & PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL) != 0) {
                if (app.isSdkSandbox) {
                    capability |= PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
                }
            }

            if (couldRecurse && shouldSkipDueToCycle(app, cstate, procState, adj, cycleReEval)) {
                return false;
            }

            if (clientProcState >= PROCESS_STATE_CACHED_ACTIVITY) {
                // If the other app is cached for any reason, for purposes here
                // we are going to consider it empty.  The specific cached state
                // doesn't propagate except under certain conditions.
                clientProcState = PROCESS_STATE_CACHED_EMPTY;
            }
            String adjType = null;
            if (cr.hasFlag(Context.BIND_ALLOW_OOM_MANAGEMENT)) {
                // Similar to BIND_WAIVE_PRIORITY, keep it unfrozen.
                if (clientAdj < CACHED_APP_MIN_ADJ) {
                    if (app.mOptRecord.setShouldNotFreeze(true, dryRun,
                            app.mOptRecord.shouldNotFreezeReason()
                                    | SHOULD_NOT_FREEZE_REASON_BINDER_ALLOW_OOM_MANAGEMENT,
                            mAdjSeq)) {
                        if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
                            // Do nothing, capability updated check will handle the dryrun output.
                        } else {
                            // Bail out early, as we only care about the return value for a dryrun.
                            return true;
                        }
                    }
                }
                // Not doing bind OOM management, so treat
                // this guy more like a started service.
                if (state.hasShownUi() && !state.getCachedIsHomeProcess()) {
                    // If this process has shown some UI, let it immediately
                    // go to the LRU list because it may be pretty heavy with
                    // UI stuff.  We'll tag it with a label just to help
                    // debug and understand what is going on.
                    if (adj > clientAdj) {
                        adjType = "cch-bound-ui-services";
                    }

                    if (state.isCached() && dryRun) {
                        // Bail out early, as we only care about the return value for a dryrun.
                        return true;
                    }

                    clientAdj = adj;
                    clientProcState = procState;
                } else {
                    if (now >= (cr.binding.service.lastActivity
                            + mConstants.MAX_SERVICE_INACTIVITY)) {
                        // This service has not seen activity within
                        // recent memory, so allow it to drop to the
                        // LRU list if there is no other reason to keep
                        // it around.  We'll also tag it with a label just
                        // to help debug and undertand what is going on.
                        if (adj > clientAdj) {
                            adjType = "cch-bound-services";
                        }
                        clientAdj = adj;
                    }
                }
            }
            if (adj > clientAdj) {
                // If this process has recently shown UI, and the process that is binding to it
                // is less important than a state that can be actively running, then we don't
                // care about the binding as much as we care about letting this process get into
                // the LRU list to be killed and restarted if needed for memory.
                if (state.hasShownUi() && !state.getCachedIsHomeProcess()
                        && clientAdj > CACHING_UI_SERVICE_CLIENT_ADJ_THRESHOLD) {
                    if (adj >= CACHED_APP_MIN_ADJ) {
                        adjType = "cch-bound-ui-services";
                    }
                } else {
                    int newAdj;
                    int lbAdj = VISIBLE_APP_ADJ; // lower bound of adj.
                    if (cr.hasFlag(Context.BIND_ABOVE_CLIENT
                            | Context.BIND_IMPORTANT)) {
                        if (clientAdj >= PERSISTENT_SERVICE_ADJ) {
                            newAdj = clientAdj;
                        } else {
                            // make this service persistent
                            newAdj = PERSISTENT_SERVICE_ADJ;
                            schedGroup = SCHED_GROUP_DEFAULT;
                            procState = ActivityManager.PROCESS_STATE_PERSISTENT;
                            if (!dryRun) {
                                cr.trackProcState(procState, mAdjSeq);
                            }
                            trackedProcState = true;
                        }
                    } else if (cr.hasFlag(Context.BIND_NOT_PERCEPTIBLE)
                            && clientAdj <= PERCEPTIBLE_APP_ADJ
                            && adj >= (lbAdj = PERCEPTIBLE_LOW_APP_ADJ)) {
                        newAdj = PERCEPTIBLE_LOW_APP_ADJ;
                    } else if (cr.hasFlag(Context.BIND_ALMOST_PERCEPTIBLE)
                            && cr.notHasFlag(Context.BIND_NOT_FOREGROUND)
                            && clientAdj < PERCEPTIBLE_APP_ADJ
                            && adj >= (lbAdj = PERCEPTIBLE_APP_ADJ)) {
                        // This is for user-initiated jobs.
                        // We use APP_ADJ + 1 here, so we can tell them apart from FGS.
                        newAdj = PERCEPTIBLE_APP_ADJ + 1;
                    } else if (cr.hasFlag(Context.BIND_ALMOST_PERCEPTIBLE)
                            && cr.hasFlag(Context.BIND_NOT_FOREGROUND)
                            && clientAdj < PERCEPTIBLE_APP_ADJ
                            && adj >= (lbAdj = (PERCEPTIBLE_MEDIUM_APP_ADJ + 2))) {
                        // This is for expedited jobs.
                        // We use MEDIUM_APP_ADJ + 2 here, so we can tell apart
                        // EJ and short-FGS.
                        newAdj = PERCEPTIBLE_MEDIUM_APP_ADJ + 2;
                    } else if (cr.hasFlag(Context.BIND_NOT_VISIBLE)
                            && clientAdj < PERCEPTIBLE_APP_ADJ
                            && adj >= (lbAdj = PERCEPTIBLE_APP_ADJ)) {
                        newAdj = PERCEPTIBLE_APP_ADJ;
                    } else if (clientAdj >= PERCEPTIBLE_APP_ADJ) {
                        newAdj = clientAdj;
                    } else if (cr.hasFlag(BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE)
                            && clientAdj <= VISIBLE_APP_ADJ
                            && adj > VISIBLE_APP_ADJ) {
                        newAdj = VISIBLE_APP_ADJ;
                    } else {
                        if (adj > VISIBLE_APP_ADJ) {
                            // TODO: Is this too limiting for apps bound from TOP?
                            newAdj = Math.max(clientAdj, lbAdj);
                        } else {
                            newAdj = adj;
                        }
                    }

                    if (!cstate.isCached()) {
                        if (state.isCached() && dryRun) {
                            // Bail out early, as we only care about the return value for a dryrun.
                            return true;
                        }
                    }

                    if (newAdj == clientAdj && app.isolated) {
                        // Make bound isolated processes have slightly worse score than their client
                        newAdj = clientAdj + 1;
                    }

                    if (adj >  newAdj) {
                        adj = newAdj;
                        if (state.setCurRawAdj(adj, dryRun)) {
                            // Bail out early, as we only care about the return value for a dryrun.
                        }
                        adjType = "service";
                    }
                }
            }
            if (cr.notHasFlag(Context.BIND_NOT_FOREGROUND
                    | Context.BIND_IMPORTANT_BACKGROUND)) {
                // This will treat important bound services identically to
                // the top app, which may behave differently than generic
                // foreground work.
                final int curSchedGroup = cstate.getCurrentSchedulingGroup();
                if (curSchedGroup > schedGroup) {
                    if (cr.hasFlag(Context.BIND_IMPORTANT)) {
                        schedGroup = curSchedGroup;
                    } else {
                        schedGroup = SCHED_GROUP_DEFAULT;
                    }
                }
                if (clientProcState < PROCESS_STATE_TOP) {
                    // Special handling for above-top states (persistent
                    // processes).  These should not bring the current process
                    // into the top state, since they are not on top.  Instead
                    // give them the best bound state after that.
                    if (cr.hasFlag(BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE)) {
                        clientProcState = PROCESS_STATE_FOREGROUND_SERVICE;
                    } else if (cr.hasFlag(Context.BIND_FOREGROUND_SERVICE)) {
                        clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
                    } else if (isDeviceFullyAwake()
                            && cr.hasFlag(Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE)) {
                        clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
                    } else {
                        clientProcState =
                                PROCESS_STATE_IMPORTANT_FOREGROUND;
                    }
                } else if (clientProcState == PROCESS_STATE_TOP) {
                    // Go at most to BOUND_TOP, unless requested to elevate
                    // to client's state.
                    clientProcState = PROCESS_STATE_BOUND_TOP;
                    final boolean enabled = cstate.getCachedCompatChange(
                            CACHED_COMPAT_CHANGE_PROCESS_CAPABILITY);
                    if (enabled) {
                        if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES)) {
                            // TOP process passes all capabilities to the service.
                            capability |= cstate.getCurCapability();
                        } else {
                            // TOP process passes no capability to the service.
                        }
                    } else {
                        // TOP process passes all capabilities to the service.
                        capability |= cstate.getCurCapability();
                    }
                }
            } else if (cr.notHasFlag(Context.BIND_IMPORTANT_BACKGROUND)) {
                if (clientProcState < PROCESS_STATE_TRANSIENT_BACKGROUND) {
                    clientProcState =
                            PROCESS_STATE_TRANSIENT_BACKGROUND;
                }
            } else {
                if (clientProcState < PROCESS_STATE_IMPORTANT_BACKGROUND) {
                    clientProcState =
                            PROCESS_STATE_IMPORTANT_BACKGROUND;
                }
            }

            if (cr.hasFlag(Context.BIND_SCHEDULE_LIKE_TOP_APP) && clientIsSystem) {
                schedGroup = SCHED_GROUP_TOP_APP;
                if (dryRun) {
                    if (prevSchedGroup < schedGroup) {
                        // Bail out early, as we only care about the return value for a dryrun.
                        return true;
                    }
                } else {
                    state.setScheduleLikeTopApp(true);
                }
            }

            if (!trackedProcState && !dryRun) {
                cr.trackProcState(clientProcState, mAdjSeq);
            }

            if (procState > clientProcState) {
                procState = clientProcState;
                if (state.setCurRawProcState(procState, dryRun)) {
                    // Bail out early, as we only care about the return value for a dryrun.
                    return true;
                }
                if (adjType == null) {
                    adjType = "service";
                }
            }
            if (procState < PROCESS_STATE_IMPORTANT_BACKGROUND
                    && cr.hasFlag(Context.BIND_SHOWING_UI) && !dryRun) {
                app.setPendingUiClean(true);
            }
            if (adjType != null && !dryRun) {
                state.setAdjType(adjType);
                state.setAdjTypeCode(ActivityManager.RunningAppProcessInfo
                        .REASON_SERVICE_IN_USE);
                state.setAdjSource(client);
                state.setAdjSourceProcState(clientProcState);
                state.setAdjTarget(cr.binding.service.instanceName);
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to " + adjType
                            + ": " + app + ", due to " + client
                            + " adj=" + adj + " procState="
                            + ProcessList.makeProcStateString(procState));
                }
            }
        } else { // BIND_WAIVE_PRIORITY == true
            // BIND_WAIVE_PRIORITY bindings are special when it comes to the
            // freezer. Processes bound via WPRI are expected to be running,
            // but they are not promoted in the LRU list to keep them out of
            // cached. As a result, they can freeze based on oom_adj alone.
            // Normally, bindToDeath would fire when a cached app would die
            // in the background, but nothing will fire when a running process
            // pings a frozen process. Accordingly, any cached app that is
            // bound by an unfrozen app via a WPRI binding has to remain
            // unfrozen.
            if (clientAdj < CACHED_APP_MIN_ADJ) {
                if (app.mOptRecord.setShouldNotFreeze(true, dryRun,
                        app.mOptRecord.shouldNotFreezeReason()
                                | ProcessCachedOptimizerRecord
                                .SHOULD_NOT_FREEZE_REASON_BIND_WAIVE_PRIORITY, mAdjSeq)) {
                    if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
                        // Do nothing, capability updated check will handle the dryrun output.
                    } else {
                        // Bail out early, as we only care about the return value for a dryrun.
                        return true;
                    }
                }
            }
        }
        if (cr.hasFlag(Context.BIND_TREAT_LIKE_ACTIVITY)) {
            if (!dryRun) {
                app.mServices.setTreatLikeActivity(true);
            }
            if (clientProcState <= PROCESS_STATE_CACHED_ACTIVITY
                    && procState > PROCESS_STATE_CACHED_ACTIVITY) {
                // This is a cached process, but somebody wants us to treat it like it has
                // an activity, okay!
                procState = PROCESS_STATE_CACHED_ACTIVITY;
                state.setAdjType("cch-as-act");
            }
        }
        final ActivityServiceConnectionsHolder a = cr.activity;
        if (cr.hasFlag(Context.BIND_ADJUST_WITH_ACTIVITY)) {
            if (a != null && adj > FOREGROUND_APP_ADJ
                    && a.isActivityVisible()) {
                adj = FOREGROUND_APP_ADJ;
                if (state.setCurRawAdj(adj, dryRun)) {
                    return true;
                }
                if (cr.notHasFlag(Context.BIND_NOT_FOREGROUND)) {
                    if (cr.hasFlag(Context.BIND_IMPORTANT)) {
                        schedGroup = SCHED_GROUP_TOP_APP_BOUND;
                    } else {
                        schedGroup = SCHED_GROUP_DEFAULT;
                    }
                }

                if (!dryRun) {
                    state.setAdjType("service");
                    state.setAdjTypeCode(ActivityManager.RunningAppProcessInfo
                            .REASON_SERVICE_IN_USE);
                    state.setAdjSource(a);
                    state.setAdjSourceProcState(procState);
                    state.setAdjTarget(cr.binding.service.instanceName);
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise to service w/activity: " + app);
                    }
                }
            }
        }

        capability |= getDefaultCapability(app, procState);

        // Procstates below BFGS should never have this capability.
        if (procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            capability &= ~PROCESS_CAPABILITY_BFSL;
        }
        if (!updated) {
            if (adj < prevRawAdj || procState < prevProcState || schedGroup > prevSchedGroup) {
                updated = true;
            }

            if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
                if ((capability != prevCapability)
                        && ((capability & prevCapability) == prevCapability)) {
                    updated = true;
                }
            } else {
                // Ignore CPU related capabilities in comparison
                final int curFiltered = capability & ~ALL_CPU_TIME_CAPABILITIES;
                final int prevFiltered = prevCapability & ~ALL_CPU_TIME_CAPABILITIES;
                if ((curFiltered != prevFiltered)
                        && ((curFiltered & prevFiltered) == prevFiltered)) {
                    updated = true;
                }
            }
        }

        if (dryRun) {
            return updated;
        }
        if (adj < prevRawAdj) {
            schedGroup = setIntermediateAdjLSP(app, adj, schedGroup);
        }
        if (procState < prevProcState) {
            setIntermediateProcStateLSP(app, procState);
        }
        if (schedGroup > prevSchedGroup) {
            setIntermediateSchedGroupLSP(state, schedGroup);
        }
        state.setCurCapability(capability);

        return updated;
    }

    @GuardedBy({"mService", "mProcLock"})
    @Override
    public boolean computeProviderHostOomAdjLSP(ContentProviderConnection conn,
            ProcessRecord app, ProcessRecord client, long now, ProcessRecord topApp,
            boolean doingAll, boolean cycleReEval, boolean computeClients, int oomAdjReason,
            int cachedAdj, boolean couldRecurse, boolean dryRun) {
        if (app.isPendingFinishAttach()) {
            // We've set the attaching process state in the computeInitialOomAdjLSP. Skip it here.
            return false;
        }

        final ProcessStateRecord state = app.mState;
        final ProcessStateRecord cstate = client.mState;

        if (client == app) {
            // Being our own client is not interesting.
            return false;
        }
        if (couldRecurse) {
            if (computeClients) {
                computeOomAdjLSP(client, cachedAdj, topApp, doingAll, now, cycleReEval, true,
                        oomAdjReason, true);
            } else if (couldRecurse) {
                cstate.setCurRawAdj(cstate.getCurAdj());
                cstate.setCurRawProcState(cstate.getCurProcState());
            }

            if (shouldSkipDueToCycle(app, cstate, state.getCurRawProcState(), state.getCurRawAdj(),
                    cycleReEval)) {
                return false;
            }
        }

        int clientAdj = cstate.getCurRawAdj();
        int clientProcState = cstate.getCurRawProcState();

        int adj = state.getCurRawAdj();
        int procState = state.getCurRawProcState();
        int schedGroup = state.getCurrentSchedulingGroup();
        int capability = state.getCurCapability();

        final int prevRawAdj = adj;
        final int prevProcState = procState;
        final int prevSchedGroup = schedGroup;
        final int prevCapability = capability;

        final int appUid = app.info.uid;
        final int logUid = mService.mCurOomAdjUid;

        // We always propagate PROCESS_CAPABILITY_BFSL to providers here,
        // but, right before actually setting it to the process,
        // we check the final procstate, and remove it if the procsate is below BFGS.
        capability |= getBfslCapabilityFromClient(client);

        capability |= getCpuCapabilityFromClient(conn, client);

        if (clientProcState >= PROCESS_STATE_CACHED_ACTIVITY) {
            // If the other app is cached for any reason, for purposes here
            // we are going to consider it empty.
            clientProcState = PROCESS_STATE_CACHED_EMPTY;
        }
        if (client.mOptRecord.shouldNotFreeze()) {
            // Propagate the shouldNotFreeze flag down the bindings.
            if (app.mOptRecord.setShouldNotFreeze(true, dryRun,
                    app.mOptRecord.shouldNotFreezeReason()
                            | client.mOptRecord.shouldNotFreezeReason(), mAdjSeq)) {
                if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
                    // Do nothing, capability updated check will handle the dryrun output.
                } else {
                    // Bail out early, as we only care about the return value for a dryrun.
                    return true;
                }
            }
        }

        if (!dryRun) {
            state.setCurBoundByNonBgRestrictedApp(state.isCurBoundByNonBgRestrictedApp()
                    || cstate.isCurBoundByNonBgRestrictedApp()
                    || clientProcState <= PROCESS_STATE_BOUND_TOP
                    || (clientProcState == PROCESS_STATE_FOREGROUND_SERVICE
                    && !cstate.isBackgroundRestricted()));
        }

        String adjType = null;
        if (adj > clientAdj) {
            if (state.hasShownUi() && !state.getCachedIsHomeProcess()
                    && clientAdj > PERCEPTIBLE_APP_ADJ) {
                adjType = "cch-ui-provider";
            } else {
                adj = Math.max(clientAdj, FOREGROUND_APP_ADJ);
                if (state.setCurRawAdj(adj, dryRun)) {
                    // Bail out early, as we only care about the return value for a dryrun.
                    return true;
                }
                adjType = "provider";
            }

            if (state.isCached() && !cstate.isCached() && dryRun) {
                // Bail out early, as we only care about the return value for a dryrun.
                return true;
            }
        }

        if (clientProcState <= PROCESS_STATE_FOREGROUND_SERVICE) {
            if (adjType == null) {
                adjType = "provider";
            }
            if (clientProcState == PROCESS_STATE_TOP) {
                clientProcState = PROCESS_STATE_BOUND_TOP;
            } else {
                clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
            }
        }

        if (!dryRun) {
            conn.trackProcState(clientProcState, mAdjSeq);
        }
        if (procState > clientProcState) {
            procState = clientProcState;
            if (state.setCurRawProcState(procState, dryRun)) {
                // Bail out early, as we only care about the return value for a dryrun.
                return true;
            }
        }
        if (cstate.getCurrentSchedulingGroup() > schedGroup) {
            schedGroup = SCHED_GROUP_DEFAULT;
        }
        if (adjType != null && !dryRun) {
            state.setAdjType(adjType);
            state.setAdjTypeCode(ActivityManager.RunningAppProcessInfo
                    .REASON_PROVIDER_IN_USE);
            state.setAdjSource(client);
            state.setAdjSourceProcState(clientProcState);
            state.setAdjTarget(conn.provider.name);
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to " + adjType
                        + ": " + app + ", due to " + client
                        + " adj=" + adj + " procState="
                        + ProcessList.makeProcStateString(procState));
            }
        }

        // Procstates below BFGS should never have this capability.
        if (procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            capability &= ~PROCESS_CAPABILITY_BFSL;
        }

        if (dryRun) {
            if (adj < prevRawAdj || procState < prevProcState || schedGroup > prevSchedGroup) {
                return true;
            }

            if (Flags.cpuTimeCapabilityBasedFreezePolicy()) {
                if ((capability != prevCapability)
                        && ((capability & prevCapability) == prevCapability)) {
                    return true;
                }
            } else {
                // Ignore CPU related capabilities in comparison
                final int curFiltered = capability & ~ALL_CPU_TIME_CAPABILITIES;
                final int prevFiltered = prevCapability & ~ALL_CPU_TIME_CAPABILITIES;
                if ((curFiltered != prevFiltered)
                        && ((curFiltered & prevFiltered) == prevFiltered)) {
                    return true;
                }
            }
        }

        if (adj < prevRawAdj) {
            schedGroup = setIntermediateAdjLSP(app, adj, schedGroup);
        }
        if (procState < prevProcState) {
            setIntermediateProcStateLSP(app, procState);
        }
        if (schedGroup > prevSchedGroup) {
            setIntermediateSchedGroupLSP(state, schedGroup);
        }
        state.setCurCapability(capability);

        return false;
    }
}
