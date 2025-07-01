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

package com.android.server.signalcollector;

import android.app.ActivityManager.ProcessState;

/**
 * Internal interface for the SignalCollectorService.
 *
 * @hide Only for use within the system server.
 */
public abstract class SignalCollectorManagerInternal {

    /**
     * Gets the process state of a uid.
     *
     * @param uid The uid to get the process state for.
     * @return The process state of the uid, or PROCESS_STATE_UNKNOWN if the uid is not found.
     */
    @ProcessState
    public abstract int getProcessState(int uid);
}
