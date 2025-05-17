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

import android.content.Context;
import android.companion.CompanionDeviceManager;
import android.util.Slog;

import java.util.function.BiConsumer;

/**
 * Responsible for receiving task continuity messages from the user's other
 * devices.
 */
class TaskReceiver {

    private static final String TAG = "TaskReceiver";

    private final Context mContext;
    private final CompanionDeviceManager mCompanionDeviceManager;

    private final BiConsumer<Integer, byte[]> mOnMessageReceivedListener
        = this::onMessageReceived;

    private boolean mIsListening = false;

    TaskReceiver(Context context) {
        mContext = context;
        mCompanionDeviceManager = context
            .getSystemService(CompanionDeviceManager.class);
    }

    /**
     * Starts listening for task continuity messages.
     */
    void startListening() {
        if (mIsListening) {
            Slog.v(TAG, "TaskReceiver is already listening");
            return;
        }

        mCompanionDeviceManager.addOnMessageReceivedListener(
            mContext.getMainExecutor(),
            MESSAGE_TASK_CONTINUITY,
            mOnMessageReceivedListener
        );

        mIsListening = true;
    }

    /**
     * Stops listening for task continuity messages.
     */
    void stopListening() {
        if (!mIsListening) {
            Slog.v(TAG, "TaskReceiver is not listening");
            return;
        }

        mCompanionDeviceManager.removeOnMessageReceivedListener(
            MESSAGE_TASK_CONTINUITY,
            mOnMessageReceivedListener);

        mIsListening = false;
    }

    private void onMessageReceived(int associationId, byte[] data) {
        Slog.v(TAG, "Received message from association id: " + associationId);
    }
}