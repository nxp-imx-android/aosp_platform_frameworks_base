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

package com.android.server.companion.datatransfer.continuity.connectivity;

import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.CompanionDeviceManager;
import android.companion.AssociationInfo;
import android.content.Context;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageSerializer;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.concurrent.Executor;
import java.util.Objects;

/**
 * Facilitates communication between devices, including sending and receiving messages between
 * devices. Internally, it uses the {@link CompanionDeviceManager} to send and receive messages.
 */
public class TaskContinuityMessenger implements ConnectedAssociationStore.Listener {

    private static final String TAG = "TaskContinuityMessenger";

    private final Context mContext;
    private final CompanionDeviceManager mCompanionDeviceManager;
    private final ConnectedAssociationStore mConnectedAssociationStore;
    private final Executor mExecutor;

    @GuardedBy("mListeners")
    private final Set<Listener> mListeners = new HashSet<>();

    private BiConsumer<Integer, byte[]> mIncomingMessageConsumer;

    public interface Listener {
        void onAssociationConnected(@NonNull AssociationInfo associationInfo);

        void onAssociationDisconnected(int associationId);

        void onMessageReceived(int associationId, @NonNull TaskContinuityMessage message);
    }

    public TaskContinuityMessenger(@NonNull Context context) {

        Objects.requireNonNull(context);

        mContext = context;
        mExecutor = context.getMainExecutor();
        mCompanionDeviceManager = context.getSystemService(CompanionDeviceManager.class);
        mConnectedAssociationStore =
                new ConnectedAssociationStore(mCompanionDeviceManager, mExecutor, this);
    }

    public void addListener(@NonNull Listener listener) {
        synchronized (mListeners) {
            boolean shouldEnable = mListeners.isEmpty();
            mListeners.add(Objects.requireNonNull(listener));
            if (shouldEnable) {
                enable();
            }
        }
    }

    public void removeListener(@NonNull Listener listener) {
        synchronized (mListeners) {
            boolean shouldDisable = mListeners.size() == 1;
            mListeners.remove(Objects.requireNonNull(listener));
            if (shouldDisable) {
                disable();
            }
        }
    }

    public void enable() {
        synchronized (this) {
            if (mIncomingMessageConsumer != null) {
                Slog.i(TAG, "TaskContinuityMessenger is already enabled.");
                return;
            }
            mIncomingMessageConsumer = this::onMessageReceived;
            mCompanionDeviceManager.addOnMessageReceivedListener(
                    mExecutor, MESSAGE_ONEWAY_TASK_CONTINUITY, mIncomingMessageConsumer);
        }

        mConnectedAssociationStore.enable();
    }

    public void disable() {
        synchronized (this) {
            if (mIncomingMessageConsumer == null) {
                Slog.i(TAG, "TaskContinuityMessenger is already disabled.");
                return;
            }
            mCompanionDeviceManager.removeOnMessageReceivedListener(
                    MESSAGE_ONEWAY_TASK_CONTINUITY, mIncomingMessageConsumer);
            mIncomingMessageConsumer = null;
        }

        mConnectedAssociationStore.disable();
    }

    @NonNull
    public ConnectedAssociationStore getConnectedAssociationStore() {
        return mConnectedAssociationStore;
    }

    @Nullable
    public AssociationInfo getAssociationInfo(int associationId) {
        return mConnectedAssociationStore.getConnectedAssociationById(associationId);
    }

    public enum SendMessageResult {
        SUCCESS,
        FAILURE_MESSAGE_SERIALIZATION_FAILED,
        FAILURE_ASSOCIATION_NOT_FOUND,
        FAILURE_INTERNAL_ERROR,
    }

    public SendMessageResult sendMessage(
            int associationId, @NonNull TaskContinuityMessage message) {

        Objects.requireNonNull(message);

        return sendMessage(new int[] {associationId}, message);
    }

    public SendMessageResult sendMessage(
            @NonNull int[] associationIds, @NonNull TaskContinuityMessage message) {

        Objects.requireNonNull(associationIds);
        Objects.requireNonNull(message);

        Slog.i(TAG, "Sending message to " + associationIds.length + " associations.");
        byte[] serializedMessage;
        try {
            serializedMessage = TaskContinuityMessageSerializer.serialize(message);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to serialize message: " + message, e);
            return SendMessageResult.FAILURE_MESSAGE_SERIALIZATION_FAILED;
        }

        for (int associationId : associationIds) {
            if (mConnectedAssociationStore.getConnectedAssociationById(associationId) == null) {
                Slog.w(TAG, "Association " + associationId + " is not connected.");
                return SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND;
            }
        }

        try {
            mCompanionDeviceManager.sendMessage(
                    CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY,
                    serializedMessage,
                    associationIds);
            Slog.i(TAG, "Sending message to " + associationIds.length + " associations.");
            return SendMessageResult.SUCCESS;
        } catch (Exception e) {
            Slog.e(TAG, "Failed to send message to associations", e);
            return SendMessageResult.FAILURE_INTERNAL_ERROR;
        }
    }

    public SendMessageResult sendMessage(@NonNull TaskContinuityMessage message) {
        Objects.requireNonNull(message);

        int[] connectedAssociations =
                mConnectedAssociationStore.getConnectedAssociations().stream()
                        .mapToInt(AssociationInfo::getId)
                        .toArray();

        return sendMessage(connectedAssociations, message);
    }

    @Override
    public void onTransportConnected(@NonNull AssociationInfo associationInfo) {
        Objects.requireNonNull(associationInfo);

        synchronized (mListeners) {
            for (Listener listener : mListeners) {
                listener.onAssociationConnected(associationInfo);
            }
        }
    }

    @Override
    public void onTransportDisconnected(int associationId) {
        synchronized (mListeners) {
            for (Listener listener : mListeners) {
                listener.onAssociationDisconnected(associationId);
            }
        }
    }

    private void onMessageReceived(int associationId, byte[] data) {
        Slog.v(TAG, "Received message from association id: " + associationId);
        try {
            TaskContinuityMessage taskContinuityMessage =
                    TaskContinuityMessageSerializer.deserialize(data);
            synchronized (mListeners) {
                for (Listener listener : mListeners) {
                    listener.onMessageReceived(associationId, taskContinuityMessage);
                }
            }
        } catch (IOException e) {
            Slog.e(TAG, "Failed to parse task continuity message", e);
        }
    }
}
