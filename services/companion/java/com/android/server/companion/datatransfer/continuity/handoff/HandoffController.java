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

import android.annotation.NonNull;
import android.companion.datatransfer.continuity.TaskContinuityManager;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.FeatureController;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.tasks.TaskSyncController;

import java.util.Objects;

public class HandoffController extends FeatureController {

    private final TaskSyncController mTaskSyncController;
    private final InboundHandoffRequestHandler mInboundHandoffRequestHandler;
    private final OutboundHandoffRequestHandler mOutboundHandoffRequestHandler;

    public HandoffController(
            int userId,
            @NonNull TaskContinuityMessenger taskContinuityMessenger,
            @NonNull TaskSyncController taskSyncController,
            @NonNull InboundHandoffRequestHandler inboundHandoffRequestHandler,
            @NonNull OutboundHandoffRequestHandler outboundHandoffRequestHandler) {
        super(userId, Objects.requireNonNull(taskContinuityMessenger));
        this.mTaskSyncController = Objects.requireNonNull(taskSyncController);
        this.mInboundHandoffRequestHandler = Objects.requireNonNull(inboundHandoffRequestHandler);
        this.mOutboundHandoffRequestHandler = Objects.requireNonNull(outboundHandoffRequestHandler);
    }

    public void requestHandoff(
            int associationId, int remoteTaskId, @NonNull IHandoffRequestCallback callback) {
        if (!isEnabled()) {
            Slog.w(getTag(), "Requested Handoff when controller is disabled. Returning failure.");
            try {
                callback.onHandoffRequestFinished(
                        associationId,
                        remoteTaskId,
                        TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_HANDOFF_DISABLED);
            } catch (RemoteException e) {
                Slog.e(getTag(), "Failed to notify callback of handoff request cancellation", e);
            }

            return;
        }

        Slog.v(getTag(), "Requesting handoff from association " + associationId);
        mOutboundHandoffRequestHandler.requestHandoff(
                associationId, remoteTaskId, Objects.requireNonNull(callback));
    }

    @Override
    public void onDisabled() {
        Slog.v(getTag(), "Cancelling all outbound handoff requests.");
        mOutboundHandoffRequestHandler.cancelAllOutboundRequests();
    }

    @Override
    public String getTag() {
        return "HandoffController";
    }

    @Override
    protected void onHandoffRequestMessageReceived(
            int associationId, @NonNull HandoffRequestMessage handoffRequestMessage) {
        Slog.v(getTag(), "Handoff request message received from association " + associationId);
        mInboundHandoffRequestHandler.onHandoffRequestMessageReceived(
                associationId, Objects.requireNonNull(handoffRequestMessage));
    }

    @Override
    protected void onHandoffRequestResultMessageReceived(
            int associationId, @NonNull HandoffRequestResultMessage handoffRequestResultMessage) {
        Slog.v(
                getTag(),
                "Handoff request result message received from association " + associationId);
        mOutboundHandoffRequestHandler.onHandoffRequestResultMessageReceived(
                associationId, Objects.requireNonNull(handoffRequestResultMessage));
    }
}
