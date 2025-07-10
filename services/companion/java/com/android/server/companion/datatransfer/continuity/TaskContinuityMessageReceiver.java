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

import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY;

import android.content.Context;
import android.companion.CompanionDeviceManager;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageSerializer;

import java.util.function.BiConsumer;

/**
 * Responsible for receiving task continuity messages from the user's other
 * devices.
 */
class TaskContinuityMessageReceiver {

    private static final String TAG = "TaskContinuityMessageReceiver";

    private final Context mContext;
    private final CompanionDeviceManager mCompanionDeviceManager;

    private final BiConsumer<Integer, byte[]> mOnMessageReceivedListener
        = this::onMessageReceived;

    private BiConsumer<Integer, TaskContinuityMessage> mOnTaskContinuityMessageReceivedListener;

    private boolean mIsListening = false;

    TaskContinuityMessageReceiver(Context context) {
        mContext = context;
        mCompanionDeviceManager = context
            .getSystemService(CompanionDeviceManager.class);
    }

    /**
     * Starts listening for task continuity messages.
     *
     * @return true if listening was started successfully, false otherwise.
     */
    boolean startListening(
        BiConsumer<Integer, TaskContinuityMessage> onTaskContinuityMessageReceivedListener) {
        if (mIsListening) {
            Slog.v(TAG, "TaskContinuityMessageReceiver is already listening");
            return false;
        }

        mOnTaskContinuityMessageReceivedListener = onTaskContinuityMessageReceivedListener;
        mCompanionDeviceManager.addOnMessageReceivedListener(
            mContext.getMainExecutor(),
            MESSAGE_ONEWAY_TASK_CONTINUITY,
            mOnMessageReceivedListener
        );

        mIsListening = true;
        return true;
    }

    /**
     * Stops listening for task continuity messages.
     */
    void stopListening() {
        if (!mIsListening) {
            Slog.v(TAG, "TaskContinuityMessageReceiver is not listening");
            return;
        }

        mOnTaskContinuityMessageReceivedListener = null;

        mCompanionDeviceManager.removeOnMessageReceivedListener(
            MESSAGE_ONEWAY_TASK_CONTINUITY,
            mOnMessageReceivedListener);

        mIsListening = false;
    }

    private void onMessageReceived(int associationId, byte[] data) {
        Slog.v(TAG, "Received message from association id: " + associationId);
      try {
            TaskContinuityMessage taskContinuityMessage
                = TaskContinuityMessageSerializer.deserialize(data);
            if (mOnTaskContinuityMessageReceivedListener != null) {
                mOnTaskContinuityMessageReceivedListener.accept(
                    associationId,
                    taskContinuityMessage);
            }
      } catch (Exception e) {
        Slog.e(TAG, "Failed to parse task continuity message", e);
      }
    }
}