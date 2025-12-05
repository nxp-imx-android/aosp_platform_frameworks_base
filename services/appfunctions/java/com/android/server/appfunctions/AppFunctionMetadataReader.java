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

import static android.app.appfunctions.AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_NAMESPACE;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_APP_FUNCTION_STATIC_METADATA_QUALIFIED_ID;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_INDEXER_PACKAGE;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.app.appfunctions.AppFunctionMetadata;
import android.app.appfunctions.AppFunctionPackageMetadata;
import android.app.appfunctions.AppFunctionRuntimeMetadata;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.JoinSpec;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.content.Context;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Manages app functions search requests. */
public final class AppFunctionMetadataReader {
    private static final String TAG = AppFunctionMetadataReader.class.getSimpleName();

    private static final SearchSpec RUNTIME_SEARCH_SPEC =
            new SearchSpec.Builder()
                    .addFilterNamespaces(APP_FUNCTION_RUNTIME_NAMESPACE)
                    .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                    .setVerbatimSearchEnabled(true)
                    .build();
    private static final JoinSpec JOIN_SPEC =
            new JoinSpec.Builder(PROPERTY_APP_FUNCTION_STATIC_METADATA_QUALIFIED_ID)
                    .setNestedSearch("", RUNTIME_SEARCH_SPEC)
                    .build();

    private final AppFunctionsMetadataCache mCache;

    public AppFunctionMetadataReader(@NonNull Context context) {
        mCache = new AppFunctionsMetadataCache(context);
    }

    /** Called when observation of the user AppSearch app functions data has started. */
    public void onMetadataObserveStartedForUser(@NonNull UserHandle user) {
        mCache.populateDataForUser(user);
    }

    /** Called when schema is changed for user. */
    public void onMetadataSchemaChangedForUser(@NonNull UserHandle user) {
        // To optimise further, consider extract specific package from the schema name instead
        // of the full re-indexation
        mCache.populateDataForUser(user);
    }

    /** Called when observation of the user AppSearch app functions data has finished. */
    public void onMetadataObserveFinishedForUser(@NonNull UserHandle user) {
        mCache.removeDataForUser(user);
    }

    /** Called when there are changes in the static metadata DB. */
    public void onStaticMetadataDocumentsChanged(UserHandle user, DocumentChangeInfo changeInfo) {
        mCache.updateDynamicFunctions(user, changeInfo.getChangedDocumentIds());
    }

    /**
     * Returns whether the function is dynamic. Dynamic app-functions are declared on the app level
     * and executed directly via AIDL.
     *
     * @param packageName The package name of the application containing the function.
     * @param functionIdentifier The unique identifier for the function within the package.
     * @return {boolean} Whether app function is dynamic.
     */
    public boolean isDynamicFunction(
            String packageName,
            String functionIdentifier,
            UserHandle user
    ) {
        return mCache.isDynamicFunction(packageName, functionIdentifier, user);
    }

    /**
     * Performs a one-time search for AppFunctionMetadata with the given searchSpec and returns the
     * result.
     *
     * @param futureGlobalSearchSession The search session to use in searching for app function
     *     documents.
     * @param appFunctionSearchSpec The search spec used to filter app functions.
     */
    // TODO(b/438413081): Handle enabled state of contextual app functions.
    @NonNull
    @WorkerThread
    List<AppFunctionMetadata> searchAppFunctions(
            @NonNull FutureGlobalSearchSession futureGlobalSearchSession,
            @NonNull AppFunctionSearchSpec appFunctionSearchSpec)
            throws ExecutionException, InterruptedException {
        requireNonNull(futureGlobalSearchSession);
        requireNonNull(appFunctionSearchSpec);

        SearchSpec appFunctionJoinedStaticWithRuntimeSearchSpec =
                new SearchSpec.Builder()
                        .addFilterNamespaces(APP_FUNCTION_STATIC_NAMESPACE)
                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                        .addFilterDocumentIds(appFunctionSearchSpec.getQualifiedIdsFilter())
                        .setJoinSpec(JOIN_SPEC)
                        .setVerbatimSearchEnabled(true)
                        .setNumericSearchEnabled(true)
                        .setListFilterQueryLanguageEnabled(true)
                        .build();

        List<AppFunctionMetadata> result;

        try (FutureSearchResults futureSearchResults =
                futureGlobalSearchSession
                        .search(
                                appFunctionSearchSpec.getStaticMetadataAppSearchQuery(),
                                appFunctionJoinedStaticWithRuntimeSearchSpec)
                        .get(); ) {
            result = readAllAppFunctions(futureSearchResults);
        }
        return result;
    }

    @NonNull
    @WorkerThread
    private List<AppFunctionMetadata> readAllAppFunctions(
            @NonNull FutureSearchResults futureSearchResults)
            throws ExecutionException, InterruptedException {
        ArrayList<AppFunctionMetadata> resultMetadataList = new ArrayList<>();
        List<SearchResult> pageResult = futureSearchResults.getNextPage().get();

        while (!pageResult.isEmpty()) {
            for (SearchResult result : pageResult) {
                AppFunctionMetadata convertedResult =
                        convertSearchResultToAppFunctionMetadata(result);
                if (convertedResult != null) {
                    resultMetadataList.add(convertedResult);
                }
            }
            pageResult = futureSearchResults.getNextPage().get();
        }
        return resultMetadataList;
    }

    /**
     * Converts the given {@link SearchResult} to an {@link AppFunctionMetadata}. Returns {@code
     * null} if the search result is in an incorrect format.
     */
    @VisibleForTesting
    @Nullable
    @WorkerThread
    public static AppFunctionMetadata convertSearchResultToAppFunctionMetadata(
            @NonNull SearchResult searchResult) {
        try {
            GenericDocument staticMetadataDocument =
                    requireNonNull(searchResult.getGenericDocument());
            if (requireNonNull(searchResult.getJoinedResults()).size() != 1) {
                throw new IllegalArgumentException(
                        TextUtils.formatSimple(
                                "Expected 1 GenericDocument for runtimeMetadata, found %d",
                                searchResult.getJoinedResults().size()));
            }
            GenericDocument runtimeMetadataDocument =
                    requireNonNull(searchResult.getJoinedResults().getFirst().getGenericDocument());
            String packageName =
                    requireNonNull(
                            runtimeMetadataDocument.getPropertyString(
                                    AppFunctionRuntimeMetadata.PROPERTY_PACKAGE_NAME));

            return AppFunctionMetadata.create(
                    staticMetadataDocument,
                    runtimeMetadataDocument,
                    // TODO(b/438413081): Add package-level components
                    AppFunctionPackageMetadata.create(packageName, Collections.emptyList()));
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failed to convert SearchResult to AppFunctionMetadata.", e);
            return null;
        }
    }
}
