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

import android.companion.datatransfer.continuity.ITaskContinuityManager;
import android.content.Context;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;

import com.android.server.SystemService;

/**
 * Service to handle task continuity features
 *
 * @hide
 *
 */
public final class TaskContinuityManagerService extends SystemService {

    private static final String TAG = "TaskContinuityManagerService";

    private TaskContinuityManagerServiceImpl mTaskContinuityManagerService;
    private TaskBroadcaster mTaskBroadcaster;
    private TaskContinuityMessageReceiver mTaskContinuityMessageReceiver;

    public TaskContinuityManagerService(Context context) {
        super(context);
        mTaskBroadcaster = new TaskBroadcaster(context);
        mTaskContinuityMessageReceiver = new TaskContinuityMessageReceiver(context);
    }

    @Override
    public void onStart() {
        mTaskContinuityManagerService = new TaskContinuityManagerServiceImpl();
        mTaskBroadcaster.startBroadcasting();
        mTaskContinuityMessageReceiver.startListening(this::onTaskContinuityMessageReceived);
        publishBinderService(Context.TASK_CONTINUITY_SERVICE, mTaskContinuityManagerService);
    }

    private final class TaskContinuityManagerServiceImpl extends ITaskContinuityManager.Stub {

    }

    private void onTaskContinuityMessageReceived(
        int associationId,
        TaskContinuityMessage taskContinuityMessage) {

        Slog.v(TAG, "Received message from association id: " + associationId);
    }
}
