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
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionRuntimeMetadata;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.IAppFunctionExecutor;
import android.app.appfunctions.ICancellationCallback;
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

/**
 * Manages the lifecycle of app functions registered at runtime for a single user. Unregisters
 * AppFunction is it's process died.
 */
final class DynamicAppFunctionRegistry {

    private static final boolean DEBUG = false;
    private static final String TAG = "DynamicAppFuncRegistry";

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final ArrayMap<String, IAppFunctionExecutor> mRegistrations = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<IBinder, ArraySet<String>> mExecutorToRegistrations = new ArrayMap<>();

    /**
     * A list of remote IAppFunctionExecutor callbacks. This is used primarily to detect when a
     * client process dies so we can clean up its registrations.
     */
    private final RemoteCallbackList<IAppFunctionExecutor> mCallbacks =
            new RemoteCallbackList<>() {
                @Override
                public void onCallbackDied(IAppFunctionExecutor callback, Object cookie) {
                    if (DEBUG) {
                        Log.d(TAG, "onCallbackDied for " + callback.toString());
                    }
                    synchronized (mLock) {
                        ArraySet<String> registrationIds =
                                mExecutorToRegistrations.remove(callback.asBinder());
                        if (registrationIds == null) {
                            return;
                        }
                        for (String id : registrationIds) {
                            if (DEBUG) {
                                Log.d(TAG, "Removing due to process death: " + id);
                            }
                            mRegistrations.remove(id);
                        }
                    }
                }
            };

    /**
     * Registers an app function with the registry.
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @param executor Executor of the app function.
     * @throws IllegalStateException If the app function is already registered.
     */
    public void registerAppFunction(
            @NonNull String packageName,
            @NonNull String functionIdentifier,
            @NonNull IAppFunctionExecutor executor) {
        String registrationId = getRegistrationId(packageName, functionIdentifier);
        if (DEBUG) {
            Log.d(TAG, "registerAppFunction with ID:" + registrationId);
        }
        synchronized (mLock) {
            if (mRegistrations.containsKey(registrationId)) {
                throw new IllegalStateException(
                        "App function already registered for ID: " + registrationId);
            }
            mRegistrations.put(registrationId, executor);

            if (!mExecutorToRegistrations.containsKey(executor.asBinder())) {
                mExecutorToRegistrations.put(executor.asBinder(), new ArraySet<>());
                mCallbacks.register(executor);
            }
            mExecutorToRegistrations.get(executor.asBinder()).add(registrationId);
        }
    }

    /**
     * Unregisters an app function from the registry. This method is no-op if app function was not
     * registered or registered with a different executor.
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @param executor Executor of the app function.
     */
    public void unregisterAppFunction(
            @NonNull String packageName,
            @NonNull String functionIdentifier,
            @NonNull IAppFunctionExecutor executor) {
        String registrationId = getRegistrationId(packageName, functionIdentifier);
        if (DEBUG) {
            Log.d(TAG, "unregisterAppFunction with ID:" + registrationId);
        }
        synchronized (mLock) {
            // Ensure the registration being removed actually belongs to the calling
            // executor.
            IAppFunctionExecutor registeredExecutor = mRegistrations.get(registrationId);
            if (registeredExecutor == null
                    || !registeredExecutor.asBinder().equals(executor.asBinder())) {
                return;
            }

            mRegistrations.remove(registrationId);

            ArraySet<String> executorRegistrations =
                    mExecutorToRegistrations.get(executor.asBinder());
            if (executorRegistrations != null) {
                executorRegistrations.remove(registrationId);
                if (executorRegistrations.isEmpty()) {
                    mExecutorToRegistrations.remove(executor.asBinder());
                    // This was the last registration for this executor.
                    mCallbacks.unregister(executor);
                }
            }
        }
    }

    /**
     * Executes an app function. Calls
     * {@link SafeOneTimeExecuteAppFunctionCallback#onError(AppFunctionException)} if the function
     * was not registered.
     * @param request Request to execute.
     * @param safeExecuteAppFunctionCallback Callback to report results to.
     * @param cancellationTransport Transport to report cancellation to.
     */
    public void executeAppFunction(
            @NonNull ExecuteAppFunctionRequest request,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            @NonNull ICancellationSignal cancellationTransport) {
        String registrationId =
                getRegistrationId(request.getTargetPackageName(), request.getFunctionIdentifier());
        if (DEBUG) {
            Log.d(TAG, "executeAppFunction with ID:" + registrationId);
        }
        IAppFunctionExecutor executor;
        synchronized (mLock) {
            executor = mRegistrations.get(registrationId);
        }

        if (executor == null) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_DISABLED,
                            "Function with ID: " + request.getFunctionIdentifier() + " is disabled"
                    )
            );
            return;
        }

        // TODO(b/447127837): report error in case caller died while executing app function
        try {
            executor.execute(
                    request,
                    getICancellationCallback(cancellationTransport),
                    safeExecuteAppFunctionCallback.wrapToExecutionCallback());
        } catch (RemoteException e) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                            "Error executing app function: " + e.getMessage()));
        }
    }

    /**
     * Checks if dynamic app function is enabled.
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @return True if the app function is enabled, false otherwise.
     */
    public boolean isAppFunctionEnabled(String packageName, String functionIdentifier) {
        synchronized (mLock) {
            return mRegistrations.containsKey(getRegistrationId(packageName, functionIdentifier));
        }
    }

    // TODO(b/447127837): switch to function name once submitted, use per package mappings and locks
    private String getRegistrationId(String packageName, String functionIdentifier) {
        return AppFunctionRuntimeMetadata.getDocumentIdForAppFunction(
                packageName, functionIdentifier);
    }

    @NonNull
    private static ICancellationCallback getICancellationCallback(
            @NonNull ICancellationSignal localCancelTransport) {
        CancellationSignal cancellationSignal =
                CancellationSignal.fromTransport(localCancelTransport);
        ICancellationCallback cancellationCallback =
                new ICancellationCallback.Stub() {
                    @Override
                    public void sendCancellationTransport(
                            @NonNull ICancellationSignal cancellationTransport) {
                        cancellationSignal.setRemote(cancellationTransport);
                    }
                };
        return cancellationCallback;
    }
}
