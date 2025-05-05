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

package android.window;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.ActivityTaskManager;
import android.os.RemoteException;
import android.system.SystemCleaner;
import android.util.AndroidRuntimeException;
import android.util.Singleton;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * Retrieve or request app snapshots in system.
 *
 * @hide
 */
public class TaskSnapshotManager {

    private static final String TAG = "TaskSnapshotManager";

    /**
     * Set or retrieve the high resolution snapshot.
     */
    public static final int RESOLUTION_HIGH = 1;

    /**
     * Set or retrieve the low resolution snapshot.
     */
    public static final int RESOLUTION_LOW = 2;

    /**
     * Retrieve in any resolution.
     */
    public static final int RESOLUTION_ANY = 3;

    /**
     * Flags for which kind of resolution snapshot.
     *
     * @hide
     */
    @IntDef(prefix = { "RESOLUTION_" }, value = {
            RESOLUTION_HIGH,
            RESOLUTION_LOW,
            RESOLUTION_ANY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Resolution {}

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final GlobalSnapshotTracker mGlobalSnapshotTracker = new GlobalSnapshotTracker();
    static final Cleaner sCleaner = SystemCleaner.cleaner();
    private static final TaskSnapshotManager sInstance = new TaskSnapshotManager();
    private TaskSnapshotManager() { }

    public static TaskSnapshotManager getInstance() {
        return sInstance;
    }

    /**
     * Fetches the snapshot for the task with the given id.
     *
     * @param taskId the id of the task to retrieve for
     * @param retrieveResolution the resolution we want to load.
     *
     * @return a graphic buffer representing a screenshot of a task, or {@code null} if no
     *         screenshot can be found.
     */
    public TaskSnapshot getTaskSnapshot(int taskId, @Resolution int retrieveResolution)
            throws RemoteException {
        final TaskSnapshot t;
        final long captureTime;
        final TaskSnapshot previousSnapshot;
        validateResolution(retrieveResolution);
        synchronized (mLock) {
            // Gets the latest snapshot from the local cache. This can be used to prevent the system
            // server from returning another snapshot that is the same as the local one.
            final SnapshotTracker st = mGlobalSnapshotTracker.peekLatestSnapshot(
                    taskId, retrieveResolution);
            // Create a temporary reference so the snapshot won't be cleared during IPC call.
            previousSnapshot = st != null ? st.mSnapshot.get() : null;
            captureTime = previousSnapshot != null ? st.mCaptureTime : -1;
        }
        try {
            t = ISnapshotManagerSingleton.get().getTaskSnapshot(taskId,
                    captureTime, retrieveResolution);
        } catch (RemoteException r) {
            Slog.e(TAG, "getTaskSnapshot fail: " + r);
            throw r;
        }
        if (t == null) {
            return previousSnapshot;
        }
        synchronized (mLock) {
            mGlobalSnapshotTracker.createTracker(taskId, t);
        }
        return t;
    }

    /**
     * Requests for a new snapshot to be taken for the task with the given id, storing it in the
     * task snapshot cache only if requested.
     *
     * @param taskId the id of the task to take a snapshot of
     * @param updateCache Whether to store the new snapshot in the system's task snapshot cache.
     *                    If it is true, the snapshot can be either real content or app-theme mode
     *                    depending on the attributes of app. Otherwise, the snapshot will be taken
     *                    with real content.
     * @return a graphic buffer representing a screenshot of a task,  or {@code null} if no
     *         corresponding task can be found.
     */
    public TaskSnapshot takeTaskSnapshot(int taskId, boolean updateCache) throws RemoteException {
        final TaskSnapshot t;
        try {
            t = ISnapshotManagerSingleton.get().takeTaskSnapshot(taskId, updateCache);
        } catch (RemoteException r) {
            Slog.e(TAG, "takeTaskSnapshot fail: " + r);
            throw r;
        }
        if (t != null) {
            synchronized (mLock) {
                mGlobalSnapshotTracker.createTracker(taskId, t);
            }
        }
        return t;
    }

    /**
     * @return Whether the resolution of snapshot align with requested resolution.
     */
    public static boolean isResolutionMatch(@NonNull TaskSnapshot snapshot,
            @Resolution int retrieveResolution) {
        if (retrieveResolution == RESOLUTION_ANY) {
            return true;
        }
        final boolean isLowRes = snapshot.isLowResolution();
        if (isLowRes) {
            return retrieveResolution == TaskSnapshotManager.RESOLUTION_LOW;
        }
        return true;
    }

    /**
     * @return Util method, convert the isLowResolution either FLAG_LOW_RES or FLAG_HIGH_RES.
     */
    public static int convertRetrieveFlag(boolean isLowResolution) {
        return isLowResolution ? TaskSnapshotManager.RESOLUTION_LOW
                : TaskSnapshotManager.RESOLUTION_HIGH;
    }

    private static final Singleton<ITaskSnapshotManager> ISnapshotManagerSingleton =
            new Singleton<ITaskSnapshotManager>() {
                @RequiresPermission(allOf = {android.Manifest.permission.MANAGE_ACTIVITY_TASKS,
                        android.Manifest.permission.READ_FRAME_BUFFER})
                @Override
                protected ITaskSnapshotManager create() {
                    try {
                        return ActivityTaskManager.getService().getTaskSnapshotManager();
                    } catch (RemoteException e) {
                        return null;
                    }
                }
            };

    void removeTracker(SnapshotTracker tracker) {
        synchronized (mLock) {
            mGlobalSnapshotTracker.removeTracker(tracker);
        }
    }

    /**
     * Dump snapshot usage in the process.
     */
    public void dump(PrintWriter pw) {
        synchronized (mLock) {
            mGlobalSnapshotTracker.dump(pw);
        }
    }

    /**
     * Util method, validate requested resolution.
     */
    public static void validateResolution(int resolution) {
        switch (resolution) {
            case RESOLUTION_ANY:
            case RESOLUTION_HIGH:
            case RESOLUTION_LOW:
                return;
            default:
                throw new IllegalArgumentException("Invalidate resolution=" + resolution);
        }
    }

    private static class GlobalSnapshotTracker {
        final SparseArray<SingleTaskTracker> mSnapshotTrackers = new SparseArray<>();

        void createTracker(int taskId, TaskSnapshot snapshot) {
            SingleTaskTracker taskTracker = mSnapshotTrackers.get(taskId);
            if (taskTracker == null) {
                taskTracker = new SingleTaskTracker();
                mSnapshotTrackers.put(taskId, taskTracker);
            }
            final SnapshotTracker tracker = new SnapshotTracker(taskId, snapshot);
            taskTracker.addTracker(tracker);
            sCleaner.register(snapshot, () -> removeTracker(tracker));
        }

        void removeTracker(SnapshotTracker tracker) {
            if (tracker == null) {
                return;
            }
            final int taskId = tracker.mTaskId;
            final SingleTaskTracker taskTracker = mSnapshotTrackers.get(taskId);
            if (taskTracker == null) {
                return;
            }
            taskTracker.stopTrack(tracker);
            if (taskTracker.isEmpty()) {
                mSnapshotTrackers.remove(taskId);
            }
        }

        SnapshotTracker peekLatestSnapshot(int taskId, @Resolution int resolution) {
            final SingleTaskTracker stt = mSnapshotTrackers.get(taskId);
            if (stt == null) {
                // shouldn't happen
                return null;
            }
            return stt.peekLatestSnapshot(resolution);
        }

        /**
         * Dump snapshot usage in the process.
         */
        void dump(PrintWriter pw) {
            if (mSnapshotTrackers.size() == 0) {
                return;
            }
            pw.println("");
            pw.println("Task Snapshot Usage:");
            for (int i = mSnapshotTrackers.size() - 1; i >= 0; --i) {
                mSnapshotTrackers.valueAt(i).dump(pw);
            }
        }

        static class SingleTaskTracker {
            final TreeSet<SnapshotTracker> mHighResSortedTrackers = new TreeSet<>(TRACKER_ORDER);
            final TreeSet<SnapshotTracker> mLowResSortedTrackers  = new TreeSet<>(TRACKER_ORDER);

            static final Comparator<SnapshotTracker> TRACKER_ORDER = new Comparator<>() {
                @Override
                public int compare(SnapshotTracker s1, SnapshotTracker s2) {
                    if (s1.mCaptureTime < s2.mCaptureTime) {
                        return 1;
                    } else if (s1.mCaptureTime > s2.mCaptureTime) {
                        return -1;
                    }
                    return 0;
                }
            };

            void addTracker(@NonNull SnapshotTracker tracker) {
                final TreeSet<SnapshotTracker> targetingSet = tracker.mIsLowResolution
                        ? mLowResSortedTrackers : mHighResSortedTrackers;
                targetingSet.add(tracker);
            }

            SnapshotTracker peekLatestSnapshot(@Resolution int resolution) {
                if (resolution == RESOLUTION_ANY) {
                    final SnapshotTracker hFirst = peekFirst(mHighResSortedTrackers);
                    final SnapshotTracker lFirst = peekFirst(mLowResSortedTrackers);
                    if (hFirst != null && lFirst != null) {
                        return hFirst.mCaptureTime > lFirst.mCaptureTime ? hFirst : lFirst;
                    } else {
                        return hFirst != null ? hFirst : lFirst;
                    }
                }
                final TreeSet<SnapshotTracker> targetingSet = resolution == RESOLUTION_LOW
                        ? mLowResSortedTrackers : mHighResSortedTrackers;
                return peekFirst(targetingSet);
            }

            private static SnapshotTracker peekFirst(TreeSet<SnapshotTracker> targetingSet) {
                return targetingSet.isEmpty() ? null : targetingSet.getFirst();
            }

            void stopTrack(SnapshotTracker tracker) {
                final TreeSet<SnapshotTracker> targetingSet = tracker.mIsLowResolution
                        ? mLowResSortedTrackers : mHighResSortedTrackers;
                targetingSet.remove(tracker);
            }

            boolean isEmpty() {
                return mHighResSortedTrackers.isEmpty() && mLowResSortedTrackers.isEmpty();
            }

            void dump(PrintWriter pw) {
                for (SnapshotTracker highResSortedTracker : mHighResSortedTrackers) {
                    highResSortedTracker.dump(pw);
                }
                for (SnapshotTracker lowResSortedTracker : mLowResSortedTrackers) {
                    lowResSortedTracker.dump(pw);
                }
            }
        }
    }

    // Tracking the snapshot usage, call getStackTrace() to know where it was created.
    static class SnapshotTracker extends AndroidRuntimeException {
        private static final int ROOT_STACK_TRACE_COUNT = 4;
        final int mTaskId;
        final long mSnapshotId;
        final long mCaptureTime;
        final boolean mIsLowResolution;
        final WeakReference<TaskSnapshot> mSnapshot;

        SnapshotTracker(int taskId, TaskSnapshot snapshot) {
            super();
            mTaskId = taskId;
            mSnapshotId = snapshot.getId();
            mCaptureTime = snapshot.getCaptureTime();
            mIsLowResolution = snapshot.isLowResolution();
            snapshot.setSnapshotTracker(this);
            mSnapshot = new WeakReference<>(snapshot);
        }

        @Override
        public String getMessage() {
            return "SnapshotTracker: @" + hashCode()
                    + " {TaskId=" + mTaskId + ", SnapshotId=" + mSnapshotId + " mCaptureTime="
                    + mCaptureTime + ", isLowResolution=" + mIsLowResolution + "}";
        }

        void dump(PrintWriter pw) {
            final StringBuilder builder = buildDumpString(this);
            pw.println("  taskId=" + mTaskId + ", SnapshotID=" + mSnapshotId
                    + ", isLowResolution=" + mIsLowResolution);
            pw.println("   Get from=" + builder);
            pw.println("");
        }

        static StringBuilder buildDumpString(AndroidRuntimeException dump) {
            final StackTraceElement[] stackTrace = dump.getStackTrace();
            final int count = Math.min(stackTrace.length, ROOT_STACK_TRACE_COUNT);
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < count; ++i) {
                builder.append(stackTrace[i]);
                if (i + 1 < count) {
                    builder.append(" ");
                }
            }
            return builder;
        }
    }
}
