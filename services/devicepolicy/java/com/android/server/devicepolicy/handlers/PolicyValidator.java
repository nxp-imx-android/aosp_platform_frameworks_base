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
import android.app.admin.metadata.EnumPolicyMetadata;
import android.app.admin.metadata.PolicyMetadata;

/**
 * A PolicyValidator provides type specific functionality for validating the values of a policy.
 *
 * @param <T> The type of the policy value.
 */
public abstract class PolicyValidator<T> {

    private static final PolicyValidator<Integer> ENUM_POLICY_VALIDATOR =
            new PolicyValidator<Integer>() {
                @Override
                public void validate(
                        @NonNull Integer value, @NonNull PolicyMetadata<Integer> policy) {
                    // This validator is only used for `EnumPolicyMetadata`, so nobody should ever
                    // pass anything else in.
                    var enumPolicy = (EnumPolicyMetadata) policy;

                    if (!enumPolicy.getAllowedValues().contains(value)) {
                        throw new IllegalArgumentException(
                                "Unsupported value "
                                        + value
                                        + " for policy "
                                        + policy.getId().getId());
                    }
                }
            };

    /** Returns a validator that can handle values of the given policy. */
    public static <T> PolicyValidator<T> getInstance(PolicyMetadata<T> policy) {
        switch (policy) {
            case EnumPolicyMetadata e:
                return (PolicyValidator<T>) ENUM_POLICY_VALIDATOR;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported policy: " + policy.getId().getId());
        }
    }

    /**
     * Validate the given value, using the information available in the {@code PolicyMetadata}.
     *
     * @throws IllegalArgumentException if the value is invalid.
     */
    public abstract void validate(@NonNull T value, @NonNull PolicyMetadata<T> policy);
}
