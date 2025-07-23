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

import static com.android.server.companion.datatransfer.contextsync.BitmapUtils.renderDrawableToByteArray;

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Responsible for broadcasting recent tasks on the current device to the user's
 *
 * other devices via {@link CompanionDeviceManager}.
 */
class TaskBroadcaster extends TaskStackListener {

    private static final String TAG = "TaskBroadcaster";

    private final Context mContext;
    private final ActivityTaskManager mActivityTaskManager;
    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final PackageManager mPackageManager;

    private boolean mIsListeningToActivityTaskManager = false;

    public TaskBroadcaster(
        @NonNull Context context,
        @NonNull TaskContinuityMessenger taskContinuityMessenger) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(taskContinuityMessenger);

        mContext = context;
        mActivityTaskManager = context.getSystemService(ActivityTaskManager.class);
        mPackageManager = context.getPackageManager();
        mTaskContinuityMessenger = taskContinuityMessenger;
    }

    public void onDeviceConnected(int id) {
        Slog.v(TAG, "Transport connected for association id: " + id);
        sendDeviceConnectedMessage(id);
        synchronized (this) {
            if (!mIsListeningToActivityTaskManager) {
                mActivityTaskManager.registerTaskStackListener(this);
                mIsListeningToActivityTaskManager = true;
            }
        }
    }

    public void onAllDevicesDisconnected() {
        synchronized (this) {
            if (mIsListeningToActivityTaskManager) {
                mActivityTaskManager.unregisterTaskStackListener(this);
                mIsListeningToActivityTaskManager = false;
            }
        }
    }

    @Override
    public void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException {
        Slog.v(TAG, "onTaskCreated: taskId=" + taskId);
        RunningTaskInfo taskInfo = getRunningTask(taskId);
        if (taskInfo == null) {
            Slog.w(TAG, "Could not find RunningTaskInfo for taskId: " + taskId);
            return;
        }

        RemoteTaskInfo remoteTaskInfo = createRemoteTaskInfo(taskInfo);
        if (remoteTaskInfo == null) {
            Slog.w(TAG, "Could not create RemoteTaskInfo for task: " + taskInfo.taskId);
            return;
        }

        RemoteTaskAddedMessage taskAddedMessage = new RemoteTaskAddedMessage(remoteTaskInfo);
        mTaskContinuityMessenger.sendMessage(taskAddedMessage);
    }

    @Override
    public void onTaskRemoved(int taskId) throws RemoteException {
        Slog.v(TAG, "onTaskRemoved: taskId=" + taskId);
        RemoteTaskRemovedMessage taskRemovedMessage = new RemoteTaskRemovedMessage(taskId);
        mTaskContinuityMessenger.sendMessage(taskRemovedMessage);
    }

    @Override
    public void onTaskMovedToFront(RunningTaskInfo taskInfo) throws RemoteException {
        Slog.v(TAG, "onTaskMovedToFront: taskId=" + taskInfo.taskId);

        RemoteTaskInfo remoteTaskInfo = createRemoteTaskInfo(taskInfo);
        if (remoteTaskInfo == null) {
            Slog.w(TAG, "Could not create RemoteTaskInfo for task: " + taskInfo.taskId);
            return;
        }

        RemoteTaskUpdatedMessage taskUpdatedMessage = new RemoteTaskUpdatedMessage(remoteTaskInfo);
        mTaskContinuityMessenger.sendMessage(taskUpdatedMessage);
    }

    private void sendDeviceConnectedMessage(int associationId) {
        Slog.v(
            TAG,
            "Sending device connected message for association id: "
                + associationId);

        List<RemoteTaskInfo> remoteTasks = getRunningTasks().stream()
            .map(this::createRemoteTaskInfo)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        ContinuityDeviceConnected deviceConnectedMessage
            = new ContinuityDeviceConnected(remoteTasks);

        mTaskContinuityMessenger.sendMessage(associationId, deviceConnectedMessage);
    }

    private RunningTaskInfo getRunningTask(int taskId) {
        List<RunningTaskInfo> runningTasks = getRunningTasks();
        if (runningTasks != null) {
            for (RunningTaskInfo info : runningTasks) {
                if (info.taskId == taskId) {
                    return info;
                }
            }
        }

        return null;
    }

    private List<RunningTaskInfo> getRunningTasks() {
        return mActivityTaskManager.getTasks(Integer.MAX_VALUE, true);
    }

    private RemoteTaskInfo createRemoteTaskInfo(RunningTaskInfo taskInfo) {
        PackageInfo packageInfo;
        try {
            packageInfo = mPackageManager.getPackageInfo(
                taskInfo.baseActivity.getPackageName(),
                PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(TAG, "Failed to get package info for task: " + taskInfo.taskId, e);
            return null;
        }

        String baseApplicationLabel = mPackageManager.getApplicationLabel(
            packageInfo.applicationInfo).toString();

        Drawable baseApplicationIcon = mPackageManager.getApplicationIcon(
            packageInfo.applicationInfo);

        return new RemoteTaskInfo(
            taskInfo.taskId,
            baseApplicationLabel,
            taskInfo.lastActiveTime,
            renderDrawableToByteArray(baseApplicationIcon));
    }
}