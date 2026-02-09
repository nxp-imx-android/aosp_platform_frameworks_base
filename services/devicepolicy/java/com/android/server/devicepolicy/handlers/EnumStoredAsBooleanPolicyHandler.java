/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER;
import static android.app.role.RoleManager.ROLE_SYSTEM_FINANCED_DEVICE_CONTROLLER;
import static android.app.role.RoleManager.ROLE_SYSTEM_SUPERVISION;
import static com.android.server.devicepolicy.PolicyDefinition.POLICY_FLAG_GLOBAL_ONLY_POLICY;
import static android.app.admin.DevicePolicyManager.RESOURCE_DEVICE_WIDE;
import static android.app.admin.DevicePolicyManager.RESOURCE_PER_USER;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.admin.BooleanPolicyValue;
import android.app.admin.DevicePolicyManager.DpcType;
import android.app.admin.DevicePolicyManager.PolicyScope;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyIdentifier;
import android.app.admin.PolicyValue;
import android.app.admin.PolicyValueTransport;
import android.app.admin.metadata.EnumPolicyMetadata;
import android.app.admin.metadata.GeneratedPolicyMetadata;
import android.app.admin.metadata.PolicyMetadata;
import android.app.admin.metadata.PolicyTransportValueConvertor;
import android.app.admin.DevicePolicyIdentifiers;

import com.android.server.devicepolicy.CallerIdentity;
import com.android.server.devicepolicy.DevicePolicyManagerService;
import com.android.server.devicepolicy.EnforcingAdmin;
import com.android.server.devicepolicy.IPermissionChecker;
import com.android.server.devicepolicy.MostRecent;
import com.android.server.devicepolicy.PolicyDefinition;
import com.android.server.devicepolicy.PolicyEnforcerCallbacks;
import com.android.server.devicepolicy.TopPriority;

import com.android.server.utils.Slogf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PolicyHandler for a policy that is modeled as an enum in the policy annotations but which is
 * stored as a boolean in the {@code DevicePolicyEngine}.
 *
 * <p>Used for preexisting policies that were already stored as booleans.
 *
 * <p>New policies should *not* use this class and should simply store their policy values as an
 * enum inside {@code DevicePolicyEngine}.
 */
public class EnumStoredAsBooleanPolicyHandler extends PolicyHandler<Integer> {

    private final PolicyDefinition<Boolean> mPolicyDefinition;
    private final int mTrueValue;
    private final int mFalseValue;

    public EnumStoredAsBooleanPolicyHandler(
            @NonNull PolicyIdentifier<Integer> identifier,
            @NonNull PolicyDefinition<Boolean> definition,
            int trueValue,
            int falseValue) {
        this(
                identifier,
                definition,
                trueValue,
                falseValue,
                GeneratedPolicyMetadata.getPolicyMetadata(identifier));
    }

    public EnumStoredAsBooleanPolicyHandler(
            @NonNull PolicyIdentifier<Integer> identifier,
            @NonNull PolicyDefinition<Boolean> definition,
            int trueValue,
            int falseValue,
            @NonNull PolicyMetadata<Integer> policyMetadata) {
        // This deliberately calls the super constructor that takes the generated policy definition,
        // so users of this handler do not need to update {@link PolicyDefinitionFactory} to return
        // {@code null} for this {@link PolicyIdentifier}.
        super(identifier, policyMetadata, /* policyDefinition= */ null);
        mPolicyDefinition = definition;
        mTrueValue = trueValue;
        mFalseValue = falseValue;

        checkAllowedValuesMatchGivenTrueAndFalseValues();
    }

    private void checkAllowedValuesMatchGivenTrueAndFalseValues() {
        var enumPolicy = (EnumPolicyMetadata) getPolicyMetadata();
        var expectedValues = Set.of(mTrueValue, mFalseValue);
        if (!enumPolicy.getAllowedValues().equals(expectedValues)) {
            throw new IllegalStateException(
                    "Policy "
                            + getKey()
                            + " should only accept the values passed into the "
                            + "constructor of `EnumStoredAsBooleanPolicyHandler` (which are "
                            + expectedValues
                            + "), but the policy actually accepts "
                            + enumPolicy.getAllowedValues());
        }
    }

    @Override
    protected void storePolicyValue(CallerIdentity caller, int scope, Integer value) {
        if (value == null) {
            clearPolicy(caller, mPolicyDefinition, scope);
        } else {
            boolean booleanValue = enumToBoolean(value);
            storePolicy(caller, mPolicyDefinition, scope, new BooleanPolicyValue(booleanValue));
        }
    }

    @Override
    protected Integer getPolicyValue(CallerIdentity caller, int scope) {
        Boolean booleanValue = getPolicySetByAdmin(caller, mPolicyDefinition, scope);
        return boxedBooleanToEnum(booleanValue);
    }

    @Override
    protected Integer getResolvedPerUserPolicyValue(int userId) {
        Boolean booleanValue = getDelegate().getResolvedPerUserPolicy(userId, mPolicyDefinition);
        return boxedBooleanToEnum(booleanValue);
    }

    @Override
    protected Integer getResolvedDeviceWidePolicyValue() {
        Boolean booleanValue = getDelegate().getResolvedDeviceWidePolicy(mPolicyDefinition);
        return boxedBooleanToEnum(booleanValue);
    }

    private Integer boxedBooleanToEnum(Boolean value) {
        if (value == null) {
            return null;
        } else if (value) {
            return mTrueValue;
        } else {
            return mFalseValue;
        }
    }

    private boolean enumToBoolean(int value) {
        return (value == mTrueValue);
    }
}
