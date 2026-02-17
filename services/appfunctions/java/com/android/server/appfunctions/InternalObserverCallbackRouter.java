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

import static android.app.appfunctions.AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appfunctions.AppFunctionAidlSearchSpec;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appfunctions.IObserveAppFunctionChangesCallback;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Routes app function change notifications to registered callbacks.
 *
 * <p>This class receives generic change notifications from AppSearch and dispatches them to the
 * appropriate {@link IObserveAppFunctionChangesCallback} instances, filtering based on the {@link
 * AppFunctionSearchSpec} each callback was registered with.
 */
class InternalObserverCallbackRouter {
    private static final String TAG = InternalObserverCallbackRouter.class.getSimpleName();

    // TODO: b/481984551 - Handle callbacks for frozen processes.
    @NonNull
    private final RemoteCallbackList<IObserveAppFunctionChangesCallback> mInternalCallbacks =
            new RemoteCallbackList<>();

    private final int mMetadataChangeDebounceMs;
    private final ScheduledExecutorService mExecutor;
    private final Object mDebounceLock = new Object();

    @GuardedBy("mDebounceLock")
    @Nullable
    private ScheduledFuture<?> mDebounceFuture;

    @GuardedBy("mDebounceLock")
    @NonNull
    private final Set<String> mPendingPackageChanges = new ArraySet<>();

    InternalObserverCallbackRouter(@NonNull ServiceConfig serviceConfig) {
        this(
                serviceConfig,
                Executors.newSingleThreadScheduledExecutor(
                        new NamedThreadFactory("AppFunctionsObserverRouter")));
    }

    @VisibleForTesting
    InternalObserverCallbackRouter(
            @NonNull ServiceConfig serviceConfig, @NonNull ScheduledExecutorService executor) {
        mMetadataChangeDebounceMs =
                serviceConfig.getAppFunctionMetadataChangeDebounceMilliseconds();
        mExecutor = executor;
    }

    public void onDocumentChanged(@NonNull DocumentChangeInfo documentChangeInfo) {
        String changedPackageName =
                getPackageNameFromSchemaName(documentChangeInfo.getSchemaName());
        if (changedPackageName != null) {
            schedulePackageLevelChange(Set.of(changedPackageName));
        }
    }

    public void onSchemaChanged(@NonNull SchemaChangeInfo schemaChangeInfo) {
        Set<String> changedPackageNames = new ArraySet<>();
        for (String changedSchemaName : schemaChangeInfo.getChangedSchemaNames()) {
            String changedPackageName = getPackageNameFromSchemaName(changedSchemaName);
            if (changedPackageName != null) {
                changedPackageNames.add(changedPackageName);
            }
        }
        schedulePackageLevelChange(changedPackageNames);
    }

    /**
     * Notifies observers of a change to the enabled state of an app function.
     *
     * <p>This is used for the runtime enabled state changes, which do not originate from app
     * search.
     */
    // TODO(b/438413081): Consider batching enabled state updates. Their frequency can be high for
    //  example in case of dynamic app functions that are registered/unregistered in a composable.
    public void onEnabledStatesChanged(@NonNull Set<AppFunctionName> changedFunctionNames) {
        try {
            var unused = mExecutor.submit(() -> onAppFunctionStatesChanged(changedFunctionNames));
        } catch (RejectedExecutionException ex) {
            Slog.w(TAG, "Failed to run onEnabledStateChanged due to executor shutdown.", ex);
        }
    }

    private void schedulePackageLevelChange(@NonNull Set<String> changedPackageNames) {
        if (changedPackageNames.isEmpty()) {
            return;
        }

        synchronized (mDebounceLock) {
            if (mDebounceFuture != null) {
                mDebounceFuture.cancel(false);
            }
            mPendingPackageChanges.addAll(changedPackageNames);

            Runnable scheduledRunnable =
                    () -> {
                        Set<String> packagesToNotify;
                        synchronized (mDebounceLock) {
                            packagesToNotify = new ArraySet<>(mPendingPackageChanges);
                            mPendingPackageChanges.clear();
                            mDebounceFuture = null;
                        }
                        if (!packagesToNotify.isEmpty()) {
                            onPackageLevelChange(packagesToNotify);
                        }
                    };

            try {
                mDebounceFuture =
                        mExecutor.schedule(
                                scheduledRunnable,
                                mMetadataChangeDebounceMs,
                                TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) {
                Slog.w(
                        TAG,
                        "Failed to schedule package level change due to executor shutdown.",
                        ex);
            }
        }
    }

    private void onPackageLevelChange(@NonNull Set<String> changedPackageNames) {
        if (changedPackageNames.isEmpty()) {
            return;
        }
        mInternalCallbacks.broadcast(
                (callback, searchSpec) -> {
                    try {
                        callback.onPackagesChanged(new ArrayList<>(changedPackageNames));
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Failed to execute callback#onPackagesChanged.", e);
                    }
                });
    }

    private void onAppFunctionStatesChanged(@NonNull Set<AppFunctionName> changedFunctionNames) {
        if (changedFunctionNames.isEmpty()) {
            return;
        }
        mInternalCallbacks.broadcast(
                (callback, searchSpec) -> {
                    try {
                        callback.onAppFunctionStatesChanged(new ArrayList<>(changedFunctionNames));
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Failed to execute callback#onAppFunctionStatesChanged.", e);
                    }
                });
    }

    void shutDown() {
        mInternalCallbacks.kill();
        mExecutor.shutdown();
    }

    void addCallback(
            @NonNull IObserveAppFunctionChangesCallback callbackToAdd,
            @NonNull AppFunctionAidlSearchSpec searchSpec) {
        mInternalCallbacks.register(callbackToAdd, searchSpec);
    }

    void removeCallback(@NonNull IObserveAppFunctionChangesCallback callbackToRemove) {
        mInternalCallbacks.unregister(callbackToRemove);
    }

    // AppSearch schema names for app functions are expected to be in the format
    // "schemaType-packageName".
    @Nullable
    private String getPackageNameFromSchemaName(String schemaName) {
        int indexBeforePackageName = schemaName.lastIndexOf('-');
        if (indexBeforePackageName == -1) {
            return null;
        }
        try {
            return schemaName.substring(indexBeforePackageName + 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private boolean isAppFunctionStaticMetadataSchema(String schemaName) {
        return schemaName.startsWith(STATIC_SCHEMA_TYPE);
    }
}
