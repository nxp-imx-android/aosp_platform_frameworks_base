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

package com.android.server.personalcontext.component.client;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.service.personalcontext.hint.ContextHintWithSignature;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client for understander services.
 *
 * @hide
 */
public class ServiceClientUnderstander extends ServiceClientRefiner {
    public ServiceClientUnderstander(Context context, UUID componentId, ServiceInfo serviceInfo) {
        super(context, componentId, serviceInfo);
    }

    @Override
    public Set<Set<ContextHintWithSignature>> getInterestedHintClusters(
            Set<ContextHintWithSignature> allContextHints, Set<UUID> seenIDs, boolean isFirstRun) {
        // TODO(b/452425566): Implement this to use a filter in the package's manifest.
        // For now this runs hints through the understander in one big block.
        final Set<ContextHintWithSignature> interestingHints = new HashSet<>();
        for (ContextHintWithSignature hint : allContextHints) {
            if (!seenIDs.contains(hint.getContextHint().getHintId())) interestingHints.add(hint);
        }
        if (interestingHints.isEmpty()) {
            return Collections.emptySet();
        } else {
            return Set.of(interestingHints);
        }
    }
}
