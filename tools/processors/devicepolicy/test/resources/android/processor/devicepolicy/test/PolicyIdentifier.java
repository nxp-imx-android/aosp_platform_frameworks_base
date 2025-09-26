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

package android.app.admin;

import android.annotation.IntDef;
import android.processor.devicepolicy.BooleanPolicyDefinition;
import android.processor.devicepolicy.EnumPolicyDefinition;
import android.processor.devicepolicy.IntegerPolicyDefinition;
import android.processor.devicepolicy.PolicyDefinition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class PolicyIdentifier<T> {
    private final String mId;

    public PolicyIdentifier(String id) {
        mId = id;
    }

    public String getId() {
        return mId;
    }

    /**
     * Test policy 1
     */
    @BooleanPolicyDefinition(
            base = @PolicyDefinition(
                    allowedScopes = {
                            1, // POLICY_SCOPE_USER
                            2  // POLICY_SCOPE_DEVICE
                    },
                    affectedResource = 2, // RESOURCE_DEVICE_PER_USER
                    requiredPermission =
                            "android.permission.MANAGE_POLICY_SIMPLE_BOOLEAN",
                    requiredCrossUserPermission =
                            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL"
            )
    )
    public static final PolicyIdentifier<Boolean> SIMPLE_BOOLEAN_POLICY =
            new PolicyIdentifier<>("SIMPLE_BOOLEAN_POLICY");

    /**
     * First entry
     */
    public static final int ENUM_ENTRY_1 = 0;

    /**
     * Second entry
     */
    public static final int ENUM_ENTRY_2 = 1;

    /**
     * Third entry
     */
    public static final int ENUM_ENTRY_3 = 2;

    /**
     * Enum for {@link SIMPLE_ENUM_POLICY}
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "ENUM_ENTRY_" }, value = {
            ENUM_ENTRY_1,
            ENUM_ENTRY_2,
            ENUM_ENTRY_3
    })
    public @interface SimpleEnumPolicyEnum {}

    /**
     * Test policy 2
     */
    @EnumPolicyDefinition(
            base = @PolicyDefinition(
                    allowedScopes = {
                            2, // POLICY_SCOPE_DEVICE
                            3  // POLICY_SCOPE_PARENT_USER
                    },
                    affectedResource = 1, // RESOURCE_DEVICE_WIDE
                    requiredPermission =
                            "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
                    requiredCrossUserPermission =
                            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS"
            ),
            defaultValue = ENUM_ENTRY_2,
            intDef = SimpleEnumPolicyEnum.class
    )
    public static final PolicyIdentifier<Integer> SIMPLE_ENUM_POLICY =
            new PolicyIdentifier<>("SIMPLE_ENUM_POLICY");

    /**
     * Test policy 3
     */
    @IntegerPolicyDefinition(
            base = @PolicyDefinition(
                    allowedScopes = {
                            1 // POLICY_SCOPE_USER
                    },
                    affectedResource = 1 // RESOURCE_DEVICE_WIDE
                    // requiredPermission and requiredCrossUserPermission using the default values.
            )
    )
    public static final PolicyIdentifier<Integer> SIMPLE_INTEGER_POLICY =
            new PolicyIdentifier<>("SIMPLE_INTEGER_POLICY");
}
