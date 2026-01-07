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
import android.app.TaskStackListener;
import android.os.RemoteException;
import android.util.Slog;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.Objects;

/**
 * Responsible for broadcasting recent tasks on the current device to the user's
 *
 * <p>other devices via {@link CompanionDeviceManager}.
 */
public class TaskBroadcaster extends TaskStackListener
        implements ActivityTaskManagerInternal.HandoffEnablementListener {

    private static final String TAG = TaskBroadcaster.class.getSimpleName();

    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final RunningTaskFetcher mRunningTaskFetcher;

    public TaskBroadcaster(
            @NonNull TaskContinuityMessenger taskContinuityMessenger,
            @NonNull RunningTaskFetcher runningTaskFetcher) {

        mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
        mRunningTaskFetcher = Objects.requireNonNull(runningTaskFetcher);
    }

    public void onDeviceConnected(int associationId) {
        Slog.v(TAG, "Transport connected for association id: " + associationId);
        broadcastTaskStack();
    }

    @Override
    public void onTaskStackChanged() throws RemoteException {
        Slog.v(TAG, "onTaskStackChanged");
        broadcastTaskStack();
    }

    @Override
    public void onHandoffEnabledChanged(int taskId, boolean isHandoffEnabled) {
        Slog.v(
                TAG,
                "onHandoffEnabledChanged: taskId="
                        + taskId
                        + ", isHandoffEnabled="
                        + isHandoffEnabled);

        broadcastTaskStack();
    }

    private void broadcastTaskStack() {
        mTaskContinuityMessenger.sendMessage(
                new TaskContinuityMessage.Builder()
                        .setTaskStackBroadcastMessage(
                                new TaskStackBroadcastMessage.Builder()
                                        .setRemoteTasks(mRunningTaskFetcher.getRunningTasks())
                                        .build())
                        .build());
    }
}
