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
package com.android.server.am.psc;

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;

import static com.android.server.am.psc.Constants.FOREGROUND_APP_ADJ;

import android.app.ActivityManager.ProcessCapability;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

/** The class that computes capabilities and CPU time reasons for processes. */
@RavenwoodKeepWholeClass
class CapabilityController {
    /** Evaluates a filter by combining all the policies of a process edge. */
    static @ProcessCapability int evaluateFilter(ProcessEdge edge) {
        // No capability is granted to a non-running process.
        if (!edge.getTarget().isProcessRunning()) return PROCESS_CAPABILITY_NONE;
        // TODO(b/466961280): Add more policies.
        return evaluateMaxAdjPolicy(edge);
    }

    /** Evaluates a filter based on the process's max oom score (maxAdj). */
    private static @ProcessCapability int evaluateMaxAdjPolicy(ProcessEdge edge) {
        if (edge.getTarget().getMaxAdj() <= FOREGROUND_APP_ADJ) {
            return PROCESS_CAPABILITY_ALL;
        } else {
            return PROCESS_CAPABILITY_NONE;
        }
    }
}
