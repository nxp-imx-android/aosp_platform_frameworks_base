/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessState;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import java.util.List;

/**
 * The controller responsible for computing and managing {@link ProcessState} using
 * the binding-based graph traversal algorithm.
 */
@RavenwoodKeepWholeClass
final class ProcStateController {
    /** Performs a full update of process states for all processes in the system. */
    void fullUpdate() {
        // TODO: b/479360024 - Implement the method.
    }

    /**
     * Performs a partial update of process states for a specific set of graph edges.
     *
     * @param edges          The list of graph edges that have changed and need re-evaluation.
     * @param reachableNodes The list of nodes that are affected by these changes and need
     *                       re-computation.
     */
    void partialUpdate(@NonNull List<GraphEdge> edges, @NonNull List<ProcessNode> reachableNodes) {
        // TODO: b/479360024 - Implement the method.
    }
}
