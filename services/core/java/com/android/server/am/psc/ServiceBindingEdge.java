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
import android.annotation.Nullable;
import android.app.ActivityManager.ProcessCapability;
import android.content.pm.ServiceInfo;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import com.android.server.am.psc.OomAdjusterImpl.Connection.CpuTimeTransmissionType;

import java.util.Objects;

/**
 * Edge that decides propagation through {@link ConnectionRecordInternal} from client to service.
 */
@RavenwoodKeepWholeClass
class ServiceBindingEdge extends GraphEdge {
    private final @NonNull ConnectionRecordInternal mConn;
    /**
     * Source node of this edge. The node represents the client process and remains unchanged
     * during the lifetime of this edge.
     */
    private final @NonNull GraphNode mSource;
    /**
     * Target node of this edge. The node represents the service host process. When
     * {@link ServiceRecordInternal#setHostProcess} is called, the host process record can change
     * or become {@code null}. Consequently, the target node may change during the lifetime of this
     * edge.
     *
     * {@link #updateTarget()} updates the target node, and should be called before evaluating this
     * edge in each update.
     */
    private GraphNode mTarget;

    ServiceBindingEdge(@NonNull ConnectionRecordInternal conn) {
        mConn = Objects.requireNonNull(conn);

        // Compute the source node and save it to mSource.
        // All the attributes used for the computation are final, so the source node is immutable
        // during the lifetime of this edge.
        final ServiceRecordInternal service = mConn.getService();
        final ProcessRecordInternal attributedClient = mConn.getAttributedClient();
        final ProcessRecordInternal client;
        // LINT.IfChange(getServiceClient)
        if (service.isSdkSandbox && attributedClient != null) {
            client = attributedClient;
        } else {
            client = mConn.getClient();
        }
        // LINT.ThenChange(OomAdjusterImpl.java:getServiceClient)

        // The client must be non-null.
        mSource = client.getGraphNode();
    }

    @CpuTimeTransmissionType
    int getCpuTimeTransmissionType() {
        return mConn.cpuTimeTransmissionType();
    }

    @Override
    @NonNull
    GraphNode getSource() {
        return mSource;
    }

    @Override
    @Nullable
    GraphNode getTarget() {
        return mTarget;
    }

    /**
     * Updates the target node of this edge. Should be called before evaluating this edge in each
     * update.
     */
    final void updateTarget() {
        final ServiceRecordInternal service = mConn.getService();
        final ProcessRecordInternal host;
        if (service.isSdkSandbox) {
            host = service.getHostProcessInternal();
        } else {
            // LINT.IfChange(getServiceHost)
            host = mConn.hasFlag(ServiceInfo.FLAG_ISOLATED_PROCESS)
                    ? service.getIsolationHostProcess() : service.getHostProcessInternal();
            // LINT.ThenChange(OomAdjusterImpl.java:getServiceHost)
        }
        mTarget = (host == null) ? null : host.getGraphNode();
    }

    @Override
    @ProcessCapability
    int evaluateCapabilityFilter() {
        return CapabilityController.evaluateFilter(this);
    }
}
