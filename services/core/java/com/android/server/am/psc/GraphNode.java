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

import android.annotation.NonNull;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import java.util.Objects;

/**
 * Represents a process within the process graph. Each node is embedded within a
 * {@link ProcessRecordInternal}.
 *
 * This class stores the calculated importance values such as capabilities and the process state
 * for the process. The calculated values are used by specialized controllers such as
 * {@link CapabilityController} to evaluate the node's outgoing edges, which are service or provider
 * bindings, to determine the importance of the edges' target processes during graph traversal.
 */
@RavenwoodKeepWholeClass
class GraphNode {
    /** A reference to the underlying ProcessRecordInternal. */
    private final @NonNull ProcessRecordInternal mProc;

    GraphNode(@NonNull ProcessRecordInternal app) {
        mProc = Objects.requireNonNull(app);
    }

    int getMaxAdj() {
        return mProc.getMaxAdj();
    }

    boolean isProcessRunning() {
        return mProc.isProcessRunning();
    }
}
