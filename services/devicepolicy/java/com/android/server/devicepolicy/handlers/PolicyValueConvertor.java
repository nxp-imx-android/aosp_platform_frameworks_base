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
import android.app.admin.IntegerPolicyValue;
import android.app.admin.PolicyValue;
import android.app.admin.metadata.EnumPolicyMetadata;
import android.app.admin.metadata.PolicyMetadata;

/**
 * A PolicyValueConvertor provides type specific functionality for converting policy values to
 * {@link PolicyValue} instances.
 *
 * @param <T>  The value kind this convertor works on.
 */
public abstract class PolicyValueConvertor<T> {

    private static final PolicyValueConvertor<Integer> INTEGER_POLICY_VALUE_CONVERTOR =
            new PolicyValueConvertor<Integer>() {
                @Override
                public @NonNull PolicyValue<Integer> toPolicyValue(@NonNull Integer value) {
                    return new IntegerPolicyValue(value);
                }

                @Override
                public @NonNull Integer fromPolicyValue(@NonNull PolicyValue<Integer> value) {
                    return value.getValue();
                }
            };

    /**
     * Gets a convertor that can handle the given policy.
     *
     * @param metadata The metadata for the policy.
     * @param <T>      The type of the policy.
     * @return         The convertor.
     */
    @NonNull
    public static <T> PolicyValueConvertor<T> getInstance(PolicyMetadata<T> metadata) {
        switch (metadata) {
            case EnumPolicyMetadata e:
                return (PolicyValueConvertor<T>) INTEGER_POLICY_VALUE_CONVERTOR;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported policy: " + metadata.getId());
        }
    }

    /**
     * Converts a value to its corresponding {@link PolicyValue}.
     */
    public abstract @NonNull PolicyValue<T> toPolicyValue(@NonNull T value);

    /**
     * Converts a {@link PolicyValue} to its corresponding value.
     */
    public abstract @NonNull T fromPolicyValue(@NonNull PolicyValue<T> value);
}
