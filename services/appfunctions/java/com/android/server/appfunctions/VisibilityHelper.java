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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionAidlSearchSpec;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appfunctions.AppFunctionActivityState;

import java.util.List;
import java.util.Set;

/** Helper for handling AppFunction visibility. */
public interface VisibilityHelper {
    /**
     * Applies the visible package filter to {@link AppFunctionSearchSpec}.
     *
     * @param aidlSearchSpec The original {@link AppFunctionAidlSearchSpec} from calling app.
     * @return {@link AppFunctionSearchSpec} with visible package filter applied. Null if the
     *     provided {@code aidlSearchSpec} cannot search anything that is visible to the caller.
     */
    @Nullable
    AppFunctionSearchSpec applyVisiblePackageFilter(
            @NonNull AppFunctionAidlSearchSpec aidlSearchSpec, int callingUid, int callingPid);

    /**
     * Filters the {@code functionNames} to only return the visible ones.
     *
     * @param functionNames The list of {@link AppFunctionName}.
     * @param callingPackageName The calling package name.
     * @param callingUid The calling uid.
     * @param callingPid The calling pid.
     * @return The list of {@link AppFunctionName} that the caller has visibility with.
     */
    @NonNull
    Set<AppFunctionName> filterVisibleAppFunctions(
            @NonNull Set<AppFunctionName> functionNames,
            @NonNull String callingPackageName,
            int callingUid,
            int callingPid);


    /**
     * Filters the {@code appFunctionActivityStates} to only return the visible ones.
     *
     * @param appFunctionActivityStates The list of {@link AppFunctionActivityState}.
     * @param callingPackageName The calling package name.
     * @param callingUid The calling uid.
     * @param callingPid The calling pid.
     * @return The list of {@link AppFunctionActivityState} that the caller has visibility with.
     */
    @NonNull
    List<AppFunctionActivityState> filterVisibleAppFunctionActivityStates(
            @NonNull List<AppFunctionActivityState> appFunctionActivityStates,
            @NonNull String callingPackageName,
            int callingUid,
            int callingPid);

    /**
     * Checks if {@code appFunctionName} is visible from {@code callingPackage}.
     *
     * @param appFunctionName The target {@link AppFunctionName}.
     * @param callingPackage The calling package name.
     * @param callingUid The calling uid.
     * @param callingPid The calling pid.
     * @return True if visible. False otherwise.
     */
    boolean isAppFunctionVisible(
            @NonNull AppFunctionName appFunctionName,
            @NonNull String callingPackage,
            int callingUid,
            int callingPid);
}
