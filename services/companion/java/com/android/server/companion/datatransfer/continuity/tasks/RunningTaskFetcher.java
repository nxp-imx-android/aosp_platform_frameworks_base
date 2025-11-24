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
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.AppOpsManager;
import android.app.HandoffActivityParams;
import android.util.Slog;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fetches the running tasks from the system, converts them to {@link RemoteTaskInfo} and filters
 * out the tasks that should not be synced.
 */
public class RunningTaskFetcher {

    private static final String TAG = RunningTaskFetcher.class.getSimpleName();

    private final int mUserId;
    private final ActivityTaskManager mActivityTaskManager;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final AppOpsManager mAppOps;

    public RunningTaskFetcher(
            int userId,
            @NonNull ActivityTaskManager activityTaskManager,
            @NonNull ActivityTaskManagerInternal activityTaskManagerInternal,
            @NonNull AppOpsManager appOps) {
        mUserId = userId;
        mActivityTaskManager = Objects.requireNonNull(activityTaskManager);
        mActivityTaskManagerInternal = Objects.requireNonNull(activityTaskManagerInternal);
        mAppOps = Objects.requireNonNull(appOps);
    }

    @NonNull
    public List<RemoteTaskInfo> getRunningTasks() {
        return getRunningTaskInfos().stream()
                .filter(this::shouldTaskBeSynced)
                .map(this::createRemoteTaskInfo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Nullable
    private RemoteTaskInfo createRemoteTaskInfo(@Nullable RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }

        if (taskInfo.baseActivity == null || taskInfo.baseActivity.getPackageName() == null) {
            Slog.w(TAG, "Package name is null for task: " + taskInfo.taskId);
            return null;
        }

        String packageName = taskInfo.baseActivity.getPackageName();

        boolean isHandoffEnabled =
                mActivityTaskManagerInternal.isHandoffEnabledForTask(taskInfo.taskId);
        if (!isHandoffEnabled) {
            return null;
        }

        boolean requirePackageInstalled = true;
        HandoffActivityParams params =
                mActivityTaskManagerInternal.getHandoffActivityParamsForTask(taskInfo.taskId);
        if (params != null) {
            requirePackageInstalled = params.isAllowHandoffWithoutPackageInstalled();
        }

        return new RemoteTaskInfo(
                taskInfo.taskId,
                packageName,
                taskInfo.isFocused,
                taskInfo.lastActiveTime,
                new HandoffOptions(isHandoffEnabled, requirePackageInstalled));
    }

    @NonNull
    private List<RunningTaskInfo> getRunningTaskInfos() {
        List<RunningTaskInfo> runningTaskInfos =
                mActivityTaskManager.getTasks(Integer.MAX_VALUE, true);
        if (runningTaskInfos == null) {
            return new ArrayList<>();
        }

        return runningTaskInfos;
    }

    private boolean shouldTaskBeSynced(@Nullable RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return false;
        }

        if (taskInfo.userId != mUserId) {
            Slog.v(TAG, "Task " + taskInfo.taskId + " is not in user " + mUserId);
            return false;
        }

        if (mAppOps.noteOpNoThrow(
                        AppOpsManager.OP_CONTINUE_ACROSS_DEVICES,
                        taskInfo.userId,
                        taskInfo.baseActivity.getPackageName())
                != AppOpsManager.MODE_ALLOWED) {
            Slog.w(
                    TAG,
                    "AppOpsManager.OP_CONTINUE_ACROSS_DEVICES is not allowed for task: "
                            + taskInfo.taskId);
            return false;
        }

        return true;
    }
}
