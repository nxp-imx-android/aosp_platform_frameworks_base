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

package com.android.server.personalcontext;

import static android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION;

import android.Manifest;
import android.annotation.ArrayRes;
import android.annotation.IntDef;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.service.personalcontext.Flags;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.server.personalcontext.component.client.BaseServiceClientComponent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link AccessController} is in charge of verifying that a specific component has the access
 * necessary to complete the given operation.
 *
 * @hide
 */
public class AccessController {
    private static final String TAG = "AccessController";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = {"ACCESS_"}, value = {
            ACCESS_NONE,
            ACCESS_PUBLISH_HINTS_ALLOWLIST,
            ACCESS_RECEIVE_HINTS_ALLOWLIST,
            ACCESS_PUBLISH_INSIGHTS_ALLOWLIST,
            ACCESS_RECEIVE_INSIGHTS_ALLOWLIST,
            ACCESS_FILTER_INSIGHTS_ALLOWLIST,
            ACCESS_PUBLISH_HINTS_PERMISSION,
            ACCESS_RECEIVE_HINTS_PERMISSION,
            ACCESS_PUBLISH_INSIGHTS_PERMISSION,
            ACCESS_RECEIVE_INSIGHTS_PERMISSION,
            ACCESS_PCC,
            ACCESS_PCC_OR_AUTO_COMPANION_ROLE,
            ACCESS_REGISTER_VISUALIZER,
    })
    public @interface Access {
    }

    /** Undefined / no access value. */
    public static final int ACCESS_NONE = 0;

    /** Access to publish hints via the allowlist. */
    public static final int ACCESS_PUBLISH_HINTS_ALLOWLIST = 1;

    /** Access to receive hints via the allowlist. */
    public static final int ACCESS_RECEIVE_HINTS_ALLOWLIST = 1 << 1;

    /** Access to publish insights via the allowlist. */
    public static final int ACCESS_PUBLISH_INSIGHTS_ALLOWLIST = 1 << 2;

    /** Access to receive insights via the allowlist. */
    public static final int ACCESS_RECEIVE_INSIGHTS_ALLOWLIST = 1 << 3;

    /** Access to receive insights without RenderTokens via the allowlist. */
    public static final int ACCESS_FILTER_INSIGHTS_ALLOWLIST = 1 << 4;

    /** Access to publish hints via permissions. */
    public static final int ACCESS_PUBLISH_HINTS_PERMISSION = 1 << 5;

    /** Access to receive hints via permissions. */
    public static final int ACCESS_RECEIVE_HINTS_PERMISSION = 1 << 6;

    /** Access to publish insights via permissions. */
    public static final int ACCESS_PUBLISH_INSIGHTS_PERMISSION = 1 << 7;

    /** Access to receive insights via permissions. */
    public static final int ACCESS_RECEIVE_INSIGHTS_PERMISSION = 1 << 8;

    /** Component is PCC compliant. */
    public static final int ACCESS_PCC = 1 << 9;

    /** Component is PCC compliant or has the automotive companion app role. */
    public static final int ACCESS_PCC_OR_AUTO_COMPANION_ROLE = 1 << 10;

    /** Access to register a visualizer. */
    public static final int ACCESS_REGISTER_VISUALIZER = 1 << 11;

    /** Interface to inject dependencies. */
    public interface Injector {
        /** Get {@link Resources}. */
        Resources getResources();

        /** Get {@link PackageManager}. */
        PackageManager getPackageManager();

        /** Get {@link PermissionManager}. */
        PermissionManager getPermissionManager();

        /** Get {@link RoleManager}. */
        RoleManager getRoleManager();

        /** Get {@link EventListener}. */
        EventListener getEventListener();
    }

    private static Injector defaultInjector(Context context, EventListener eventListener) {
        return new Injector() {
            @Override
            public Resources getResources() {
                return context.getResources();
            }

            @Override
            public PackageManager getPackageManager() {
                return context.getPackageManager();
            }

            @Override
            public PermissionManager getPermissionManager() {
                return context.getSystemService(PermissionManager.class);
            }

            @Override
            public RoleManager getRoleManager() {
                return context.getSystemService(RoleManager.class);
            }

            @Override
            public EventListener getEventListener() {
                return eventListener;
            }
        };
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"RESULT_"}, value = {
            RESULT_ALLOWED,
            RESULT_DENIED,
            RESULT_BYPASSED,
    })
    public @interface AccessResult {
    }

    /** Allowed. */
    public static final int RESULT_ALLOWED = 0;

    /** Allowed. */
    public static final int RESULT_DENIED = 1;

    /** Allowed. */
    public static final int RESULT_BYPASSED = 2;

    /** Listener for access checks. */
    public interface EventListener {
        /** Called whenever an access determination is made. */
        void onAccessChecked(
                String packageName,
                UserHandle user,
                String description,
                @AccessResult int result);
    }

    private final Resources mResources;
    private final PackageManager mPackageManager;
    private final PermissionManager mPermissionManager;
    private final RoleManager mRoleManager;
    private final UserHandle mUser;
    private final SparseArray<Set<String>> mAllowLists = new SparseArray<>();
    private final EventListener mEventListener;

    /**
     * Creates an {@link AccessController} from the given {@link Context}, which is used to
     * retrieve the configured allowlists for the device.
     */
    public AccessController(Context context, EventListener eventListener, UserHandle user) {
        this(defaultInjector(context, eventListener), user);
    }

    /**
     * Creates an {@link AccessController} from the given {@link Context}, which is used to
     * retrieve the configured allowlists for the device.
     */
    AccessController(Injector injector, UserHandle user) {
        mResources = injector.getResources();
        mPackageManager = injector.getPackageManager();
        mPermissionManager = injector.getPermissionManager();
        mRoleManager = injector.getRoleManager();
        mEventListener = injector.getEventListener();
        mUser = user;
    }

    /**
     * Checks whether the given package uid satisfies the specified access rule.
     *
     * @param uid         The uid whose packages should be checked
     * @param accessFlags The access permissions to check
     * @return {@code true} if the service client has the specified access, {@code false} otherwise
     */
    public boolean isAnyPackageForUidAllowed(int uid, @Access int accessFlags) {
        final String[] packagesForUid = mPackageManager.getPackagesForUid(uid);

        if (packagesForUid == null) {
            return false;
        }

        final HashSet<String> uidPackages = new HashSet<>(Arrays.asList(packagesForUid));
        return isAnyPackageAllowed(uidPackages, accessFlags);
    }

    /**
     * Checks whether the given service client satisfies the specified access rule.
     *
     * @param serviceClient The service client to be verified
     * @param accessFlags   The access permissions to check
     * @return {@code true} if the service client has the specified access, {@code false} otherwise
     */
    public boolean isClientAllowed(
            BaseServiceClientComponent<?> serviceClient, @Access int accessFlags) {
        return isServiceAllowed(serviceClient.getServiceInfo(), accessFlags);
    }

    /**
     * Checks whether any of the given package satisfies the specified access rule. Not all rules
     * can be verified by package name.
     *
     * @param packageNames The names of the package to be verified
     * @param accessFlags  The access permissions to check
     * @return {@code true} if the package has the specified access, {@code false} otherwise
     */
    public boolean isAnyPackageAllowed(Collection<String> packageNames, @Access int accessFlags) {
        for (String packageName : packageNames) {
            if (isPackageAllowed(packageName, accessFlags)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether the given service satisfies the specified access rule.
     *
     * @param serviceInfo The service to be verified
     * @param accessFlags The rule to check
     * @return {@code true} if the service has the specified access, {@code false} otherwise
     */
    public boolean isServiceAllowed(ServiceInfo serviceInfo, @Access int accessFlags) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Checking service " + serviceInfo);
        }

        boolean result = true;

        // We only need the service info to check the PCC flag.
        if ((accessFlags & ACCESS_PCC) != 0) {
            result &= checkPccFlag(serviceInfo);
        } else if ((accessFlags & ACCESS_PCC_OR_AUTO_COMPANION_ROLE) != 0) {
            result &= checkPccOrAutoCompanionFlag(serviceInfo);
        }

        return result & isPackageAllowed(
                serviceInfo.packageName,
                accessFlags & ~ACCESS_PCC & ~ACCESS_PCC_OR_AUTO_COMPANION_ROLE);
    }

    /**
     * Checks whether the given package satisfies the specified access rule. Not all rules can be
     * verified by package name.
     *
     * @param packageName The name of the package to be verified
     * @param accessFlags The access permissions to check
     * @return {@code true} if the package has the specified access, {@code false} otherwise
     */
    public boolean isPackageAllowed(String packageName, @Access int accessFlags) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Checking package " + packageName);
        }

        boolean result = true;

        // We need the service info to check the PCC flag.
        if ((accessFlags & (ACCESS_PCC | ACCESS_PCC_OR_AUTO_COMPANION_ROLE)) != 0) {
            Log.d(TAG, "PCC check: requires ServiceInfo");
            result = false;
        }

        if ((accessFlags & ACCESS_PUBLISH_HINTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextHintPublishing);
        }

        if ((accessFlags & ACCESS_RECEIVE_HINTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextHintReceiving);
        }

        if ((accessFlags & ACCESS_PUBLISH_INSIGHTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextInsightPublishing);
        }

        if ((accessFlags & ACCESS_RECEIVE_INSIGHTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextInsightReceiving);
        }

        if ((accessFlags & ACCESS_FILTER_INSIGHTS_ALLOWLIST) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextInsightFiltering);
        }

        if ((accessFlags & ACCESS_PUBLISH_HINTS_PERMISSION) != 0) {
            result &= checkPermission(
                    packageName, Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        }

        if ((accessFlags & ACCESS_RECEIVE_HINTS_PERMISSION) != 0) {
            result &= checkPermission(
                    packageName, Manifest.permission.PERSONAL_CONTEXT_RECEIVE_HINTS);
        }

        if ((accessFlags & ACCESS_PUBLISH_INSIGHTS_PERMISSION) != 0) {
            result &= checkPermission(
                    packageName, Manifest.permission.PERSONAL_CONTEXT_PUBLISH_INSIGHTS);
        }

        if ((accessFlags & ACCESS_RECEIVE_INSIGHTS_PERMISSION) != 0) {
            result &= checkPermission(
                    packageName, Manifest.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS);
        }

        if ((accessFlags & ACCESS_REGISTER_VISUALIZER) != 0) {
            result &= checkAllowList(
                    packageName, R.array.config_allowlistPersonalContextVisualizers);
        }

        return result;
    }

    /** Performs checks for PCC or the auto companion role. */
    private boolean checkPccOrAutoCompanionFlag(ServiceInfo serviceInfo) {
        return checkPccFlag(serviceInfo)
                || checkRoleFlag(serviceInfo.packageName, DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);
    }

    /** Performs checks for PCC. */
    private boolean checkPccFlag(ServiceInfo serviceInfo) {
        if (!Flags.enforcePersonalContextPccAccessControl()) {
            return makeAccessDecision(serviceInfo.packageName, "PCC", RESULT_BYPASSED);
        }

        return makeAccessDecision(
                serviceInfo.packageName,
                "PCC",
                (serviceInfo.flags & ServiceInfo.FLAG_RUN_IN_PCC_SANDBOX) != 0
                        ? RESULT_ALLOWED : RESULT_DENIED);
    }

    /** Performs checks for a role. */
    private boolean checkRoleFlag(String packageName, String role) {
        if (!Flags.enforcePersonalContextRoleAccessControl()) {
            return makeAccessDecision(packageName, "Role " + role, RESULT_BYPASSED);
        }

        return makeAccessDecision(
                packageName,
                "Role " + role,
                mRoleManager.getRoleHolders(role).contains(packageName)
                        ? RESULT_ALLOWED : RESULT_DENIED);
    }

    /** Performs checks for a permission. */
    private boolean checkPermission(String packageName, String permission) {
        if (!Flags.enforcePersonalContextPermissions()) {
            return makeAccessDecision(packageName, "Permission " + permission, RESULT_BYPASSED);
        }

        final int permissionResult = mPermissionManager.checkPackageNamePermission(
                permission,
                packageName,
                Context.DEVICE_ID_DEFAULT,
                mUser.getIdentifier());

        return makeAccessDecision(
                packageName,
                "Permission " + permission,
                permissionResult == PackageManager.PERMISSION_GRANTED
                        ? RESULT_ALLOWED : RESULT_DENIED);
    }

    /** Performs checks for an allowlist. */
    private boolean checkAllowList(String packageName, @ArrayRes int allowListResId) {
        final String description = "AllowList " + (mResources == null
                ? "[unknown]"
                : mResources.getResourceName(allowListResId));
        if (!Flags.enforcePersonalContextAllowlistAccessControl()) {
            return makeAccessDecision(packageName, description, RESULT_BYPASSED);
        }

        if (!mAllowLists.contains(allowListResId)) {
            mAllowLists.put(allowListResId, Set.of(mResources.getStringArray(allowListResId)));
        }

        return makeAccessDecision(
                packageName,
                description,
                mAllowLists.get(allowListResId).contains(packageName)
                        ? RESULT_ALLOWED : RESULT_DENIED);
    }

    private boolean makeAccessDecision(
            String packageName,
            String description,
            @AccessResult int result) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format(
                    "%s check: %s",
                    description,
                    result == RESULT_ALLOWED
                            ? "allowed" : result == RESULT_DENIED ? "denied" : "bypassed"));
        }

        if (mEventListener != null) {
            mEventListener.onAccessChecked(
                    packageName,
                    mUser,
                    description,
                    result);
        }

        return result != RESULT_DENIED;
    }
}
