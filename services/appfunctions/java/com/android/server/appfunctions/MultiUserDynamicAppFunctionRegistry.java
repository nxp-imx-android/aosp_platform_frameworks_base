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
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.app.appfunctions.IAppFunctionExecutor;
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback;
import android.os.Build;
import android.os.ICancellationSignal;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.SystemService;

/**
 * Manages the lifecycle of app functions registered at runtime cross user. Creates a per user
 * {@link DynamicAppFunctionRegistry} when user is unlocked, and deletes it when the user is
 * stopped. Redirects calls to the per user {@link DynamicAppFunctionRegistry}.
 */
public final class MultiUserDynamicAppFunctionRegistry {

    private static final boolean DEBUG = Build.TYPE.equals("eng");

    private static final String TAG = "DynamicAppFuncRegistry";

    private static MultiUserDynamicAppFunctionRegistry sInstance;

    /** Gets a singleton instance. */
    public static synchronized MultiUserDynamicAppFunctionRegistry getInstance() {
        if (sInstance == null) {
            sInstance = new MultiUserDynamicAppFunctionRegistry();
        }
        return sInstance;
    }

    private final Object mCrossUserLock = new Object();

    @GuardedBy("mCrossUserLock")
    private SparseArray<DynamicAppFunctionRegistry> mPerUserRegistrations = new SparseArray<>();

    /**
     * Called after an existing user is unlocked.
     *
     * <p>This will create registry for this {@code user}.
     */
    public void onUserUnlocked(@NonNull SystemService.TargetUser user) {
        maybePrintDebugLog("onUserUnlocked: " + user.getUserIdentifier());
        synchronized (mCrossUserLock) {
            if (!mPerUserRegistrations.contains(user.getUserIdentifier())) {
                mPerUserRegistrations.put(
                        user.getUserIdentifier(), new DynamicAppFunctionRegistry());
            }
        }
    }

    /**
     * Called when an existing user was stopped.
     *
     * <p>This will delete cache for {@code user}.
     */
    public void onUserStopped(@NonNull SystemService.TargetUser user) {
        maybePrintDebugLog("onUserStopped: " + user.getUserIdentifier());
        synchronized (mCrossUserLock) {
            mPerUserRegistrations.remove(user.getUserIdentifier());
        }
    }

    /**
     * Registers an app function with the registry.
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @param session Executor of the app function.
     * @param userHandle Handle of the user to register the app function for.
     * @throws IllegalStateException If the app function is already registered or user was not
     *      unlocked.
     */
    public void registerAppFunction(
            String packageName,
            String functionIdentifier,
            IAppFunctionExecutor session,
            UserHandle userHandle) {
        maybePrintDebugLog("registerAppFunction: " + packageName + "/" + functionIdentifier);
        getPerUserRegistry(userHandle)
                .registerAppFunction(packageName, functionIdentifier, session);
    }

    /**
     * Unregisters app function with the registry.
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @param session Executor of the app function.
     * @param userHandle Handle of the user to register the app function for.
     * @throws IllegalStateException If {@code userHandle} was not unlocked.
     */
    public void unregisterAppFunction(
            String packageName,
            String functionIdentifier,
            IAppFunctionExecutor session,
            UserHandle userHandle) {
        maybePrintDebugLog("unregisterAppFunction: " + packageName + "/" + functionIdentifier);
        getPerUserRegistry(userHandle)
                .unregisterAppFunction(packageName, functionIdentifier, session);
    }

    /**
     * Checks if dynamic app function is registered.
     * @param packageName Name of the package containing the app function.
     * @param functionIdentifier Identifier of the app function.
     * @param userHandle Handle of the user to register the app function for.
     * @return True if the app function is registered, false otherwise.
     * @throws IllegalStateException If the user was not unlocked.
     */
    public boolean isAppFunctionRegistered(
            String packageName, String functionIdentifier, UserHandle userHandle) {
        return getPerUserRegistry(userHandle)
                .isAppFunctionRegistered(packageName, functionIdentifier);
    }

    /**
     * Executes an app function.
     * @param request Request to execute.
     * @param safeExecuteAppFunctionCallback Callback to report results to.
     * @param cancellationTransport The cancellation signal.
     * @throws IllegalStateException If the user was not unlocked.
     */
    public void executeAppFunction(
            ExecuteAppFunctionAidlRequest request,
            SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            ICancellationSignal cancellationTransport) {
        getPerUserRegistry(request.getUserHandle())
                .executeAppFunction(
                        request.getClientRequest(),
                        safeExecuteAppFunctionCallback,
                        cancellationTransport);
    }

    @NonNull
    private DynamicAppFunctionRegistry getPerUserRegistry(UserHandle userHandle) {
        synchronized (mCrossUserLock) {
            if (!mPerUserRegistrations.contains(userHandle.getIdentifier())) {
                throw new IllegalStateException(
                        "User " + userHandle.getIdentifier() + " has not been unlocked yet");
            }
            return mPerUserRegistrations.get(userHandle.getIdentifier());
        }
    }

    private static void maybePrintDebugLog(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }
}
