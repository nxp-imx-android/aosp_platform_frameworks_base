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

import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager.PolicyScope;
import android.app.admin.PolicyIdentifier;
import android.app.admin.PolicyValueTransport;
import android.app.admin.metadata.GeneratedPolicyMetadata;
import android.app.admin.metadata.PolicyMetadata;

import com.android.server.devicepolicy.CallerIdentity;
import com.android.server.devicepolicy.DevicePolicyManagerService;
import com.android.server.devicepolicy.DevicePolicyManagerService.DpcType;
import com.android.server.devicepolicy.IPermissionChecker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A PolicyHandler contains all the logic required for setting the policy with the given key.
 *
 * <p>This logic uses the information provided in the PolicyDefinition annotation applied to the key
 * (of type {@link PolicyIdentifier}).
 *
 * <p>To add support for a new policy, simply add it to the {@link PolicyHandler.HANDLERS} list in
 * this class:
 *
 * {@snippet :
 *     public static List<PolicyHandler<?>> HANDLERS;
 *     static {
 *         // <snip other handlers>
 *         HANDLERS.add(new PolicyHandler<>(PolicyIdentifier.MY_POLICY));
 *     }
 * }
 *
 * If handling of your policy needs to do something extra, you can achieve that by overriding the
 * corresponding `protected` function in your policy handler. For example, to completely overrule
 * the permission checks you can override the checkPermissions() method:
 *
 * {@snippet :
 *     public static List<PolicyHandler<?>> HANDLERS;
 *     static {
 *         // <snip other handlers>
 *         HANDLERS.add(new PolicyHandler<>(PolicyIdentifier.MY_POLICY) {
 *            protected void checkPermissions(CallerIdentity caller, int scope) {
 *                 // Your custom permission checks go here.
 *            }
 *         })
 *     }
 * }
 *
 * If you want to *augment* the permission checks instead, you can use `super` to invoke the default
 * version:
 *
 * {@snippet :
 *     public static List<PolicyHandler<?>> HANDLERS;
 *     static {
 *         // <snip other handlers>
 *         HANDLERS.add(new PolicyHandler<>(PolicyIdentifier.MY_POLICY) {
 *             protected void checkPermissions(CallerIdentity caller, int scope) {
 *                  // Invoke normal permission checks
 *                  super.checkPermissions(caller, scope);
 *                 // Your custom permission checks go here.
 *             }
 *         })
 *     }
 * }
 *
 * @param <T> Represents the type of the value that is associated with this policy.
 */
public class PolicyHandler<T> {

    /**
     * The list of {@link PolicyHandler}s that do not need to call a private method in the {@link
     * DevicePolicyManagerService}.
     */
    public static List<PolicyHandler<?>> HANDLERS = new ArrayList<>();

    static {
        // Currently empty, just waiting for YOU to add the very first handler here :)
    }

    private final PolicyIdentifier<T> mKey;
    private final PolicyInformation<T> mPolicyInformation;
    private final PolicyMetadata<T> mPolicyMetadata;
    private final PolicyValidator<T> mValidator;

    /**
     * Helper class that provides access to methods used while processing policies. Must be
     * initialized by calling {@code setDelegate()} before using the policy handler.
     */
    private Delegate mDelegate = null;

    public PolicyHandler(
            @NonNull PolicyIdentifier<T> key,
            @NonNull PolicyInformation<T> policyInformation,
            @NonNull PolicyMetadata<T> policyMetadata) {
        mKey = key;
        mPolicyInformation = policyInformation;
        mPolicyMetadata = policyMetadata;
        mValidator = PolicyValidator.getInstance(mPolicyMetadata);
    }

    /** Constructor that uses the generated {@link PolicyInformation} and {@link PolicyMetadata} */
    public PolicyHandler(@NonNull PolicyIdentifier<T> key) {
        this(
                key,
                getGeneratedPolicyInformation(key),
                GeneratedPolicyMetadata.getPolicyMetadata(key));
    }

    /** Convenience constructor used by unittests */
    public PolicyHandler(
            @NonNull PolicyIdentifier<T> key,
            @NonNull PolicyInformation<T> policyInformation,
            @NonNull PolicyMetadata<T> policyMetadata,
            @NonNull Delegate delegate) {
        this(key, policyInformation, policyMetadata);
        setDelegate(delegate);
    }

    @NonNull
    private static <T> PolicyInformation<T> getGeneratedPolicyInformation(
            @NonNull PolicyIdentifier<T> key) {
        PolicyInformation<T> result = GeneratedPolicyInformation.get(key);
        if (result == null) {
            throw new IllegalArgumentException("Unknown policy " + key.getId());
        }
        return result;
    }

    public PolicyIdentifier<T> getKey() {
        return mKey;
    }

    @NonNull
    protected PolicyInformation<T> getPolicyInformation() {
        return mPolicyInformation;
    }

    @NonNull
    protected PolicyMetadata<T> getPolicyMetadata() {
        return mPolicyMetadata;
    }

    @NonNull
    protected Delegate getDelegate() {
        if (mDelegate == null) {
            throw new IllegalStateException("Delegate must be set before accessing it");
        }
        return mDelegate;
    }

    public void setDelegate(@NonNull Delegate delegate) {
        mDelegate = delegate;
    }

    /** Performs every step required to set the policy. */
    public void setPolicy(
            @NonNull CallerIdentity caller,
            @PolicyScope int scope,
            @Nullable PolicyValueTransport transportValue) {
        validateScope(scope);

        T value = convertValue(caller, transportValue);

        checkPermissions(caller, scope);
        validateValue(caller, value);

        storePolicyValue(caller, scope, value);
    }

    /**
     * Validates if the {@link PolicyScope} can be used for this policy.
     *
     * @throws IllegalArgumentException if the scope can not be used.
     */
    protected void validateScope(@PolicyScope int scope) {
        if (!getPolicyMetadata().getAllowedScopes().contains(scope)) {
            throw new IllegalArgumentException(
                    "Policy "
                            + getKey().getId()
                            + " only supports scopes "
                            + scopesToString(getPolicyMetadata().getAllowedScopes())
                            + ", but got scope "
                            + scopeToString(scope));
        }
    }

    /**
     * Converts the given {@link PolicyValueTransport} to the value of type `T` that should be
     * stored.
     */
    @Nullable
    protected T convertValue(
            @NonNull CallerIdentity caller, @Nullable PolicyValueTransport transportValue) {
        return getPolicyInformation().valueFromTransportValue(transportValue);
    }

    /**
     * Performs permission checks, based on the information in the {@link PolicyDefinition}
     * information provided on the {@link PolicyIdentifier}.
     *
     * <p>If the policy is applied on global or parent scope this additionally checks if the caller
     * has the proper 'manage across users' permission.
     *
     * <p>Can be overridden to provide custom permission checks instead.
     *
     * @throws SecurityException when the caller does not have the required permissions.
     */
    protected void checkPermissions(CallerIdentity caller, @PolicyScope int scope) {
        var permissionChecker = getDelegate().getPermissionChecker();

        permissionChecker.enforce(getPolicyInformation().getRequiredPermission(), caller);

        if (scope != POLICY_SCOPE_USER) {
            permissionChecker.enforce(
                    getPolicyInformation().getRequiredCrossUserPermission(), caller);
        }
    }

    /**
     * Performs validation of the provided value, based on the information in the {@link
     * PolicyDefinition} information provided on the {@link PolicyIdentifier}.
     *
     * <p>Can be overridden to provide custom validation instead.
     *
     * @throws IllegalArgumentException when validation rejects the value.
     */
    protected void validateValue(@NonNull CallerIdentity caller, @Nullable T value) {
        if (value != null) {
            getValidator().validate(value, getPolicyMetadata());
        }
    }

    /**
     * Stores the policy value inside the {@code DevicePolicyEngine}.
     *
     * <p>Can be overridden to store the value somewhere else instead.
     */
    protected void storePolicyValue(
            @NonNull CallerIdentity caller, @PolicyScope int scope, @Nullable T value) {
        // TODO(443666776): Implement
    }

    protected static String scopeToString(@PolicyScope int scope) {
        return switch (scope) {
            case POLICY_SCOPE_DEVICE -> "SCOPE_DEVICE";
            case POLICY_SCOPE_USER -> "SCOPE_USER";
            case POLICY_SCOPE_PARENT_USER -> "SCOPE_PARENT_USER";
            default -> "INVALID_SCOPE_" + Integer.toString(scope);
        };
    }

    protected static String scopesToString(Collection<Integer> scopes) {
        return "["
                + scopes.stream().map(s -> scopeToString(s)).collect(Collectors.joining(", "))
                + "]";
    }

    protected final @NonNull PolicyValidator<T> getValidator() {
        return mValidator;
    }

    /** Helper class that provides access to helper methods used while processing policies. */
    public interface Delegate {
        /** Returns the DPC type of the given caller. */
        @DpcType
        int getDpcType(@NonNull CallerIdentity caller);

        /** Returns the permission checker that can be used to check permissions. */
        IPermissionChecker getPermissionChecker();
    }
}
