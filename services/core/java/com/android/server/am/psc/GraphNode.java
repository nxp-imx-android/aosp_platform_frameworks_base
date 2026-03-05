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
import android.app.ActivityManager.ProcessCapability;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import java.util.function.Consumer;

/**
 * Represents a node within the process graph.
 *
 * <p>A node can be either a {@link ProcessNode}, representing a specific process,
 * or the {@link SystemNode}, representing the system itself as a source of importance.
 */
@RavenwoodKeepWholeClass
interface GraphNode {
    /** Streams the outgoing edges of this node to {@code consumer}. */
    void forEachOutgoingEdge(@NonNull Consumer<GraphEdge> consumer);

    /** Gets output capabilities from the node. */
    @ProcessCapability
    int getCapability();
}
