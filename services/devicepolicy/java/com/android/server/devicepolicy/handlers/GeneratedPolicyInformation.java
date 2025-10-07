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

package com.android.server.devicepolicy.handlers;


import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.PolicyIdentifier;
import android.util.ArrayMap;

import java.util.Map;
import java.util.Set;

/**
 * TODO(433951394): Generate this file.
 * Generated class/file containing the {@link PolicyInformation} instances
 * generated based on the {@link PolicyIdentifier}s annotated by @PolicyDefinition.
 */
public class GeneratedPolicyInformation {

    private static final Map<String, PolicyInformation<?>> POLICIES = new ArrayMap<>();

    static {
        addPolicy(
                new EnumPolicyInformation(
                        PolicyIdentifier.SCREEN_CAPTURE,
                        Set.of(
                                PolicyIdentifier.SCREEN_CAPTURE_ALLOWED,
                                PolicyIdentifier.SCREEN_CAPTURE_DISALLOWED),
                        android.Manifest.permission.MANAGE_DEVICE_POLICY_SCREEN_CAPTURE,
                        android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS));
    }

    private GeneratedPolicyInformation() {
    }

    private static <T> void addPolicy(@NonNull PolicyInformation<T> policy) {
        POLICIES.put(policy.getKey().getId(), policy);
    }

    /**
     * Retrieve the policy information for the given policy with the given {@code id}, or null
     * if no such policy information exist.
     */
    @Nullable
    public static PolicyInformation<?> get(@NonNull String id) {
        return POLICIES.get(id);
    }

    /**
     * Retrieve the policy information for the given policy with the given {@code id}, or null
     * if no such policy information exist.
     */
    @Nullable
    public static <T> PolicyInformation<T> get(@NonNull PolicyIdentifier<T> id) {
        // Casting is safe here, since {@link AddPolicy} ensured the type of the key matched
        // the type of the {@link PolicyInformation} when populating the map.
        return (PolicyInformation<T>) POLICIES.get(id.getId());
    }
}
