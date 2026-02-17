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

import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_DEFAULT;
import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_ENABLED;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_NAMESPACE;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_APP_FUNCTION_STATIC_METADATA_QUALIFIED_ID;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_ENABLED;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_INDEXER_PACKAGE;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.WorkerThread;
import android.app.appfunctions.AppFunctionActivityId;
import android.app.appfunctions.AppFunctionActivityState;
import android.app.appfunctions.AppFunctionManagerHelper;
import android.app.appfunctions.AppFunctionMetadata;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionPackageMetadata;
import android.app.appfunctions.AppFunctionRuntimeMetadata;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appfunctions.AppFunctionState;
import android.app.appfunctions.AppFunctionStaticMetadataHelper;
import android.app.appfunctions.IAppFunctionSearchResultCallback;
import android.app.appfunctions.IAppFunctionSearchResults;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.JoinSpec;
import android.app.appsearch.PropertyPath;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.os.Binder;
import android.os.ParcelableException;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/** Manages app functions search requests. */
final class AppFunctionMetadataReader {
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
    private final MultiUserDynamicAppFunctionRegistry mMultiUserDynamicAppFunctionRegistry;
    private final AppFunctionsMetadataCache mCache;
    private final ServiceConfig mServiceConfig;

    public AppFunctionMetadataReader(
            @NonNull MultiUserDynamicAppFunctionRegistry multiUserDynamicAppFunctionRegistry,
            @NonNull AppFunctionsMetadataCache metadataCache,
            @NonNull ServiceConfig serviceConfig) {
        mMultiUserDynamicAppFunctionRegistry =
                Objects.requireNonNull(multiUserDynamicAppFunctionRegistry);
        mCache = Objects.requireNonNull(metadataCache);
        mServiceConfig = Objects.requireNonNull(serviceConfig);
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
            String packageName, String functionIdentifier, UserHandle user) {
        return mCache.isDynamicFunction(packageName, functionIdentifier, user);
    }

    /**
     * Returns whether the function is activity scoped dynamic app function. Activity scoped dynamic
     * app functions must be registered within the activity context and support multiregistration
     * (so the same app function can be registered by multiple activities).
     *
     * <p>See {@link android.app.appfunctions.AppFunctionActivityId}.
     *
     * @param packageName The package name of the application containing the function.
     * @param functionIdentifier The unique identifier for the function within the package.
     * @return {boolean} Whether app function is activity scoped dynamic.
     */
    public boolean isActivityScopedDynamicFunction(
            String packageName, String functionIdentifier, UserHandle user) {
        return mCache.isActivityScopedDynamicFunction(packageName, functionIdentifier, user);
    }

    /**
     * Checks if the {@code targetAppFunction} is enabled or not.
     *
     * @param futureGlobalSearchSession The session to search AppFunctions.
     * @param targetAppFunction The target AppFunction.
     * @param userId The target user id.
     * @return True if the function is enabled. False otherwise.
     */
    CompletableFuture<Boolean> isAppFunctionEnabled(
            @NonNull FutureGlobalSearchSession futureGlobalSearchSession,
            @NonNull AppFunctionName targetAppFunction,
            int userId) {
        // Unregistered dynamic function is effectively disabled. No need to call app search.
        if (mCache.isDynamicFunction(
                        targetAppFunction.getPackageName(),
                        targetAppFunction.getFunctionIdentifier(),
                        UserHandle.of(userId))
                && !mMultiUserDynamicAppFunctionRegistry.hasRegistrations(
                        targetAppFunction.getPackageName(),
                        targetAppFunction.getFunctionIdentifier(),
                        UserHandle.of(userId))) {
            return AndroidFuture.completedFuture(false);
        }
        SearchSpec appFunctionJoinedStaticWithRuntimeSearchSpec =
                new SearchSpec.Builder()
                        .addFilterDocumentIds(List.of(targetAppFunction.getQualifiedId()))
                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                        .addFilterSchemas(
                                AppFunctionStaticMetadataHelper.getStaticSchemaNameForPackage(
                                        targetAppFunction.getPackageName()))
                        .addProjectionPaths(
                                SearchSpec.SCHEMA_TYPE_WILDCARD,
                                List.of(
                                        new PropertyPath(
                                                AppFunctionMetadata.PROPERTY_ENABLED_BY_DEFAULT)))
                        .setJoinSpec(JOIN_SPEC)
                        .setVerbatimSearchEnabled(true)
                        .build();
        return futureGlobalSearchSession
                .search("", appFunctionJoinedStaticWithRuntimeSearchSpec)
                .thenCompose(FutureSearchResults::getNextPage)
                .thenCompose(
                        result -> {
                            if (result.size() != 1) {
                                return AndroidFuture.failedFuture(
                                        new AppFunctionManagerHelper.AppFunctionNotFoundException(
                                                "App function not found."));
                            }
                            List<SearchResult> joinedResult = result.getFirst().getJoinedResults();
                            if (joinedResult.size() != 1) {
                                return AndroidFuture.failedFuture(
                                        new RuntimeException(
                                                TextUtils.formatSimple(
                                                        "Expected 1 GenericDocument for"
                                                                + " runtimeMetadata, found %d",
                                                        joinedResult.size())));
                            }

                            GenericDocument staticDocument = result.getFirst().getGenericDocument();
                            GenericDocument runtimeDocument =
                                    joinedResult.getFirst().getGenericDocument();
                            return AndroidFuture.completedFuture(
                                    calculateEffectiveEnabledState(
                                            staticDocument, runtimeDocument, userId));
                        });
    }

    private CompletableFuture<AppFunctionPackageMetadata> searchAppFunctionPackageMetadata(
            @NonNull FutureGlobalSearchSession futureSession, @NonNull String packageName) {
        Objects.requireNonNull(futureSession);
        Objects.requireNonNull(packageName);

        SearchSpec appFunctionDocumentSearchSpec =
                new SearchSpec.Builder()
                        .addFilterNamespaces(APP_FUNCTION_STATIC_NAMESPACE)
                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                        .setVerbatimSearchEnabled(true)
                        .setListFilterQueryLanguageEnabled(true)
                        .setListFilterHasPropertyFunctionEnabled(true)
                        .setResultCountPerPage(250) // Maximum AppFunction related document per app
                        .build();
        // TODO(b/473468720): -hasProperty("functionId") is not a reliable signal
        String query =
                TextUtils.formatSimple(
                        "packageName:\"%s\" -propertyDefined(\"functionId\")", packageName);
        return futureSession
                .search(query, appFunctionDocumentSearchSpec)
                .thenCompose(FutureSearchResults::getNextPage)
                .thenApply(
                        page -> {
                            ArrayList<GenericDocument> topLevelDocuments = new ArrayList<>();
                            for (int i = 0; i < page.size(); i++) {
                                topLevelDocuments.add(page.get(i).getGenericDocument());
                            }
                            return AppFunctionPackageMetadata.create(
                                    packageName, topLevelDocuments);
                        });
    }

    /** Gets a list of {@link AppFunctionState}. */
    @NonNull
    AndroidFuture<List<AppFunctionState>> getAppFunctionStates(
            @NonNull FutureGlobalSearchSession futureGlobalSearchSession,
            @NonNull Set<AppFunctionName> appFunctionNames,
            int userId) {
        Objects.requireNonNull(futureGlobalSearchSession);
        Objects.requireNonNull(appFunctionNames);

        if (appFunctionNames.isEmpty()) {
            return AndroidFuture.completedFuture(List.of());
        }

        List<String> documentIds = new ArrayList<>();
        for (AppFunctionName name : appFunctionNames) {
            documentIds.add(name.getQualifiedId());
        }

        SearchSpec appFunctionJoinedStaticWithRuntime =
                new SearchSpec.Builder()
                        .addFilterDocumentIds(documentIds)
                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                        .addFilterSchemas(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                        .addProjectionPaths(
                                SearchSpec.SCHEMA_TYPE_WILDCARD,
                                List.of(
                                        new PropertyPath(
                                                AppFunctionMetadata.PROPERTY_ENABLED_BY_DEFAULT)))
                        .setJoinSpec(JOIN_SPEC)
                        .setVerbatimSearchEnabled(true)
                        .setResultCountPerPage(
                                mServiceConfig.getSearchAppFunctionInternalPageSize())
                        .build();

        return futureGlobalSearchSession
                .search("", appFunctionJoinedStaticWithRuntime)
                .thenCompose(
                        searchResults -> {
                            List<AppFunctionState> accumulator = new ArrayList<>();
                            return parseAppFunctionStates(searchResults, userId, accumulator)
                                    .whenComplete(
                                            (result, exception) -> {
                                                searchResults.close();
                                            });
                        });
    }

    @WorkerThread
    @NonNull
    AndroidFuture<List<AppFunctionActivityState>> getAppFunctionActivityStates(
            @NonNull List<AppFunctionActivityId> activityIds, int userId) {
        return AndroidFuture.completedFuture(
                mMultiUserDynamicAppFunctionRegistry.getAppFunctionActivityStates(
                        activityIds, UserHandle.of(userId)));
    }

    @NonNull
    private AndroidFuture<List<AppFunctionState>> parseAppFunctionStates(
            @NonNull FutureSearchResults searchResults,
            int userId,
            @NonNull List<AppFunctionState> accumulator) {
        Objects.requireNonNull(searchResults);
        Objects.requireNonNull(accumulator);

        return searchResults
                .getNextPage()
                .thenCompose(
                        page -> {
                            if (page.isEmpty()) {
                                return AndroidFuture.completedFuture(accumulator);
                            }

                            for (SearchResult result : page) {
                                AppFunctionState state = parseAppFunctionState(result, userId);
                                if (state != null) accumulator.add(state);
                            }

                            return parseAppFunctionStates(searchResults, userId, accumulator);
                        });
    }

    @Nullable
    private AppFunctionState parseAppFunctionState(@NonNull SearchResult searchResult, int userId) {
        Objects.requireNonNull(searchResult);

        try {
            GenericDocument staticDocument = requireNonNull(searchResult.getGenericDocument());
            if (requireNonNull(searchResult.getJoinedResults()).size() != 1) {
                Slog.w(
                        TAG,
                        TextUtils.formatSimple(
                                "Expected 1 GenericDocument for runtimeMetadata, found %d",
                                searchResult.getJoinedResults().size()));
                return null;
            }
            GenericDocument runtimeDocument =
                    requireNonNull(searchResult.getJoinedResults().getFirst().getGenericDocument());
            AppFunctionName appFunctionName =
                    AppFunctionName.fromQualifiedId(staticDocument.getId());

            return new AppFunctionState(
                    appFunctionName,
                    calculateEffectiveEnabledState(staticDocument, runtimeDocument, userId),
                    getActivityIdInfo(appFunctionName, UserHandle.of(userId)));
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failed to convert SearchResult to AppFunctionState.", e);
            return null;
        }
    }

    @Nullable
    private ArraySet<AppFunctionActivityId> getActivityIdInfo(
            @NonNull AppFunctionName functionName, @NonNull UserHandle user) {
        if (!mCache.isActivityScopedDynamicFunction(
                functionName.getPackageName(), functionName.getFunctionIdentifier(), user)) {
            return null;
        }
        return mMultiUserDynamicAppFunctionRegistry.getRegisteredActivityIds(functionName, user);
    }

    /**
     * Performs a one-time search for AppFunctionMetadata with the given searchSpec and returns the
     * result.
     *
     * @param futureGlobalSearchSession The search session to use in searching for app function
     *     documents.
     * @param appFunctionSearchSpec The search spec used to filter app functions.
     * @param resultExecutor The executor to be used by {@link IAppFunctionSearchResults} when
     *     reading the next page.
     */
    @WorkerThread
    @NonNull
    IAppFunctionSearchResults searchAppFunctions(
            @NonNull FutureGlobalSearchSession futureGlobalSearchSession,
            @NonNull AppFunctionSearchSpec appFunctionSearchSpec,
            @NonNull Executor resultExecutor)
            throws ExecutionException, InterruptedException {
        requireNonNull(futureGlobalSearchSession);
        requireNonNull(appFunctionSearchSpec);
        requireNonNull(resultExecutor);

        SearchSpec staticMetadataSearchSpec =
                new SearchSpec.Builder()
                        .addFilterNamespaces(APP_FUNCTION_STATIC_NAMESPACE)
                        .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                        .addFilterDocumentIds(appFunctionSearchSpec.getQualifiedIdsFilter())
                        .addFilterSchemas(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                        .setVerbatimSearchEnabled(true)
                        .setNumericSearchEnabled(true)
                        .setListFilterQueryLanguageEnabled(true)
                        .setResultCountPerPage(
                                mServiceConfig.getSearchAppFunctionInternalPageSize())
                        .build();

        FutureSearchResults futureSearchResults =
                futureGlobalSearchSession
                        .search(
                                appFunctionSearchSpec.getStaticMetadataAppSearchQuery(),
                                staticMetadataSearchSpec)
                        .get();
        return new AppFunctionSearchResultsImpl(
                this, futureGlobalSearchSession, futureSearchResults, resultExecutor);
    }

    /**
     * Converts the given {@link SearchResult} to an {@link AppFunctionMetadata}. Returns {@code
     * null} if the search result is in an incorrect format.
     */
    @VisibleForTesting
    @Nullable
    @WorkerThread
    public AppFunctionMetadata buildAppFunctionMetadata(
            @NonNull SearchResult searchResult,
            @NonNull AppFunctionPackageMetadata packageMetadata) {
        try {
            GenericDocument staticDocument = requireNonNull(searchResult.getGenericDocument());
            return new AppFunctionMetadata.Builder(staticDocument, packageMetadata).build();
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failed to convert SearchResult to AppFunctionMetadata.", e);
            return null;
        }
    }

    private boolean calculateEffectiveEnabledState(
            @NonNull GenericDocument staticDocument,
            @NonNull GenericDocument runtimeDocument,
            int userId) {
        Objects.requireNonNull(staticDocument);
        Objects.requireNonNull(runtimeDocument);

        boolean isRuntimeEnabled;
        if (runtimeDocument.getPropertyLong(PROPERTY_ENABLED) == APP_FUNCTION_STATE_DEFAULT) {
            isRuntimeEnabled =
                    staticDocument.getPropertyBoolean(
                            AppFunctionMetadata.PROPERTY_ENABLED_BY_DEFAULT);
        } else {
            isRuntimeEnabled =
                    runtimeDocument.getPropertyLong(PROPERTY_ENABLED) == APP_FUNCTION_STATE_ENABLED;
        }

        AppFunctionName appFunctionName = AppFunctionName.fromQualifiedId(staticDocument.getId());
        String packageName = appFunctionName.getPackageName();
        String functionId = appFunctionName.getFunctionIdentifier();

        if (mCache.isDynamicFunction(packageName, functionId, UserHandle.of(userId))) {
            return isRuntimeEnabled
                    && mMultiUserDynamicAppFunctionRegistry.hasRegistrations(
                            packageName, functionId, UserHandle.of(userId));
        } else {
            // TODO(b/467317154): Take into account AppFunctionService availability for static
            //  app functions
            return isRuntimeEnabled;
        }
    }

    private static class AppFunctionSearchResultsImpl extends IAppFunctionSearchResults.Stub {
        @NonNull private final AppFunctionMetadataReader mReader;

        @GuardedBy("mLock")
        @NonNull
        private final FutureGlobalSearchSession mGlobalSession;

        @GuardedBy("mLock")
        @NonNull
        private final FutureSearchResults mSearchResults;

        @NonNull private final Executor mExecutor;

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private Boolean mIsClosed = false;

        AppFunctionSearchResultsImpl(
                @NonNull AppFunctionMetadataReader reader,
                @NonNull FutureGlobalSearchSession globalSession,
                @NonNull FutureSearchResults searchResults,
                @NonNull Executor executor) {
            mReader = Objects.requireNonNull(reader);
            mGlobalSession = Objects.requireNonNull(globalSession);
            mSearchResults = Objects.requireNonNull(searchResults);
            mExecutor = Objects.requireNonNull(executor);
        }

        @PermissionManuallyEnforced
        @Override
        public void getNextPage(IAppFunctionSearchResultCallback callback) {
            getNextValidPage()
                    .whenComplete(
                            (metadataList, exception) -> {
                                if (exception != null) {
                                    try {
                                        callback.onError(new ParcelableException(exception));
                                    } catch (RemoteException re) {
                                        Slog.w(TAG, "Fail to call onError", re);
                                    }
                                } else {
                                    try {
                                        callback.onResult(metadataList);
                                    } catch (RemoteException e) {
                                        Slog.w(TAG, "Fail to call onSuccess", e);
                                    }
                                }
                            });
        }

        /**
         * Gets the next page and block the caller thread.
         *
         * @return The list of {@link AppFunctionMetadata} from next page. Empty list indicates that
         *     there is no next page. However, {@code null} means all the documents from the current
         *     page are invalid, but there is still next page to query.
         */
        @NonNull
        @WorkerThread
        private CompletableFuture<List<AppFunctionMetadata>> getNextValidPage() {
            synchronized (mLock) {
                if (mIsClosed) {
                    return AndroidFuture.failedFuture(
                            new IllegalStateException("Session is closed"));
                }
            }

            AndroidFuture<List<SearchResult>> nextPageFuture;
            final long token = Binder.clearCallingIdentity();
            try {
                nextPageFuture = mSearchResults.getNextPage();
            } finally {
                Binder.restoreCallingIdentity(token);
            }

            return nextPageFuture
                    .thenComposeAsync(this::searchWithPackageMetadata, mExecutor)
                    .thenCompose(this::processSearchResult);
        }

        // TODO(b/473468720): Optimize top-level search
        @NonNull
        private CompletableFuture<Pair<List<SearchResult>, Map<String, AppFunctionPackageMetadata>>>
                searchWithPackageMetadata(@NonNull List<SearchResult> results) {
            Objects.requireNonNull(results);

            ArrayMap<String, CompletableFuture<AppFunctionPackageMetadata>> futuresMap =
                    new ArrayMap<>();
            for (SearchResult result : results) {
                String packageName =
                        result.getGenericDocument()
                                .getPropertyString(
                                        AppFunctionRuntimeMetadata.PROPERTY_PACKAGE_NAME);
                if (packageName == null || futuresMap.containsKey(packageName)) {
                    continue;
                }
                futuresMap.put(
                        packageName,
                        mReader.searchAppFunctionPackageMetadata(mGlobalSession, packageName));
            }

            CompletableFuture<Void> allFutures =
                    CompletableFuture.allOf(futuresMap.values().toArray(new CompletableFuture[0]));
            return allFutures.thenApply(
                    v -> {
                        ArrayMap<String, AppFunctionPackageMetadata> packageMetadataMap =
                                new ArrayMap<>();
                        for (int i = 0; i < futuresMap.size(); i++) {
                            packageMetadataMap.put(
                                    futuresMap.keyAt(i), futuresMap.valueAt(i).join());
                        }
                        return new Pair<>(results, packageMetadataMap);
                    });
        }

        @NonNull
        private CompletableFuture<List<AppFunctionMetadata>> processSearchResult(
                @NonNull
                        Pair<List<SearchResult>, Map<String, AppFunctionPackageMetadata>>
                                searchResult) {
            Objects.requireNonNull(searchResult);

            List<SearchResult> page = searchResult.first;
            Map<String, AppFunctionPackageMetadata> packageMetadataMap = searchResult.second;

            if (page.isEmpty()) {
                // No more next page
                return AndroidFuture.completedFuture(Collections.emptyList());
            }
            List<AppFunctionMetadata> metadataList = new ArrayList<>(page.size());
            for (SearchResult result : page) {
                String pkg =
                        result.getGenericDocument()
                                .getPropertyString(
                                        AppFunctionRuntimeMetadata.PROPERTY_PACKAGE_NAME);
                if (pkg == null) continue;
                AppFunctionPackageMetadata packageMetadata;
                if (packageMetadataMap.containsKey(pkg)) {
                    packageMetadata = Objects.requireNonNull(packageMetadataMap.get(pkg));
                } else {
                    packageMetadata =
                            AppFunctionPackageMetadata.create(pkg, Collections.emptyList());
                }

                AppFunctionMetadata meta =
                        mReader.buildAppFunctionMetadata(result, packageMetadata);
                if (meta != null) metadataList.add(meta);
            }

            if (metadataList.isEmpty()) {
                return getNextValidPage();
            } else {
                return AndroidFuture.completedFuture(metadataList);
            }
        }

        @PermissionManuallyEnforced
        @Override
        public void close() {
            try {
                synchronized (mLock) {
                    if (mIsClosed) {
                        return;
                    }

                    mSearchResults.close();
                    mGlobalSession.close();
                    mIsClosed = true;
                }
            } catch (Exception e) {
                Slog.w(TAG, "Fail to close AppFunctionSearchResultsImpl", e);
            }
        }
    }
}
