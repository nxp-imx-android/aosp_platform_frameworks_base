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

import static android.app.admin.PolicyIdentifier.SIMPLE_BOOLEAN_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_ENUM_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_INTEGER_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_INTEGER_POLICY_WITH_RANGE;
import static android.app.admin.PolicyIdentifier.SIMPLE_LONG_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_LONG_POLICY_WITH_RANGE;
import static android.app.admin.PolicyIdentifier.SIMPLE_STRING_LIST_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_STRING_POLICY;
import static android.app.admin.PolicyIdentifier.TEST_AFFILIATED_PROFILE_OWNER_ON_USER_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED;
import static android.app.admin.PolicyIdentifier.TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED_DISALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_DEFAULT_DEVICE_OWNER_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_FINANCED_DEVICE_OWNER_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_MULTIPLE_DPC_TYPES_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_PROFILE_OWNER_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_PROFILE_OWNER_ON_USER0_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_PROFILE_OWNER_ON_USER_ALLOWED;

import android.app.admin.PolicyIdentifier;
import java.lang.String;
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
            /* id= */ SIMPLE_BOOLEAN_POLICY,
            /* allowedScopes= */ Set.of(
                1,
                2
            ),
            /* affectedResource= */ 2,
            /* requiredPermission= */ "android.permission.MANAGE_POLICY_SIMPLE_BOOLEAN",
            /* requiredCrossUserPermission= */ "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL",
            /* allowedDpcTypes= */ Set.of()
        ));
        policies.add(new EnumPolicyMetadata(
            /* id= */ SIMPLE_ENUM_POLICY,
            /* allowedScopes= */ Set.of(
                2,
                3
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
            /* requiredCrossUserPermission= */ "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
            /* allowedDpcTypes= */ Set.of(),
            /* allowedValues= */ Set.of(
                0,
                1,
                2
            )
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ SIMPLE_INTEGER_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ SIMPLE_INTEGER_POLICY_WITH_RANGE,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* minValue= */ -100,
            /* maxValue= */ 100
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_DEFAULT_DEVICE_OWNER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                1  // DEVICE_OWNER
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_FINANCED_DEVICE_OWNER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                2  // FINANCED_DEVICE_OWNER
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                3  // MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_PROFILE_OWNER_ON_USER0_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                4  // PROFILE_OWNER_ON_USER0
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_PROFILE_OWNER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                5  // MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_PROFILE_OWNER_ON_USER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                6, // UNAFFILIATED_FULL_USER_PROFILE_OWNER
                7  // AFFILIATED_FULL_USER_PROFILE_OWNER
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_AFFILIATED_PROFILE_OWNER_ON_USER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                7  // AFFILIATED_FULL_USER_PROFILE_OWNER
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                6, // UNAFFILIATED_FULL_USER_PROFILE_OWNER
                7  // AFFILIATED_FULL_USER_PROFILE_OWNER
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED_DISALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_MULTIPLE_DPC_TYPES_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                1, // DEVICE_OWNER
                3, // MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE
                5, // MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
                7  // AFFILIATED_FULL_USER_PROFILE_OWNER
            ),
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new LongPolicyMetadata(
            /* id= */ SIMPLE_LONG_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* minValue= */ Long.MIN_VALUE,
            /* maxValue= */ Long.MAX_VALUE
        ));
        policies.add(new LongPolicyMetadata(
            /* id= */ SIMPLE_LONG_POLICY_WITH_RANGE,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* minValue= */ 10L,
            /* maxValue= */ 100L
        ));
        policies.add(new StringPolicyMetadata(
            /* id= */ SIMPLE_STRING_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* emptyStringAllowed= */ false
        ));
        policies.add(new ListPolicyMetadata<String>(
            /* id= */ SIMPLE_STRING_LIST_POLICY,
            /* elementMetadata= */ new StringPolicyMetadata(
                /* id= */ new PolicyIdentifier<String>(SIMPLE_STRING_LIST_POLICY.getId() + "#elements"),
                /* allowedScopes= */ Set.of(
                    1
                ),
                /* affectedResource= */ 1,
                /* requiredPermission= */ null,
                /* requiredCrossUserPermission= */ null,
                /* allowedDpcTypes= */ Set.of(),
                /* emptyStringAllowed= */ true
            ),
            /* emptyListAllowed= */ false
        ));
        return policies;
    }
}
