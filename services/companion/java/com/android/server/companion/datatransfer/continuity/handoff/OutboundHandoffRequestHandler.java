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

import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_HANDOFF_DISABLED;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS;

import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.content.Context;
import android.util.Slog;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.tasks.TaskSyncController;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Controller for outbound handoff requests.
 *
 * <p>This class is responsible for sending handoff request messages to the remote device and
 * handling the results, either launching the task locally or falling back to a web URL if provided.
 */
public class OutboundHandoffRequestHandler {

    private static final String TAG = OutboundHandoffRequestHandler.class.getSimpleName();

    private record PendingHandoffRequest(int associationId, int taskId) {}

    private final Context mContext;
    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final TaskSyncController mTaskSyncController;
    private final HandoffRequestCallbackHolder mHandoffRequestCallbackHolder =
            new HandoffRequestCallbackHolder();
    private final Set<PendingHandoffRequest> mPendingHandoffRequests = new HashSet<>();

    public OutboundHandoffRequestHandler(
            @NonNull Context context,
            @NonNull TaskContinuityMessenger taskContinuityMessenger,
            @NonNull TaskSyncController taskSyncController) {

        mContext = Objects.requireNonNull(context);
        mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
        mTaskSyncController = Objects.requireNonNull(taskSyncController);
    }

    public void requestHandoff(int associationId, int taskId, IHandoffRequestCallback callback) {
        synchronized (mPendingHandoffRequests) {
            PendingHandoffRequest request = new PendingHandoffRequest(associationId, taskId);
            boolean isNewRequest = !mPendingHandoffRequests.contains(request);
            mHandoffRequestCallbackHolder.registerCallback(associationId, taskId, callback);
            if (!isNewRequest) {
                return;
            }

            mPendingHandoffRequests.add(request);
            TaskContinuityMessenger.SendMessageResult result =
                    mTaskContinuityMessenger.sendMessage(
                            associationId, new HandoffRequestMessage(taskId));

            switch (result) {
                case TaskContinuityMessenger.SendMessageResult.SUCCESS:
                    Slog.i(TAG, "Successfully sent handoff request message.");
                    break;
                case TaskContinuityMessenger.SendMessageResult.FAILURE_MESSAGE_SERIALIZATION_FAILED:
                    Slog.e(TAG, "Failed to serialize handoff request message.");
                    finishHandoffRequest(
                            associationId,
                            taskId,
                            HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);
                    break;
                case TaskContinuityMessenger.SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND:
                    Slog.w(TAG, "Association " + associationId + " is not connected.");
                    finishHandoffRequest(
                            associationId, taskId, HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND);
                    break;
                case TaskContinuityMessenger.SendMessageResult.FAILURE_INTERNAL_ERROR:
                    Slog.e(TAG, "Failed to send handoff request message - internal error.");
                    finishHandoffRequest(
                            associationId,
                            taskId,
                            HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);
                    break;
            }
        }
    }

    public void cancelAllOutboundRequests() {
        mHandoffRequestCallbackHolder.finishAllCallbacks(
                HANDOFF_REQUEST_RESULT_FAILURE_HANDOFF_DISABLED);
    }

    public void onHandoffRequestResultMessageReceived(
            int associationId, HandoffRequestResultMessage handoffRequestResultMessage) {

        synchronized (mPendingHandoffRequests) {
            PendingHandoffRequest request =
                    new PendingHandoffRequest(associationId, handoffRequestResultMessage.taskId());
            if (!mPendingHandoffRequests.contains(request)) {
                return;
            }

            if (handoffRequestResultMessage.statusCode() != HANDOFF_REQUEST_RESULT_SUCCESS) {
                finishHandoffRequest(
                        associationId,
                        handoffRequestResultMessage.taskId(),
                        handoffRequestResultMessage.statusCode());
                return;
            }

            if (!HandoffActivityStarter.start(mContext, handoffRequestResultMessage.activities())) {
                finishHandoffRequest(
                        associationId,
                        handoffRequestResultMessage.taskId(),
                        HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);
                return;
            } else {
                finishHandoffRequest(
                        associationId,
                        handoffRequestResultMessage.taskId(),
                        HANDOFF_REQUEST_RESULT_SUCCESS);
            }
        }
    }

    private void finishHandoffRequest(int associationId, int taskId, int statusCode) {
        synchronized (mPendingHandoffRequests) {
            PendingHandoffRequest request = new PendingHandoffRequest(associationId, taskId);
            if (!mPendingHandoffRequests.contains(request)) {
                return;
            }

            mPendingHandoffRequests.remove(request);
            mHandoffRequestCallbackHolder.notifyAndRemoveCallbacks(
                    associationId, taskId, statusCode);
            mTaskSyncController.removeTask(associationId, taskId);
        }
    }
}
