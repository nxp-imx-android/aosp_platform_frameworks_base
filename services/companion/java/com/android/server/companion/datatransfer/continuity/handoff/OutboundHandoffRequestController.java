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

package com.android.server.companion.datatransfer.continuity.handoff;

import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS;

import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageSerializer;

import android.app.ActivityOptions;
import android.app.HandoffActivityData;
import android.content.Context;
import android.content.Intent;
import android.companion.CompanionDeviceManager;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Slog;
import android.os.UserHandle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for outbound handoff requests.
 *
 * This class is responsible for sending handoff request messages to the remote device and handling
 * the results, either launching the task locally or falling back to a web URL if provided.
 */
public class OutboundHandoffRequestController {

    private static final String TAG = "OutboundHandoffRequestController";

    private final Context mContext;
    private final CompanionDeviceManager mCompanionDeviceManager;
    private final ConnectedAssociationStore mConnectedAssociationStore;
    private final Map<Integer, Map<Integer, List<IHandoffRequestCallback>>> mPendingCallbacks
        = new HashMap<>();

    public OutboundHandoffRequestController(
        Context context,
        ConnectedAssociationStore connectedAssociationStore) {
        mContext = context;
        mCompanionDeviceManager = context.getSystemService(CompanionDeviceManager.class);
        mConnectedAssociationStore = connectedAssociationStore;
    }

    public void requestHandoff(int associationId, int taskId, IHandoffRequestCallback callback) {
        if (mConnectedAssociationStore.getConnectedAssociationById(associationId) == null) {
            Slog.w(TAG, "Association " + associationId + " is not connected.");
            try {
                callback.onHandoffRequestFinished(
                    associationId,
                    taskId,
                    HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to notify callback of handoff request result", e);
            }

            return;
        }

        synchronized (mPendingCallbacks) {
            if (!mPendingCallbacks.containsKey(associationId)) {
                mPendingCallbacks.put(associationId, new HashMap<>());
            }

            if (mPendingCallbacks.get(associationId).containsKey(taskId)) {
                mPendingCallbacks.get(associationId).get(taskId).add(callback);
                return;
            }

            List<IHandoffRequestCallback> callbacks = new ArrayList<>();
            callbacks.add(callback);
            mPendingCallbacks.get(associationId).put(taskId, callbacks);
            HandoffRequestMessage handoffRequestMessage = new HandoffRequestMessage(taskId);
            try {
                mCompanionDeviceManager.sendMessage(
                    CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY,
                    TaskContinuityMessageSerializer.serialize(handoffRequestMessage),
                    new int[] {associationId});
            } catch (IOException e) {
                Slog.e(TAG, "Failed to send handoff request message to device " + associationId, e);
            }
        }
    }

    public void onHandoffRequestResultMessageReceived(
        int associationId,
        HandoffRequestResultMessage handoffRequestResultMessage) {

        synchronized (mPendingCallbacks) {
            if (handoffRequestResultMessage.statusCode() != HANDOFF_REQUEST_RESULT_SUCCESS) {
                finishHandoffRequest(
                    associationId,
                    handoffRequestResultMessage.taskId(),
                    handoffRequestResultMessage.statusCode());
            }

            if (handoffRequestResultMessage.activities().isEmpty()) {
                finishHandoffRequest(
                    associationId,
                    handoffRequestResultMessage.taskId(),
                    HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK);
                return;
            }

            launchHandoffTask(
                associationId,
                handoffRequestResultMessage.taskId(),
                handoffRequestResultMessage.activities());
        }
    }

    private void launchHandoffTask(
        int associationId,
        int taskId,
        List<HandoffActivityData> activities) {

        HandoffActivityData topActivity = activities.get(0);
        Intent intent = new Intent();
        intent.setComponent(topActivity.getComponentName());
        intent.putExtras(new Bundle(topActivity.getExtras()));
        // TODO (joeantonetti): Handle failures here and fall back to a web URL.
        mContext.startActivityAsUser(
            intent,
            ActivityOptions.makeBasic().toBundle(),
            UserHandle.CURRENT);
        finishHandoffRequest(associationId, taskId, HANDOFF_REQUEST_RESULT_SUCCESS);
    }

    private void finishHandoffRequest(int associationId, int taskId, int statusCode) {
        synchronized (mPendingCallbacks) {
            if (!mPendingCallbacks.containsKey(associationId)) {
                return;
            }

            Map<Integer, List<IHandoffRequestCallback>> pendingCallbacksForAssociation =
                mPendingCallbacks.get(associationId);

            if (!pendingCallbacksForAssociation.containsKey(taskId)) {
                return;
            }

            for (IHandoffRequestCallback callback : pendingCallbacksForAssociation.get(taskId)) {
                try {
                    callback.onHandoffRequestFinished(associationId, taskId, statusCode);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to notify callback of handoff request result", e);
                }
            }

            pendingCallbacksForAssociation.remove(taskId);
        }
    }
}