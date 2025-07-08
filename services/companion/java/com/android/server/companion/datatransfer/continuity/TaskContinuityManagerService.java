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

import android.annotation.NonNull;
import android.companion.CompanionDeviceManager;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.companion.datatransfer.continuity.ITaskContinuityManager;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;
import android.content.Context;
import android.os.Binder;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.handoff.InboundHandoffRequestController;
import com.android.server.companion.datatransfer.continuity.handoff.OutboundHandoffRequestController;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.tasks.RemoteTaskStore;

import com.android.server.SystemService;
import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to handle task continuity features
 *
 * @hide
 *
 */
public final class TaskContinuityManagerService extends SystemService {

    private static final String TAG = "TaskContinuityManagerService";

    private InboundHandoffRequestController mInboundHandoffRequestController;
    private OutboundHandoffRequestController mOutboundHandoffRequestController;
    private TaskContinuityManagerServiceImpl mTaskContinuityManagerService;
    private TaskBroadcaster mTaskBroadcaster;
    private ConnectedAssociationStore mConnectedAssociationStore;
    private TaskContinuityMessageReceiver mTaskContinuityMessageReceiver;
    private RemoteTaskStore mRemoteTaskStore;

    public TaskContinuityManagerService(Context context) {
        super(context);
        mConnectedAssociationStore = new ConnectedAssociationStore(context);

        mTaskBroadcaster = new TaskBroadcaster(
            context,
            mConnectedAssociationStore);

        mTaskContinuityMessageReceiver = new TaskContinuityMessageReceiver(context);
        mRemoteTaskStore = new RemoteTaskStore(mConnectedAssociationStore);
        mOutboundHandoffRequestController = new OutboundHandoffRequestController(
            context,
            mConnectedAssociationStore);
        mInboundHandoffRequestController = new InboundHandoffRequestController(context);
    }

    @Override
    public void onStart() {
        mTaskContinuityManagerService = new TaskContinuityManagerServiceImpl();
        mTaskBroadcaster.startBroadcasting();
        mTaskContinuityMessageReceiver.startListening(this::onTaskContinuityMessageReceived);
        publishBinderService(Context.TASK_CONTINUITY_SERVICE, mTaskContinuityManagerService);
    }

    private final class TaskContinuityManagerServiceImpl extends ITaskContinuityManager.Stub {
        @Override
        public List<RemoteTask> getRemoteTasks() {
            return mRemoteTaskStore.getMostRecentTasks();
        }

        @Override
        public void registerRemoteTaskListener(@NonNull IRemoteTaskListener listener) {
            mRemoteTaskStore.addListener(listener);
        }

        @Override
        public void unregisterRemoteTaskListener(@NonNull IRemoteTaskListener listener) {
            mRemoteTaskStore.removeListener(listener);
        }

        @Override
        public void requestHandoff(
            int associationId,
            int remoteTaskId,
            @NonNull IHandoffRequestCallback callback) {
            final long ident = Binder.clearCallingIdentity();
            try {
                mOutboundHandoffRequestController.requestHandoff(
                    associationId,
                    remoteTaskId,
                    callback);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    private void onTaskContinuityMessageReceived(
        int associationId,
        TaskContinuityMessage taskContinuityMessage) {

        Slog.v(TAG, "Received message from association id: " + associationId);

        switch (taskContinuityMessage.getData()) {
            case ContinuityDeviceConnected continuityDeviceConnected:
                mRemoteTaskStore.setTasks(
                    associationId,
                    continuityDeviceConnected.remoteTasks());
                break;
            case RemoteTaskAddedMessage remoteTaskAddedMessage:
                mRemoteTaskStore.addTask(
                    associationId,
                    remoteTaskAddedMessage.task());
                break;
            case RemoteTaskRemovedMessage remoteTaskRemovedMessage:
                mRemoteTaskStore.removeTask(
                    associationId,
                    remoteTaskRemovedMessage.taskId());
                break;
            case RemoteTaskUpdatedMessage remoteTaskUpdatedMessage:
                mRemoteTaskStore.updateTask(
                    associationId,
                    remoteTaskUpdatedMessage.task());
                break;
            case HandoffRequestResultMessage handoffRequestResultMessage:
                mOutboundHandoffRequestController.onHandoffRequestResultMessageReceived(
                    associationId,
                    handoffRequestResultMessage);
                break;
            case HandoffRequestMessage handoffRequestMessage:
                mInboundHandoffRequestController.onHandoffRequestMessageReceived(
                    associationId,
                    handoffRequestMessage);
                break;
            default:
                Slog.w(TAG, "Received unknown message from device: " + associationId);
                break;
        }
    }
}
