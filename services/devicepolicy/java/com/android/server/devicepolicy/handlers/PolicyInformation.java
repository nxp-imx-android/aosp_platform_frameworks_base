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
import android.app.admin.PolicyValueTransport;

import java.util.Set;

public abstract class PolicyInformation<T> {
    private final PolicyIdentifier<T> mKey;
    // TODO(443666602): Support multiple permissions and modeling of DO/PO.
    private final String mRequiredPermission;
    private final String mRequiredCrossUserPermission;
    private final Set<Integer> mAcceptedScopes;

    public PolicyInformation(
            @NonNull PolicyIdentifier<T> key,
            @NonNull Set<Integer> acceptedScopes,
            @NonNull String requiredPermission,
            @NonNull String requiredCrossUserPermission) {
        mKey = key;
        mAcceptedScopes = Set.copyOf(acceptedScopes);
        mRequiredPermission = requiredPermission;
        mRequiredCrossUserPermission = requiredCrossUserPermission;
    }

    @NonNull
    public PolicyIdentifier<T> getKey() {
        return mKey;
    }

    @NonNull
    public Set<Integer> getAcceptedScopes() {
        return mAcceptedScopes;
    }

    @NonNull
    public String getRequiredPermission() {
        return mRequiredPermission;
    }

    @NonNull
    public String getRequiredCrossUserPermission() {
        return mRequiredCrossUserPermission;
    }

    /**
     * Performs a checked conversion of the transportValue to a value of type `T`.
     *
     * @return The value contained inside {@link transportValue}, or null if {@link transportValue}
     * is null.
     * @throws IllegalArgumentException if transportValue is of a wrong type
     */
    @Nullable
    public abstract T valueFromTransportValue(@Nullable PolicyValueTransport transportValue);

    /**
     * Validate the given value, using the information available in the {@code PolicyDefinition}
     * annotation.
     *
     * @throws IllegalArgumentException if the value is invalid.
     */
    @Nullable
    public abstract void validateValue(@NonNull T value);
}
