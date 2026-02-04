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
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.IAppFunctionExecutor;
import android.app.appfunctions.ICancellationCallback;
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.appfunctions.MultiUserDynamicAppFunctionRegistry.RegistrationScopeId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the lifecycle of app functions registered at runtime for a single user. Unregisters
 * AppFunction is it's process died.
 */
final class DynamicAppFunctionRegistry {

    private static final boolean DEBUG = Build.TYPE.equals("eng");
    private static final String TAG = "DynamicAppFuncRegistry";
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final ArrayMap<AppFunctionName, ArrayMap<RegistrationScopeId, IAppFunctionExecutor>>
            mRegistrations = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<IBinder, ArraySet<AppFunctionRegistrationId>> mExecutorToRegistrations =
            new ArrayMap<>();

    /**
     * A list of remote IAppFunctionExecutor callbacks. This is used primarily to detect when a
     * client process dies so we can clean up its registrations.
     */
    private final RemoteCallbackList<IAppFunctionExecutor> mCallbacks;

    DynamicAppFunctionRegistry(@NonNull OnBinderDeathCleanupCallback onBinderDeathCleanupCallback) {
        mCallbacks =
                new RemoteCallbackList<>() {
                    @Override
                    public void onCallbackDied(IAppFunctionExecutor callback, Object cookie) {
                        if (DEBUG) {
                            Log.d(TAG, "onCallbackDied for " + callback.toString());
                        }
                        synchronized (mLock) {
                            ArraySet<AppFunctionRegistrationId> registrationIds =
                                    mExecutorToRegistrations.remove(callback.asBinder());
                            if (registrationIds == null) {
                                return;
                            }
                            Set<AppFunctionName> unregisteredFunctionNames = new ArraySet<>();
                            for (AppFunctionRegistrationId registrationId : registrationIds) {
                                if (DEBUG) {
                                    Log.d(TAG, "Removing due to process death: " + registrationId);
                                }
                                AppFunctionName removedName = registrationId.getFunctionName();
                                if (!mRegistrations.containsKey(removedName)) {
                                    Log.w(TAG, "Couldn't find registration " + registrationId);
                                } else {
                                    Objects.requireNonNull(mRegistrations.get(removedName))
                                            .remove(registrationId.getActivityToken());
                                    if (Objects.requireNonNull(mRegistrations.get(removedName))
                                            .isEmpty()) {
                                        mRegistrations.remove(removedName);
                                        unregisteredFunctionNames.add(removedName);
                                    }
                                }
                            }
                            if (!unregisteredFunctionNames.isEmpty()) {
                                onBinderDeathCleanupCallback.run(unregisteredFunctionNames);
                            }
                        }
                    }
                };
    }

    /**
     * Registers one or more app functions, making them available for execution as a single atomic
     * operation.
     *
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifiers A list of identifiers of the app functions.
     * @param executor Executor of the app function.
     * @param scopeIds Identifiers of the source corresponding to each functionIdentifier. Activity
     *     identifier for activity scoped functions, empty class for global scoped functions.
     * @throws IllegalStateException If any of the provided app functions is already registered.
     */
    public void registerAppFunctions(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull IAppFunctionExecutor executor,
            @NonNull List<RegistrationScopeId> scopeIds) {
        if (functionIdentifiers.size() != scopeIds.size()) {
            throw new IllegalArgumentException(
                    "Scope identifiers list must be same size as function identifiers");
        }
        synchronized (mLock) {
            ArrayList<AppFunctionRegistrationId> registrationIds =
                    new ArrayList<>(functionIdentifiers.size());
            for (int index = 0; index < functionIdentifiers.size(); index++) {
                AppFunctionName name =
                        new AppFunctionName(packageName, functionIdentifiers.get(index));
                RegistrationScopeId source = scopeIds.get(index);
                if (mRegistrations.containsKey(name)
                        && Objects.requireNonNull(mRegistrations.get(name)).containsKey(source)) {
                    throw new IllegalStateException(
                            "App function already registered: "
                                    + name
                                    + " with activity token: "
                                    + source);
                }
                registrationIds.add(new AppFunctionRegistrationId(name, source));
            }

            for (AppFunctionRegistrationId registrationId : registrationIds) {
                mRegistrations.putIfAbsent(registrationId.getFunctionName(), new ArrayMap<>());
                Objects.requireNonNull(mRegistrations.get(registrationId.getFunctionName()))
                        .put(registrationId.getActivityToken(), executor);

                if (!mExecutorToRegistrations.containsKey(executor.asBinder())) {
                    mExecutorToRegistrations.put(executor.asBinder(), new ArraySet<>());
                    mCallbacks.register(executor);
                }
                Objects.requireNonNull(mExecutorToRegistrations.get(executor.asBinder()))
                        .add(registrationId);

                if (DEBUG) {
                    Log.d(TAG, "registerAppFunction with ID:" + registrationId);
                }
            }
        }
    }

    /**
     * Unregisters one or more app functions, making them unavailable for execution.
     *
     * <p>This removes the association between the function identifiers and the client's execution
     * session. If a function is not currently registered, the request to unregister for it will be
     * silently ignored.
     *
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifiers List of identifier of the app functions.
     * @param executor Executor of the app function.
     * @param scopeIds Identifiers of the registration source corresponding to each
     *     functionIdentifier. Activity identifier for activity scoped functions, empty class for
     *     global scoped functions, empty class for global scoped functions.
     */
    public void unregisterAppFunctions(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull IAppFunctionExecutor executor,
            @NonNull List<RegistrationScopeId> scopeIds) {
        if (functionIdentifiers.size() != scopeIds.size()) {
            throw new IllegalArgumentException(
                    "Scope identifiers list must be same size as function identifiers");
        }
        synchronized (mLock) {
            for (int index = 0; index < functionIdentifiers.size(); index++) {
                AppFunctionName name =
                        new AppFunctionName(packageName, functionIdentifiers.get(index));
                RegistrationScopeId activityToken = scopeIds.get(index);
                AppFunctionRegistrationId registrationId =
                        new AppFunctionRegistrationId(name, activityToken);

                // Ensure the registration being removed actually belongs to the calling
                // executor.
                if (!mRegistrations.containsKey(name)
                        || !mRegistrations.get(name).containsKey(activityToken)) {
                    if (DEBUG) {
                        Log.d(TAG, "Skip unregistering function with ID:" + registrationId);
                    }
                    continue;
                }
                IAppFunctionExecutor registeredExecutor =
                        mRegistrations.get(name).get(activityToken);
                if (registeredExecutor == null
                        || !registeredExecutor.asBinder().equals(executor.asBinder())) {
                    if (DEBUG) {
                        Log.d(TAG, "Skip unregistering function with ID:" + registrationId);
                    }
                    continue;
                }
                Objects.requireNonNull(mRegistrations.get(name)).remove(activityToken);
                if (Objects.requireNonNull(mRegistrations.get(name)).isEmpty()) {
                    mRegistrations.remove(name);
                }

                ArraySet<AppFunctionRegistrationId> executorRegistrations =
                        mExecutorToRegistrations.get(executor.asBinder());
                if (executorRegistrations != null) {
                    executorRegistrations.remove(registrationId);
                    if (executorRegistrations.isEmpty()) {
                        mExecutorToRegistrations.remove(executor.asBinder());
                        // This was the last registration for this executor.
                        mCallbacks.unregister(executor);
                    }
                }
                if (DEBUG) {
                    Log.d(TAG, "unregisterAppFunction with ID:" + registrationId);
                }
            }
        }
    }

    /**
     * Executes an app function. Calls {@link
     * SafeOneTimeExecuteAppFunctionCallback#onError(AppFunctionException)} if the function was not
     * registered.
     *
     * @param request Request to execute.
     * @param safeExecuteAppFunctionCallback Callback to report results to.
     * @param cancellationTransport Transport to report cancellation to.
     */
    public void executeAppFunction(
            @NonNull ExecuteAppFunctionRequest request,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            @NonNull ICancellationSignal cancellationTransport) {
        AppFunctionName name =
                new AppFunctionName(
                        request.getTargetPackageName(), request.getFunctionIdentifier());
        RegistrationScopeId sourceId = new RegistrationScopeId(request.getActivityId());
        if (DEBUG) {
            Log.d(TAG, "executeAppFunction with ID:" + name + " with activity token: " + sourceId);
        }
        IAppFunctionExecutor executor = null;
        synchronized (mLock) {
            if (mRegistrations.containsKey(name)) {
                executor = Objects.requireNonNull(mRegistrations.get(name)).get(sourceId);
            }
        }

        if (executor == null) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_DISABLED,
                            "Function with ID: "
                                    + request.getFunctionIdentifier()
                                    + " is disabled"));
            return;
        }

        try {
            safeExecuteAppFunctionCallback.attachOnDeathListener(executor.asBinder());
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
     * Checks if dynamic app function is registered.
     *
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @return True if the app function is registered, false otherwise.
     */
    public boolean isAppFunctionRegistered(String packageName, String functionIdentifier) {
        synchronized (mLock) {
            return mRegistrations.containsKey(new AppFunctionName(packageName, functionIdentifier));
        }
    }

    static class AppFunctionRegistrationId {
        @Nullable private final RegistrationScopeId mActivityToken;

        @NonNull private final AppFunctionName mName;

        AppFunctionRegistrationId(
                @NonNull AppFunctionName name, @Nullable RegistrationScopeId activityToken) {
            mName = name;
            mActivityToken = activityToken;
        }

        @NonNull
        AppFunctionName getFunctionName() {
            return mName;
        }

        @Nullable
        RegistrationScopeId getActivityToken() {
            return mActivityToken;
        }

        @Override
        public String toString() {
            return "AppFunctionRegistrationId{" + mName + ":activityToken(" + mActivityToken + ")}";
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName, mActivityToken);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AppFunctionRegistrationId)) {
                return false;
            }
            if (this == o) {
                return true;
            }
            AppFunctionRegistrationId that = (AppFunctionRegistrationId) o;
            return Objects.equals(mName, that.mName)
                    && Objects.equals(mActivityToken, that.mActivityToken);
        }
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

    /**
     * A callback interface to be invoked when dynamic app functions are unregistered due to the
     * process being killed.
     */
    interface OnBinderDeathCleanupCallback {
        void run(Set<AppFunctionName> unregisteredFunctionNames);
    }
}
