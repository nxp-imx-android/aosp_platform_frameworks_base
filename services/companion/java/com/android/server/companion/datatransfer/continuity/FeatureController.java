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
import android.companion.AssociationInfo;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import java.util.Objects;

/**
 * Base class for a feature controller. This class is meant to be instantiated once, and then
 * enabled and disabled as needed. This class manages its own instance of TaskContinuityMessenger.
 */
public abstract class FeatureController implements TaskContinuityMessenger.Listener {

    @GuardedBy("this")
    private boolean mEnabled = false;

    protected final int mUserId;

    protected final TaskContinuityMessenger mTaskContinuityMessenger;

    protected FeatureController(
            int userId, @NonNull TaskContinuityMessenger taskContinuityMessenger) {
        this.mUserId = userId;
        this.mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
    }

    /** Returns true if the feature is enabled. */
    public boolean isEnabled() {
        synchronized (this) {
            return mEnabled;
        }
    }

    /** Enables the feature. If the feature is already enabled, this method does nothing. */
    public void enable() {
        synchronized (this) {
            if (mEnabled) {
                Slog.w(getTag(), "Feature is already enabled");
                return;
            }

            Slog.v(getTag(), "Enabling feature");
            mEnabled = true;
            onEnabled();
            mTaskContinuityMessenger.addListener(this);
            Slog.i(getTag(), "Feature enabled");
        }
    }

    /** Disables the feature. If the feature is already disabled, this method does nothing. */
    public void disable() {
        synchronized (this) {
            if (!mEnabled) {
                Slog.w(getTag(), "Feature is already disabled");
                return;
            }

            Slog.v(getTag(), "Disabling feature");
            mEnabled = false;
            onDisabled();
            mTaskContinuityMessenger.removeListener(this);
            Slog.i(getTag(), "Feature disabled");
        }
    }

    /** Override this method to perform any necessary setup when the feature is enabled. */
    protected void onEnabled() {}

    /** Override this method to perform any necessary cleanup when the feature is disabled. */
    protected void onDisabled() {}

    /** Override this method to return the tag to be used for logging. */
    @NonNull
    protected abstract String getTag();

    /**
     * Called when a ContinuityDeviceConnected message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param continuityDeviceConnected The ContinuityDeviceConnected message.
     */
    protected void onContinuityDeviceConnectedMessageReceived(
            int associationId, @NonNull ContinuityDeviceConnected continuityDeviceConnected) {}

    /**
     * Called when a RemoteTaskAddedMessage message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param remoteTaskAddedMessage The RemoteTaskAddedMessage message.
     */
    protected void onRemoteTaskAddedMessageReceived(
            int associationId, @NonNull RemoteTaskAddedMessage remoteTaskAddedMessage) {}

    /**
     * Called when a RemoteTaskRemovedMessage message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param remoteTaskRemovedMessage The RemoteTaskRemovedMessage message.
     */
    protected void onRemoteTaskRemovedMessageReceived(
            int associationId, @NonNull RemoteTaskRemovedMessage remoteTaskRemovedMessage) {}

    /**
     * Called when a RemoteTaskUpdatedMessage message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param remoteTaskUpdatedMessage The RemoteTaskUpdatedMessage message.
     */
    protected void onRemoteTaskUpdatedMessageReceived(
            int associationId, @NonNull RemoteTaskUpdatedMessage remoteTaskUpdatedMessage) {}

    /**
     * Called when a HandoffRequestMessage message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param handoffRequestMessage The HandoffRequestMessage message.
     */
    protected void onHandoffRequestMessageReceived(
            int associationId, @NonNull HandoffRequestMessage handoffRequestMessage) {}

    /**
     * Called when a HandoffRequestResultMessage message is received.
     *
     * @param associationId The association ID of the device that sent the message.
     * @param handoffRequestResultMessage The HandoffRequestResultMessage message.
     */
    protected void onHandoffRequestResultMessageReceived(
            int associationId, @NonNull HandoffRequestResultMessage handoffRequestResultMessage) {}

    /**
     * Called when an association is connected.
     *
     * @param associationInfo The AssociationInfo of the connected device.
     */
    @Override
    public void onAssociationConnected(@NonNull AssociationInfo associationInfo) {}

    /**
     * Called when an association is disconnected.
     *
     * @param associationId The association ID of the disconnected device.
     * @param connectedAssociations The list of all connected devices.
     */
    @Override
    public void onAssociationDisconnected(int associationId) {}

    @Override
    public void onMessageReceived(
            int associationId, @NonNull TaskContinuityMessage taskContinuityMessage) {
        Slog.v(getTag(), "Received message from association " + associationId);
        switch (Objects.requireNonNull(taskContinuityMessage)) {
            case ContinuityDeviceConnected continuityDeviceConnected:
                onContinuityDeviceConnectedMessageReceived(
                        associationId, continuityDeviceConnected);
                break;
            case RemoteTaskAddedMessage remoteTaskAddedMessage:
                onRemoteTaskAddedMessageReceived(associationId, remoteTaskAddedMessage);
                break;
            case RemoteTaskRemovedMessage remoteTaskRemovedMessage:
                onRemoteTaskRemovedMessageReceived(associationId, remoteTaskRemovedMessage);
                break;
            case RemoteTaskUpdatedMessage remoteTaskUpdatedMessage:
                onRemoteTaskUpdatedMessageReceived(associationId, remoteTaskUpdatedMessage);
                break;
            case HandoffRequestMessage handoffRequestMessage:
                onHandoffRequestMessageReceived(associationId, handoffRequestMessage);
                break;
            case HandoffRequestResultMessage handoffRequestResultMessage:
                onHandoffRequestResultMessageReceived(associationId, handoffRequestResultMessage);
                break;
            default:
                Slog.w(getTag(), "Received unknown message from device: " + associationId);
                break;
        }
    }
}
