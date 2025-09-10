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

package com.android.server.am.psc;

import android.annotation.Nullable;

import com.android.internal.annotations.CompositeRWLock;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

/** The base class providing common process list operations primarily used by the OomAdjuster. */
public abstract class ProcessListInternal {
    /** The struct used to store about changes in a process's state. */
    public static final class ProcessChangeItem {
        /** Flag for {@link #changes}: the process's foreground activity state has changed. */
        public static final int CHANGE_ACTIVITIES = 1 << 0;
        /** Flag for {@link #changes}: the process's foreground service types have changed. */
        public static final int CHANGE_FOREGROUND_SERVICES = 1 << 1;

        /** A bitmask of change flags. */
        public int changes;
        /** The UID of the process that has changed. */
        public int uid;
        /** The PID of the process that has changed. */
        public int pid;
        /** Whether the process has any foreground activities. */
        public boolean foregroundActivities;
        /** A bitmask of foreground service types. See ServiceInfo.FOREGROUND_SERVICE_TYPE_*. */
        public int foregroundServiceTypes;
    }

    /** The ActivityManagerService object, which can only be used as a lock object. */
    private Object mServiceLock;
    /** The ActivityManagerGlobalLock object, which can only be used as a lock object. */
    private Object mProcLock;

    /** Current sequence id for process LRU updating. */
    @CompositeRWLock({"mServiceLock", "mProcLock"})
    private int mLruSeq = 0;

    /**
     * The maximum pss size in kb that we consider a process acceptable to restore from its cached
     * state for running in the background when RAM is low.
     */
    private long mCachedRestoreThresholdKb;

    protected void init(Object serviceLock, Object procLock) {
        mServiceLock = serviceLock;
        mProcLock = procLock;
    }

    protected void setCachedRestoreThresholdKb(long value) {
        mCachedRestoreThresholdKb = value;
    }

    public long getCachedRestoreThresholdKb() {
        return mCachedRestoreThresholdKb;
    }

    /** Returns a reference to the Least Recently Used (LRU) process list. */
    public abstract ArrayList<? extends ProcessRecordInternal> getLruProcessesLOSP();

    /** Returns the associated SDK sandbox processes for a UID. */
    public abstract @Nullable List<? extends ProcessRecordInternal>
            getSdkSandboxProcessesForAppLocked(int uid);

    @GuardedBy(anyOf = {"mServiceLock", "mProcLock"})
    public int getLruSeqLOSP() {
        return mLruSeq;
    }

    /** Increments the sequence id for LRU updating. */
    @GuardedBy({"mServiceLock", "mProcLock"})
    protected void incrementLruSeq() {
        mLruSeq++;
    }
}
