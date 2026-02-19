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
import static android.app.role.RoleManager.ROLE_SYSTEM_FINANCED_DEVICE_CONTROLLER;
import static android.app.role.RoleManager.ROLE_SYSTEM_SUPERVISION;
import static com.android.server.devicepolicy.PolicyDefinition.POLICY_FLAG_GLOBAL_ONLY_POLICY;
import static com.android.server.devicepolicy.PolicyDefinition.POLICY_FLAG_LOCAL_ONLY_POLICY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.DevicePolicyIdentifiers;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyIdentifier;
import android.app.admin.metadata.EnumPolicyMetadata;
import android.app.admin.metadata.GeneratedPolicyMetadata;
import android.app.admin.metadata.IntegerPolicyMetadata;
import android.app.admin.metadata.ListPolicyMetadata;
import android.app.admin.metadata.PolicyMetadata;
import android.app.admin.metadata.StringPolicyMetadata;
import android.util.Pair;
import androidx.annotation.VisibleForTesting;
import com.android.server.devicepolicy.EnforcingAdmin;
import com.android.server.devicepolicy.IntegerPolicySerializer;
import com.android.server.devicepolicy.ListOfStringPolicySerializer;
import com.android.server.devicepolicy.MostRecent;
import com.android.server.devicepolicy.PolicyDefinition;
import com.android.server.devicepolicy.PolicyEnforcerCallbacks;
import com.android.server.devicepolicy.PolicySerializer;
import com.android.server.devicepolicy.StringPolicySerializer;
import com.android.server.devicepolicy.TopPriority;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates a {@link PolicyDefinition.Builder} based on the information from the {@link
 * PolicyIdentifier} and {@link PolicyMetadata}.
 */
public class PolicyDefinitionFactory {

    private interface Factory<T> {

        @Nullable
        public PolicyDefinition<T> build(@NonNull PolicyDefinition.Builder<T> builder);
    }
    ;

    private static final Map<PolicyIdentifier, Factory> FACTORIES = new java.util.HashMap<>();

    /**
     * A list of custom factory methods to create the {@link PolicyDefinition} for storing a policy
     * inside the {@link DevicePolicyEngine}.
     *
     * <p>New policies likely do *not* need to register a custom factory method here, unless they
     * use a custom resolution mechanism.
     *
     * <p>The passed in {@code builder} is pre-populated with the information from the policy
     * annotations:
     *
     * <ol>
     *   <li>{@code key}: Set to a no-args key with the identifier used in the policy annotations.
     *   <li>{@code resolutionMechanism}: This field currently has no default, so it *must* be
     *       manually set.
     *   <li>{@code enforcerCallback}: This field defaults to {@link PolicyEnforcerCallbacks.noOp}.
     * </ol>
     *
     * <p>Your factory method can return `null` when the policy is stored as a different type than
     * the type used in the policy annotation. For example, pre-existing boolean policies are stored
     * as a boolean but represented as an enum in the annotations.
     */
    static {
        addFactory(
                PolicyIdentifier.SCREEN_CAPTURE,
                builder -> {
                    // This (pre-existing) enum policy is stored as a boolean inside DPE, so return
                    // null here and pass the pre-existing PolicyDefinition into the constructor
                    // of `EnumStoredAsBooleanPolicyHandler`.
                    return null;
                });
        addFactory(
                PolicyIdentifier.AUTO_TIME,
                builder -> {
                    return builder
                            // Override the name so it matches the name previously used to store
                            // this policy inside DPE. This way no migration is needed.
                            .setKey(new NoArgsPolicyKey(DevicePolicyIdentifiers.AUTO_TIME_POLICY))
                            // TODO(b/464477084): Add a resolution mechanism for AUTO_TIME to
                            // include most restrictive resolution approach.
                            .setResolutionMechanism(
                                    new TopPriority<>(
                                            List.of(
                                                    EnforcingAdmin.getRoleAuthorityOf(
                                                            ROLE_SYSTEM_SUPERVISION),
                                                    EnforcingAdmin.getRoleAuthorityOf(
                                                            ROLE_SYSTEM_FINANCED_DEVICE_CONTROLLER),
                                                    EnforcingAdmin.DPC_AUTHORITY)))
                            .setEnforcerCallback(PolicyEnforcerCallbacks::setAutoTimePolicy)
                            .build();
                });
        addFactory(
                PolicyIdentifier.LOCKSCREEN_MESSAGE,
                builder -> {
                    return builder
                            // TODO(b/457343029): Replace with DPC priority.
                            .setResolutionMechanism(new MostRecent<>())
                            .setEnforcerCallback(PolicyEnforcerCallbacks::setLockScreenInfoPolicy)
                            .build();
                });
    }

    public static Map<PolicyIdentifier<?>, PolicyDefinition<?>> buildAll() {
        return buildAll(GeneratedPolicyMetadata.getAllPolicyMetadata());
    }

    /** Builds the {@link PolicyDefinition}s for every {@link PolicyMetadata} passed in. */
    public static Map<PolicyIdentifier<?>, PolicyDefinition<?>> buildAll(
            Collection<PolicyMetadata<?>> allPolicyMetadata) {
        return allPolicyMetadata.stream()
                .map(m -> new Pair<>(m.getId(), build(m)))
                .filter(pair -> pair.second != null)
                .collect(Collectors.toMap(pair -> pair.first, pair -> pair.second));
    }

    @Nullable
    private static <T> PolicyDefinition<T> build(@NonNull PolicyMetadata<T> metadata) {
        var builder = createPrePopulatedBuilder(metadata);

        if (FACTORIES.containsKey(metadata.getId())) {
            return FACTORIES.get(metadata.getId()).build(builder);
        }

        return builder.build();
    }

    private static <T> void addFactory(PolicyIdentifier<T> identifier, Factory<T> factory) {
        FACTORIES.put(identifier, factory);
    }

    @VisibleForTesting
    public static <T> PolicyDefinition.Builder<T> createPrePopulatedBuilder(
            @NonNull PolicyMetadata<T> metadata) {
        var builder =
                PolicyDefinition.<T>builder()
                        .setKey(new NoArgsPolicyKey(metadata.getId().getId()))
                        .setEnforcerCallback(PolicyEnforcerCallbacks::noOp)
                        .setSerializer(getSerializer(metadata));

        if (isGlobalOnly(metadata)) {
            builder.addFlag(POLICY_FLAG_GLOBAL_ONLY_POLICY);
        }

        if (isLocalOnly(metadata)) {
            builder.addFlag(POLICY_FLAG_LOCAL_ONLY_POLICY);
        }

        return builder;
    }

    private static boolean isGlobalOnly(@NonNull PolicyMetadata<?> metadata) {
        return metadata.getAllowedScopes().contains(POLICY_SCOPE_DEVICE)
                && !metadata.getAllowedScopes().contains(POLICY_SCOPE_USER)
                && !metadata.getAllowedScopes().contains(POLICY_SCOPE_PARENT_USER);
    }

    private static boolean isLocalOnly(@NonNull PolicyMetadata<?> metadata) {
        return (metadata.getAllowedScopes().contains(POLICY_SCOPE_USER)
                        || metadata.getAllowedScopes().contains(POLICY_SCOPE_PARENT_USER))
                && !metadata.getAllowedScopes().contains(POLICY_SCOPE_DEVICE);
    }

    private static <T> PolicySerializer<T> getSerializer(@NonNull PolicyMetadata<T> metadata) {
        // Cast is safe since metadata already checked the type when building.
        return (PolicySerializer<T>)
                switch (metadata) {
                    case EnumPolicyMetadata e -> new IntegerPolicySerializer();
                    case IntegerPolicyMetadata e -> new IntegerPolicySerializer();
                    case StringPolicyMetadata s -> new StringPolicySerializer();
                    case ListPolicyMetadata l -> getListSerializer(l);
                    default ->
                            throw new UnsupportedOperationException(
                                    "Unsupported policy: " + metadata.getId());
                };
    }

    private static <T> PolicySerializer<List<T>> getListSerializer(
            ListPolicyMetadata<T> listPolicy) {
        // Cast is safe since metadata already checked the type when building.
        return (PolicySerializer)
                switch (listPolicy.getElementMetadata()) {
                    case StringPolicyMetadata s -> new ListOfStringPolicySerializer();
                    default ->
                            throw new UnsupportedOperationException(
                                    "Unsupported list policy: " + listPolicy.getId());
                };
    }
}
