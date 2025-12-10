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

import static android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_DEVICE_POLICY_SCREEN_CAPTURE;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER;
import static android.app.admin.DevicePolicyManager.RESOURCE_PER_USER;
import static android.app.admin.flags.Flags.FLAG_POLICY_STREAMLINING;
import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.TestApi;
import android.processor.devicepolicy.AllowedDpcTypes;
import android.processor.devicepolicy.EnumPolicyDefinition;
import android.processor.devicepolicy.PolicyDefinition;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents a type safe identifier for a policy. Use it as a key for {@link
 * DevicePolicyManager#setPolicy setPolicy} and related APIs.
 *
 * <p>Policies should be structured as:
 *
 * <pre>{@code
 * {@literal @}TypePolicyDefinition
 * private static final PolicyIdentifier<Type> POLICY_NAME =
 *     new PolicyIdentifier<>("POLICY_NAME");
 * }</pre>
 *
 * <p>Currently policy definitions are restricted to fields of {@link PolicyIdentifier}. This
 * restriction might be lifted in the future.
 *
 * @param <T> Represents the type of the value that is associated with this identifier.
 */
@FlaggedApi(FLAG_POLICY_STREAMLINING)
public final class PolicyIdentifier<T> {
    private final String mId;

    /**
     * Create an instance of PolicyIdentifier. Should only be used to create the static definitions
     * below.
     *
     * @hide
     */
    @TestApi
    public PolicyIdentifier(@NonNull String id) {
        this.mId = id;
    }

    /**
     * Get the string representation of this identifier.
     *
     * @return The string representation of this identifier
     * @hide
     */
    @NonNull
    @TestApi
    public String getId() {
        return mId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyIdentifier)) return false;
        PolicyIdentifier<?> that = (PolicyIdentifier<?>) o;
        return mId.equals(that.mId);
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }

    @Override
    @NonNull
    public String toString() {
        return mId;
    }

    /**
     * Screen capture is disallowed. See {@link android.view.Display#FLAG_SECURE} for more details
     * on how blocking works.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING)
    public static final int SCREEN_CAPTURE_DISALLOWED = 1;

    /** Screen capture is allowed. */
    @FlaggedApi(FLAG_POLICY_STREAMLINING)
    public static final int SCREEN_CAPTURE_ALLOWED = 2;

    /**
     * Possible values {@link SCREEN_CAPTURE}
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = {"SCREEN_CAPTURE_"},
            value = {
                SCREEN_CAPTURE_DISALLOWED,
                SCREEN_CAPTURE_ALLOWED,
            })
    public @interface ScreenCaptureValue {}

    /**
     * Policy that controls whether the screen capture is allowed or disallowed. Disallowing screen
     * capture also prevents the content from being shown on display devices that do not have a
     * secure video output. See {@link android.view.Display#FLAG_SECURE} for more details about
     * secure surfaces and secure displays. Throws SecurityException if the caller is not permitted
     * to control screen capture policy. If the scope is set to {@link
     * DevicePolicyManager.POLICY_SCOPE_DEVICE} and the caller is not a profile owner of an
     * organization-owned managed profile or a device owner, a security exception will be thrown.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING)
    @NonNull
    @EnumPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE},
                            affectedResource = RESOURCE_PER_USER,
                            requiredPermission = MANAGE_DEVICE_POLICY_SCREEN_CAPTURE,
                            requiredCrossUserPermission = MANAGE_DEVICE_POLICY_ACROSS_USERS,
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = ALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                            unaffiliatedFullUserProfileOwner = ALLOWED,
                                            profileOwnerOnUser0 = ALLOWED,
                                            affiliatedFullUserProfileOwner = ALLOWED)),
            intDef = ScreenCaptureValue.class,
            defaultValue = SCREEN_CAPTURE_ALLOWED)
    public static final PolicyIdentifier<Integer> SCREEN_CAPTURE =
            new PolicyIdentifier<>("SCREEN_CAPTURE");
}
