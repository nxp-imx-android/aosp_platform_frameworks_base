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
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appfunctions.IObserveAppFunctionChangesCallback;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.os.IBinder;
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
    private final Object mInternalCallbacksLock = new Object();

    @GuardedBy("mInternalCallbacksLock")
    @NonNull
    private final Set<InternalCallbackWrapper> mInternalCallbacks = new ArraySet<>();

    @NonNull private final ExecutorService mExecutor;

    private final int mMetadataChangeDebounceMs;
    private final ScheduledExecutorService mDebounceExecutor;
    private final Object mDebounceLock = new Object();

    @GuardedBy("mDebounceLock")
    @Nullable
    private ScheduledFuture<?> mDebounceFuture;

    @GuardedBy("mDebounceLock")
    @NonNull
    private final Set<String> mPendingPackageChanges = new ArraySet<>();

    InternalObserverCallbackRouter(
            @NonNull ExecutorService executor, @NonNull ServiceConfig serviceConfig) {
        this(
                executor,
                serviceConfig,
                Executors.newSingleThreadScheduledExecutor(
                        new NamedThreadFactory("DebounceExecutor")));
    }

    @VisibleForTesting
    InternalObserverCallbackRouter(
            @NonNull ExecutorService executor,
            @NonNull ServiceConfig serviceConfig,
            @NonNull ScheduledExecutorService debounceExecutor) {
        mExecutor = requireNonNull(executor);
        mMetadataChangeDebounceMs =
                serviceConfig.getAppFunctionMetadataChangeDebounceMilliseconds();
        mDebounceExecutor = debounceExecutor;
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
                        mDebounceExecutor.schedule(
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
        Set<InternalCallbackWrapper> callbacksToNotify;
        synchronized (mInternalCallbacksLock) {
            // Make a copy before iterating over the callbacks to prevent deadlocks.
            callbacksToNotify = new ArraySet<>(mInternalCallbacks);
        }
        for (InternalCallbackWrapper internalCallbackWrapper : callbacksToNotify) {
            Set<String> filteredPackages = new ArraySet<>();
            for (String changedPackageName : changedPackageNames) {
                if (internalCallbackWrapper.isObservedPackage(changedPackageName)) {
                    filteredPackages.add(changedPackageName);
                }
            }
            if (!filteredPackages.isEmpty()) {
                try {
                    internalCallbackWrapper.mInternalCallback.onPackagesChanged(
                            new ArrayList<>(filteredPackages));
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to execute callback#onPackagesChanged.", e);
                }
            }
        }
    }

    private void onAppFunctionStatesChanged(@NonNull Set<AppFunctionName> changedFunctionNames) {
        Set<InternalCallbackWrapper> callbacksToNotify;
        synchronized (mInternalCallbacksLock) {
            // Make a copy before iterating over the callbacks to prevent deadlocks.
            callbacksToNotify = new ArraySet<>(mInternalCallbacks);
        }
        for (InternalCallbackWrapper internalCallbackWrapper : callbacksToNotify) {
            Set<AppFunctionName> filteredNames = new ArraySet<>();
            for (AppFunctionName functionName : changedFunctionNames) {
                if (internalCallbackWrapper.isObservedFunction(functionName)) {
                    filteredNames.add(functionName);
                }
            }
            if (!filteredNames.isEmpty()) {
                try {
                    internalCallbackWrapper.mInternalCallback.onAppFunctionStatesChanged(
                            new ArrayList<>(filteredNames));
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to execute callback#onAppFunctionStatesChanged.", e);
                }
            }
        }
    }

    void shutDown() {
        mExecutor.shutdown();
        mDebounceExecutor.shutdown();
    }

    void addCallback(
            @NonNull IObserveAppFunctionChangesCallback callbackToAdd,
            @NonNull AppFunctionSearchSpec searchSpec)
            throws RemoteException {
        synchronized (mInternalCallbacksLock) {
            mInternalCallbacks.add(
                    new InternalCallbackWrapper(
                            callbackToAdd,
                            searchSpec.getObservedPackageNames(),
                            searchSpec.getObservedAppFunctions(),
                            attachDeathRecipient(callbackToAdd)));
        }
    }

    void removeCallback(@NonNull IObserveAppFunctionChangesCallback callbackToRemove) {
        synchronized (mInternalCallbacksLock) {
            for (InternalCallbackWrapper callbackWrapper : mInternalCallbacks) {
                if (Objects.equals(
                        callbackWrapper.mInternalCallback.asBinder(),
                        callbackToRemove.asBinder())) {
                    mInternalCallbacks.remove(callbackWrapper);
                    callbackWrapper.unlinkDeathRecipient();
                    break;
                }
            }
        }
    }

    private IBinder.DeathRecipient attachDeathRecipient(
            @NonNull IObserveAppFunctionChangesCallback observeAppFunctionsCallback)
            throws RemoteException {
        IBinder remoteBinder = observeAppFunctionsCallback.asBinder();
        IBinder.DeathRecipient deathRecipient =
                new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        removeCallback(observeAppFunctionsCallback);
                        remoteBinder.unlinkToDeath(this, 0);
                    }
                };

        remoteBinder.linkToDeath(deathRecipient, 0);

        return deathRecipient;
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

    /** Wraps an {@link IObserveAppFunctionChangesCallback} with its observation filters. */
    static class InternalCallbackWrapper {
        @NonNull private final IObserveAppFunctionChangesCallback mInternalCallback;
        @Nullable private final Set<AppFunctionName> mObservedAppFunctions;
        @Nullable private final Set<String> mObservedPackages;
        @NonNull private final IBinder.DeathRecipient mDeathRecipient;

        /**
         * Creates an instance of {@link InternalCallbackWrapper}.
         *
         * @param internalCallback The callback to notify with updates to observed packages and app
         *     functions
         * @param observedPackages The set of packages to receive updates for. If null, all packages
         *     and functions are accepted.
         * @param observedAppFunctions The set of app functions to receive updates for. If null, all
         *     app functions matching {@code observedPackages} are accepted.
         * @param deathRecipient The listener linked to the callback's death. Used to release the
         *     memory when the callback is no longer used.
         */
        InternalCallbackWrapper(
                @NonNull IObserveAppFunctionChangesCallback internalCallback,
                @Nullable Set<String> observedPackages,
                @Nullable Set<AppFunctionName> observedAppFunctions,
                @NonNull IBinder.DeathRecipient deathRecipient) {
            mInternalCallback = requireNonNull(internalCallback);
            mObservedPackages = observedPackages;
            mObservedAppFunctions = observedAppFunctions;
            mDeathRecipient = deathRecipient;
        }

        private void unlinkDeathRecipient() {
            mInternalCallback.asBinder().unlinkToDeath(mDeathRecipient, 0);
        }

        private boolean isObservedPackage(String packageName) {
            return mObservedPackages == null || mObservedPackages.contains(packageName);
        }

        private boolean isObservedFunction(AppFunctionName functionName) {
            if (mObservedAppFunctions == null) {
                return isObservedPackage(functionName.getPackageName());
            }
            return mObservedAppFunctions.contains(functionName);
        }
    }
}
