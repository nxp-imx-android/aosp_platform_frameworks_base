/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static android.app.ActivityManager.PROCESS_STATE_NONEXISTENT;

import static com.android.server.am.ProcessList.CACHED_APP_MIN_ADJ;
import static com.android.server.wm.WindowProcessController.ACTIVITY_STATE_FLAG_IS_VISIBLE;
import static com.android.server.wm.WindowProcessController.ACTIVITY_STATE_FLAG_MASK_MIN_TASK_LAYER;

import android.annotation.ElapsedRealtimeLong;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.SystemClock;
import android.os.Trace;
import android.util.TimeUtils;

import com.android.internal.annotations.CompositeRWLock;
import com.android.internal.annotations.GuardedBy;
import com.android.server.am.psc.PlatformCompatCache.CachedCompatChangeId;

import java.io.PrintWriter;

/**
 * The state info of the process, including proc state, oom adj score, et al.
 */
public final class ProcessStateRecord {
    /**
     * An observer interface for {@link ProcessStateRecord} to notify about changes to its internal
     * state fields.
     * TODO(b/429069530): Investigate why WindowManager needs to know any of the mCurXXX value.
     */
    public interface Observer {
        /**
         * Called when mCurRawAdj changes.
         *
         * @param curRawAdj The new mCurRawAdj value.
         */
        void onCurRawAdjChanged(int curRawAdj);

        /**
         * Called when mCurAdj changes.
         *
         * @param curAdj The new mCurAdj value.
         */
        void onCurAdjChanged(int curAdj);

        /**
         * Called when mCurSchedGroup changes.
         *
         * @param curSchedGroup The new mCurSchedGroup value.
         */
        void onCurrentSchedulingGroupChanged(int curSchedGroup);

        /**
         * Called when mCurProcState changes.
         *
         * @param curProcState The new mCurProcState value.
         */
        void onCurProcStateChanged(int curProcState);

        /**
         * Called when mRepProcState changes.
         *
         * @param repProcState The new mRepProcState value.
         */
        void onReportedProcStateChanged(int repProcState);

        /**
         * Called when mHasTopUi changes.
         *
         * @param hasTopUi The new mHasTopUi value.
         */
        void onHasTopUiChanged(boolean hasTopUi);

        /**
         * Called when mHasOverlayUi changes.
         *
         * @param hasOverlayUi The new mHasOverlayUi value.
         */
        void onHasOverlayUiChanged(boolean hasOverlayUi);

        /**
         * Called when mInteractionEventTime changes.
         *
         * @param interactionEventTime The new mInteractionEventTime value.
         */
        void onInteractionEventTimeChanged(long interactionEventTime);

        /**
         * Called when mFgInteractionTime changes.
         *
         * @param fgInteractionTime The new mFgInteractionTime value.
         */
        void onFgInteractionTimeChanged(long fgInteractionTime);

        /**
         * Called when mWhenUnimportant changes.
         *
         * @param whenUnimportant The new mWhenUnimportant value.
         */
        void onWhenUnimportantChanged(long whenUnimportant);
    }

    /**
     * An observer interface for {@link ProcessStateRecord} to notify about changes
     * to component-related states like services, receivers, and activities.
     */
    public interface StartedServiceObserver {
        /**
         * Called when mHasStartedServices changes.
         *
         * @param hasStartedServices The new mHasStartedServices value.
         */
        void onHasStartedServicesChanged(boolean hasStartedServices);

        /**
         * Called when the broadcast-receiving state changes.
         *
         * @param isReceivingBroadcast The new isReceivingBroadcast value.
         */
        void onIsReceivingBroadcastChanged(boolean isReceivingBroadcast);

        /**
         * Called when the activity-hosting state changes.
         *
         * @param hasActivities The new hasActivities value.
         */
        void onHasActivitiesChanged(boolean hasActivities);
    }

    /**
     * A temporary interface for {@link ProcessStateRecord} to pull state information from its
     * owner, avoiding a direct dependency.
     * <p>
     * This is implemented by the owner of the ProcessStateRecord (e.g., ProcessRecord)
     * to provide on-demand state information required for OOM adjustment calculations.
     * TODO(b/401350380): Remove the interface after the push model is migrated.
     */
    public interface ProcessRecordReader {
        /**
         * @return {@code true} if the process has any activities.
         */
        boolean hasActivities();

        /**
         * @return {@code true} if the process is considered a heavy-weight process.
         */
        boolean isHeavyWeightProcess();

        /**
         * @return {@code true} if the process has any visible activities.
         */
        boolean hasVisibleActivities();

        /**
         * @return {@code true} if the process is the current home process.
         */
        boolean isHomeProcess();

        /**
         * @return {@code true} if the process was the previous top process.
         */
        boolean isPreviousProcess();

        /**
         * @return {@code true} if the process is associated with any recent tasks.
         */
        boolean hasRecentTasks();

        /**
         * Checks if the process is currently receiving a broadcast.
         *
         * @param outSchedGroup An output array of size 1 where the scheduling group associated
         *                      with the broadcast will be placed if one is active.
         * @return {@code true} if the process is receiving a broadcast.
         */
        boolean isReceivingBroadcast(int[] outSchedGroup);

        /**
         * Checks if a specific compatibility change is enabled for the process.
         *
         * @param cachedCompatChangeId The ID of the compatibility change to check.
         * @return {@code true} if the change is enabled.
         */
        boolean hasCompatChange(@CachedCompatChangeId int cachedCompatChangeId);
    }

    // Enable this to trace all OomAdjuster state transitions
    private static final boolean TRACE_OOM_ADJ = false;

    private final String mProcessName;
    private final int mUid;
    private String mTrackName;

    private final Observer mObserver;
    private final StartedServiceObserver mStartedServiceObserver;
    private final ProcessRecordReader mProcessRecordReader;

    // The ActivityManagerService object, which can only be used as a lock object.
    private final Object mServiceLock;
    // The ActivityManagerGlobalLock object, which can only be used as a lock object.
    private final Object mProcLock;

    /**
     * Maximum OOM adjustment for this process.
     */
    @GuardedBy("mServiceLock")
    private int mMaxAdj = ProcessList.UNKNOWN_ADJ;

    /**
     *  Current OOM unlimited adjustment for this process.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mCurRawAdj = ProcessList.INVALID_ADJ;

    /**
     * Last set OOM unlimited adjustment for this process.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mSetRawAdj = ProcessList.INVALID_ADJ;

    /**
     * Current OOM adjustment for this process.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mCurAdj = ProcessList.INVALID_ADJ;

    /**
     * Last set OOM adjustment for this process.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mSetAdj = ProcessList.INVALID_ADJ;

    /**
     * The last adjustment that was verified as actually being set.
     */
    @GuardedBy("mServiceLock")
    private int mVerifiedAdj = ProcessList.INVALID_ADJ;

    /**
     * Current capability flags of this process.
     * For example, PROCESS_CAPABILITY_FOREGROUND_LOCATION is one capability.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mCurCapability = PROCESS_CAPABILITY_NONE;

    /**
     * Last set capability flags.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mSetCapability = PROCESS_CAPABILITY_NONE;

    /**
     * Currently desired scheduling class.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mCurSchedGroup = ProcessList.SCHED_GROUP_BACKGROUND;

    /**
     * Last set to background scheduling class.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mSetSchedGroup = ProcessList.SCHED_GROUP_BACKGROUND;

    /**
     * Currently computed process state.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mCurProcState = PROCESS_STATE_NONEXISTENT;

    /**
     * Last reported process state.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mRepProcState = PROCESS_STATE_NONEXISTENT;

    /**
     * Temp state during computation.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mCurRawProcState = PROCESS_STATE_NONEXISTENT;

    /**
     * Last set process state in process tracker.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mSetProcState = PROCESS_STATE_NONEXISTENT;

    /**
     * Last time mSetProcState changed.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private long mLastStateTime;

    /**
     * Previous priority value if we're switching to non-SCHED_OTHER.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mSavedPriority;

    /**
     * Process currently is on the service B list.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private boolean mServiceB;

    /**
     * We are forcing to service B list due to its RAM use.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private boolean mServiceHighRam;

    /**
     * Are there any started services running in this process?
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private boolean mHasStartedServices;

    /**
     * Running any activities that are foreground?
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private boolean mHasForegroundActivities;

    /**
     * Last reported foreground activities.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private boolean mRepForegroundActivities;

    /**
     * Has UI been shown in this process since it was started?
     */
    @GuardedBy("mServiceLock")
    private boolean mHasShownUi;

    /**
     * Is this process currently showing a non-activity UI that the user
     * is interacting with? E.g. The status bar when it is expanded, but
     * not when it is minimized. When true the
     * process will be set to use the ProcessList#SCHED_GROUP_TOP_APP
     * scheduling group to boost performance.
     */
    @GuardedBy("mServiceLock")
    private boolean mHasTopUi;

    /**
     * Is the process currently showing a non-activity UI that
     * overlays on-top of activity UIs on screen. E.g. display a window
     * of type android.view.WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY
     * When true the process will oom adj score will be set to
     * ProcessList#PERCEPTIBLE_APP_ADJ at minimum to reduce the chance
     * of the process getting killed.
     */
    @GuardedBy("mServiceLock")
    private boolean mHasOverlayUi;

    /**
     * Is the process currently running a RemoteAnimation? When true
     * the process will be set to use the
     * ProcessList#SCHED_GROUP_TOP_APP scheduling group to boost
     * performance, as well as oom adj score will be set to
     * ProcessList#VISIBLE_APP_ADJ at minimum to reduce the chance
     * of the process getting killed.
     */
    @GuardedBy("mServiceLock")
    private boolean mRunningRemoteAnimation;

    /**
     * Keep track of whether we changed 'mSetAdj'.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private boolean mProcStateChanged;

    /**
     * Whether we have told usage stats about it being an interaction.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private boolean mReportedInteraction;

    /**
     * The time we sent the last interaction event.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private long mInteractionEventTime;

    /**
     * When we became foreground for interaction purposes.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private long mFgInteractionTime;

    /**
     * Token that is forcing this process to be important.
     */
    @GuardedBy("mServiceLock")
    private Object mForcingToImportant;

    /**
     * Sequence id for identifying oom_adj assignment cycles.
     */
    @GuardedBy("mServiceLock")
    private int mAdjSeq;

    /**
     * Sequence id for identifying oom_adj assignment cycles.
     */
    @GuardedBy("mServiceLock")
    private int mCompletedAdjSeq;

    /**
     * Sequence id for identifying LRU update cycles.
     */
    @GuardedBy("mServiceLock")
    private int mLruSeq;

    /**
     * When (uptime) the process last became unimportant.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private long mWhenUnimportant;

    /**
     * The last time the process was in the TOP state or greater.
     */
    @GuardedBy("mServiceLock")
    private long mLastTopTime = Long.MIN_VALUE;

    /**
     * This is a system process, but not currently showing UI.
     */
    @GuardedBy("mServiceLock")
    private boolean mSystemNoUi;

    /**
     * Whether or not the app is background restricted (OP_RUN_ANY_IN_BACKGROUND is NOT allowed).
     */
    @GuardedBy("mServiceLock")
    private boolean mBackgroundRestricted = false;

    /**
     * Whether or not this process is being bound by a non-background restricted app.
     */
    @GuardedBy("mServiceLock")
    private boolean mCurBoundByNonBgRestrictedApp = false;

    /**
     * Last set state of {@link #mCurBoundByNonBgRestrictedApp}.
     */
    private boolean mSetBoundByNonBgRestrictedApp = false;

    /**
     * Debugging: primary thing impacting oom_adj.
     */
    @GuardedBy("mServiceLock")
    private String mAdjType;

    /**
     * Debugging: adj code to report to app.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mAdjTypeCode;

    /**
     * Debugging: option dependent object.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private Object mAdjSource;

    /**
     * Debugging: proc state of mAdjSource's process.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mAdjSourceProcState;

    /**
     * Debugging: target component impacting oom_adj.
     */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private Object mAdjTarget;

    /**
     * Approximates the usage count of the app, used for cache re-ranking by CacheOomRanker.
     *
     * Counts the number of times the process is re-added to the cache (i.e. setCached(false);
     * setCached(true)). This over counts, as setCached is sometimes reset while remaining in the
     * cache. However, this happens uniformly across processes, so ranking is not affected.
     */
    @GuardedBy("mServiceLock")
    private int mCacheOomRankerUseCount;

    /**
     * Process memory usage (RSS).
     *
     * Periodically populated by {@code CacheOomRanker}, stored in this object to cache the values.
     */
    @GuardedBy("mServiceLock")
    private long mCacheOomRankerRss;

    /**
     * The last time, in milliseconds since boot, since {@link #mCacheOomRankerRss} was updated.
     */
    @GuardedBy("mServiceLock")
    private long mCacheOomRankerRssTimeMs;

    /**
     * Whether or not this process is reachable from given process.
     */
    @GuardedBy("mServiceLock")
    private boolean mReachable;

    /**
     * The most recent time when the last visible activity within this process became invisible.
     *
     * <p> It'll be set to 0 if there is never a visible activity, or Long.MAX_VALUE if there is
     * any visible activities within this process at this moment.</p>
     */
    @GuardedBy("mServiceLock")
    @ElapsedRealtimeLong
    private long mLastInvisibleTime;

    /**
     * Last set value of {@link #isCached()}.
     */
    @GuardedBy("mServiceLock")
    private boolean mSetCached;

    /**
     * When the proc became cached. Used to debounce killing bg restricted apps in
     * an idle UID.
     */
    @GuardedBy("mServiceLock")
    private @ElapsedRealtimeLong long mLastCachedTime;

    @GuardedBy("mServiceLock")
    private boolean mHasActivities = false;

    @GuardedBy("mServiceLock")
    private int mActivityStateFlags = ACTIVITY_STATE_FLAG_MASK_MIN_TASK_LAYER;

    @GuardedBy("mServiceLock")
    private long mPerceptibleTaskStoppedTimeMillis = Long.MIN_VALUE;

    @GuardedBy("mServiceLock")
    private boolean mHasRecentTask = false;

    // Below are the cached task info for OomAdjuster only
    private static final int VALUE_INVALID = -1;
    private static final int VALUE_FALSE = 0;
    private static final int VALUE_TRUE = 1;

    @GuardedBy("mServiceLock")
    private int mCachedHasActivities = VALUE_INVALID;
    @GuardedBy("mServiceLock")
    private int mCachedIsHeavyWeight = VALUE_INVALID;
    @GuardedBy("mServiceLock")
    private int mCachedHasVisibleActivities = VALUE_INVALID;
    @GuardedBy("mServiceLock")
    private int mCachedIsHomeProcess = VALUE_INVALID;
    @GuardedBy("mServiceLock")
    private int mCachedIsPreviousProcess = VALUE_INVALID;
    @GuardedBy("mServiceLock")
    private int mCachedHasRecentTasks = VALUE_INVALID;
    @GuardedBy("mServiceLock")
    private int mCachedIsReceivingBroadcast = VALUE_INVALID;

    /**
     * Cache the return value of PlatformCompat.isChangeEnabled().
     */
    @GuardedBy("mServiceLock")
    private int[] mCachedCompatChanges = new int[] {
        VALUE_INVALID, // CACHED_COMPAT_CHANGE_PROCESS_CAPABILITY
        VALUE_INVALID, // CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY
        VALUE_INVALID, // CACHED_COMPAT_CHANGE_USE_SHORT_FGS_USAGE_INTERACTION_TIME
    };

    @GuardedBy("mServiceLock")
    private String mCachedAdjType = null;
    @GuardedBy("mServiceLock")
    private int mCachedAdj = ProcessList.INVALID_ADJ;
    @GuardedBy("mServiceLock")
    private boolean mCachedForegroundActivities = false;
    @GuardedBy("mServiceLock")
    private int mCachedProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
    @GuardedBy("mServiceLock")
    private int mCachedSchedGroup = ProcessList.SCHED_GROUP_BACKGROUND;

    @GuardedBy("mServiceLock")
    private boolean mScheduleLikeTopApp = false;

    @GuardedBy("mServiceLock")
    private long mFollowupUpdateUptimeMs = Long.MAX_VALUE;

    ProcessStateRecord(String processName, int uid, Observer observer,
            StartedServiceObserver startedServiceObserver, ProcessRecordReader processRecordReader,
            Object serviceLock, Object procLock) {
        mProcessName = processName;
        mUid = uid;
        mObserver = observer;
        mStartedServiceObserver = startedServiceObserver;
        mProcessRecordReader  = processRecordReader;
        mServiceLock = serviceLock;
        mProcLock = procLock;
    }

    void init(long now) {
        mLastStateTime = now;
    }

    @GuardedBy("mServiceLock")
    void setMaxAdj(int maxAdj) {
        mMaxAdj = maxAdj;
    }

    @GuardedBy("mServiceLock")
    int getMaxAdj() {
        return mMaxAdj;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setCurRawAdj(int curRawAdj) {
        setCurRawAdj(curRawAdj, false);
    }

    /**
     * @return {@code true} if it's a dry run and it's going to bump the adj score of the process
     * if it was a real run.
     */
    @GuardedBy({"mServiceLock", "mProcLock"})
    boolean setCurRawAdj(int curRawAdj, boolean dryRun) {
        if (dryRun) {
            return mCurRawAdj > curRawAdj;
        }
        mCurRawAdj = curRawAdj;
        mObserver.onCurRawAdjChanged(mCurRawAdj);
        return false;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getCurRawAdj() {
        return mCurRawAdj;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setSetRawAdj(int setRawAdj) {
        mSetRawAdj = setRawAdj;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getSetRawAdj() {
        return mSetRawAdj;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setCurAdj(int curAdj) {
        mCurAdj = curAdj;
        mObserver.onCurAdjChanged(mCurAdj);
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getCurAdj() {
        return mCurAdj;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setSetAdj(int setAdj) {
        mSetAdj = setAdj;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getSetAdj() {
        return mSetAdj;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getSetAdjWithServices() {
        if (mSetAdj >= CACHED_APP_MIN_ADJ) {
            if (mHasStartedServices) {
                return ProcessList.SERVICE_B_ADJ;
            }
        }
        return mSetAdj;
    }

    @GuardedBy("mServiceLock")
    void setVerifiedAdj(int verifiedAdj) {
        mVerifiedAdj = verifiedAdj;
    }

    @GuardedBy("mServiceLock")
    int getVerifiedAdj() {
        return mVerifiedAdj;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setCurCapability(int curCapability) {
        mCurCapability = curCapability;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getCurCapability() {
        return mCurCapability;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setSetCapability(int setCapability) {
        mSetCapability = setCapability;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getSetCapability() {
        return mSetCapability;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setCurrentSchedulingGroup(int curSchedGroup) {
        mCurSchedGroup = curSchedGroup;
        mObserver.onCurrentSchedulingGroupChanged(mCurSchedGroup);
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getCurrentSchedulingGroup() {
        return mCurSchedGroup;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setSetSchedGroup(int setSchedGroup) {
        mSetSchedGroup = setSchedGroup;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getSetSchedGroup() {
        return mSetSchedGroup;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setCurProcState(int curProcState) {
        mCurProcState = curProcState;
        mObserver.onCurProcStateChanged(mCurProcState);
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getCurProcState() {
        return mCurProcState;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setCurRawProcState(int curRawProcState) {
        setCurRawProcState(curRawProcState, false);
    }

    /**
     * @return {@code true} if it's a dry run and it's going to bump the procstate of the process
     * if it was a real run.
     */
    @GuardedBy({"mServiceLock", "mProcLock"})
    boolean setCurRawProcState(int curRawProcState, boolean dryRun) {
        if (dryRun) {
            return mCurRawProcState > curRawProcState;
        }
        mCurRawProcState = curRawProcState;
        return false;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getCurRawProcState() {
        return mCurRawProcState;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setReportedProcState(int repProcState) {
        mRepProcState = repProcState;
        mObserver.onReportedProcStateChanged(mRepProcState);
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getReportedProcState() {
        return mRepProcState;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setSetProcState(int setProcState) {
        if (ActivityManager.isProcStateCached(mSetProcState)
                && !ActivityManager.isProcStateCached(setProcState)) {
            mCacheOomRankerUseCount++;
        }
        mSetProcState = setProcState;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getSetProcState() {
        return mSetProcState;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setLastStateTime(long lastStateTime) {
        mLastStateTime = lastStateTime;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    long getLastStateTime() {
        return mLastStateTime;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setSavedPriority(int savedPriority) {
        mSavedPriority = savedPriority;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getSavedPriority() {
        return mSavedPriority;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setServiceB(boolean serviceb) {
        mServiceB = serviceb;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    boolean isServiceB() {
        return mServiceB;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setServiceHighRam(boolean serviceHighRam) {
        mServiceHighRam = serviceHighRam;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    boolean isServiceHighRam() {
        return mServiceHighRam;
    }

    @GuardedBy("mProcLock")
    void setHasStartedServices(boolean hasStartedServices) {
        mHasStartedServices = hasStartedServices;
        mStartedServiceObserver.onHasStartedServicesChanged(mHasStartedServices);
    }

    @GuardedBy("mProcLock")
    boolean hasStartedServices() {
        return mHasStartedServices;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setHasForegroundActivities(boolean hasForegroundActivities) {
        mHasForegroundActivities = hasForegroundActivities;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    boolean hasForegroundActivities() {
        return mHasForegroundActivities;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setRepForegroundActivities(boolean repForegroundActivities) {
        mRepForegroundActivities = repForegroundActivities;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    boolean hasRepForegroundActivities() {
        return mRepForegroundActivities;
    }

    @GuardedBy("mServiceLock")
    void setHasShownUi(boolean hasShownUi) {
        mHasShownUi = hasShownUi;
    }

    @GuardedBy("mServiceLock")
    boolean hasShownUi() {
        return mHasShownUi;
    }

    @GuardedBy("mServiceLock")
    void setHasTopUi(boolean hasTopUi) {
        mHasTopUi = hasTopUi;
        mObserver.onHasTopUiChanged(mHasTopUi);
    }

    @GuardedBy("mServiceLock")
    boolean hasTopUi() {
        return mHasTopUi;
    }

    @GuardedBy("mServiceLock")
    void setHasOverlayUi(boolean hasOverlayUi) {
        mHasOverlayUi = hasOverlayUi;
        mObserver.onHasOverlayUiChanged(mHasOverlayUi);
    }

    @GuardedBy("mServiceLock")
    boolean hasOverlayUi() {
        return mHasOverlayUi;
    }

    @GuardedBy("mServiceLock")
    boolean isRunningRemoteAnimation() {
        return mRunningRemoteAnimation;
    }

    @GuardedBy("mServiceLock")
    void setRunningRemoteAnimation(boolean runningRemoteAnimation) {
        mRunningRemoteAnimation = runningRemoteAnimation;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setProcStateChanged(boolean procStateChanged) {
        mProcStateChanged = procStateChanged;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    boolean hasProcStateChanged() {
        return mProcStateChanged;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setReportedInteraction(boolean reportedInteraction) {
        mReportedInteraction = reportedInteraction;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    boolean hasReportedInteraction() {
        return mReportedInteraction;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setInteractionEventTime(long interactionEventTime) {
        mInteractionEventTime = interactionEventTime;
        mObserver.onInteractionEventTimeChanged(mInteractionEventTime);
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    long getInteractionEventTime() {
        return mInteractionEventTime;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setFgInteractionTime(long fgInteractionTime) {
        mFgInteractionTime = fgInteractionTime;
        mObserver.onFgInteractionTimeChanged(mFgInteractionTime);
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    long getFgInteractionTime() {
        return mFgInteractionTime;
    }

    @GuardedBy("mServiceLock")
    void setForcingToImportant(Object forcingToImportant) {
        mForcingToImportant = forcingToImportant;
    }

    @GuardedBy("mServiceLock")
    Object getForcingToImportant() {
        return mForcingToImportant;
    }

    @GuardedBy("mServiceLock")
    void setAdjSeq(int adjSeq) {
        mAdjSeq = adjSeq;
    }

    @GuardedBy("mServiceLock")
    void decAdjSeq() {
        mAdjSeq--;
    }

    @GuardedBy("mServiceLock")
    int getAdjSeq() {
        return mAdjSeq;
    }

    @GuardedBy("mServiceLock")
    void setCompletedAdjSeq(int completedAdjSeq) {
        mCompletedAdjSeq = completedAdjSeq;
    }

    @GuardedBy("mServiceLock")
    void decCompletedAdjSeq() {
        mCompletedAdjSeq--;
    }

    @GuardedBy("mServiceLock")
    int getCompletedAdjSeq() {
        return mCompletedAdjSeq;
    }

    @GuardedBy("mServiceLock")
    int getLruSeq() {
        return mLruSeq;
    }

    @GuardedBy("mServiceLock")
    void setLruSeq(int lruSeq) {
        mLruSeq = lruSeq;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setWhenUnimportant(long whenUnimportant) {
        mWhenUnimportant = whenUnimportant;
        mObserver.onWhenUnimportantChanged(mWhenUnimportant);
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    long getWhenUnimportant() {
        return mWhenUnimportant;
    }

    @GuardedBy("mServiceLock")
    void setLastTopTime(long lastTopTime) {
        mLastTopTime = lastTopTime;
    }

    @GuardedBy("mServiceLock")
    long getLastTopTime() {
        return mLastTopTime;
    }

    @GuardedBy("mServiceLock")
    boolean isEmpty() {
        return mCurProcState >= PROCESS_STATE_CACHED_EMPTY;
    }

    @GuardedBy("mServiceLock")
    boolean isCached() {
        return mCurAdj >= CACHED_APP_MIN_ADJ;
    }

    @GuardedBy("mServiceLock")
    int getCacheOomRankerUseCount() {
        return mCacheOomRankerUseCount;
    }

    @GuardedBy("mServiceLock")
    void setSystemNoUi(boolean systemNoUi) {
        mSystemNoUi = systemNoUi;
    }

    @GuardedBy("mServiceLock")
    boolean isSystemNoUi() {
        return mSystemNoUi;
    }

    @GuardedBy("mServiceLock")
    void setAdjType(String adjType) {
        if (TRACE_OOM_ADJ) {
            Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER, getTrackName(), 0);
            Trace.asyncTraceForTrackBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, getTrackName(),
                    adjType, 0);
        }
        mAdjType = adjType;
    }

    @GuardedBy("mServiceLock")
    String getAdjType() {
        return mAdjType;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setAdjTypeCode(int adjTypeCode) {
        mAdjTypeCode = adjTypeCode;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getAdjTypeCode() {
        return mAdjTypeCode;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setAdjSource(Object adjSource) {
        mAdjSource = adjSource;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    Object getAdjSource() {
        return mAdjSource;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setAdjSourceProcState(int adjSourceProcState) {
        mAdjSourceProcState = adjSourceProcState;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    int getAdjSourceProcState() {
        return mAdjSourceProcState;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void setAdjTarget(Object adjTarget) {
        mAdjTarget = adjTarget;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    Object getAdjTarget() {
        return mAdjTarget;
    }

    @GuardedBy("mServiceLock")
    boolean isReachable() {
        return mReachable;
    }

    @GuardedBy("mServiceLock")
    void setReachable(boolean reachable) {
        mReachable = reachable;
    }

    @GuardedBy("mServiceLock")
    void setHasActivities(boolean hasActivities) {
        mHasActivities = hasActivities;
    }

    @GuardedBy("mServiceLock")
    int getActivityStateFlags() {
        return mActivityStateFlags;
    }

    @GuardedBy("mServiceLock")
    void setActivityStateFlags(int flags) {
        mActivityStateFlags = flags;
    }

    @GuardedBy("mServiceLock")
    long getPerceptibleTaskStoppedTimeMillis() {
        return mPerceptibleTaskStoppedTimeMillis;
    }

    @GuardedBy("mServiceLock")
    void setPerceptibleTaskStoppedTimeMillis(long uptimeMs) {
        mPerceptibleTaskStoppedTimeMillis = uptimeMs;
    }

    @GuardedBy("mServiceLock")
    void setHasRecentTask(boolean hasRecentTask) {
        mHasRecentTask = hasRecentTask;
    }

    @GuardedBy("mServiceLock")
    void resetCachedInfo() {
        mCachedHasActivities = VALUE_INVALID;
        mCachedIsHeavyWeight = VALUE_INVALID;
        mCachedHasVisibleActivities = VALUE_INVALID;
        mCachedIsHomeProcess = VALUE_INVALID;
        mCachedIsPreviousProcess = VALUE_INVALID;
        mCachedHasRecentTasks = VALUE_INVALID;
        mCachedIsReceivingBroadcast = VALUE_INVALID;
        mCachedAdj = ProcessList.INVALID_ADJ;
        mCachedForegroundActivities = false;
        mCachedProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
        mCachedSchedGroup = ProcessList.SCHED_GROUP_BACKGROUND;
        mCachedAdjType = null;
    }

    @GuardedBy("mServiceLock")
    private boolean getCachedHasActivities() {
        if (mCachedHasActivities == VALUE_INVALID) {
            final boolean hasActivities = mProcessRecordReader.hasActivities();
            mCachedHasActivities = hasActivities ? VALUE_TRUE : VALUE_FALSE;
            mStartedServiceObserver.onHasActivitiesChanged(hasActivities);
        }
        return mCachedHasActivities == VALUE_TRUE;
    }

    @GuardedBy("mServiceLock")
    boolean getHasActivities() {
        if (Flags.pushActivityStateToOomadjuster()) {
            return mHasActivities;
        } else {
            return getCachedHasActivities();
        }
    }

    @GuardedBy("mServiceLock")
    boolean getCachedIsHeavyWeight() {
        if (mCachedIsHeavyWeight == VALUE_INVALID) {
            mCachedIsHeavyWeight = mProcessRecordReader.isHeavyWeightProcess()
                    ? VALUE_TRUE : VALUE_FALSE;
        }
        return mCachedIsHeavyWeight == VALUE_TRUE;
    }

    @GuardedBy("mServiceLock")
    private boolean getCachedHasVisibleActivities() {
        if (mCachedHasVisibleActivities == VALUE_INVALID) {
            setCachedHasVisibleActivities(mProcessRecordReader.hasVisibleActivities());
        }
        return mCachedHasVisibleActivities == VALUE_TRUE;
    }

    @GuardedBy("mServiceLock")
    void setCachedHasVisibleActivities(boolean cachedHasVisibleActivities) {
        mCachedHasVisibleActivities = cachedHasVisibleActivities ? VALUE_TRUE : VALUE_FALSE;
    }

    @GuardedBy("mServiceLock")
    boolean getHasVisibleActivities() {
        if (Flags.pushActivityStateToOomadjuster()) {
            return (mActivityStateFlags & ACTIVITY_STATE_FLAG_IS_VISIBLE) != 0;
        } else {
            return getCachedHasVisibleActivities();
        }
    }

    @GuardedBy("mServiceLock")
    boolean getCachedIsHomeProcess() {
        if (mCachedIsHomeProcess == VALUE_INVALID) {
            mCachedIsHomeProcess = mProcessRecordReader.isHomeProcess() ? VALUE_TRUE : VALUE_FALSE;
        }
        return mCachedIsHomeProcess == VALUE_TRUE;
    }

    @GuardedBy("mServiceLock")
    boolean getCachedIsPreviousProcess() {
        if (mCachedIsPreviousProcess == VALUE_INVALID) {
            mCachedIsPreviousProcess = mProcessRecordReader.isPreviousProcess()
                    ? VALUE_TRUE : VALUE_FALSE;
        }
        return mCachedIsPreviousProcess == VALUE_TRUE;
    }

    @GuardedBy("mServiceLock")
    boolean getCachedHasRecentTasks() {
        if (mCachedHasRecentTasks == VALUE_INVALID) {
            mCachedHasRecentTasks = mProcessRecordReader.hasRecentTasks()
                    ? VALUE_TRUE : VALUE_FALSE;
        }
        return mCachedHasRecentTasks == VALUE_TRUE;
    }

    @GuardedBy("mServiceLock")
    boolean getHasRecentTasks() {
        if (Flags.pushActivityStateToOomadjuster()) {
            return mHasRecentTask;
        } else {
            return getCachedHasRecentTasks();
        }
    }

    @GuardedBy("mServiceLock")
    boolean getCachedIsReceivingBroadcast(int[] outSchedGroup) {
        if (mCachedIsReceivingBroadcast == VALUE_INVALID) {
            final boolean isReceivingBroadcast =
                    mProcessRecordReader.isReceivingBroadcast(outSchedGroup);
            mCachedIsReceivingBroadcast = isReceivingBroadcast ? VALUE_TRUE : VALUE_FALSE;
            if (isReceivingBroadcast) {
                mCachedSchedGroup = outSchedGroup[0];
            }
            mStartedServiceObserver.onIsReceivingBroadcastChanged(isReceivingBroadcast);
        }
        return mCachedIsReceivingBroadcast == VALUE_TRUE;
    }

    @GuardedBy("mServiceLock")
    boolean getCachedCompatChange(@CachedCompatChangeId int cachedCompatChangeId) {
        if (mCachedCompatChanges[cachedCompatChangeId] == VALUE_INVALID) {
            mCachedCompatChanges[cachedCompatChangeId] =
                    mProcessRecordReader.hasCompatChange(cachedCompatChangeId)
                            ? VALUE_TRUE : VALUE_FALSE;
        }
        return mCachedCompatChanges[cachedCompatChangeId] == VALUE_TRUE;
    }

    @GuardedBy("mServiceLock")
    int getCachedAdj() {
        return mCachedAdj;
    }

    @GuardedBy("mServiceLock")
    void setCachedAdj(int cachedAdj) {
        mCachedAdj = cachedAdj;
    }

    @GuardedBy("mServiceLock")
    boolean getCachedForegroundActivities() {
        return mCachedForegroundActivities;
    }

    @GuardedBy("mServiceLock")
    void setCachedForegroundActivities(boolean cachedForegroundActivities) {
        mCachedForegroundActivities = cachedForegroundActivities;
    }

    @GuardedBy("mServiceLock")
    int getCachedProcState() {
        return mCachedProcState;
    }

    @GuardedBy("mServiceLock")
    void setCachedProcState(int cachedProcState) {
        mCachedProcState = cachedProcState;
    }

    @GuardedBy("mServiceLock")
    int getCachedSchedGroup() {
        return mCachedSchedGroup;
    }

    @GuardedBy("mServiceLock")
    void setCachedSchedGroup(int cachedSchedGroup) {
        mCachedSchedGroup = cachedSchedGroup;
    }

    @GuardedBy("mServiceLock")
    String getCachedAdjType() {
        return mCachedAdjType;
    }

    @GuardedBy("mServiceLock")
    void setCachedAdjType(String cachedAdjType) {
        mCachedAdjType = cachedAdjType;
    }

    @GuardedBy("mServiceLock")
    boolean shouldScheduleLikeTopApp() {
        return mScheduleLikeTopApp;
    }

    @GuardedBy("mServiceLock")
    void setScheduleLikeTopApp(boolean scheduleLikeTopApp) {
        mScheduleLikeTopApp = scheduleLikeTopApp;
    }

    @GuardedBy("mServiceLock")
    long getFollowupUpdateUptimeMs() {
        return mFollowupUpdateUptimeMs;
    }

    @GuardedBy("mServiceLock")
    void setFollowupUpdateUptimeMs(long updateUptimeMs) {
        mFollowupUpdateUptimeMs = updateUptimeMs;
    }

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    public String makeAdjReason() {
        if (mAdjSource != null || mAdjTarget != null) {
            StringBuilder sb = new StringBuilder(128);
            sb.append(' ');
            if (mAdjTarget instanceof ComponentName) {
                sb.append(((ComponentName) mAdjTarget).flattenToShortString());
            } else if (mAdjTarget != null) {
                sb.append(mAdjTarget.toString());
            } else {
                sb.append("{null}");
            }
            sb.append("<=");
            if (mAdjSource instanceof ProcessRecord) {
                sb.append("Proc{");
                sb.append(((ProcessRecord) mAdjSource).toShortString());
                sb.append("}");
            } else if (mAdjSource != null) {
                sb.append(mAdjSource.toString());
            } else {
                sb.append("{null}");
            }
            return sb.toString();
        }
        return null;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void onCleanupApplicationRecordLSP() {
        if (TRACE_OOM_ADJ) {
            Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER, getTrackName(), 0);
        }
        setHasForegroundActivities(false);
        mHasShownUi = false;
        mForcingToImportant = null;
        mCurRawAdj = mSetRawAdj = mCurAdj = mSetAdj = mVerifiedAdj = ProcessList.INVALID_ADJ;
        mCurCapability = mSetCapability = PROCESS_CAPABILITY_NONE;
        mCurSchedGroup = mSetSchedGroup = ProcessList.SCHED_GROUP_BACKGROUND;
        mCurProcState = mCurRawProcState = mSetProcState = PROCESS_STATE_NONEXISTENT;
        for (int i = 0; i < mCachedCompatChanges.length; i++) {
            mCachedCompatChanges[i] = VALUE_INVALID;
        }
        mHasActivities = false;
        mActivityStateFlags = ACTIVITY_STATE_FLAG_MASK_MIN_TASK_LAYER;
        mPerceptibleTaskStoppedTimeMillis = Long.MIN_VALUE;
        mHasRecentTask = false;
    }

    @GuardedBy("mServiceLock")
    boolean isBackgroundRestricted() {
        return mBackgroundRestricted;
    }

    @GuardedBy("mServiceLock")
    void setBackgroundRestricted(boolean restricted) {
        mBackgroundRestricted = restricted;
    }

    @GuardedBy("mServiceLock")
    boolean isCurBoundByNonBgRestrictedApp() {
        return mCurBoundByNonBgRestrictedApp;
    }

    @GuardedBy("mServiceLock")
    void setCurBoundByNonBgRestrictedApp(boolean bound) {
        mCurBoundByNonBgRestrictedApp = bound;
    }

    @GuardedBy("mServiceLock")
    boolean isSetBoundByNonBgRestrictedApp() {
        return mSetBoundByNonBgRestrictedApp;
    }

    @GuardedBy("mServiceLock")
    void setSetBoundByNonBgRestrictedApp(boolean bound) {
        mSetBoundByNonBgRestrictedApp = bound;
    }

    @GuardedBy("mServiceLock")
    void updateLastInvisibleTime(boolean hasVisibleActivities) {
        if (hasVisibleActivities) {
            mLastInvisibleTime = Long.MAX_VALUE;
        } else if (mLastInvisibleTime == Long.MAX_VALUE) {
            mLastInvisibleTime = SystemClock.elapsedRealtime();
        }
    }

    @GuardedBy("mServiceLock")
    @ElapsedRealtimeLong
    long getLastInvisibleTime() {
        return mLastInvisibleTime;
    }

    @GuardedBy("mServiceLock")
    void setSetCached(boolean cached) {
        mSetCached = cached;
    }

    @GuardedBy("mServiceLock")
    boolean isSetCached() {
        return mSetCached;
    }

    @GuardedBy("mServiceLock")
    void setLastCachedTime(@ElapsedRealtimeLong long now) {
        mLastCachedTime = now;
    }

    @ElapsedRealtimeLong
    @GuardedBy("mServiceLock")
    long getLastCachedTime() {
        return mLastCachedTime;
    }

    public void setCacheOomRankerRss(long rss, long rssTimeMs) {
        mCacheOomRankerRss = rss;
        mCacheOomRankerRssTimeMs = rssTimeMs;
    }

    @GuardedBy("mServiceLock")
    public long getCacheOomRankerRss() {
        return mCacheOomRankerRss;
    }

    @GuardedBy("mServiceLock")
    public long getCacheOomRankerRssTimeMs() {
        return mCacheOomRankerRssTimeMs;
    }

    /**
     * Lazily initiate and return the track name.
     */
    private String getTrackName() {
        if (mTrackName == null) {
            mTrackName = "oom:" + mProcessName + "/u" + mUid;
        }
        return mTrackName;
    }

    @GuardedBy({"mServiceLock", "mProcLock"})
    void dump(PrintWriter pw, String prefix, long nowUptime) {
        if (mReportedInteraction || mFgInteractionTime != 0) {
            pw.print(prefix); pw.print("reportedInteraction=");
            pw.print(mReportedInteraction);
            if (mInteractionEventTime != 0) {
                pw.print(" time=");
                TimeUtils.formatDuration(mInteractionEventTime, SystemClock.elapsedRealtime(), pw);
            }
            if (mFgInteractionTime != 0) {
                pw.print(" fgInteractionTime=");
                TimeUtils.formatDuration(mFgInteractionTime, SystemClock.elapsedRealtime(), pw);
            }
            pw.println();
        }
        pw.print(prefix); pw.print("adjSeq="); pw.print(mAdjSeq);
        pw.print(" lruSeq="); pw.println(mLruSeq);
        pw.print(prefix); pw.print("oom adj: max="); pw.print(mMaxAdj);
        pw.print(" curRaw="); pw.print(mCurRawAdj);
        pw.print(" setRaw="); pw.print(mSetRawAdj);
        pw.print(" cur="); pw.print(mCurAdj);
        pw.print(" set="); pw.println(mSetAdj);
        pw.print(prefix); pw.print("mCurSchedGroup="); pw.print(mCurSchedGroup);
        pw.print(" setSchedGroup="); pw.print(mSetSchedGroup);
        pw.print(" systemNoUi="); pw.println(mSystemNoUi);
        pw.print(prefix); pw.print("curProcState="); pw.print(getCurProcState());
        pw.print(" mRepProcState="); pw.print(mRepProcState);
        pw.print(" setProcState="); pw.print(mSetProcState);
        pw.print(" lastStateTime=");
        TimeUtils.formatDuration(getLastStateTime(), nowUptime, pw);
        pw.println();
        pw.print(prefix); pw.print("curCapability=");
        ActivityManager.printCapabilitiesFull(pw, mCurCapability);
        pw.print(" setCapability=");
        ActivityManager.printCapabilitiesFull(pw, mSetCapability);
        pw.println();
        if (mBackgroundRestricted) {
            pw.print(" backgroundRestricted=");
            pw.print(mBackgroundRestricted);
            pw.print(" boundByNonBgRestrictedApp=");
            pw.print(mSetBoundByNonBgRestrictedApp);
        }
        pw.println();
        if (mHasShownUi) {
            pw.print(prefix); pw.print("hasShownUi="); pw.println(mHasShownUi);
        }
        pw.print(prefix); pw.print("cached="); pw.print(isCached());
        pw.print(" empty="); pw.println(isEmpty());
        if (mServiceB) {
            pw.print(prefix); pw.print("serviceb="); pw.print(mServiceB);
            pw.print(" serviceHighRam="); pw.println(mServiceHighRam);
        }
        if (hasTopUi() || hasOverlayUi() || mRunningRemoteAnimation) {
            pw.print(prefix); pw.print("hasTopUi="); pw.print(hasTopUi());
            pw.print(" hasOverlayUi="); pw.print(hasOverlayUi());
            pw.print(" runningRemoteAnimation="); pw.println(mRunningRemoteAnimation);
        }
        if (mHasForegroundActivities || mRepForegroundActivities) {
            pw.print(prefix);
            pw.print("foregroundActivities="); pw.print(mHasForegroundActivities);
            pw.print(" (rep="); pw.print(mRepForegroundActivities); pw.println(")");
        }
        if (mSetProcState > ActivityManager.PROCESS_STATE_SERVICE) {
            pw.print(prefix);
            pw.print("whenUnimportant=");
            TimeUtils.formatDuration(mWhenUnimportant - nowUptime, pw);
            pw.println();
        }
        if (mLastTopTime > 0) {
            pw.print(prefix); pw.print("lastTopTime=");
            TimeUtils.formatDuration(mLastTopTime, nowUptime, pw);
            pw.println();
        }
        if (mLastInvisibleTime > 0 && mLastInvisibleTime < Long.MAX_VALUE) {
            pw.print(prefix); pw.print("lastInvisibleTime=");
            final long elapsedRealtimeNow = SystemClock.elapsedRealtime();
            final long currentTimeNow = System.currentTimeMillis();
            final long lastInvisibleCurrentTime =
                    currentTimeNow - elapsedRealtimeNow + mLastInvisibleTime;
            TimeUtils.dumpTimeWithDelta(pw, lastInvisibleCurrentTime, currentTimeNow);
            pw.println();
        }
        if (mHasStartedServices) {
            pw.print(prefix); pw.print("hasStartedServices="); pw.println(mHasStartedServices);
        }
    }
}
