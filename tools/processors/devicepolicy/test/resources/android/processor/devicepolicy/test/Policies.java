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

package android.app.admin.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generated class to load policy metadata
 */
public class Policies {
    /**
     * Generated method that returns a list of all policy metadata
     */
    public static List<PolicyMetadata<?>> loadPolicyMetadata() {
        List<PolicyMetadata<?>> policies = new ArrayList<PolicyMetadata<?>>();
        policies.add(new BooleanPolicyMetadata(
            /* id= */ android.app.admin.PolicyIdentifier.SIMPLE_BOOLEAN_POLICY,
            /* allowedScopes= */ Set.of(
                1,
                2
            ),
            /* affectedResource= */ 2,
            /* requiredPermission= */ "android.permission.MANAGE_POLICY_SIMPLE_BOOLEAN",
            /* requiredCrossUserPermission= */ "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL"
        ));
        policies.add(new EnumPolicyMetadata(
            /* id= */ android.app.admin.PolicyIdentifier.SIMPLE_ENUM_POLICY,
            /* allowedScopes= */ Set.of(
                2,
                3
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
            /* requiredCrossUserPermission= */ "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
            /* allowedValues= */ Set.of(
                0,
                1,
                2
            )
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ android.app.admin.PolicyIdentifier.SIMPLE_INTEGER_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null
        ));
        return policies;
    }
}
