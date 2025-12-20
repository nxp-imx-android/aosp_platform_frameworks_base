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

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessCapability;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

/** The class that computes capabilities and CPU time reasons for processes. */
@RavenwoodKeepWholeClass
class CapabilityController {
    /** Representation of a process in the process graph. */
    static class Node {
        /** A reference to the underlying ProcessRecordInternal. */
        final @NonNull ProcessRecordInternal mApp;

        Node(@NonNull ProcessRecordInternal app) {
            mApp = app;
        }

        int getMaxAdj() {
            return mApp.getMaxAdj();
        }

        boolean isProcessRunning() {
            return mApp.isProcessRunning();
        }
    }

    /**
     * A directional edge that propagates capabilities from its source to its target.
     * For each capability, if the edge source has the capability, and the edge also propagates it,
     * then the edge target will obtain that capability.
     */
    abstract static class Edge {
        /**
         * Evaluates whether the edge propagates each capability.
         *
         * @return a bitmask where each bit indicates whether the edge propagates a capability.
         */
        abstract @ProcessCapability int evaluatePropagationFilter();
    }

    /** Edge that represents how capability is granted to the process directly by the system. */
    static class ProcessEdge extends Edge {
        private final @NonNull Node mProc;

        ProcessEdge(@NonNull Node proc) {
            mProc = proc;
        }

        @Override
        @ProcessCapability int evaluatePropagationFilter() {
            // No capability is granted to a non-running process.
            if (!mProc.isProcessRunning()) return PROCESS_CAPABILITY_NONE;
            // TODO(b/466961280): Add more policies.
            return evaluatePolicyMaxAdj();
        }

        /** Evaluates a filter based on the process's max oom score (maxAdj). */
        private @ProcessCapability int evaluatePolicyMaxAdj() {
            if (mProc.getMaxAdj() <= FOREGROUND_APP_ADJ) {
                return PROCESS_CAPABILITY_ALL;
            } else {
                return PROCESS_CAPABILITY_NONE;
            }
        }
    }
}
