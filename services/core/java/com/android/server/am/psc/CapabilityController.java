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
import static android.app.ActivityManager.PROCESS_CAPABILITY_BFSL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static android.media.audio.Flags.roForegroundAudioControl;

import static com.android.server.am.psc.Constants.FOREGROUND_APP_ADJ;

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessCapability;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import java.util.ArrayList;

/** The class that computes capabilities and CPU time reasons for processes. */
@RavenwoodKeepWholeClass
class CapabilityController {
    /**
     * A bitmask of all capabilities that can be granted by a foreground service.
     * Used for an optimization in {@link #evaluateForegroundServicePolicy}.
     */
    private static final @ProcessCapability int ALL_FOREGROUND_SERVICE_CAPABILITIES =
            PROCESS_CAPABILITY_FOREGROUND_LOCATION | PROCESS_CAPABILITY_FOREGROUND_CAMERA
                    | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE
                    | PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;

    /** Evaluates a filter by combining all the policies of a process edge. */
    static @ProcessCapability int evaluateFilter(@NonNull ProcessEdge edge) {
        // No capability is granted to a non-running process.
        if (!edge.getTarget().isProcessRunning()) return PROCESS_CAPABILITY_NONE;
        // TODO(b/466961280): Add more policies.
        // TODO(b/473696073): Optimize: If all the capabilities that a policy is able to give are
        //  already given by its preceding policies, the policy evaluation can be skipped.
        return evaluateMaxAdjPolicy(edge) | evaluateInstrumentationPolicy(edge)
                | evaluateForegroundServicePolicy(edge);
    }

    /** Evaluates a filter based on the process's max oom score (maxAdj). */
    private static @ProcessCapability int evaluateMaxAdjPolicy(@NonNull ProcessEdge edge) {
        if (edge.getTarget().getMaxAdj() <= FOREGROUND_APP_ADJ) {
            return PROCESS_CAPABILITY_ALL;
        } else {
            return PROCESS_CAPABILITY_NONE;
        }
    }

    /** Grants BFSL if the process has an active instrumentation. */
    private static @ProcessCapability int evaluateInstrumentationPolicy(@NonNull ProcessEdge edge) {
        // TODO(b/471530626): The policy ignores whether the process is running remote animation
        //  or not, which is different from current OomAdjuster impl. Revisit this policy if needed.
        if (edge.getTarget().hasActiveInstrumentation()) {
            return PROCESS_CAPABILITY_BFSL;
        } else {
            return PROCESS_CAPABILITY_NONE;
        }
    }

    /**
     * Grants capabilities based on the process's foreground services. This includes BFSL for
     * non-short FGS, and other foreground capabilities including location, camera, microphone,
     * and audio control.
     */
    private static @ProcessCapability int evaluateForegroundServicePolicy(
            @NonNull ProcessEdge edge) {
        final GraphNode node = edge.getTarget();
        if (!node.hasForegroundServices()) {
            return PROCESS_CAPABILITY_NONE;
        }
        // Grant BFSL for regular (non-short) FGS.
        @ProcessCapability int result = node.hasNonShortForegroundServices()
                ? PROCESS_CAPABILITY_BFSL : PROCESS_CAPABILITY_NONE;
        for (int i = node.getNumberOfRunningServices() - 1; i >= 0; i--) {
            result |= getForegroundServiceCapability(node, i);
            // Stop the iteration if we have already granted all foreground capabilities.
            if ((result & ALL_FOREGROUND_SERVICE_CAPABILITIES)
                    == ALL_FOREGROUND_SERVICE_CAPABILITIES) {
                break;
            }
        }
        return result;
    }

    /**
     * Gets foreground service capabilities from the node's service record at index {@code index}.
     */
    // LINT.IfChange(getForegroundServiceCapability)
    private static @ProcessCapability int getForegroundServiceCapability(@NonNull GraphNode node,
            int index) {
        if (!node.isForegroundService(index) || !node.isFgsAllowedWiuForCapabilities(index)) {
            return PROCESS_CAPABILITY_NONE;
        }

        final int fgsType = node.getForegroundServiceType(index);
        @ProcessCapability int result = PROCESS_CAPABILITY_NONE;
        // Any FGS grants audio control if the flag is enabled.
        if (roForegroundAudioControl()) {
            result |= PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
        }

        if ((fgsType & FOREGROUND_SERVICE_TYPE_LOCATION) != 0) {
            result |= PROCESS_CAPABILITY_FOREGROUND_LOCATION;
        }

        if (node.getCachedCompatChangeCameraMicrophoneCapability()) {
            if ((fgsType & FOREGROUND_SERVICE_TYPE_CAMERA) != 0) {
                result |= PROCESS_CAPABILITY_FOREGROUND_CAMERA;
            }
            if ((fgsType & FOREGROUND_SERVICE_TYPE_MICROPHONE) != 0) {
                result |= PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
            }
        } else {
            // For apps that don't have the compat change enabled, any FGS grants
            // camera and microphone capabilities.
            result |= PROCESS_CAPABILITY_FOREGROUND_CAMERA
                    | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
        }

        return result;
    }
    // LINT.ThenChange(OomAdjusterImpl.java:getForegroundServiceCapability)

    /** Performs a partial update from a list of edges. */
    void update(@NonNull ArrayList<GraphEdge> edges) {
        for (int i = 0, size = edges.size(); i < size; i++) {
            final GraphEdge edge = edges.get(i);
            edge.updateCachedCapabilityFilter();
            // TODO(b/466961280): Set edge target to be reachable.
        }

        // TODO(b/466961280): Search for all other reachable nodes and propagate capabilities.
    }
}
