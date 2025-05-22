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
import android.companion.CompanionDeviceManager;
import android.companion.AssociationInfo;
import android.content.Context;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Responsible for broadcasting recent tasks on the current device to the user's
 * other devices via {@link CompanionDeviceManager}.
 */
class TaskBroadcaster {

    private static final String TAG = "TaskBroadcaster";

    private final Context mContext;
    private final ActivityTaskManager mActivityTaskManager;
    private final CompanionDeviceManager mCompanionDeviceManager;
    private final Set<Integer> mConnectedAssociationIds = new HashSet<>();

    private final Consumer<List<AssociationInfo>> mOnTransportsChangedListener =
        this::onTransportsChanged;

    private boolean mIsBroadcasting = false;

    public TaskBroadcaster(Context context) {
        mContext = context;

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
        mCompanionDeviceManager.addOnTransportsChangedListener(
            mContext.getMainExecutor(),
            mOnTransportsChangedListener
        );
        mIsBroadcasting = true;
    }

    void stopBroadcasting(){
        if (!mIsBroadcasting) {
            Slog.v(TAG, "TaskBroadcaster is not broadcasting");
            return;
        }

        Slog.v(TAG, "Stopping broadcasting");
        mIsBroadcasting = false;
        mCompanionDeviceManager.removeOnTransportsChangedListener(
            mOnTransportsChangedListener
        );
    }

    private void onTransportsChanged(List<AssociationInfo> associationInfos) {
        Set<Integer> removedAssociationIds
            = new HashSet<>(mConnectedAssociationIds);

        for (AssociationInfo associationInfo : associationInfos) {
            if (!mConnectedAssociationIds.contains(associationInfo.getId())) {
                sendDeviceConnectedMessage(associationInfo.getId());
            } else {
                removedAssociationIds.remove(associationInfo.getId());
            }

            mConnectedAssociationIds.add(associationInfo.getId());
        }

        for (Integer removedAssociationId : removedAssociationIds) {
            mConnectedAssociationIds.remove(removedAssociationId);
        }
    }

    private void sendDeviceConnectedMessage(int associationId) {
        Slog.v(
            TAG,
            "Sending device connected message for association id: "
                + associationId);

        List<ActivityManager.RunningTaskInfo> runningTasks
            = mActivityTaskManager.getTasks(Integer.MAX_VALUE, true);

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

    private void sendMessage(int associationId, TaskContinuityMessageData data) {

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
}