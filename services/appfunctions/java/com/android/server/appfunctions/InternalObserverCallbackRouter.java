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
import android.os.ParcelableException;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

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

    InternalObserverCallbackRouter(@NonNull ExecutorService executor) {
        mExecutor = requireNonNull(executor);
    }

    public void onDocumentChanged(@NonNull DocumentChangeInfo documentChangeInfo) {
        Runnable runnable =
                () -> {
                    if (isAppFunctionStaticMetadataSchema(documentChangeInfo.getSchemaName())) {
                        Set<AppFunctionName> changedFunctions = new ArraySet<>();
                        for (String functionId : documentChangeInfo.getChangedDocumentIds()) {
                            try {
                                AppFunctionName functionName =
                                        AppFunctionName.fromQualifiedId(functionId);
                                changedFunctions.add(functionName);
                            } catch (IllegalArgumentException e) {
                                // Incorrect function id format, skip document
                            }
                        }
                        onAppFunctionsChanged(changedFunctions);
                    } else {
                        // If the schema is for a top-level document and not
                        // a function, treat it as a package change.
                        String changedPackageName =
                                getPackageNameFromSchemaName(documentChangeInfo.getSchemaName());
                        if (changedPackageName != null) {
                            onPackagesChanged(Set.of(changedPackageName));
                        }
                    }
                };
        try {
            var unused = mExecutor.submit(runnable);
        } catch (RejectedExecutionException ex) {
            Slog.w(TAG, "Failed to run onDocumentChanged due to executor shutdown.", ex);
        }
    }

    public void onSchemaChanged(@NonNull SchemaChangeInfo schemaChangeInfo) {
        Runnable runnable =
                () -> {
                    Set<String> changedPackageNames = new ArraySet<>();
                    for (String changedSchemaName : schemaChangeInfo.getChangedSchemaNames()) {
                        String changedPackageName = getPackageNameFromSchemaName(changedSchemaName);
                        if (changedPackageName != null) {
                            changedPackageNames.add(changedPackageName);
                        }
                    }
                    onPackagesChanged(changedPackageNames);
                };
        try {
            var unused = mExecutor.submit(runnable);
        } catch (RejectedExecutionException ex) {
            Slog.w(TAG, "Failed to run onSchemaChanged due to executor shutdown.", ex);
        }
    }

    private void onPackagesChanged(@NonNull Set<String> changedPackageNames) {
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

    private void onAppFunctionsChanged(@NonNull Set<AppFunctionName> changedFunctionNames) {
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
                    internalCallbackWrapper.mInternalCallback.onAppFunctionsChanged(
                            new ArrayList<>(filteredNames));
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to execute callback#onAppFunctionsChanged.", e);
                }
            }
        }
    }

    void shutDown() {
        mExecutor.shutdown();
    }

    void addCallback(
            @NonNull IObserveAppFunctionChangesCallback callbackToAdd,
            @NonNull AppFunctionSearchSpec searchSpec) {
        synchronized (mInternalCallbacksLock) {
            mInternalCallbacks.add(
                    new InternalCallbackWrapper(
                            callbackToAdd,
                            searchSpec.getObservedPackageNames(),
                            searchSpec.getObservedAppFunctions()));
        }
        attachOnDeathListener(callbackToAdd);
    }

    void removeCallback(@NonNull IObserveAppFunctionChangesCallback callbackToRemove) {
        synchronized (mInternalCallbacksLock) {
            mInternalCallbacks.removeIf(
                    internalCallbackWrapper ->
                            internalCallbackWrapper.mInternalCallback == callbackToRemove);
        }
    }

    private void attachOnDeathListener(
            @NonNull IObserveAppFunctionChangesCallback observeAppFunctionsCallback) {
        IBinder remoteBinder = observeAppFunctionsCallback.asBinder();
        IBinder.DeathRecipient deathRecipient =
                new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        removeCallback(observeAppFunctionsCallback);
                        remoteBinder.unlinkToDeath(this, 0);
                    }
                };
        try {
            remoteBinder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            try {
                observeAppFunctionsCallback.onRegistrationError(new ParcelableException(e));
            } catch (RemoteException ex) {
                Slog.e(TAG, "Failed to execute callback#onRegistrationError.", ex);
            }
        }
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

        /**
         * Creates an instance of {@link InternalCallbackWrapper}.
         *
         * @param internalCallback The callback to notify with updates to observed packages and app
         *     functions
         * @param observedPackages The set of packages to receive updates for. If null, all packages
         *     and functions are accepted.
         * @param observedAppFunctions The set of app functions to receive updates for. If null, all
         *     app functions matching {@code observedPackages} are accepted.
         */
        InternalCallbackWrapper(
                @NonNull IObserveAppFunctionChangesCallback internalCallback,
                @Nullable Set<String> observedPackages,
                @Nullable Set<AppFunctionName> observedAppFunctions) {
            mInternalCallback = requireNonNull(internalCallback);
            mObservedPackages = observedPackages;
            mObservedAppFunctions = observedAppFunctions;
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
