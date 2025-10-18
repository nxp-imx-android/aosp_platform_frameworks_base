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
import android.content.Context;

import com.android.server.companion.datatransfer.continuity.FeatureController;
import com.android.server.companion.datatransfer.continuity.FeatureControllerCache;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.tasks.TaskSyncController;

import java.util.Objects;

public class HandoffControllerCache extends FeatureControllerCache<HandoffController> {

    private final Context mContext;
    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final FeatureControllerCache<TaskSyncController> mTaskSyncControllerCache;

    public HandoffControllerCache(
            @NonNull Context context,
            @NonNull TaskContinuityMessenger taskContinuityMessenger,
            @NonNull FeatureControllerCache<TaskSyncController> taskSyncControllerCache) {
        mContext = Objects.requireNonNull(context);
        mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
        mTaskSyncControllerCache = Objects.requireNonNull(taskSyncControllerCache);
    }

    @Override
    protected HandoffController createFeatureControllerForUser(int userId) {
        return new HandoffController(
                userId,
                mTaskContinuityMessenger,
                mTaskSyncControllerCache.getOrCreateFeatureController(userId),
                new InboundHandoffRequestHandler(mTaskContinuityMessenger),
                new OutboundHandoffRequestHandler(
                        mContext,
                        mTaskContinuityMessenger,
                        mTaskSyncControllerCache.getOrCreateFeatureController(userId)));
    }
}
