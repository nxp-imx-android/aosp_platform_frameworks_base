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

package com.android.server.appop;

import static android.app.AppOpsManager.ATTRIBUTION_CHAIN_ID_NONE;
import static android.app.AppOpsManager.ATTRIBUTION_FLAG_ACCESSOR;
import static android.app.AppOpsManager.ATTRIBUTION_FLAG_RECEIVER;
import static android.app.AppOpsManager.ATTRIBUTION_FLAG_TRUSTED;
import static android.app.AppOpsManager.flagsToString;
import static android.app.AppOpsManager.getUidStateName;

import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_CACHE_FULL;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_MIGRATION;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_PERIODIC;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ;
import static com.android.internal.util.FrameworkStatsLog.SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_SHUTDOWN;
import static com.android.server.appop.HistoricalRegistry.AggregationTimeWindow;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.ArrayMap;
import android.util.IntArray;
import android.util.LongSparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ServiceThread;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Helper class to read/write aggregated app op access events.
 *
 * <p>This class manages aggregation and persistence of app op access events. It aggregates app op
 * access events in a fixed time window interval and stores in a SQLite database. It also
 * provides methods for querying the data.</p>
 *
 * <p>This class uses {@link AppOpHistoryDbHelper} to interact with the SQLite database and
 * {@link AppOpHistoryCache} to manage the in-memory cache of events. It employs a
 * {@link SqliteWriteHandler} to perform database writes asynchronously, ensuring that
 * the main thread is not blocked.</p>
 */
public class AppOpHistoryHelper {
    private static final String TAG = "AppOpHistoryHelper";
    private static final long PERIODIC_JOB_MAX_VARIATION_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final long DB_WRITE_INTERVAL_PERIODIC_MILLIS =
            Duration.ofMinutes(10).toMillis();
    private static final long EXPIRED_ENTRY_DELETION_INTERVAL_MILLIS =
            Duration.ofHours(6).toMillis();
    // Event type handled by SqliteWriteHandler
    private static final int WRITE_DATABASE_PERIODIC = 1;
    private static final int DELETE_EXPIRED_ENTRIES_PERIODIC = 2;
    private static final int WRITE_DATABASE_CACHE_FULL = 3;
    // Used in adding variation to periodic job interval
    private final Random mRandom = new Random();
    // time window interval for aggregation
    private long mQuantizationMillis;
    private long mHistoryRetentionMillis;
    private final File mDatabaseFile;
    private final Context mContext;
    private final AppOpHistoryDbHelper mDbHelper;
    private final SqliteWriteHandler mSqliteWriteHandler;
    private final AppOpHistoryCache mCache = new AppOpHistoryCache(1024);

    AppOpHistoryHelper(@NonNull Context context, File databaseFile,
            AggregationTimeWindow aggregationTimeWindow, int databaseVersion) {
        mContext = context;
        mDatabaseFile = databaseFile;
        mDbHelper = new AppOpHistoryDbHelper(
                context, databaseFile, aggregationTimeWindow, databaseVersion);
        ServiceThread thread =
                new ServiceThread(TAG, Process.THREAD_PRIORITY_DEFAULT, true);
        thread.start();
        mSqliteWriteHandler = new SqliteWriteHandler(thread.getLooper());
    }

    // Set parameters before using this class.
    void systemReady(long quantizationMillis, long historyRetentionMillis) {
        mQuantizationMillis = quantizationMillis;
        mHistoryRetentionMillis = historyRetentionMillis;

        ensurePeriodicJobsAreScheduled();
    }

    void incrementOpAccessedCount(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag, int uidState, int flags,
            long accessTime, int attributionFlags, long attributionChainId, int accessCount,
            boolean isStartOrResume) {
        long duration = isStartOrResume ? 0 : -1;
        AppOpAccessEvent appOpAccess = new AppOpAccessEvent(uid, packageName, op, deviceId,
                attributionTag, flags, uidState, attributionFlags, attributionChainId, accessTime,
                discretizeTimestamp(accessTime), duration, discretizeDuration(duration));
        // increase in duration for aggregation is passed as 0 explicitly
        mCache.insertOrUpdate(appOpAccess, accessCount, 0, 0);
    }

    void incrementOpRejectedCount(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag, int uidState, int flags,
            long rejectTime, int attributionFlags, long attributionChainId, int rejectCount) {
        long duration = -1;
        AppOpAccessEvent appOpAccess = new AppOpAccessEvent(uid, packageName, op, deviceId,
                attributionTag, flags, uidState, attributionFlags, attributionChainId, rejectTime,
                discretizeTimestamp(rejectTime), duration, discretizeDuration(duration));
        mCache.insertOrUpdate(appOpAccess, 0, rejectCount, 0);
    }

    void recordOpAccessDuration(int op, int uid, @NonNull String packageName,
            @NonNull String deviceId, @Nullable String attributionTag,
            @AppOpsManager.UidState int uidState,
            @AppOpsManager.OpFlags int flags, long eventStartTime,
            int attributionFlags, long attributionChainId, long duration) {
        AppOpAccessEvent appOpAccess = new AppOpAccessEvent(uid, packageName, op, deviceId,
                attributionTag, flags, uidState, attributionFlags, attributionChainId,
                eventStartTime, discretizeTimestamp(eventStartTime), duration,
                discretizeDuration(duration));
        // This is pause or finish, no needs to increase access count.
        mCache.insertOrUpdate(appOpAccess, 0, 0, duration);
    }

    void addShortIntervalOpsToHistoricalOpsResult(AppOpsManager.HistoricalOps result,
            long beginTimeMillis, long endTimeMillis,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, int uidFilter,
            @Nullable String packageNameFilter,
            @Nullable String[] opNamesFilter,
            @Nullable String attributionTagFilter, int opFlagsFilter,
            Set<String> attributionExemptPkgs) {
        List<AggregatedAppOpAccessEvent> discreteOps = getAppOpHistory(result,
                beginTimeMillis, endTimeMillis, filter, uidFilter, packageNameFilter, opNamesFilter,
                attributionTagFilter, opFlagsFilter);
        boolean assembleChains = attributionExemptPkgs != null;
        LongSparseArray<AttributionChain> attributionChains = null;
        if (assembleChains) {
            attributionChains = createAttributionChains(discreteOps, attributionExemptPkgs);
        }

        int nEvents = discreteOps.size();
        for (int j = 0; j < nEvents; j++) {
            AggregatedAppOpAccessEvent event = discreteOps.get(j);
            AppOpsManager.OpEventProxyInfo proxy = null;
            if (assembleChains && event.attributionChainId() != ATTRIBUTION_CHAIN_ID_NONE) {
                AttributionChain chain = attributionChains.get(event.attributionChainId());
                if (chain != null && chain.isComplete()
                        && chain.isStart(event)
                        && chain.mLastVisibleEvent != null) {
                    AggregatedAppOpAccessEvent proxyEvent = chain.mLastVisibleEvent;
                    proxy = new AppOpsManager.OpEventProxyInfo(proxyEvent.uid(),
                            proxyEvent.packageName(), proxyEvent.attributionTag());
                }
            }
            result.addDiscreteAccess(event.opCode(), event.uid(), event.packageName(),
                    event.attributionTag(), event.uidState(), event.opFlags(),
                    discretizeTimestamp(event.accessTimeMillis()),
                    discretizeDuration(event.durationMillis()), proxy);
        }
    }

    void addLongIntervalOpsToHistoricalOpsResult(AppOpsManager.HistoricalOps result,
            long beginTimeMillis, long endTimeMillis,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, int uidFilter,
            @Nullable String packageNameFilter,
            @Nullable String[] opNamesFilter,
            @Nullable String attributionTagFilter, int opFlagsFilter) {
        List<AggregatedAppOpAccessEvent> appOpHistoryAccesses = getAppOpHistory(result,
                beginTimeMillis, endTimeMillis, filter, uidFilter, packageNameFilter, opNamesFilter,
                attributionTagFilter, opFlagsFilter);
        for (AggregatedAppOpAccessEvent opEvent : appOpHistoryAccesses) {
            result.increaseAccessCount(opEvent.opCode(), opEvent.uid(),
                    opEvent.packageName(),
                    opEvent.attributionTag(), opEvent.uidState(), opEvent.opFlags(),
                    opEvent.totalAccessCount());
            result.increaseRejectCount(opEvent.opCode(), opEvent.uid(),
                    opEvent.packageName(),
                    opEvent.attributionTag(), opEvent.uidState(), opEvent.opFlags(),
                    opEvent.totalRejectCount());
            result.increaseAccessDuration(opEvent.opCode(), opEvent.uid(),
                    opEvent.packageName(),
                    opEvent.attributionTag(), opEvent.uidState(), opEvent.opFlags(),
                    opEvent.totalDurationMillis());
        }
    }

    private List<AggregatedAppOpAccessEvent> getAppOpHistory(AppOpsManager.HistoricalOps result,
            long beginTimeMillis, long endTimeMillis, int filter, int uidFilter,
            @Nullable String packageNameFilter,
            @Nullable String[] opNamesFilter,
            @Nullable String attributionTagFilter, int opFlagsFilter) {
        IntArray opCodes = AppOpHistoryQueryHelper.getAppOpCodes(filter, opNamesFilter);
        // flush the cache into database before read.
        if (opCodes != null) {
            mDbHelper.insertAppOpHistory(mCache.evict(opCodes),
                    SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        } else {
            mDbHelper.insertAppOpHistory(mCache.evictAll(),
                    SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        }
        // Adjust begin & end time to time window's boundary.
        beginTimeMillis = Math.max(discretizeTimestamp(beginTimeMillis),
                discretizeTimestamp((System.currentTimeMillis() - mHistoryRetentionMillis)));
        endTimeMillis = discretizeTimestamp(endTimeMillis + mQuantizationMillis);
        result.setBeginAndEndTime(beginTimeMillis, endTimeMillis);

        return mDbHelper.getAppOpHistory(
                filter, beginTimeMillis, endTimeMillis, uidFilter, packageNameFilter,
                attributionTagFilter, opCodes, opFlagsFilter, -1, null, false);
    }

    boolean deleteDatabase() {
        mDbHelper.close();
        return mContext.deleteDatabase(mDatabaseFile.getAbsolutePath());
    }

    long getLargestAttributionChainId() {
        return mDbHelper.getLargestAttributionChainId();
    }

    void shutdown() {
        mSqliteWriteHandler.removeAllPendingMessages();
        mDbHelper.insertAppOpHistory(mCache.evictAll(),
                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_SHUTDOWN);
        mDbHelper.close();
    }

    void clearHistory() {
        mCache.clear();
        mDbHelper.execSQL(AppOpHistoryTable.DELETE_TABLE_DATA);
    }

    void clearHistory(int uid, String packageName) {
        mCache.clear(uid, packageName);
        mDbHelper.execSQL(AppOpHistoryTable.DELETE_DATA_FOR_UID_PACKAGE,
                new Object[]{uid, packageName});
    }

    long discretizeTimestamp(long timestamp) {
        return timestamp / mQuantizationMillis * mQuantizationMillis;
    }

    long discretizeDuration(long duration) {
        return duration == -1 ? -1 : (duration + mQuantizationMillis - 1)
                / mQuantizationMillis * mQuantizationMillis;
    }

    void migrateDiscreteAppOpHistory(List<AggregatedAppOpAccessEvent> appOpEvents) {
        mDbHelper.insertAppOpHistory(appOpEvents,
                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_MIGRATION);
    }

    @VisibleForTesting
    List<AggregatedAppOpAccessEvent> getAppOpHistory() {
        List<AggregatedAppOpAccessEvent> ops = new ArrayList<>();
        synchronized (mCache) {
            ops.addAll(mCache.snapshot());
            ops.addAll(mDbHelper.getAppOpHistory());
        }
        return ops;
    }

    @VisibleForTesting
    List<AggregatedAppOpAccessEvent> getAppOpHistory(
            long beginTimeMillis, long endTimeMillis, int filter, int uidFilter,
            @Nullable String packageNameFilter,
            @Nullable String[] opNamesFilter,
            @Nullable String attributionTagFilter, int opFlagsFilter) {
        IntArray opCodes = AppOpHistoryQueryHelper.getAppOpCodes(filter, opNamesFilter);
        // flush the cache into database before read.
        if (opCodes != null) {
            mDbHelper.insertAppOpHistory(mCache.evict(opCodes),
                    SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        } else {
            mDbHelper.insertAppOpHistory(mCache.evictAll(),
                    SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        }
        // Adjust begin & end time to time window's boundary.
        beginTimeMillis = Math.max(discretizeTimestamp(beginTimeMillis),
                discretizeTimestamp((System.currentTimeMillis() - mHistoryRetentionMillis)));
        endTimeMillis = discretizeTimestamp(endTimeMillis + mQuantizationMillis);
        return mDbHelper.getAppOpHistory(
                filter, beginTimeMillis, endTimeMillis, uidFilter, packageNameFilter,
                attributionTagFilter, opCodes, opFlagsFilter, -1, null, false);
    }

    private LongSparseArray<AttributionChain> createAttributionChains(
            List<AggregatedAppOpAccessEvent> discreteOps, Set<String> attributionExemptPkgs) {
        LongSparseArray<AttributionChain> chains = new LongSparseArray<>();
        final int count = discreteOps.size();

        for (int i = 0; i < count; i++) {
            AggregatedAppOpAccessEvent opEvent = discreteOps.get(i);
            if (opEvent.attributionChainId() == ATTRIBUTION_CHAIN_ID_NONE
                    || (opEvent.attributionFlags() & ATTRIBUTION_FLAG_TRUSTED) == 0) {
                continue;
            }
            AttributionChain chain = chains.get(opEvent.attributionChainId());
            if (chain == null) {
                chain = new AttributionChain(attributionExemptPkgs);
                chains.put(opEvent.attributionChainId(), chain);
            }
            chain.addEvent(opEvent);
        }
        return chains;
    }

    void dump(PrintWriter pw, int filterUid,
            @Nullable String filterPackage, @Nullable String filterAttributionTag, int filterOp,
            @AppOpsManager.HistoricalOpsRequestFilter int filter, @NonNull SimpleDateFormat sdf,
            @NonNull Date date, int limit) {
        // flush caches to the database
        mDbHelper.insertAppOpHistory(mCache.evictAll(),
                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_READ);
        long currentTime = System.currentTimeMillis();
        long beginTimeMillis = discretizeTimestamp(currentTime - mHistoryRetentionMillis);
        IntArray opCodes = new IntArray();
        if ((filter & AppOpsManager.FILTER_BY_OP_NAMES) != 0
                && filterOp != AppOpsManager.OP_NONE) {
            opCodes.add(filterOp);
        }

        List<AggregatedAppOpAccessEvent> appOpHistoryAccesses = mDbHelper.getAppOpHistory(
                filter, beginTimeMillis, currentTime, filterUid, filterPackage,
                filterAttributionTag, opCodes, AppOpsManager.OP_FLAGS_ALL, limit,
                AppOpHistoryTable.Columns.ACCESS_TIME, false);

        pw.println();
        pw.println("UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|UID_STATE|OP_FLAGS|"
                + "ACCESS_TIME|ACCESS_COUNTS|REJECT_COUNTS|DURATION");
        for (AggregatedAppOpAccessEvent aggAppOpAccess : appOpHistoryAccesses) {
            date.setTime(aggAppOpAccess.accessTimeMillis());
            pw.println(aggAppOpAccess.uid() + "|"
                    + aggAppOpAccess.packageName() + "|"
                    + aggAppOpAccess.deviceId() + "|"
                    + AppOpsManager.opToName(aggAppOpAccess.opCode()) + "|"
                    + aggAppOpAccess.attributionTag() + "|"
                    + getUidStateName(aggAppOpAccess.uidState()) + "|"
                    + flagsToString(aggAppOpAccess.opFlags()) + "|"
                    + sdf.format(date) + "|"
                    + aggAppOpAccess.totalAccessCount() + "|"
                    + aggAppOpAccess.totalRejectCount() + "|"
                    + aggAppOpAccess.durationMillis());
        }
        pw.println();
    }

    private void ensurePeriodicJobsAreScheduled() {
        if (!mSqliteWriteHandler.hasMessages(WRITE_DATABASE_PERIODIC)) {
            mSqliteWriteHandler.sendEmptyMessageDelayed(WRITE_DATABASE_PERIODIC,
                    DB_WRITE_INTERVAL_PERIODIC_MILLIS + mRandom.nextLong(0,
                            PERIODIC_JOB_MAX_VARIATION_MILLIS));
        }
        if (!mSqliteWriteHandler.hasMessages(DELETE_EXPIRED_ENTRIES_PERIODIC)) {
            mSqliteWriteHandler.sendEmptyMessageDelayed(
                    DELETE_EXPIRED_ENTRIES_PERIODIC,
                    EXPIRED_ENTRY_DELETION_INTERVAL_MILLIS + mRandom.nextLong(0,
                            PERIODIC_JOB_MAX_VARIATION_MILLIS));
        }
    }

    private class SqliteWriteHandler extends Handler {
        SqliteWriteHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case WRITE_DATABASE_PERIODIC -> {
                    try {
                        mDbHelper.insertAppOpHistory(mCache.evict(),
                                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_PERIODIC);
                    } finally {
                        ensurePeriodicJobsAreScheduled();
                    }
                }
                case WRITE_DATABASE_CACHE_FULL -> {
                    try {
                        List<AggregatedAppOpAccessEvent> evictedEvents;
                        synchronized (mCache) {
                            evictedEvents = mCache.evict();
                            // if nothing to evict, just write the whole cache to database.
                            if (evictedEvents.isEmpty()
                                    && mCache.size() >= mCache.capacity()) {
                                evictedEvents.addAll(mCache.evictAll());
                            }
                        }
                        mDbHelper.insertAppOpHistory(evictedEvents,
                                SQLITE_APP_OP_EVENT_REPORTED__WRITE_TYPE__WRITE_CACHE_FULL);
                    } finally {
                        ensurePeriodicJobsAreScheduled();
                    }
                }
                case DELETE_EXPIRED_ENTRIES_PERIODIC -> {
                    try {
                        long cutOffTimeStamp = System.currentTimeMillis() - mHistoryRetentionMillis;
                        mDbHelper.execSQL(
                            AppOpHistoryTable.DELETE_TABLE_DATA_BEFORE_ACCESS_TIME,
                            new Object[]{cutOffTimeStamp});
                    } finally {
                        ensurePeriodicJobsAreScheduled();
                    }
                }
            }
        }

        void removeAllPendingMessages() {
            removeMessages(WRITE_DATABASE_PERIODIC);
            removeMessages(DELETE_EXPIRED_ENTRIES_PERIODIC);
            removeMessages(WRITE_DATABASE_CACHE_FULL);
        }
    }

    /**
     * A cache for aggregating app op accesses in a time window. Individual app op events
     * aren't stored on the disk, instead an aggregated events are persisted on the disk.
     *
     * <p>
     * These events are persisted into sqlite database
     * 1) Periodic interval.
     * 2) When the cache become full.
     * 3) During read call, flush the cache to disk to make read simpler.
     * 4) During shutdown.
     */
    class AppOpHistoryCache {
        private static final String TAG = "AppOpHistoryCache";
        private final int mCapacity;
        private final ArrayMap<AppOpAccessEvent, AggregatedAppOpValues> mCache;

        AppOpHistoryCache(int capacity) {
            mCapacity = capacity;
            mCache = new ArrayMap<>();
        }

        /**
         * Records an app op access event, aggregating access, reject count and duration.
         *
         * @param accessKey   Key to group/aggregate app op events in a time window.
         * @param accessCount Access counts to be aggregated for an event.
         * @param rejectCount Reject counts to be aggregated for an event.
         * @param duration    Access duration to be aggregated for an event.
         */
        private void insertOrUpdate(AppOpAccessEvent accessKey, int accessCount,
                int rejectCount, long duration) {
            synchronized (this) {
                AggregatedAppOpValues appOpAccessValue = mCache.get(accessKey);
                if (appOpAccessValue != null) {
                    appOpAccessValue.add(accessCount, rejectCount, duration);
                    return;
                }
                mCache.put(accessKey,
                        new AggregatedAppOpValues(accessCount, rejectCount, duration));
                if (mCache.size() >= mCapacity) {
                    mSqliteWriteHandler.sendEmptyMessage(WRITE_DATABASE_CACHE_FULL);
                }
            }
        }

        private int size() {
            return mCache.size();
        }

        private int capacity() {
            return mCapacity;
        }

        /**
         * Evict older events i.e. events from previous time windows.
         */
        private List<AggregatedAppOpAccessEvent> evict() {
            synchronized (this) {
                List<AggregatedAppOpAccessEvent> evictedEvents = new ArrayList<>();
                ArrayMap<AppOpAccessEvent, AggregatedAppOpValues> snapshot =
                        new ArrayMap<>(mCache);
                long evictionTimestamp = System.currentTimeMillis() - mQuantizationMillis;
                evictionTimestamp = discretizeTimestamp(evictionTimestamp);
                for (Map.Entry<AppOpAccessEvent, AggregatedAppOpValues> opEvent :
                        snapshot.entrySet()) {
                    if (opEvent.getKey().mAccessTime <= evictionTimestamp) {
                        evictedEvents.add(
                                getAggregatedAppOpEvent(opEvent.getKey(), opEvent.getValue()));
                        mCache.remove(opEvent.getKey());
                    }
                }
                return evictedEvents;
            }
        }

        private AggregatedAppOpAccessEvent getAggregatedAppOpEvent(AppOpAccessEvent accessEvent,
                AggregatedAppOpValues appOpValues) {
            return new AggregatedAppOpAccessEvent(accessEvent.mUid, accessEvent.mPackageName,
                    accessEvent.mOpCode, accessEvent.mDeviceId, accessEvent.mAttributionTag,
                    accessEvent.mOpFlags, accessEvent.mUidState, accessEvent.mAttributionFlags,
                    accessEvent.mAttributionChainId, accessEvent.mAccessTime,
                    accessEvent.mDuration, appOpValues.mTotalDuration,
                    appOpValues.mTotalAccessCount, appOpValues.mTotalRejectCount);
        }

        /**
         * Evict specified app ops from cache, and return the list of evicted ops.
         */
        private List<AggregatedAppOpAccessEvent> evict(IntArray ops) {
            synchronized (this) {
                List<AggregatedAppOpAccessEvent> cachedOps = new ArrayList<>();
                List<AppOpAccessEvent> keysToBeRemoved = new ArrayList<>();
                if (mCache.isEmpty()) {
                    return cachedOps;
                }
                for (Map.Entry<AppOpAccessEvent, AggregatedAppOpValues> event :
                        mCache.entrySet()) {
                    if (ops.contains(event.getKey().mOpCode)) {
                        keysToBeRemoved.add(event.getKey());
                        cachedOps.add(getAggregatedAppOpEvent(event.getKey(), event.getValue()));
                    }
                }
                if (!cachedOps.isEmpty()) {
                    for (AppOpAccessEvent eventKey : keysToBeRemoved) {
                        mCache.remove(eventKey);
                    }
                }
                return cachedOps;
            }
        }

        /**
         * Remove all the entries from cache.
         *
         * @return return all removed entries.
         */
        private List<AggregatedAppOpAccessEvent> evictAll() {
            synchronized (this) {
                List<AggregatedAppOpAccessEvent> cachedOps = snapshot();
                mCache.clear();
                return cachedOps;
            }
        }

        /**
         * Remove all entries from the cache.
         */
        private void clear() {
            synchronized (this) {
                mCache.clear();
            }
        }

        private List<AggregatedAppOpAccessEvent> snapshot() {
            List<AggregatedAppOpAccessEvent> events = new ArrayList<>();
            synchronized (this) {
                for (Map.Entry<AppOpAccessEvent, AggregatedAppOpValues> event :
                        mCache.entrySet()) {
                    events.add(getAggregatedAppOpEvent(event.getKey(), event.getValue()));
                }
            }
            return events;
        }

        /** Remove cached events for given UID and package. */
        private void clear(int uid, String packageName) {
            synchronized (this) {
                List<AppOpAccessEvent> keysToBeDeleted = new ArrayList<>();
                for (Map.Entry<AppOpAccessEvent, AggregatedAppOpValues> event :
                        mCache.entrySet()) {
                    if (Objects.equals(packageName, event.getKey().mPackageName)
                            && uid == event.getKey().mUid) {
                        keysToBeDeleted.add(event.getKey());
                    }
                }
                for (AppOpAccessEvent key : keysToBeDeleted) {
                    mCache.remove(key);
                }
            }
        }
    }

    /**
     * This class represents an individual app op access event. It is used as a key in the cache
     * to aggregate access counts during a time window. {@link #mDiscretizedAccessTime} and
     * {@link #mDiscretizedDuration} are used in {@link #equals} and {@link #hashCode}.
     */
    private record AppOpAccessEvent(
            int mUid,
            String mPackageName,
            int mOpCode,
            String mDeviceId,
            String mAttributionTag,
            int mOpFlags,
            int mUidState,
            int mAttributionFlags,
            long mAttributionChainId,
            long mAccessTime,
            long mDiscretizedAccessTime,
            long mDuration,
            long mDiscretizedDuration
    ) {
        public AppOpAccessEvent {
            if (mPackageName != null) {
                mPackageName = mPackageName.intern();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AppOpAccessEvent that)) return false;
            return mUid == that.mUid
                    && mOpCode == that.mOpCode
                    && mOpFlags == that.mOpFlags
                    && mUidState == that.mUidState
                    && mAttributionFlags == that.mAttributionFlags
                    && mAttributionChainId == that.mAttributionChainId
                    && mDiscretizedAccessTime == that.mDiscretizedAccessTime
                    && mDiscretizedDuration == that.mDiscretizedDuration
                    && Objects.equals(mPackageName, that.mPackageName)
                    && Objects.equals(mDeviceId, that.mDeviceId)
                    && Objects.equals(mAttributionTag, that.mAttributionTag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUid, mPackageName, mOpCode, mDeviceId, mAttributionTag,
                    mOpFlags, mUidState, mAttributionFlags, mAttributionChainId,
                    mDiscretizedAccessTime, mDiscretizedDuration);
        }

        @Override
        public String toString() {
            return "AppOpHistoryAccessKey{"
                    + "uid=" + mUid
                    + ", packageName='" + mPackageName + '\''
                    + ", attributionTag='" + mAttributionTag + '\''
                    + ", deviceId='" + mDeviceId + '\''
                    + ", opCode=" + AppOpsManager.opToName(mOpCode)
                    + ", opFlag=" + flagsToString(mOpFlags)
                    + ", uidState=" + getUidStateName(mUidState)
                    + ", attributionFlags=" + mAttributionFlags
                    + ", attributionChainId=" + mAttributionChainId
                    + ", mDuration=" + mDuration
                    + ", mAccessTime=" + mAccessTime + '}';
        }
    }

    private static final class AggregatedAppOpValues {
        private int mTotalAccessCount;
        private int mTotalRejectCount;
        private long mTotalDuration;

        AggregatedAppOpValues(int totalAccessCount, int totalRejectCount, long totalDuration) {
            mTotalAccessCount = totalAccessCount;
            mTotalRejectCount = totalRejectCount;
            mTotalDuration = totalDuration;
        }

        private void add(int accessCount, int rejectCount, long totalDuration) {
            mTotalAccessCount += accessCount;
            mTotalRejectCount += rejectCount;
            mTotalDuration += totalDuration;
        }
    }

    static class AttributionChain {
        List<AggregatedAppOpAccessEvent> mChain = new ArrayList<>();
        Set<String> mExemptPkgs;
        AggregatedAppOpAccessEvent mStartEvent = null;
        AggregatedAppOpAccessEvent mLastVisibleEvent = null;

        AttributionChain(Set<String> exemptPkgs) {
            mExemptPkgs = exemptPkgs;
        }

        boolean isComplete() {
            return !mChain.isEmpty() && getStart() != null && isEnd(mChain.get(mChain.size() - 1));
        }

        AggregatedAppOpAccessEvent getStart() {
            return mChain.isEmpty() || !isStart(mChain.get(0)) ? null : mChain.get(0);
        }

        private boolean isEnd(AggregatedAppOpAccessEvent event) {
            return event != null
                    && (event.attributionFlags() & ATTRIBUTION_FLAG_ACCESSOR) != 0;
        }

        private boolean isStart(AggregatedAppOpAccessEvent event) {
            return event != null
                    && (event.attributionFlags() & ATTRIBUTION_FLAG_RECEIVER) != 0;
        }

        AggregatedAppOpAccessEvent getLastVisible() {
            // Search all nodes but the first one, which is the start node
            for (int i = mChain.size() - 1; i > 0; i--) {
                AggregatedAppOpAccessEvent event = mChain.get(i);
                if (!mExemptPkgs.contains(event.packageName())) {
                    return event;
                }
            }
            return null;
        }

        boolean equalsExceptDuration(AggregatedAppOpAccessEvent obj1,
                AggregatedAppOpAccessEvent obj2) {
            if (obj1.uid() != obj2.uid()) return false;
            if (obj1.opCode() != obj2.opCode()) return false;
            if (obj1.opFlags() != obj2.opFlags()) return false;
            if (obj1.attributionFlags() != obj2.attributionFlags()) return false;
            if (obj1.uidState() != obj2.uidState()) return false;
            if (obj1.attributionChainId() != obj2.attributionChainId()) return false;
            if (!Objects.equals(obj1.packageName(), obj2.packageName())) {
                return false;
            }
            if (!Objects.equals(obj1.attributionTag(), obj2.attributionTag())) {
                return false;
            }
            if (!Objects.equals(obj1.deviceId(), obj2.deviceId())) {
                return false;
            }
            return obj1.accessTimeMillis() == obj2.accessTimeMillis();
        }

        void addEvent(AggregatedAppOpAccessEvent opEvent) {
            // check if we have a matching event except duration.
            AggregatedAppOpAccessEvent matchingItem = null;
            for (int i = 0; i < mChain.size(); i++) {
                AggregatedAppOpAccessEvent item = mChain.get(i);
                if (equalsExceptDuration(item, opEvent)) {
                    matchingItem = item;
                    break;
                }
            }

            if (matchingItem != null) {
                // exact match or existing event has longer duration
                if (matchingItem.durationMillis() == opEvent.durationMillis()
                        || matchingItem.durationMillis() > opEvent.durationMillis()) {
                    return;
                }
                mChain.remove(matchingItem);
            }

            if (mChain.isEmpty() || isEnd(opEvent)) {
                mChain.add(opEvent);
            } else if (isStart(opEvent)) {
                mChain.add(0, opEvent);
            } else {
                for (int i = 0; i < mChain.size(); i++) {
                    AggregatedAppOpAccessEvent currEvent = mChain.get(i);
                    if ((!isStart(currEvent)
                            && currEvent.accessTimeMillis() > opEvent.accessTimeMillis())
                            || (i == mChain.size() - 1 && isEnd(currEvent))) {
                        mChain.add(i, opEvent);
                        break;
                    } else if (i == mChain.size() - 1) {
                        mChain.add(opEvent);
                        break;
                    }
                }
            }
            mStartEvent = isComplete() ? getStart() : null;
            mLastVisibleEvent = isComplete() ? getLastVisible() : null;
        }
    }
}
