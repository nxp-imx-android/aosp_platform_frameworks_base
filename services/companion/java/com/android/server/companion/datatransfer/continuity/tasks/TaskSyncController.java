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
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.AssociationInfo;
import android.content.Context;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.companion.datatransfer.continuity.FeatureController;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;

import java.util.Objects;
import java.util.Collection;

/**
 * Controller for task synchronization.
 *
 * <p>This class is responsible for broadcasting task changes to connected devices and maintaining a
 * local store of tasks from connected devices.
 */
public class TaskSyncController extends FeatureController {

    private final TaskBroadcaster mTaskBroadcaster;
    private final RemoteTaskStore mRemoteTaskStore;
    private final ActivityTaskManager mActivityTaskManager;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;

    public TaskSyncController(
            @NonNull Context context, @NonNull TaskContinuityMessenger messenger) {
        this(
                Objects.requireNonNull(messenger),
                new TaskBroadcaster(
                        Objects.requireNonNull(messenger), new RunningTaskFetcher(context)),
                new RemoteTaskStore(),
                context.getSystemService(ActivityTaskManager.class),
                LocalServices.getService(ActivityTaskManagerInternal.class));
    }

    public TaskSyncController(
            @NonNull TaskContinuityMessenger messenger,
            @NonNull TaskBroadcaster taskBroadcaster,
            @NonNull RemoteTaskStore remoteTaskStore,
            @NonNull ActivityTaskManager activityTaskManager,
            @NonNull ActivityTaskManagerInternal activityTaskManagerInternal) {
        super(Objects.requireNonNull(messenger));
        mTaskBroadcaster = Objects.requireNonNull(taskBroadcaster);
        mRemoteTaskStore = Objects.requireNonNull(remoteTaskStore);
        mActivityTaskManager = Objects.requireNonNull(activityTaskManager);
        mActivityTaskManagerInternal = Objects.requireNonNull(activityTaskManagerInternal);
    }

    public void registerTaskListener(@NonNull IRemoteTaskListener listener) {
        Slog.v(getTag(), "Registering task listener");
        mRemoteTaskStore.addListener(Objects.requireNonNull(listener));
    }

    public void unregisterTaskListener(@NonNull IRemoteTaskListener listener) {
        Slog.v(getTag(), "Unregistering task listener");
        mRemoteTaskStore.removeListener(Objects.requireNonNull(listener));
    }

    public void removeTask(int associationId, int taskId) {
        Slog.v(getTag(), "Removing task " + taskId + " from association " + associationId);
        mRemoteTaskStore.removeTask(associationId, taskId);
    }

    @Override
    protected String getTag() {
        return "TaskSyncController";
    }

    @Override
    public void onEnabled() {
        Slog.v(getTag(), "Registering listeners from ActivityTaskManager");
        mActivityTaskManager.registerTaskStackListener(mTaskBroadcaster);
        mActivityTaskManagerInternal.registerHandoffEnablementListener(mTaskBroadcaster);
    }

    @Override
    public void onDisabled() {
        Slog.v(getTag(), "Unregistering listeners from ActivityTaskManager");
        mActivityTaskManager.unregisterTaskStackListener(mTaskBroadcaster);
        mActivityTaskManagerInternal.unregisterHandoffEnablementListener(mTaskBroadcaster);
    }

    @Override
    public void onAssociationConnected(@NonNull AssociationInfo associationInfo) {
        Slog.v(getTag(), "Association connected: " + associationInfo.getId());
        Objects.requireNonNull(associationInfo);

        mTaskBroadcaster.onDeviceConnected(associationInfo.getId());
        mRemoteTaskStore.addDevice(
                associationInfo.getId(), associationInfo.getDisplayName().toString());
    }

    @Override
    public void onAssociationDisconnected(
            int associationId, @NonNull Collection<AssociationInfo> connectedAssociations) {
        Slog.v(getTag(), "Association disconnected: " + associationId);
        Objects.requireNonNull(connectedAssociations);

        mRemoteTaskStore.removeDevice(associationId);
    }

    @Override
    protected void onContinuityDeviceConnectedMessageReceived(
            int associationId, @NonNull ContinuityDeviceConnected continuityDeviceConnected) {
        Slog.v(getTag(), "Continuity device connected: " + associationId);
        mRemoteTaskStore.setTasks(
                associationId, Objects.requireNonNull(continuityDeviceConnected).remoteTasks());
    }

    @Override
    protected void onRemoteTaskAddedMessageReceived(
            int associationId, @NonNull RemoteTaskAddedMessage remoteTaskAddedMessage) {
        Slog.v(
                getTag(),
                "Remote task added: "
                        + remoteTaskAddedMessage.task().id()
                        + " on association "
                        + associationId);
        mRemoteTaskStore.addTask(
                associationId, Objects.requireNonNull(remoteTaskAddedMessage).task());
    }

    @Override
    protected void onRemoteTaskRemovedMessageReceived(
            int associationId, @NonNull RemoteTaskRemovedMessage remoteTaskRemovedMessage) {
        Slog.v(
                getTag(),
                "Remote task removed: "
                        + remoteTaskRemovedMessage.taskId()
                        + " on association "
                        + associationId);
        mRemoteTaskStore.removeTask(
                associationId, Objects.requireNonNull(remoteTaskRemovedMessage).taskId());
    }

    @Override
    protected void onRemoteTaskUpdatedMessageReceived(
            int associationId, @NonNull RemoteTaskUpdatedMessage remoteTaskUpdatedMessage) {
        Slog.v(
                getTag(),
                "Remote task updated: "
                        + remoteTaskUpdatedMessage.task().id()
                        + " on association "
                        + associationId);
        mRemoteTaskStore.updateTask(
                associationId, Objects.requireNonNull(remoteTaskUpdatedMessage).task());
    }
}
