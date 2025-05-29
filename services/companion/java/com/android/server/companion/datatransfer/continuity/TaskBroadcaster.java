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

package com.android.server.companion.datatransfer.continuity;

import static android.companion.CompanionDeviceManager.MESSAGE_TASK_CONTINUITY;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.companion.CompanionDeviceManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Responsible for broadcasting recent tasks on the current device to the user's
 *
 * other devices via {@link CompanionDeviceManager}.
 */
class TaskBroadcaster
    extends TaskStackListener
    implements ConnectedAssociationStore.Observer {

    private static final String TAG = "TaskBroadcaster";

    private final Context mContext;
    private final ActivityTaskManager mActivityTaskManager;
    private final CompanionDeviceManager mCompanionDeviceManager;
    private final ConnectedAssociationStore mConnectedAssociationStore;

    private boolean mIsBroadcasting = false;

    public TaskBroadcaster(
        Context context,
        ConnectedAssociationStore connectedAssociationStore) {

        mContext = context;
        mConnectedAssociationStore = connectedAssociationStore;

        mActivityTaskManager
            = context.getSystemService(ActivityTaskManager.class);

        mCompanionDeviceManager
            = context.getSystemService(CompanionDeviceManager.class);
    }

    void startBroadcasting(){
        if (mIsBroadcasting) {
            Slog.v(TAG, "TaskBroadcaster is already broadcasting");
            return;
        }

        Slog.v(TAG, "Starting broadcasting");
        mConnectedAssociationStore.addObserver(this);
        mActivityTaskManager.registerTaskStackListener(this);

        mIsBroadcasting = true;
    }

    void stopBroadcasting(){
        if (!mIsBroadcasting) {
            Slog.v(TAG, "TaskBroadcaster is not broadcasting");
            return;
        }

        Slog.v(TAG, "Stopping broadcasting");
        mIsBroadcasting = false;
        mConnectedAssociationStore.removeObserver(this);
        mActivityTaskManager.unregisterTaskStackListener(this);
    }

    @Override
    public void onTransportConnected(int associationId) {
        Slog.v(
            TAG,
            "Transport connected for association id: " + associationId);

        sendDeviceConnectedMessage(associationId);
    }

    @Override
    public void onTransportDisconnected(int associationId) {
        Slog.v(
            TAG,
            "Transport disconnected for association id: " + associationId);
    }

    @Override
    public void onTaskCreated(
        int taskId,
        ComponentName componentName) throws RemoteException {

        Slog.v(TAG, "onTaskCreated: taskId=" + taskId);

        ActivityManager.RunningTaskInfo taskInfo = getRunningTask(taskId);

        if (taskInfo != null) {
            RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(taskInfo);
            RemoteTaskAddedMessage taskAddedMessage
                = new RemoteTaskAddedMessage(remoteTaskInfo);

            sendMessageToAllConnectedAssociations(taskAddedMessage);
        } else {
            Slog.w(TAG, "Could not find RunningTaskInfo for taskId: " + taskId);
        }
    }

    private void sendDeviceConnectedMessage(int associationId) {
        Slog.v(
            TAG,
            "Sending device connected message for association id: "
                + associationId);

        List<ActivityManager.RunningTaskInfo> runningTasks = getRunningTasks();

        int currentForegroundTaskId = -1;
        if (runningTasks.size() > 0) {
            currentForegroundTaskId = runningTasks.get(0).taskId;
        }

        List<RemoteTaskInfo> remoteTasks = new ArrayList<>();
        for (ActivityManager.RunningTaskInfo taskInfo : runningTasks) {
            remoteTasks.add(new RemoteTaskInfo(taskInfo));
        }

        ContinuityDeviceConnected deviceConnectedMessage =
            new ContinuityDeviceConnected(currentForegroundTaskId, remoteTasks);

        sendMessage(associationId, deviceConnectedMessage);
    }

    private void sendMessage(
        int associationId,
        TaskContinuityMessageData data) {

        Slog.v(
            TAG,
            "Sending message to association id: "
                + associationId);

        TaskContinuityMessage message = new TaskContinuityMessage.Builder()
                .setData(data)
                .build();

        mCompanionDeviceManager.sendMessage(
            CompanionDeviceManager.MESSAGE_TASK_CONTINUITY,
            message.toBytes(),
            new int[] {associationId});
    }

    private void sendMessageToAllConnectedAssociations(
        TaskContinuityMessageData data) {

        Set<Integer> connectedAssociations
            = mConnectedAssociationStore.getConnectedAssociations();

        Slog.v(
            TAG,
            "Sending message to " + connectedAssociations.size() + " associations.");

        for (Integer associationId : connectedAssociations) {
            sendMessage(associationId, data);
        }
    }

    private ActivityManager.RunningTaskInfo getRunningTask(int taskId) {
        List<ActivityManager.RunningTaskInfo> runningTasks = getRunningTasks();
        if (runningTasks != null) {
            for (ActivityManager.RunningTaskInfo info : runningTasks) {
                if (info.taskId == taskId) {
                    return info;
                }
            }
        }

        return null;
    }

    private List<ActivityManager.RunningTaskInfo> getRunningTasks() {
        return mActivityTaskManager.getTasks(Integer.MAX_VALUE, true);
    }
}