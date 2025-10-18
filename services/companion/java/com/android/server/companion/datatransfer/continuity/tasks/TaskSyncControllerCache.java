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

package com.android.server.companion.datatransfer.continuity.tasks;

import android.annotation.NonNull;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.FeatureControllerCache;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.util.Objects;

public class TaskSyncControllerCache extends FeatureControllerCache<TaskSyncController> {

    private final Context mContext;
    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final ActivityTaskManager mActivityTaskManager;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final PackageManager mPackageManager;

    public TaskSyncControllerCache(
            @NonNull Context context, @NonNull TaskContinuityMessenger taskContinuityMessenger) {
        mContext = Objects.requireNonNull(context);
        mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
        mActivityTaskManager =
                Objects.requireNonNull(context.getSystemService(ActivityTaskManager.class));
        mActivityTaskManagerInternal =
                Objects.requireNonNull(LocalServices.getService(ActivityTaskManagerInternal.class));
        mPackageManager = Objects.requireNonNull(context.getPackageManager());
    }

    @Override
    protected TaskSyncController createFeatureControllerForUser(int userId) {
        return new TaskSyncController(
                userId,
                mTaskContinuityMessenger,
                new TaskBroadcaster(
                        mTaskContinuityMessenger,
                        new RunningTaskFetcher(
                                userId,
                                mActivityTaskManager,
                                mActivityTaskManagerInternal,
                                mPackageManager)),
                new RemoteTaskStore(),
                mActivityTaskManager,
                mActivityTaskManagerInternal);
    }
}
