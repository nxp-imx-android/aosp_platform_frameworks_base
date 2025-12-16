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
import android.annotation.WorkerThread;
import android.app.appfunctions.AppFunctionMetadata;
import android.app.appfunctions.AppFunctionStaticMetadataHelper;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.PropertyPath;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchSpec;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides an in-memory cache for {@code AppFunction} types. This class is thread-safe.
 */
class AppFunctionsMetadataCache {

    private static final boolean DEBUG = Build.TYPE.equals("eng");

    private static final String TAG = "AppFuncsMetadataCache";

    private static final SearchSpec APP_SEARCH_FN_SPEC =
            new SearchSpec.Builder()
                    .addFilterSchemas(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                    .addProjectionPaths(
                            AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE,
                            List.of(new PropertyPath(AppFunctionMetadata.PROPERTY_SERVICE_NAME)))
                    .setVerbatimSearchEnabled(true)
                    .build();

    private static final String DYNAMIC_APP_FUNCTIONS_QUERY =
            TextUtils.formatSimple(
                    "%s:\"%s\"",
                    AppFunctionMetadata.PROPERTY_SERVICE_NAME,
                    AppFunctionMetadata.DYNAMIC_APP_FUNCTIONS_SERVICE_NAME);

    private final Context mContext;

    private final ExecutorService mExecutor;

    private final Object mCrossUserLock = new Object();

    @GuardedBy("mCrossUserLock")
    private final SparseArray<ArraySet<String>> mPerUserDynamicFunctionsCache = new SparseArray<>();

    /** Constructs a new {@link AppFunctionsMetadataCache}. */
    AppFunctionsMetadataCache(Context context) {
        mContext = context;
        mExecutor =
                Executors.newSingleThreadExecutor(
                        new NamedThreadFactory("AppFunctionCacheExecutors"));
    }

    void populateDataForUser(UserHandle user) {
        synchronized (mCrossUserLock) {
            mPerUserDynamicFunctionsCache.put(user.getIdentifier(), new ArraySet<>());
        }
        populateAllDynamicAppFunctions(user);
    }

    void removeDataForUser(UserHandle user) {
        synchronized (mCrossUserLock) {
            mPerUserDynamicFunctionsCache.remove(user.getIdentifier());
        }
    }

    /**
     * Returns whether the function is dynamic. Dynamic app-functions are declared on the app level
     * and executed directly via AIDL.
     *
     * @param packageName The package name of the application containing the function.
     * @param functionIdentifier The unique identifier for the function within the package.
     * @return {@link AppFunctionMetadata#FUNCTION_TYPE_STATIC} if the function requires the
     *     AppFunctionService, or {@link AppFunctionMetadata#FUNCTION_TYPE_DYNAMIC} if it does not.
     */
    boolean isDynamicFunction(String packageName, String functionIdentifier, UserHandle user) {
        synchronized (mCrossUserLock) {
            if (!mPerUserDynamicFunctionsCache.contains(user.getIdentifier())) {
                throw new IllegalArgumentException("User is not unlocked");
            }

            return mPerUserDynamicFunctionsCache
                    .get(user.getIdentifier())
                    .contains(
                            AppFunctionStaticMetadataHelper.getDocumentIdForAppFunction(
                                    packageName, functionIdentifier));
        }
    }

    void populateAllDynamicAppFunctions(UserHandle user) {
        var unused = mExecutor.submit(
                () -> {
                    Set<String> dynamicFunctions =
                            searchForDynamicFunctions(user, /* idsToFilter= */ null);
                    if (dynamicFunctions == null) {
                        return;
                    }

                    int userId = user.getIdentifier();
                    synchronized (mCrossUserLock) {
                        if (!mPerUserDynamicFunctionsCache.contains(userId)) {
                            Slog.e(
                                    TAG,
                                    "Cache is not available for user "
                                            + userId
                                            + ". Ignoring update.");
                            return;
                        }

                        ArraySet<String> userCachedFunctions =
                                mPerUserDynamicFunctionsCache.get(userId);
                        userCachedFunctions.clear();
                        userCachedFunctions.addAll(dynamicFunctions);

                        maybeLogForDebug(
                                "Cache after update for user " + userId,
                                mPerUserDynamicFunctionsCache.get(userId));
                    }
                });
    }

    void updateDynamicFunctions(UserHandle user, @NonNull Set<String> idsToFilter) {
        var unused = mExecutor.submit(
                () -> {
                    Set<String> dynamicFunctions = searchForDynamicFunctions(user, idsToFilter);
                    if (dynamicFunctions == null) {
                        return;
                    }

                    int userId = user.getIdentifier();
                    synchronized (mCrossUserLock) {
                        if (!mPerUserDynamicFunctionsCache.contains(userId)) {
                            Slog.w(TAG, "Cache is not available for user " + userId);
                            return;
                        }

                        ArraySet<String> userCachedFunctions =
                                mPerUserDynamicFunctionsCache.get(userId);
                        // Add functions that are (or have become) dynamic.
                        userCachedFunctions.addAll(dynamicFunctions);

                        // Remove functions that are no longer dynamic.
                        Set<String> obsoleteFunctions = new ArraySet<>(idsToFilter);
                        obsoleteFunctions.removeAll(dynamicFunctions);
                        userCachedFunctions.removeAll(obsoleteFunctions);

                        maybeLogForDebug(
                                "Cache after update for user " + userId,
                                mPerUserDynamicFunctionsCache.get(userId));
                    }
                });
    }

    @Nullable
    @WorkerThread
    private Set<String> searchForDynamicFunctions(
            UserHandle user, @Nullable Set<String> idsToFilter) {
        AppSearchManager appSearchManager =
                mContext.createContextAsUser(user, /* flags= */ 0)
                        .getSystemService(AppSearchManager.class);
        if (appSearchManager == null) {
            Slog.e(TAG, "AppSearchManager not found for user: " + user.getIdentifier());
            return null;
        }
        AppSearchManager.SearchContext staticMetadataSearchContext =
                new AppSearchManager.SearchContext.Builder(
                                AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB)
                        .build();
        SearchSpec searchFnSpec = APP_SEARCH_FN_SPEC;
        if (idsToFilter != null) {
            searchFnSpec =
                    new SearchSpec.Builder(searchFnSpec).addFilterDocumentIds(idsToFilter).build();
        }
        ArraySet<String> populatedFunctions = new ArraySet<>();
        try (FutureAppSearchSession staticMetadataSearchSession =
                        new FutureAppSearchSessionImpl(
                                appSearchManager, Runnable::run, staticMetadataSearchContext);
                FutureSearchResults futureSearchResults =
                        staticMetadataSearchSession
                                .search(DYNAMIC_APP_FUNCTIONS_QUERY, searchFnSpec)
                                .get()) {
            List<SearchResult> searchResultsList = futureSearchResults.getNextPage().get();
            while (!searchResultsList.isEmpty()) {
                for (SearchResult searchResult : searchResultsList) {
                    populatedFunctions.add(searchResult.getGenericDocument().getId());
                }
                searchResultsList = futureSearchResults.getNextPage().get();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error while populating cache: " + ex.getMessage());
        }
        return populatedFunctions;
    }

    private void maybeLogForDebug(String message, Iterable<String> ids) {
        if (DEBUG) {
            Log.d(TAG, TextUtils.formatSimple("%s: %s", message, String.join(", ", ids)));
        }
    }
}
