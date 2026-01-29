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
import android.companion.AssociationInfo;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;
import android.os.Trace;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.continuity.FeatureController;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controller for task synchronization.
 *
 * <p>This class is responsible for broadcasting task changes to connected devices and maintaining a
 * local store of tasks from connected devices.
 */
public class TaskSyncController extends FeatureController {

    @GuardedBy("this")
    private boolean mIsRegistered = false;

    @GuardedBy("mRemoteTaskListenerHolder")
    private final RemoteTaskListenerHolder mRemoteTaskListenerHolder =
            new RemoteTaskListenerHolder();

    private final TaskBroadcaster mTaskBroadcaster;
    private final ActivityTaskManager mActivityTaskManager;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final RemoteTaskFactory mRemoteTaskFactory;

    public TaskSyncController(
            int userId,
            @NonNull TaskContinuityMessenger messenger,
            @NonNull TaskBroadcaster taskBroadcaster,
            @NonNull ActivityTaskManager activityTaskManager,
            @NonNull ActivityTaskManagerInternal activityTaskManagerInternal,
            @NonNull RemoteTaskFactory remoteTaskFactory) {
        super(userId, Objects.requireNonNull(messenger));
        mTaskBroadcaster = Objects.requireNonNull(taskBroadcaster);
        mActivityTaskManager = Objects.requireNonNull(activityTaskManager);
        mActivityTaskManagerInternal = Objects.requireNonNull(activityTaskManagerInternal);
        mRemoteTaskFactory = Objects.requireNonNull(remoteTaskFactory);
    }

    public void registerTaskListener(@NonNull IRemoteTaskListener listener) {
        Slog.v(getTag(), "Registering task listener");
        synchronized (mRemoteTaskListenerHolder) {
            mRemoteTaskListenerHolder.addListener(Objects.requireNonNull(listener));
        }
    }

    public void unregisterTaskListener(@NonNull IRemoteTaskListener listener) {
        Slog.v(getTag(), "Unregistering task listener");
        synchronized (mRemoteTaskListenerHolder) {
            mRemoteTaskListenerHolder.removeListener(Objects.requireNonNull(listener));
        }
    }

    public void removeTask(int associationId, int taskId) {
        Slog.v(getTag(), "Removing task " + taskId + " from association " + associationId);
        synchronized (mRemoteTaskListenerHolder) {
            mRemoteTaskListenerHolder.notifyTaskHandedOff(associationId, taskId);
        }
    }

    @Override
    protected String getTag() {
        return TaskSyncController.class.getSimpleName();
    }

    @Override
    public void onEnabled() {
        Slog.v(getTag(), "Registering listeners from ActivityTaskManager");
        maybeListenToActivityTaskManager();
    }

    @Override
    public void onDisabled() {
        Slog.v(getTag(), "Unregistering listeners from ActivityTaskManager");
        unlistenFromActivityTaskManager();
        Slog.v(getTag(), "Clearing all tasks from RemoteTaskStore");
        synchronized (mRemoteTaskListenerHolder) {
            mRemoteTaskListenerHolder.notifyAllRemoteTasksChanged(null);
        }
    }

    @Override
    public void onAssociationConnected(@NonNull AssociationInfo associationInfo) {
        maybeListenToActivityTaskManager();
        int associationId = Objects.requireNonNull(associationInfo).getId();
        Slog.v(getTag(), "Association connected: " + associationId);
        mTaskBroadcaster.onDeviceConnected(associationId);
    }

    @Override
    public void onAssociationDisconnected(int associationId) {
        Slog.v(getTag(), "Association disconnected: " + associationId);
        synchronized (mRemoteTaskListenerHolder) {
            mRemoteTaskListenerHolder.notifyRemoteTasksChangedForAssociation(associationId, null);
        }

        synchronized (this) {
            if (mTaskContinuityMessenger.getConnectedAssociations().isEmpty()) {
                unlistenFromActivityTaskManager();
            }
        }
    }

    @Override
    protected void onTaskStackBroadcastMessageReceived(
            int associationId, @NonNull TaskStackBroadcastMessage taskStackBroadcastMessage) {
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, "onTaskStackBroadcastMessageReceived");
        Slog.v(getTag(), "Received task stack broadcast message from association " + associationId);
        AssociationInfo associationInfo =
                mTaskContinuityMessenger.getAssociationInfo(associationId);
        if (associationInfo != null) {
            List<RemoteTask> remoteTasks = new ArrayList<>();
            for (RemoteTaskInfo taskInfo :
                    Objects.requireNonNull(taskStackBroadcastMessage).remoteTasks()) {
                RemoteTask remoteTask =
                        mRemoteTaskFactory.create(
                                associationInfo.getId(),
                                associationInfo.getDisplayName().toString(),
                                taskInfo);
                if (remoteTask != null) {
                    remoteTasks.add(remoteTask);
                }
            }

            synchronized (mRemoteTaskListenerHolder) {
                mRemoteTaskListenerHolder.notifyRemoteTasksChangedForAssociation(
                        associationId, remoteTasks);
            }
        }
        Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
    }

    private void maybeListenToActivityTaskManager() {
        synchronized (this) {
            if (!mIsRegistered && !mTaskContinuityMessenger.getConnectedAssociations().isEmpty()) {
                mActivityTaskManager.registerTaskStackListener(mTaskBroadcaster);
                mActivityTaskManagerInternal.registerHandoffEnablementListener(mTaskBroadcaster);
                mIsRegistered = true;
            }
        }
    }

    private void unlistenFromActivityTaskManager() {
        synchronized (this) {
            if (mIsRegistered) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskBroadcaster);
                mActivityTaskManagerInternal.unregisterHandoffEnablementListener(mTaskBroadcaster);
                mIsRegistered = false;
            }
        }
    }
}
