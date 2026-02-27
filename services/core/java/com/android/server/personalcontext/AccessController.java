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

import android.annotation.IntDef;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.internal.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link AccessController} is in charge of verifying that a specific component has the access
 * necessary to complete the given operation.
 *
 * @hide
 */
public class AccessController {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = {"ACCESS_"}, value = {
            ACCESS_UNDEFINED,
            ACCESS_PUBLISH_HINTS,
            ACCESS_RECEIVE_HINTS,
            ACCESS_PUBLISH_INSIGHTS,
            ACCESS_RECEIVE_INSIGHTS,
    })
    public @interface Access {
    }

    /**
     * Undefined access value.
     */
    public static final int ACCESS_UNDEFINED = 0;

    /**
     * Access to publish hints
     */
    public static final int ACCESS_PUBLISH_HINTS = 1;

    /**
     * Access to receive hints
     */
    public static final int ACCESS_RECEIVE_HINTS = 1 << 1;

    /**
     * Access to publish insights.
     */
    public static final int ACCESS_PUBLISH_INSIGHTS = 1 << 2;

    /**
     * Access to receive insights.
     */
    public static final int ACCESS_RECEIVE_INSIGHTS = 1 << 3;

    private final PackageManager mPackageManager;
    private final Context mContext;

    private final Set<String> mHintPublishers;

    private final Set<String> mHintReceivers;

    private final Set<String> mInsightPublishers;

    private final Set<String> mInsightReceivers;


    /**
     * Creates an {@link AccessController} from the given {@link Context}, which is used to
     * retrieve the configured allowlists for the device.
     * @param context {@link Context} to access resources from.
     */
    public AccessController(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();

        mHintPublishers = Set.of(mContext.getResources().getStringArray(
                R.array.config_allowlistPersonalContextHintPublishing));

        mHintReceivers = Set.of(mContext.getResources().getStringArray(
                R.array.config_allowlistPersonalContextHintReceiving));

        mInsightPublishers = Set.of(mContext.getResources().getStringArray(
                R.array.config_allowlistPersonalContextInsightPublishing));

        mInsightReceivers = Set.of(mContext.getResources().getStringArray(
                R.array.config_allowlistPersonalContextInsightReceiving));
    }

    /**
     * Checks whether the given UID has access based on the packages it is associated with.
     * @param uid The UID to check.
     * @param accessFlags The access level to be checked.
     * @return {@code true} if the UID has matching access, {@code false otherwise}.
     */
    public boolean hasAccess(int uid, @Access int accessFlags) {
        final String[] packagesForUid = mPackageManager.getPackagesForUid(uid);

        if (packagesForUid == null) {
            return false;
        }

        final HashSet<String> uidPackages = new HashSet<>(Arrays.asList(packagesForUid));
        return hasAccess(uidPackages, accessFlags);
    }

    /**
     * Checks whether the given packages have the specified access level.
     * @param packages The packages to check
     * @param accessFlags The access level to be verified
     * @return {@code true} if the packages have the specified access, {@code false otherwise}
     */
    public boolean hasAccess(Set<String> packages, @Access int accessFlags) {
        for (String targetPackage : packages) {
            if (hasAccess(targetPackage, accessFlags)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether the given package has the specified access level.
     * @param targetPackage The package to check
     * @param accessFlags The access level to be verified
     * @return {@code true} if the package has the specified access, {@code false otherwise}
     */
    public boolean hasAccess(String targetPackage, @Access int accessFlags) {
        if ((accessFlags & ACCESS_PUBLISH_HINTS) == ACCESS_PUBLISH_HINTS
                && !mHintPublishers.contains(targetPackage)) {
            return false;
        }

        if ((accessFlags & ACCESS_RECEIVE_HINTS) == ACCESS_RECEIVE_HINTS
                && !mHintReceivers.contains(targetPackage)) {
            return false;
        }

        if ((accessFlags & ACCESS_PUBLISH_INSIGHTS) == ACCESS_PUBLISH_INSIGHTS
                && !mInsightPublishers.contains(targetPackage)) {
            return false;
        }

        if ((accessFlags & ACCESS_RECEIVE_INSIGHTS) == ACCESS_RECEIVE_INSIGHTS
                && !mInsightReceivers.contains(targetPackage)) {
            return false;
        }

        return true;
    }
}
