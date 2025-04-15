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

package android.companion.datatransfer.continuity;

import android.annotation.SystemService;
import android.content.Context;

/**
 * This class facilitates task continuity between devices owned by the same user.
 * This includes synchronizing lists of open tasks between a user's devices, as well as requesting
 * to hand off a task from one device to another. Handing a task off to a device will resume the
 * application on the receiving device, preserving the state of the task.
 *
 * @hide
 */
@SystemService(Context.TASK_CONTINUITY_SERVICE)
public class TaskContinuityManager {
    private final Context mContext;
    private final ITaskContinuityManager mService;

    public TaskContinuityManager(Context context, ITaskContinuityManager service) {
        mContext = context;
        mService = service;
    }
}
