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

package com.android.server.appfunctions;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionAidlSearchSpec;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class VisibilityHelperImpl implements VisibilityHelper {

    @NonNull private final Context mContext;

    @NonNull private final PackageManagerInternal mPmInternal;

    public VisibilityHelperImpl(
            @NonNull Context context, @NonNull PackageManagerInternal packageManagerInternal) {
        mContext = Objects.requireNonNull(context);
        mPmInternal = Objects.requireNonNull(packageManagerInternal);
    }

    @Nullable
    @Override
    public AppFunctionSearchSpec applyVisiblePackageFilter(
            @NonNull AppFunctionAidlSearchSpec aidlSearchSpec, int callingUid, int callingPid) {
        Objects.requireNonNull(aidlSearchSpec);

        AppFunctionSearchSpec clientSearchSpec = aidlSearchSpec.getClientSearchSpec();
        List<String> originalSearchPackages = clientSearchSpec.getPackageNames();
        List<AppFunctionName> originalSearchFunctions = clientSearchSpec.getFunctionNames();

        final long token = Binder.clearCallingIdentity();
        try {
            // First base case, if the caller doesn't have EXECUTE_APP_FUNCTIONS permission, it
            // can only sees the function from its own package
            if (mContext.checkPermission(
                            Manifest.permission.EXECUTE_APP_FUNCTIONS, callingPid, callingUid)
                    != PackageManager.PERMISSION_GRANTED) {
                Set<String> visiblePackages = new ArraySet<>();
                visiblePackages.add(aidlSearchSpec.getCallingPackageName());
                List<String> filteredPackages =
                        getFilteredPackages(visiblePackages, originalSearchPackages);
                List<AppFunctionName> filteredFunctionNames =
                        getFilteredFunctionNames(visiblePackages, originalSearchFunctions);
                if (isInvalidSearch(filteredPackages, filteredFunctionNames)) {
                    return null;
                }
                return new AppFunctionSearchSpec.Builder(clientSearchSpec)
                        .setPackageNames(filteredPackages)
                        .setFunctionNames(filteredFunctionNames)
                        .build();
            }

            // Second base case - If the caller has both EXECUTE_APP_FUNCTIONS and
            // QUERY_ALL_PACKAGES permission, then it can see any AppFunctions
            if (mContext.checkPermission(
                            Manifest.permission.QUERY_ALL_PACKAGES, callingPid, callingUid)
                    == PackageManager.PERMISSION_GRANTED) {
                return clientSearchSpec;
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        Set<String> visiblePackages =
                getVisiblePackages(callingUid, aidlSearchSpec.getTargetUserId());
        List<String> filteredPackages =
                getFilteredPackages(visiblePackages, originalSearchPackages);
        List<AppFunctionName> filteredFunctionNames =
                getFilteredFunctionNames(visiblePackages, originalSearchFunctions);
        if (isInvalidSearch(filteredPackages, filteredFunctionNames)) {
            return null;
        }
        return new AppFunctionSearchSpec.Builder(clientSearchSpec)
                .setPackageNames(filteredPackages)
                .setFunctionNames(filteredFunctionNames)
                .build();
    }

    @NonNull
    private List<String> getFilteredPackages(
            @NonNull Set<String> visiblePackages, @Nullable List<String> originalPackages) {
        ArrayList<String> updatedPackages = new ArrayList<>();
        if (originalPackages == null) {
            // Missing package means search all packages, therefore use all visible packages here
            updatedPackages.addAll(visiblePackages);
        } else {
            for (String originalPackage : originalPackages) {
                if (visiblePackages.contains(originalPackage)) {
                    updatedPackages.add(originalPackage);
                }
            }
        }
        return updatedPackages;
    }

    @Nullable
    private List<AppFunctionName> getFilteredFunctionNames(
            @NonNull Set<String> visiblePackages,
            @Nullable List<AppFunctionName> originalFunctionNames) {
        ArrayList<AppFunctionName> updatedFunctionNames = null;
        if (originalFunctionNames != null) {
            updatedFunctionNames = new ArrayList<>();
            for (AppFunctionName functionName : originalFunctionNames) {
                if (visiblePackages.contains(functionName.getPackageName())) {
                    updatedFunctionNames.add(functionName);
                }
            }
        }
        return updatedFunctionNames;
    }

    @NonNull
    private Set<String> getVisiblePackages(int callingUid, int userId) {
        final long token = Binder.clearCallingIdentity();
        try {
            ArraySet<String> visiblePackages = new ArraySet<>();
            List<ApplicationInfo> allApps =
                    mPmInternal.getInstalledApplications(/* flags= */ 0, userId, callingUid);
            for (ApplicationInfo appInfo : allApps) {
                visiblePackages.add(appInfo.packageName);
            }
            return visiblePackages;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Checks if searching with {@code packageNames} and {@code functionNames} is an invalid search.
     *
     * <p>It is an invalid search when attempting to search empty package or empty function names,
     * since it would always return empty search result.
     */
    private boolean isInvalidSearch(
            @NonNull List<String> packageNames, @Nullable List<AppFunctionName> functionNames) {
        return packageNames.isEmpty() || (functionNames != null && functionNames.isEmpty());
    }
}
