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
package android.app.appfunctions;

import android.annotation.NonNull;

import java.util.List;

/**
 * A callback interface used to notify clients about changes to app functions that match the
 * criteria specified in the {@link AppFunctionSearchSpec} or their packages.
 *
 * <p>The provided information in the received callbacks can be used to construct a focused {@link
 * AppFunctionSearchSpec} and call {@link AppFunctionManager#searchAppFunctions} to retrieve the
 * full, updated {@link AppFunctionMetadata} for the changed functions.
 *
 * @hide
 */
public interface AppFunctionMetadataChangeCallback {
    /**
     * Called when one or more app functions or their {@link AppFunctionMetadata} has changed.
     *
     * @param changedFunctionNames The {@link AppFunctionName}s of the app functions that were
     *     added, updated or removed.
     */
    void onAppFunctionsChanged(@NonNull List<AppFunctionName> changedFunctionNames);

    /**
     * Called when changes occur to a package exposing app functions that may impact the state or
     * metadata of its contained functions.
     *
     * <p>This includes changes such as:
     *
     * <ul>
     *   <li>The definition of the function metadata exposed by the package has changed (e.g, new
     *       properties or fields are now available for all functions in the package).
     *   <li>
     *   <li>All functions within the package are added or removed due to the package being
     *       installed or uninstalled.
     *   <li>The package's {@link AppFunctionPackageMetadata} has been updated.
     * </ul>
     *
     * @param changedPackageNames The names of the updated packages.
     */
    void onPackagesChanged(@NonNull List<String> changedPackageNames);

    /**
     * Called when an unrecoverable error occurs during the registration of this callback.
     *
     * <p>Once this method is invoked, the callback is considered unregistered and will receive no
     * further updates.
     *
     * @param exception The exception describing the failure.
     */
    void onRegistrationError(@NonNull Exception exception);
}
