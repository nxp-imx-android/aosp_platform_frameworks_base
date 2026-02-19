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
import static android.Manifest.permission.MANAGE_DEVICE_POLICY_LOCKSCREEN_MESSAGE;
import static android.Manifest.permission.MANAGE_DEVICE_POLICY_SCREEN_CAPTURE;
import static android.Manifest.permission.SET_TIME;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE;
import static android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER;
import static android.app.admin.DevicePolicyManager.RESOURCE_DEVICE_WIDE;
import static android.app.admin.DevicePolicyManager.RESOURCE_PER_USER;
import static android.app.admin.flags.Flags.FLAG_POLICY_STREAMLINING;
import static android.app.admin.flags.Flags.FLAG_POLICY_STREAMLINING_AUTO_TIME;
import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Intent;
import android.os.UserManager;
import android.processor.devicepolicy.AllowedDpcTypes;
import android.processor.devicepolicy.EnumPolicyDefinition;
import android.processor.devicepolicy.PolicyDefinition;
import android.processor.devicepolicy.StringPolicyDefinition;

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

    // LINT.IfChange

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
     * DevicePolicyManager#POLICY_SCOPE_DEVICE} and the caller is not a profile owner of an
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

    /** The user can choose whether the time is automatically obtained from the network or not. */
    @FlaggedApi(FLAG_POLICY_STREAMLINING_AUTO_TIME)
    public static final int AUTO_TIME_USER_CHOICE = 0;

    /**
     * The admin has disabled the time to be automatically obtained from the network. This is not
     * enforced and the user can still enable it. Use {@link UserManager#DISALLOW_CONFIG_DATE_TIME}
     * to enforce the policy.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING_AUTO_TIME)
    public static final int AUTO_TIME_DISABLED_UNENFORCED = 1;

    /**
     * The admin has enabled the time to be automatically obtained from the network. This is not
     * enforced and the user can still disable it. Use {@link UserManager#DISALLOW_CONFIG_DATE_TIME}
     * to enforce the policy.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING_AUTO_TIME)
    public static final int AUTO_TIME_ENABLED_UNENFORCED = 2;

    /**
     * The admin has disabled the time to be automatically obtained from the network. This is
     * enforced and the user cannot enable it.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING_AUTO_TIME)
    public static final int AUTO_TIME_DISABLED = 3;

    /**
     * The admin has enabled the time to be automatically obtained from the network. This is
     * enforced and the user cannot disable it.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING_AUTO_TIME)
    public static final int AUTO_TIME_ENABLED = 4;

    /**
     * Possible values {@link AUTO_TIME}
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = {"AUTO_TIME_"},
            value = {
                AUTO_TIME_USER_CHOICE,
                AUTO_TIME_DISABLED_UNENFORCED,
                AUTO_TIME_ENABLED_UNENFORCED,
                AUTO_TIME_DISABLED,
                AUTO_TIME_ENABLED,
            })
    public @interface AutoTimeValue {}

    /** Policy that controls whether the time is automatically obtained from the network or not. */
    @FlaggedApi(FLAG_POLICY_STREAMLINING_AUTO_TIME)
    @NonNull
    @EnumPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {POLICY_SCOPE_DEVICE},
                            affectedResource = RESOURCE_DEVICE_WIDE,
                            requiredPermission = SET_TIME,
                            requiredCrossUserPermission = MANAGE_DEVICE_POLICY_ACROSS_USERS,
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = ALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED,
                                            profileOwnerOnUser0 = ALLOWED)),
            intDef = AutoTimeValue.class,
            defaultValue = AUTO_TIME_USER_CHOICE)
    public static final PolicyIdentifier<Integer> AUTO_TIME = new PolicyIdentifier<>("AUTO_TIME");

    /**
     * Policy that sets a custom message to be shown on the lock screen. This message is displayed
     * on the device screen when locked, and is useful for a lost or stolen device.
     *
     * <p>The message set using this method overrides any owner information manually set by the user
     * and prevents the user from further changing it.
     *
     * <p>If the message is {@code null} then the device owner info is cleared and the user owner
     * info is shown on the lock screen if it is set.
     *
     * <p>If the message contains only whitespaces then the message on the lock screen will be blank
     * and the user will not be allowed to change it.
     *
     * <p>If the message needs to be localized, it is the responsibility of the {@link
     * DeviceAdminReceiver} to listen to the {@link Intent#ACTION_LOCALE_CHANGED} broadcast and set
     * a new version of this string accordingly.
     */
    @FlaggedApi(FLAG_POLICY_STREAMLINING)
    @NonNull
    @StringPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {POLICY_SCOPE_DEVICE},
                            affectedResource = RESOURCE_DEVICE_WIDE,
                            requiredPermission = MANAGE_DEVICE_POLICY_LOCKSCREEN_MESSAGE,
                            requiredCrossUserPermission = MANAGE_DEVICE_POLICY_ACROSS_USERS,
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = ALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            emptyStringAllowed = false)
    public static final PolicyIdentifier<String> LOCKSCREEN_MESSAGE =
            new PolicyIdentifier<>("LOCKSCREEN_MESSAGE");

    // LINT.ThenChange(/tools/policymetadata/policies.textproto)
}
