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

/**
 * Represents information about an enum policy.
 */
public class EnumPolicyInformation extends PolicyInformation<Integer> {

    public EnumPolicyInformation(
            @NonNull PolicyIdentifier<Integer> key,
            @NonNull String requiredPermission,
            @NonNull String requiredCrossUserPermission) {
        super(key, requiredPermission, requiredCrossUserPermission);
    }

    @Override
    @Nullable
    public Integer valueFromTransportValue(@Nullable PolicyValueTransport value) {
        if (value == null) {
            return null;
        }

        if (value.getTag() != PolicyValueTransport.Tag.integerField) {
            throw new IllegalArgumentException(getKey().getId() + " requires an Enum value");
        }
        return value.getIntegerField();
    }
}
