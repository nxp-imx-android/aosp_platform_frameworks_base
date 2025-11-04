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

package com.android.server.wm;

import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.window.flags.Flags;

import java.util.Map;

/**
 * Manages the persistent state of tasks for a package that is undergoing an update.
 *
 * <p>This class stores the persistent state from a task's root activity *before* a package
 * update. This state is then retrieved when the task is relaunched *after* the update,
 * allowing the task to be restored.
 *
 * <p>State is stored on a per-task basis, mapped by package name.
 */
class PackageUpdateManager {
    private static final String TAG = "PackageUpdateManager";
    private final ActivityTaskManagerService mService;

    /**
     * A map to store tasks with persistent state for packages undergoing an update.
     * The outer map is keyed by {@code packageName}, and the value is another map of {@code taskId}
     * to
     * {@code persistentState} associated with it.
     */
    private final Map<String, Map<Integer, PersistableBundle>> mUpdatingPackageToPersistentTasks =
            new ArrayMap<>();

    PackageUpdateManager(ActivityTaskManagerService service) {
        mService = service;
    }

    /**
     * Add a task for a package that can be used to provide a persistent state next time the root
     * activity of the task is being started.
     */
    void addPersistentTaskForPackage(String pkg, int taskId, PersistableBundle persistableBundle) {
        if (!Flags.enableAppRestartAfterUpdate()) {
            return;
        }
        Slog.d(TAG, "Adding pkg= " + pkg + " with task " + taskId);
        mUpdatingPackageToPersistentTasks
                .computeIfAbsent(pkg, k -> new ArrayMap<>())
                .put(taskId, persistableBundle);
    }

    /**
     * Retrieves the correct persistent state to used for the start of the given task.
     * Returns null if no such state is found.
     */
    @Nullable
    PersistableBundle getPersistentStateForTask(Task task) {
        if (!Flags.enableAppRestartAfterUpdate()) {
            return null;
        }
        final String pkg = task.getBasePackageName();
        final Map<Integer, PersistableBundle> tasks = mUpdatingPackageToPersistentTasks.get(pkg);

        if (tasks == null) {
            Slog.d(TAG, "Couldn't find a persistent state for task= " + task.mTaskId);
            return null;
        }

        final PersistableBundle persistentStateMatchingTaskId = tasks.remove(task.mTaskId);

        // If the inner map is now empty, remove it from the outer map to prevent leaks.
        if (tasks.isEmpty()) {
            mUpdatingPackageToPersistentTasks.remove(pkg);
        }

        // If a matching persistent state was found.
        if (persistentStateMatchingTaskId != null) {
            Slog.d(TAG,
                    "Found a persistent state for task= " + task.mTaskId + " through id match!");
            return persistentStateMatchingTaskId;
        }

        Slog.d(TAG,
                "Package found but couldn't find a persistent state for task= " + task.mTaskId);

        // TODO: b/457451784 - Handle if a launch is using a new task.

        // TODO: b/457393309 - For multi-instance applications we should provide the correct
        // persistentState for the correct task

        // TODO: b/457453769 - Remove tasks when they are removed from recents
        return null;
    }
}
