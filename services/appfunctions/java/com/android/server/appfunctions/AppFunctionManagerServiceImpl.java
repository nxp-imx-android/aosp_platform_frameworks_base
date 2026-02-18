/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.app.appfunctions.AppFunctionException.ERROR_SYSTEM_ERROR;
import static android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_UNREQUESTABLE;
import static android.app.appfunctions.AppFunctionManager.ACTION_REQUEST_APP_FUNCTION_ACCESS;
import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_DEFAULT;
import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_ENABLED;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_METADATA_DB;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_NAMESPACE;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB;
import static android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE;

import static com.android.server.appfunctions.AppFunctionExecutors.THREAD_POOL_EXECUTOR;
import static com.android.server.appfunctions.CallerValidator.CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_HAS_PERMISSION;
import static com.android.server.appfunctions.CallerValidator.CAN_EXECUTE_APP_FUNCTIONS_DENIED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.WorkerThread;
import android.app.IUriGrantsManager;
import android.app.appfunctions.AppFunctionAccessServiceInterface;
import android.app.appfunctions.AppFunctionActivityId;
import android.app.appfunctions.AppFunctionActivityStateList;
import android.app.appfunctions.AppFunctionAidlSearchSpec;
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionManager;
import android.app.appfunctions.AppFunctionManagerHelper;
import android.app.appfunctions.AppFunctionManagerHelper.AppFunctionNotFoundException;
import android.app.appfunctions.AppFunctionName;
import android.app.appfunctions.AppFunctionRuntimeMetadata;
import android.app.appfunctions.AppFunctionSearchSpec;
import android.app.appfunctions.AppFunctionStateList;
import android.app.appfunctions.AppFunctionStaticMetadataHelper;
import android.app.appfunctions.AppFunctionUriGrant;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.app.appfunctions.IAppFunctionExecutor;
import android.app.appfunctions.IAppFunctionManager;
import android.app.appfunctions.IAppFunctionSearchResultCallback;
import android.app.appfunctions.IAppFunctionSearchResults;
import android.app.appfunctions.IAppFunctionService;
import android.app.appfunctions.ICancellationCallback;
import android.app.appfunctions.IExecuteAppFunctionCallback;
import android.app.appfunctions.IGetAppFunctionActivityStatesCallback;
import android.app.appfunctions.IGetAppFunctionStatesCallback;
import android.app.appfunctions.IIsAppFunctionEnabledCallback;
import android.app.appfunctions.IObserveAppFunctionChangesCallback;
import android.app.appfunctions.IOnAppFunctionAccessChangeListener;
import android.app.appfunctions.ISearchAppFunctionsCallback;
import android.app.appfunctions.ISetAppFunctionEnabledCallback;
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchManager.SearchContext;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelableException;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.DumpUtils;
import com.android.server.IoThread;
import com.android.server.SystemService.TargetUser;
import com.android.server.appfunctions.MultiUserDynamicAppFunctionRegistry.RegistrationScopeId;
import com.android.server.appfunctions.allowlist.AppFunctionAllowlistReader;
import com.android.server.appinteraction.AppInteractionService;
import com.android.server.uri.UriGrantsManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Implementation of the AppFunctionManagerService. */
public class AppFunctionManagerServiceImpl extends IAppFunctionManager.Stub {
    private static final String TAG = AppFunctionManagerServiceImpl.class.getSimpleName();

    private final RemoteServiceCaller<IAppFunctionService> mRemoteServiceCaller;
    private final CallerValidator mCallerValidator;
    private final ServiceHelper mInternalServiceHelper;
    private final ServiceConfig mServiceConfig;
    private final Context mContext;
    private final Map<String, Object> mLocks = new WeakHashMap<>();
    private final AppFunctionsLoggerWrapper mLoggerWrapper;
    private final PackageManagerInternal mPackageManagerInternal;
    // Not Guarded by lock since this is only accessed in main thread.
    private final SparseArray<PackageMonitor> mPackageMonitors = new SparseArray<>();

    private final AppFunctionAccessServiceInterface mAppFunctionAccessService;

    private final IUriGrantsManager mUriGrantsManager;

    private final UriGrantsManagerInternal mUriGrantsManagerInternal;

    private final IBinder mPermissionOwner;

    private final MultiUserDynamicAppFunctionRegistry mDynamicAppFunctionRegistry;

    private final AppFunctionMetadataReader mAppFunctionMetadataReader;
    private final AppFunctionMetadataObserver mAppFunctionMetadataObserver;

    private final Object mRequestResponseLoggerPerUserLock = new Object();

    @GuardedBy("mRequestResponseLoggerPerUserLock")
    private final SparseArray<AppFunctionRequestResponseLogger> mRequestResponseLoggerPerUser =
            new SparseArray<>();

    @Nullable private final AppInteractionService mAppInteractionService;

    private final VisibilityHelper mVisibilityHelper;

    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;

    public AppFunctionManagerServiceImpl(
            @NonNull Context context,
            @NonNull PackageManagerInternal packageManagerInternal,
            @NonNull AppFunctionAccessServiceInterface appFunctionAccessServiceInterface,
            @NonNull IUriGrantsManager uriGrantsManager,
            @NonNull UriGrantsManagerInternal uriGrantsManagerInternal,
            @NonNull AppFunctionsLoggerWrapper loggerWrapper,
            @NonNull MultiUserDynamicAppFunctionRegistry dynamicAppFunctionRegistry,
            @Nullable AppInteractionService appInteractionService,
            @NonNull AppFunctionMetadataReader appFunctionMetadataReader,
            @NonNull ActivityTaskManagerInternal activityTaskManagerInternal,
            @NonNull AppFunctionAllowlistReader allowlistReader) {
        this(
                context,
                new RemoteServiceCallerImpl<>(
                        context, IAppFunctionService.Stub::asInterface, THREAD_POOL_EXECUTOR),
                new CallerValidatorImpl(
                        context,
                        Objects.requireNonNull(context.getSystemService(UserManager.class)),
                        allowlistReader),
                new ServiceHelperImpl(context),
                new ServiceConfigImpl(),
                loggerWrapper,
                packageManagerInternal,
                appFunctionAccessServiceInterface,
                uriGrantsManager,
                uriGrantsManagerInternal,
                dynamicAppFunctionRegistry,
                appFunctionMetadataReader,
                appInteractionService,
                new VisibilityHelperImpl(context, packageManagerInternal),
                activityTaskManagerInternal);
    }

    private AppFunctionManagerServiceImpl(
            Context context,
            RemoteServiceCaller<IAppFunctionService> remoteServiceCaller,
            CallerValidator callerValidator,
            ServiceHelper appFunctionInternalServiceHelper,
            ServiceConfig serviceConfig,
            AppFunctionsLoggerWrapper loggerWrapper,
            PackageManagerInternal packageManagerInternal,
            AppFunctionAccessServiceInterface appFunctionAccessServiceInterface,
            IUriGrantsManager uriGrantsManager,
            UriGrantsManagerInternal uriGrantsManagerInternal,
            MultiUserDynamicAppFunctionRegistry dynamicAppFunctionRegistry,
            AppFunctionMetadataReader appFunctionMetadataReader,
            @Nullable AppInteractionService appInteractionService,
            VisibilityHelper visibilityHelper,
            ActivityTaskManagerInternal activityTaskManagerInternal) {
        mContext = Objects.requireNonNull(context);
        mRemoteServiceCaller = Objects.requireNonNull(remoteServiceCaller);
        mCallerValidator = Objects.requireNonNull(callerValidator);
        mInternalServiceHelper = Objects.requireNonNull(appFunctionInternalServiceHelper);
        mServiceConfig = Objects.requireNonNull(serviceConfig);
        mLoggerWrapper = Objects.requireNonNull(loggerWrapper);
        mPackageManagerInternal = Objects.requireNonNull(packageManagerInternal);
        mAppFunctionAccessService = Objects.requireNonNull(appFunctionAccessServiceInterface);
        mUriGrantsManager = Objects.requireNonNull(uriGrantsManager);
        mUriGrantsManagerInternal = Objects.requireNonNull(uriGrantsManagerInternal);
        mPermissionOwner =
                Objects.requireNonNull(
                        mUriGrantsManagerInternal.newUriPermissionOwner("appfunctions"));
        mDynamicAppFunctionRegistry = Objects.requireNonNull(dynamicAppFunctionRegistry);
        mAppFunctionMetadataReader = Objects.requireNonNull(appFunctionMetadataReader);
        mAppFunctionMetadataObserver =
                new AppFunctionMetadataObserver(context, appFunctionMetadataReader, serviceConfig);
        mAppInteractionService = appInteractionService;
        mVisibilityHelper = Objects.requireNonNull(visibilityHelper);
        mActivityTaskManagerInternal = Objects.requireNonNull(activityTaskManagerInternal);
    }

    /** Called when the user is unlocked. */
    public void onUserUnlocked(TargetUser user) {
        Objects.requireNonNull(user);
        if (android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
            mAppFunctionMetadataObserver.registerAppSearchObserverForUser(user);
        } else {
            registerAppSearchObserver(user);
        }
        // TODO: b/472621015 - Consider optimizing resetting RuntimeMetadata schema in
        // onUserUnlocked. AppSearch already optimizes for unchanged schema definition but we can
        // consider doing a diff or use a version to decide when to update the schema.
        trySyncRuntimeMetadata(
                user,
                /* shouldSetRuntimeMetadataSchemaUnconditionally= */ android.app.appfunctions.flags
                        .Flags.enableAppFunctionPermissionV2());
        PackageMonitor pkgMonitorForUser =
                AppFunctionPackageMonitor.registerPackageMonitorForUser(mContext, user);
        mPackageMonitors.append(user.getUserIdentifier(), pkgMonitorForUser);

        File appFunctionsLogDir =
                new File(
                        Environment.getDataSystemCeDirectory(user.getUserIdentifier()),
                        "appfunctions");
        try {
            synchronized (mRequestResponseLoggerPerUserLock) {
                mRequestResponseLoggerPerUser.append(
                        user.getUserIdentifier(),
                        new AppFunctionRequestResponseLogger(appFunctionsLogDir));
            }
        } catch (IOException e) {
            Slog.e(
                    TAG,
                    "Failed to create request/response logger for user "
                            + user.getUserIdentifier(),
                    e);
        }

        if (android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
            mDynamicAppFunctionRegistry.onUserUnlocked(mAppFunctionMetadataObserver, user);
        }

        if (android.app.appfunctions.flags.Flags.enableAppInteractionApi()) {
            // TODO(b/459347717): Make AppInteractionService a published system service so that
            // it can observe user unlocked/stopped internally.
            Objects.requireNonNull(mAppInteractionService).onUserUnlocked(user);
        }
    }

    /** Called when a the user is starting. */
    public void onUserStarting(@NonNull TargetUser user) {
        mAppFunctionAccessService.onUserStarting(user.getUserIdentifier());
    }

    /** Called when the user is stopping. */
    public void onUserStopping(@NonNull TargetUser user) {
        Objects.requireNonNull(user);

        if (android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
            mAppFunctionMetadataObserver.unregisterAppSearchObserverForUser(user);
        } else {
            MetadataSyncPerUser.removeUserSyncAdapter(user.getUserHandle());
        }

        int userIdentifier = user.getUserIdentifier();
        if (mPackageMonitors.contains(userIdentifier)) {
            mPackageMonitors.get(userIdentifier).unregister();
            mPackageMonitors.delete(userIdentifier);
        }

        synchronized (mRequestResponseLoggerPerUserLock) {
            AppFunctionRequestResponseLogger logger =
                    mRequestResponseLoggerPerUser.get(userIdentifier);
            if (logger != null) {
                logger.close();
                mRequestResponseLoggerPerUser.delete(userIdentifier);
            }
        }

        if (android.app.appfunctions.flags.Flags.enableAppInteractionApi()) {
            Objects.requireNonNull(mAppInteractionService).onUserStopping(user);
        }
    }

    /** Called when the user stopped. */
    public void onUserStopped(@NonNull TargetUser user) {
        if (android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
            Objects.requireNonNull(user);
            mDynamicAppFunctionRegistry.onUserStopped(user);
            mAppFunctionMetadataReader.onMetadataObserveFinishedForUser(user.getUserHandle());
        }
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter pw, @NonNull String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) {
            return;
        }

        final long token = Binder.clearCallingIdentity();
        try {
            AppFunctionDumpHelper.dumpAppFunctionsState(mContext, pw, args);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void onShellCommand(
            FileDescriptor in,
            FileDescriptor out,
            FileDescriptor err,
            @NonNull String[] args,
            ShellCallback callback,
            @NonNull ResultReceiver resultReceiver) {
        new AppFunctionManagerServiceShellCommand(mContext, this)
                .exec(this, in, out, err, args, callback, resultReceiver);
    }

    @Override
    public ICancellationSignal executeAppFunction(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            @NonNull IExecuteAppFunctionCallback executeAppFunctionCallback) {
        Objects.requireNonNull(requestInternal);
        Objects.requireNonNull(executeAppFunctionCallback);

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        final SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback =
                initializeSafeExecuteAppFunctionCallback(
                        requestInternal, executeAppFunctionCallback, callingUid);

        String validatedCallingPackage;
        try {
            validatedCallingPackage =
                    mCallerValidator.validateCallingPackage(requestInternal.getCallingPackage());
            mCallerValidator.verifyTargetUserHandle(
                    requestInternal.getUserHandle(), validatedCallingPackage);
        } catch (SecurityException exception) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_DENIED, exception.getMessage()));
            return null;
        }

        ICancellationSignal localCancelTransport = CancellationSignal.createTransport();

        THREAD_POOL_EXECUTOR.execute(
                () -> {
                    try {
                        executeAppFunctionInternal(
                                requestInternal,
                                callingUid,
                                callingPid,
                                localCancelTransport,
                                safeExecuteAppFunctionCallback,
                                executeAppFunctionCallback.asBinder());
                    } catch (Exception e) {
                        safeExecuteAppFunctionCallback.onError(
                                mapExceptionToExecuteAppFunctionResponse(e));
                    }
                });
        return localCancelTransport;
    }

    @WorkerThread
    private void executeAppFunctionInternal(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            int callingUid,
            int callingPid,
            @NonNull ICancellationSignal localCancelTransport,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            @NonNull IBinder callerBinder) {
        UserHandle targetUser = requestInternal.getUserHandle();
        UserHandle callingUser = UserHandle.getUserHandleForUid(callingUid);
        if (!mCallerValidator.verifyEnterprisePolicyIsAllowed(callingUser, targetUser)) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_ENTERPRISE_POLICY_DISALLOWED,
                            "Cannot run on a user with a restricted enterprise policy"));
            return;
        }
        String targetPackageName = requestInternal.getClientRequest().getTargetPackageName();
        if (TextUtils.isEmpty(targetPackageName)) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            AppFunctionException.ERROR_INVALID_ARGUMENT,
                            "Target package name cannot be empty."));
            return;
        }

        mCallerValidator
                .verifyCallerCanExecuteAppFunction(
                        callingUid,
                        callingPid,
                        targetUser,
                        requestInternal.getCallingPackage(),
                        targetPackageName,
                        requestInternal.getClientRequest().getFunctionIdentifier())
                .thenCompose(
                        canExecuteResult -> {
                            if (canExecuteResult == CAN_EXECUTE_APP_FUNCTIONS_DENIED) {
                                return AndroidFuture.failedFuture(
                                        new SecurityException(
                                                "Caller does not have permission to execute the"
                                                        + " appfunction"));
                            }

                            if (android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
                                try {
                                    validateExecuteAppFunctionRequestTargetScope(
                                            requestInternal.getClientRequest(),
                                            targetPackageName,
                                            targetUser
                                    );
                                } catch (AppFunctionNotFoundException e) {
                                    return AndroidFuture.failedFuture(e);
                                }
                            }

                            return isAppFunctionEnabledInternal(
                                            requestInternal
                                                    .getClientRequest()
                                                    .getFunctionIdentifier(),
                                            requestInternal
                                                    .getClientRequest()
                                                    .getTargetPackageName(),
                                            getAppSearchManagerAsUser(
                                                    requestInternal.getUserHandle()),
                                            THREAD_POOL_EXECUTOR,
                                            requestInternal.getUserHandle().getIdentifier())
                                    .thenApply(
                                            isEnabled -> {
                                                if (!isEnabled) {
                                                    throw new DisabledAppFunctionException(
                                                            "The app function is disabled");
                                                }
                                                return canExecuteResult;
                                            });
                        })
                .thenAccept(
                        canExecuteResult -> {
                            if (android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()
                                    && mAppFunctionMetadataReader.isDynamicFunction(
                                            targetPackageName,
                                            requestInternal
                                                    .getClientRequest()
                                                    .getFunctionIdentifier(),
                                            targetUser)) {
                                maybeGrantImplicitAccess(
                                        callingUid,
                                        /* causingIntent= */ null,
                                        targetUser,
                                        targetPackageName);
                                mDynamicAppFunctionRegistry.executeAppFunction(
                                        requestInternal,
                                        safeExecuteAppFunctionCallback,
                                        localCancelTransport);
                            } else {
                                executeServiceAppFunctionInternal(
                                        requestInternal,
                                        callingUid,
                                        localCancelTransport,
                                        safeExecuteAppFunctionCallback,
                                        callerBinder,
                                        canExecuteResult,
                                        targetPackageName);
                            }
                        })
                .exceptionally(
                        ex -> {
                            safeExecuteAppFunctionCallback.onError(
                                    mapExceptionToExecuteAppFunctionResponse(ex));
                            return null;
                        });
    }

    /**
     * Validates the target scope of the execute app function request. If the function is a
     * SCOPE_GLOBAL function, the request must not have an AppFunctionActivityId. If the function
     * is a SCOPE_ACTIVITY function, the request must have an AppFunctionActivityId and the
     * function must be registered for the given AppFunctionActivityId.
     *
     * @param executeRequest The execute app function request.
     * @param targetPackageName The target package name of the app function.
     * @param targetUser The target user of the app function.
     * @throws AppFunctionNotFoundException If the target scope is not valid.
     */
    private void validateExecuteAppFunctionRequestTargetScope(
            @NonNull ExecuteAppFunctionRequest executeRequest,
            @NonNull String targetPackageName,
            @NonNull UserHandle targetUser)
            throws AppFunctionNotFoundException {
        final String functionIdentifier = executeRequest.getFunctionIdentifier();
        final AppFunctionActivityId activityId = executeRequest.getActivityId();
        final boolean isDynamic =
                mAppFunctionMetadataReader.isDynamicFunction(
                        targetPackageName, functionIdentifier, targetUser);

        if (!isDynamic) {
            if (activityId != null) {
                throw new AppFunctionNotFoundException(
                        "SCOPE_GLOBAL functions cannot have an AppFunctionActivityId.");
            }
        } else {
            final boolean isActivityScoped =
                    mAppFunctionMetadataReader.isActivityScopedDynamicFunction(
                            targetPackageName, functionIdentifier, targetUser);

            if (isActivityScoped) {
                if (activityId == null) {
                    throw new AppFunctionNotFoundException(
                            "SCOPE_ACTIVITY functions must be targeted with an"
                                    + " AppFunctionActivityId");
                }
                final RegistrationScopeId scopeId = new RegistrationScopeId(activityId);
                if (!mDynamicAppFunctionRegistry.isRegistered(
                        targetPackageName, functionIdentifier, targetUser, scopeId)) {
                    throw new AppFunctionNotFoundException(
                            "Function is not registered for the given AppFunctionActivityId.");
                }
            } else { // Global-scoped dynamic function
                if (activityId != null) {
                    throw new AppFunctionNotFoundException(
                            "SCOPE_GLOBAL functions cannot have an AppFunctionActivityId.");
                }
            }
        }
    }

    private void executeServiceAppFunctionInternal(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            int callingUid,
            @NonNull ICancellationSignal localCancelTransport,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            @NonNull IBinder callerBinder,
            Integer canExecuteResult,
            String targetPackageName) {
        int bindFlags = Context.BIND_AUTO_CREATE;
        UserHandle targetUser = requestInternal.getUserHandle();
        if (canExecuteResult == CAN_EXECUTE_APP_FUNCTIONS_ALLOWED_HAS_PERMISSION) {
            // If the caller doesn't have the permission, do not use
            // BIND_FOREGROUND_SERVICE to avoid it raising its process state
            // by calling its own AppFunctions.
            bindFlags |= Context.BIND_FOREGROUND_SERVICE;
        }
        Intent serviceIntent =
                mInternalServiceHelper.resolveAppFunctionService(
                        targetPackageName, requestInternal.getUserHandle());
        if (serviceIntent == null) {
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            ERROR_SYSTEM_ERROR, "Cannot find the target service."));
            return;
        }
        maybeGrantImplicitAccess(
                callingUid,
                serviceIntent,
                targetUser,
                requestInternal.getClientRequest().getTargetPackageName());
        bindAppFunctionServiceUnchecked(
                requestInternal,
                serviceIntent,
                targetUser,
                localCancelTransport,
                safeExecuteAppFunctionCallback,
                bindFlags,
                callerBinder,
                callingUid);
    }

    private void maybeGrantImplicitAccess(
            int callingUid,
            @Nullable Intent causingIntent,
            @NonNull UserHandle targetUser,
            @NonNull String targetPackageName) {
        // Grant target app implicit visibility to the caller
        final int grantRecipientUserId = targetUser.getIdentifier();
        final int grantRecipientAppId =
                UserHandle.getAppId(
                        mPackageManagerInternal.getPackageUid(
                                targetPackageName,
                                /* flags= */ 0,
                                /* userId= */ grantRecipientUserId));
        if (grantRecipientAppId > 0) {
            mPackageManagerInternal.grantImplicitAccess(
                    grantRecipientUserId,
                    causingIntent,
                    grantRecipientAppId,
                    callingUid,
                    /* direct= */ true);
        }
    }

    @Override
    public void getAppFunctionStates(
            @NonNull List<AppFunctionName> appFunctionNames,
            @NonNull String callingPackageName,
            int targetUserId,
            @NonNull IGetAppFunctionStatesCallback callback)
            throws RemoteException {
        Objects.requireNonNull(appFunctionNames);
        Objects.requireNonNull(callback);

        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        try {
            // The calling package name will be used to determine the visible packages.
            mCallerValidator.validateCallingPackage(callingPackageName);
            mCallerValidator.verifyUserInteraction(
                    /* targetUserId= */ targetUserId,
                    /* callingUid= */ callingUid,
                    /* callingPid= */ callingPid,
                    /* callingPackageName= */ callingPackageName);
        } catch (SecurityException e) {
            try {
                callback.onError(new ParcelableException(e));
            } catch (RemoteException ex) {
                Slog.e(TAG, "Failed to execute callback#onError.", e);
            }
            return;
        }

        UserHandle targetUser = UserHandle.of(targetUserId);
        AppSearchManager perUserAppSearchManager = getAppSearchManagerAsUser(targetUser);
        if (perUserAppSearchManager == null) {
            throw new IllegalStateException(
                    "AppSearchManager not found for user:" + targetUser.getIdentifier());
        }

        THREAD_POOL_EXECUTOR.execute(
                () -> {
                    Set<AppFunctionName> visibleAppFunctionNames =
                            mVisibilityHelper.filterVisibleAppFunctions(
                                    Set.copyOf(appFunctionNames),
                                    callingPackageName,
                                    callingUid,
                                    callingPid);

                    FutureGlobalSearchSession futureGlobalSearchSession =
                            new FutureGlobalSearchSession(
                                    perUserAppSearchManager, THREAD_POOL_EXECUTOR);

                    var unused =
                            mAppFunctionMetadataReader
                                    .getAppFunctionStates(
                                            futureGlobalSearchSession,
                                            visibleAppFunctionNames,
                                            targetUserId)
                                    .whenCompleteAsync(
                                            (states, exception) -> {
                                                try {
                                                    if (exception != null) {
                                                        callback.onError(
                                                                new ParcelableException(exception));
                                                    } else {
                                                        callback.onSuccess(
                                                                new AppFunctionStateList(states));
                                                    }
                                                } catch (RemoteException re) {
                                                    Slog.w(TAG, "Fail to call onError");
                                                } finally {
                                                    futureGlobalSearchSession.close();
                                                }
                                            },
                                            THREAD_POOL_EXECUTOR);
                });
    }

    @Override
    public void getAppFunctionActivityStates(
            @NonNull List<AppFunctionActivityId> activityIds,
            @NonNull String callingPackageName,
            int targetUserId,
            @NonNull IGetAppFunctionActivityStatesCallback callback)
            throws RemoteException {
        Objects.requireNonNull(activityIds);
        Objects.requireNonNull(callback);

        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        try {
            mCallerValidator.validateCallingPackage(callingPackageName);
            mCallerValidator.verifyUserInteraction(
                    /* targetUserId= */ targetUserId,
                    /* callingUid= */ callingUid,
                    /* callingPid= */ callingPid,
                    /* callingPackageName= */ callingPackageName);
        } catch (SecurityException e) {
            try {
                callback.onError(new ParcelableException(e));
            } catch (RemoteException ex) {
                Slog.e(TAG, "Failed to execute callback#onError.", e);
            }
            return;
        }

        THREAD_POOL_EXECUTOR.execute(
                () -> {
                    var unused =
                            mAppFunctionMetadataReader
                                    .getAppFunctionActivityStates(activityIds, targetUserId)
                                    .thenApplyAsync(
                                            states ->
                                                    mVisibilityHelper
                                                            .filterVisibleAppFunctionActivityStates(
                                                                    states,
                                                                    callingPackageName,
                                                                    callingUid,
                                                                    callingPid),
                                            THREAD_POOL_EXECUTOR)
                                    .whenComplete(
                                            (states, exception) -> {
                                                try {
                                                    if (exception != null) {
                                                        callback.onError(
                                                                new ParcelableException(exception));
                                                    } else {
                                                        callback.onSuccess(
                                                                new AppFunctionActivityStateList(
                                                                        states));
                                                    }
                                                } catch (RemoteException ex) {
                                                    Slog.w(TAG, "Fail to call onError", ex);
                                                }
                                            });
                });
    }

    @Override
    public void searchAppFunctions(
            @NonNull AppFunctionAidlSearchSpec aidlSearchSpec,
            @NonNull ISearchAppFunctionsCallback searchAppFunctionsCallback)
            throws RemoteException {
        Objects.requireNonNull(aidlSearchSpec);
        Objects.requireNonNull(searchAppFunctionsCallback);

        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        try {
            // The calling package name will be used to determine the visible packages.
            mCallerValidator.validateCallingPackage(aidlSearchSpec.getCallingPackageName());
            mCallerValidator.verifyUserInteraction(
                    /* targetUserId= */ aidlSearchSpec.getTargetUserId(),
                    /* callingUid= */ callingUid,
                    /* callingPid= */ callingPid,
                    /* callingPackageName= */ aidlSearchSpec.getCallingPackageName());
        } catch (SecurityException e) {
            try {
                searchAppFunctionsCallback.onError(new ParcelableException(e));
            } catch (RemoteException ex) {
                Slog.e(TAG, "Failed to execute callback#onError.", e);
            }
            return;
        }

        UserHandle targetUser = UserHandle.of(aidlSearchSpec.getTargetUserId());
        AppSearchManager perUserAppSearchManager = getAppSearchManagerAsUser(targetUser);
        if (perUserAppSearchManager == null) {
            throw new IllegalStateException(
                    "AppSearchManager not found for user:" + targetUser.getIdentifier());
        }

        THREAD_POOL_EXECUTOR.execute(
                () -> {
                    AppFunctionSearchSpec filteredSearchSpec =
                            mVisibilityHelper.applyVisiblePackageFilter(
                                    aidlSearchSpec, callingUid, callingPid);
                    if (filteredSearchSpec == null) {
                        // Early return when the calling package is unable to see any AppFunction
                        try {
                            searchAppFunctionsCallback.onSuccess(
                                    new IAppFunctionSearchResults.Stub() {
                                        @PermissionManuallyEnforced
                                        @Override
                                        public void getNextPage(
                                                IAppFunctionSearchResultCallback callback)
                                                throws RemoteException {
                                            callback.onResult(Collections.emptyList());
                                        }

                                        @PermissionManuallyEnforced
                                        @Override
                                        public void close() {}
                                    });
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Failed to execute callback#onSuccess.", e);
                        }
                        return;
                    }

                    // Clear the caller identity since the AppFunction service needs to search
                    // with "android" capability and filter the documents via search query.
                    final long token = Binder.clearCallingIdentity();
                    try {
                        FutureGlobalSearchSession futureGlobalSearchSession =
                                new FutureGlobalSearchSession(
                                        perUserAppSearchManager, Runnable::run);
                        searchAppFunctionsCallback
                                .asBinder()
                                .linkToDeath(futureGlobalSearchSession::close, /* flags= */ 0);
                        IAppFunctionSearchResults results =
                                mAppFunctionMetadataReader.searchAppFunctions(
                                        futureGlobalSearchSession,
                                        filteredSearchSpec,
                                        THREAD_POOL_EXECUTOR);
                        try {
                            searchAppFunctionsCallback.onSuccess(results);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Failed to execute callback#onSuccess.", e);
                            results.close();
                        }
                    } catch (Exception e) {
                        try {
                            searchAppFunctionsCallback.onError(new ParcelableException(e));
                        } catch (RemoteException ex) {
                            Slog.e(TAG, "Failed to execute callback#onError.", ex);
                        }
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
    }

    @Override
    public void observeAppFunctions(
            @NonNull AppFunctionAidlSearchSpec aidlSearchSpec,
            @NonNull IObserveAppFunctionChangesCallback observeAppFunctionsCallback)
            throws RemoteException, SecurityException {
        Objects.requireNonNull(aidlSearchSpec);
        Objects.requireNonNull(observeAppFunctionsCallback);

        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        // The calling package name will be used to determine the visible packages.
        mCallerValidator.validateCallingPackage(aidlSearchSpec.getCallingPackageName());
        mCallerValidator.verifyUserInteraction(
                /* targetUserId= */ aidlSearchSpec.getTargetUserId(),
                /* callingUid= */ callingUid,
                /* callingPid= */ callingPid,
                /* callingPackageName= */ aidlSearchSpec.getCallingPackageName());

        UserHandle targetUser = UserHandle.of(aidlSearchSpec.getTargetUserId());

        // TODO(b/438413081): Instead of applying visibility filter before observing,
        //  apply changes to observed packages/functions at runtime.
        AppFunctionSearchSpec filteredSearchSpec =
                mVisibilityHelper.applyVisiblePackageFilter(aidlSearchSpec, callingUid, callingPid);

        if (filteredSearchSpec == null) {
            return;
        }

        mAppFunctionMetadataObserver.registerClientAppCallback(
                targetUser, filteredSearchSpec, observeAppFunctionsCallback);
    }

    @Override
    public void unregisterAppFunctionObserver(
            @NonNull String callingPackage,
            @NonNull UserHandle userHandle,
            @NonNull IObserveAppFunctionChangesCallback observeAppFunctionsCallback)
            throws SecurityException {
        Objects.requireNonNull(callingPackage);
        Objects.requireNonNull(userHandle);
        Objects.requireNonNull(observeAppFunctionsCallback);

        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        mCallerValidator.validateCallingPackage(callingPackage);
        mCallerValidator.verifyUserInteraction(
                /* targetUserId= */ userHandle.getIdentifier(),
                /* callingUid= */ callingUid,
                /* callingPid= */ callingPid,
                /* callingPackageName= */ callingPackage);

        mAppFunctionMetadataObserver.unregisterClientAppCallback(
                userHandle, observeAppFunctionsCallback);
    }

    @Override
    public void isAppFunctionEnabled(
            @NonNull String callingPackage,
            @NonNull String targetPackage,
            @NonNull String functionIdentifier,
            @NonNull UserHandle userHandle,
            @NonNull IIsAppFunctionEnabledCallback callback)
            throws RemoteException {
        Objects.requireNonNull(callingPackage);
        Objects.requireNonNull(targetPackage);
        Objects.requireNonNull(functionIdentifier);
        Objects.requireNonNull(userHandle);
        Objects.requireNonNull(callback);

        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        try {
            // The calling package name will be used to determine the visible packages.
            mCallerValidator.validateCallingPackage(callingPackage);
            mCallerValidator.verifyUserInteraction(
                    /* targetUserId= */ userHandle.getIdentifier(),
                    /* callingUid= */ callingUid,
                    /* callingPid= */ callingPid,
                    /* callingPackageName= */ callingPackage);
        } catch (SecurityException e) {
            try {
                callback.onError(new ParcelableException(e));
            } catch (RemoteException ex) {
                Slog.e(TAG, "Failed to execute callback#onError.", e);
            }
            return;
        }

        if (!mVisibilityHelper.isAppFunctionVisible(
                new AppFunctionName(targetPackage, functionIdentifier),
                callingPackage,
                callingUid,
                callingPid)) {
            try {
                callback.onError(
                        new ParcelableException(
                                new AppFunctionNotFoundException("App Function not found")));
            } catch (RemoteException re) {
                Slog.e(TAG, "Failed to execute callback#onError.", re);
            }
            return;
        }

        UserHandle targetUser = UserHandle.of(userHandle.getIdentifier());
        AppSearchManager perUserAppSearchManager = getAppSearchManagerAsUser(targetUser);
        if (perUserAppSearchManager == null) {
            throw new IllegalStateException(
                    "AppSearchManager not found for user:" + targetUser.getIdentifier());
        }

        final long token = Binder.clearCallingIdentity();
        try {
            isAppFunctionEnabledInternal(
                            functionIdentifier,
                            targetPackage,
                            perUserAppSearchManager,
                            THREAD_POOL_EXECUTOR,
                            targetUser.getIdentifier())
                    .thenAccept(
                            isEnabled -> {
                                try {
                                    callback.onSuccess(isEnabled);
                                } catch (RemoteException re) {
                                    Slog.e(TAG, "Failed to execute callback#onSuccess.", re);
                                }
                            })
                    .exceptionally(
                            exception -> {
                                try {
                                    callback.onError(new ParcelableException(exception.getCause()));
                                } catch (RemoteException re) {
                                    Slog.e(TAG, "Failed to execute callback#onError.", re);
                                }
                                return null;
                            });
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private CompletableFuture<Boolean> isAppFunctionEnabledInternal(
            @NonNull String functionIdentifier,
            @NonNull String targetPackage,
            @NonNull AppSearchManager appSearchManager,
            @NonNull Executor executor,
            int userId) {
        if (android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
            return isAppFunctionEnabledInternal2(
                    functionIdentifier, targetPackage, appSearchManager, userId);
        }

        AndroidFuture<Boolean> future = new AndroidFuture<>();
        AppFunctionManagerHelper.isAppFunctionEnabled(
                functionIdentifier,
                targetPackage,
                appSearchManager,
                executor,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Boolean result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        future.completeExceptionally(error);
                    }
                });
        return future;
    }

    // TODO(b/438413081): Consider caching runtime enabled states for all app functions
    //  for quick lookup
    private CompletableFuture<Boolean> isAppFunctionEnabledInternal2(
            @NonNull String functionIdentifier,
            @NonNull String targetPackage,
            @NonNull AppSearchManager appSearchManager,
            int userId) {
        FutureGlobalSearchSession futureSession =
                new FutureGlobalSearchSession(appSearchManager, Runnable::run);
        return mAppFunctionMetadataReader
                .isAppFunctionEnabled(
                        futureSession,
                        new AppFunctionName(targetPackage, functionIdentifier),
                        userId)
                .whenComplete(
                        (isEnabled, exception) -> {
                            futureSession.close();
                        });
    }

    @Override
    public void setAppFunctionEnabled(
            @NonNull String callingPackage,
            @NonNull String functionIdentifier,
            @NonNull UserHandle userHandle,
            @AppFunctionManager.EnabledState int enabledState,
            @NonNull ISetAppFunctionEnabledCallback callback) {
        try {
            // Skip validation for shell to allow changing enabled state via shell.
            if (Binder.getCallingUid() != Process.SHELL_UID) {
                mCallerValidator.validateCallingPackage(callingPackage);
            }
        } catch (SecurityException e) {
            reportException(callback, e);
            return;
        }
        THREAD_POOL_EXECUTOR.execute(
                () -> {
                    try {
                        synchronized (getLockForPackage(callingPackage)) {
                            setAppFunctionEnabledInternalLocked(
                                    callingPackage, functionIdentifier, userHandle, enabledState);
                        }
                        callback.onSuccess();
                    } catch (Exception e) {
                        Slog.e(TAG, "Error in setAppFunctionEnabled: ", e);
                        reportException(callback, e);
                    }
                });
    }

    @Override
    public int getAccessFlags(
            String agentPackageName, int agentUserId, String targetPackageName, int targetUserId)
            throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return 0;
        }
        return mAppFunctionAccessService.getAccessFlags(
                agentPackageName, agentUserId, targetPackageName, targetUserId);
    }

    @Override
    public boolean updateAccessFlags(
            String agentPackageName,
            int agentUserId,
            String targetPackageName,
            int targetUserId,
            int flagMask,
            int flags)
            throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return false;
        }
        return mAppFunctionAccessService.updateAccessFlags(
                agentPackageName, agentUserId, targetPackageName, targetUserId, flagMask, flags);
    }

    @Override
    public void registerAppFunctions(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull IAppFunctionExecutor executor,
            @Nullable IBinder activityToken) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(functionIdentifiers);
        Objects.requireNonNull(executor);

        UserHandle callingUserHandle = Binder.getCallingUserHandle();
        mCallerValidator.validateCallingPackage(packageName);
        // TODO(b/343261179): Remove this reference when the activity is destroyed to avoid leaking
        // the activity token.
        List<RegistrationScopeId> scopeIds =
                verifyDynamicRegistrationRequestsAndCollectScopeIds(
                        packageName,
                        functionIdentifiers,
                        callingUserHandle,
                        activityToken,
                        /* operationName= */ "register");

        mDynamicAppFunctionRegistry.registerAppFunctions(
                packageName, functionIdentifiers, executor, callingUserHandle, scopeIds);

        onDynamicFunctionRegistrationChanged(callingUserHandle, packageName, functionIdentifiers);
    }

    @Override
    public void unregisterAppFunctions(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull IAppFunctionExecutor session,
            @Nullable IBinder activityToken) {
        UserHandle callingUserHandle = Binder.getCallingUserHandle();
        mCallerValidator.validateCallingPackage(packageName);
        List<RegistrationScopeId> activityTokens =
                verifyDynamicRegistrationRequestsAndCollectScopeIds(
                        packageName,
                        functionIdentifiers,
                        callingUserHandle,
                        activityToken,
                        /* operationName= */ "unregister");
        mDynamicAppFunctionRegistry.unregisterAppFunctions(
                packageName, functionIdentifiers, session, callingUserHandle, activityTokens);

        onDynamicFunctionRegistrationChanged(callingUserHandle, packageName, functionIdentifiers);
    }

    private List<RegistrationScopeId> verifyDynamicRegistrationRequestsAndCollectScopeIds(
            @NonNull String packageName,
            @NonNull List<String> functionIdentifiers,
            @NonNull UserHandle callingUserHandle,
            @Nullable IBinder activityToken,
            @NonNull String operationName) {
        ArrayList<RegistrationScopeId> scopeIds = new ArrayList<>(functionIdentifiers.size());
        RegistrationScopeId passedScopeId =
                (activityToken != null)
                        ? new RegistrationScopeId(getAppFunctionActivityId(activityToken))
                        : RegistrationScopeId.GLOBAL_SCOPE;
        for (String functionIdentifier : functionIdentifiers) {
            if (!mAppFunctionMetadataReader.isDynamicFunction(
                    packageName, functionIdentifier, callingUserHandle)) {
                throw new IllegalArgumentException(
                        "Unable to "
                                + operationName
                                + " AppFunction "
                                + functionIdentifier
                                + ". Ensure this function is declared in the XML resource"
                                + " referenced by the property within the <application> tag of your"
                                + " AndroidManifest.xml.");
            }
            if (mAppFunctionMetadataReader.isActivityScopedDynamicFunction(
                    packageName, functionIdentifier, callingUserHandle)) {
                if (activityToken == null) {
                    throw new IllegalArgumentException(
                            "Activity scoped function "
                                    + functionIdentifier
                                    + " must be registered within an activity context");
                }
                scopeIds.add(passedScopeId);
            } else {
                scopeIds.add(RegistrationScopeId.GLOBAL_SCOPE);
            }
        }
        return scopeIds;
    }

    @Nullable
    private AppFunctionActivityId getAppFunctionActivityId(@Nullable IBinder activityToken) {
        if (activityToken == null) {
            return null;
        }
        IBinder assistToken =
                mActivityTaskManagerInternal.getAssistTokenForActivityToken(activityToken);
        if (assistToken == null) {
            throw new IllegalArgumentException(
                    "Unable to process AppFunction registration. Activity not attached.");
        }
        return new AppFunctionActivityId(assistToken);
    }

    private void onDynamicFunctionRegistrationChanged(
            UserHandle callingUserHandle, String packageName, List<String> functionIdentifiers) {
        Set<AppFunctionName> functionNames = new ArraySet<>();
        for (String functionId : functionIdentifiers) {
            functionNames.add(new AppFunctionName(packageName, functionId));
        }
        // TODO(b/438413081): Verify that the function is runtime enabled before notifying after
        //   registration/unregistration to avoid redundant calls when the effective state hasn't
        //   changed.
        THREAD_POOL_EXECUTOR.execute(
                () ->
                        mAppFunctionMetadataObserver.onEnabledStatesChanged(
                                callingUserHandle, functionNames));
    }

    @Override
    public void revokeSelfAccess(String targetPackageName) {
        if (!accessCheckFlagsEnabled()) {
            return;
        }
        mAppFunctionAccessService.revokeSelfAccess(targetPackageName);
    }

    @Override
    public int getAccessRequestState(
            String agentPackageName, int agentUserId, String targetPackageName, int targetUserId)
            throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return ACCESS_REQUEST_STATE_UNREQUESTABLE;
        }
        return mAppFunctionAccessService.getAccessRequestState(
                agentPackageName, agentUserId, targetPackageName, targetUserId);
    }

    @Override
    public List<String> getValidAgents(int userId) throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return List.of();
        }
        return mAppFunctionAccessService.getValidAgents(userId);
    }

    @Override
    public List<String> getValidTargets(int userId) throws RemoteException {
        if (!accessCheckFlagsEnabled()) {
            return List.of();
        }
        final List<String> validTargets = mAppFunctionAccessService.getValidTargets(userId);
        final ArraySet<String> validPermissionOwnerTargets = new ArraySet<>();

        final int validTargetSize = validTargets.size();
        for (int i = 0; i < validTargetSize; i++) {
            final String target = validTargets.get(i);
            validPermissionOwnerTargets.add(target);
        }

        return List.copyOf(validPermissionOwnerTargets);
    }

    @Override
    public void addOnAccessChangedListener(
            @NonNull IOnAppFunctionAccessChangeListener listener, int userId) {
        mAppFunctionAccessService.addOnAccessChangedListener(listener, userId);
    }

    @Override
    public void removeOnAccessChangedListener(
            @NonNull IOnAppFunctionAccessChangeListener listener, int userId) {
        mAppFunctionAccessService.removeOnAccessChangedListener(listener, userId);
    }

    @Override
    @NonNull
    public Intent createRequestAccessIntent(@NonNull String targetPackageName) {
        Objects.requireNonNull(targetPackageName);
        Intent intent = new Intent(ACTION_REQUEST_APP_FUNCTION_ACCESS);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, targetPackageName);
        intent.setPackage(mContext.getPackageManager().getPermissionControllerPackageName());
        return intent;
    }

    private boolean accessCheckFlagsEnabled() {
        return android.permission.flags.Flags.appFunctionAccessApiEnabled()
                && android.permission.flags.Flags.appFunctionAccessServiceEnabled();
    }

    /**
     * Grants temporary uri permission on behalf of the app that returns the response to the caller
     * that sends the request.
     *
     * <p>All {@link AppFunctionUriGrant} in {@link ExecuteAppFunctionResponse} would be granted to
     * the receiver until the system service is finished. That is usually until device reboots.
     */
    private void grantTemporaryUriPermissions(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            @NonNull ExecuteAppFunctionResponse response,
            int callingUid) {
        if (!android.app.appfunctions.flags.Flags.enableAppFunctionPermissionV2()) return;

        final int uriReceiverUserId = UserHandle.getUserId(callingUid);
        final int targetUserId = requestInternal.getUserHandle().getIdentifier();
        final String uriOwnerPackageName =
                requestInternal.getClientRequest().getTargetPackageName();
        final int uriOwnerUid =
                mPackageManagerInternal.getPackageUid(
                        uriOwnerPackageName, /* flags= */ 0, /* userId= */ targetUserId);

        final long ident = Binder.clearCallingIdentity();
        try {
            final int uriGrantsSize = response.getUriGrants().size();
            for (int i = 0; i < uriGrantsSize; i++) {
                final AppFunctionUriGrant uriGrant = response.getUriGrants().get(i);
                if (!ContentResolver.SCHEME_CONTENT.equals(uriGrant.getUri().getScheme())) continue;

                final String uriReceiverPackageName = requestInternal.getCallingPackage();
                final int uriOwnerUserid =
                        ContentProvider.getUserIdFromUri(
                                uriGrant.getUri(), UserHandle.getUserId(uriOwnerUid));

                mUriGrantsManager.grantUriPermissionFromOwner(
                        mPermissionOwner,
                        uriOwnerUid,
                        uriReceiverPackageName,
                        ContentProvider.getUriWithoutUserId(uriGrant.getUri()),
                        uriGrant.getModeFlags(),
                        uriOwnerUserid,
                        uriReceiverUserId);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Granting URI permissions failed", e);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static void reportException(
            @NonNull ISetAppFunctionEnabledCallback callback, @NonNull Exception exception) {
        try {
            callback.onError(new ParcelableException(exception));
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to report the exception", e);
        }
    }

    /**
     * Sets the enabled status of a specified app function.
     *
     * <p>Required to hold a lock to call this function to avoid document changes during the
     * process.
     */
    @WorkerThread
    @GuardedBy("getLockForPackage(callingPackage)")
    private void setAppFunctionEnabledInternalLocked(
            @NonNull String callingPackage,
            @NonNull String functionIdentifier,
            @NonNull UserHandle userHandle,
            @AppFunctionManager.EnabledState int enabledState)
            throws Exception {
        if (android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()) {
            setAppFunctionEnabledInternalLocked2(
                    callingPackage, functionIdentifier, userHandle, enabledState);
            return;
        }

        AppSearchManager perUserAppSearchManager = getAppSearchManagerAsUser(userHandle);

        if (perUserAppSearchManager == null) {
            throw new IllegalStateException(
                    "AppSearchManager not found for user:" + userHandle.getIdentifier());
        }
        SearchContext runtimeMetadataSearchContext =
                new SearchContext.Builder(APP_FUNCTION_RUNTIME_METADATA_DB).build();

        try (FutureAppSearchSession runtimeMetadataSearchSession =
                new FutureAppSearchSessionImpl(
                        perUserAppSearchManager,
                        THREAD_POOL_EXECUTOR,
                        runtimeMetadataSearchContext)) {
            AppFunctionRuntimeMetadata existingMetadata =
                    new AppFunctionRuntimeMetadata(
                            getRuntimeMetadataGenericDocument(
                                    callingPackage,
                                    functionIdentifier,
                                    runtimeMetadataSearchSession));
            AppFunctionRuntimeMetadata newMetadata =
                    new AppFunctionRuntimeMetadata.Builder(existingMetadata)
                            .setEnabled(enabledState)
                            .build();
            AppSearchBatchResult<String, Void> putDocumentBatchResult =
                    runtimeMetadataSearchSession
                            .put(
                                    new PutDocumentsRequest.Builder()
                                            .addGenericDocuments(newMetadata)
                                            .build())
                            .get();
            if (!putDocumentBatchResult.isSuccess()) {
                throw new IllegalStateException(
                        "Failed writing updated doc to AppSearch due to " + putDocumentBatchResult);
            }
        }
    }

    @WorkerThread
    @GuardedBy("getLockForPackage(callingPackage)")
    private void setAppFunctionEnabledInternalLocked2(
            @NonNull String callingPackage,
            @NonNull String functionIdentifier,
            @NonNull UserHandle userHandle,
            @AppFunctionManager.EnabledState int requestedRuntimeState)
            throws Exception {
        AppSearchManager perUserAppSearchManager = getAppSearchManagerAsUser(userHandle);

        if (perUserAppSearchManager == null) {
            throw new IllegalStateException(
                    "AppSearchManager not found for user:" + userHandle.getIdentifier());
        }
        SearchContext runtimeMetadataSearchContext =
                new SearchContext.Builder(APP_FUNCTION_RUNTIME_METADATA_DB).build();

        try (FutureAppSearchSession runtimeMetadataSearchSession =
                new FutureAppSearchSessionImpl(
                        perUserAppSearchManager,
                        THREAD_POOL_EXECUTOR,
                        runtimeMetadataSearchContext)) {
            AppFunctionRuntimeMetadata existingMetadata =
                    new AppFunctionRuntimeMetadata(
                            getRuntimeMetadataGenericDocument(
                                    callingPackage,
                                    functionIdentifier,
                                    runtimeMetadataSearchSession));

            // No need to overwrite metadata if the runtime enabled state value isn't changing.
            if (requestedRuntimeState == existingMetadata.getEnabled()) {
                return;
            }

            // TODO(b/438413081): Optimize this function to call app search only once to retrieve
            //  both the runtime metadata and the effective enabled state.
            final boolean isExistingMetadataEffectivelyEnabled =
                    isAppFunctionEnabledInternal2(
                                    existingMetadata,
                                    perUserAppSearchManager,
                                    userHandle.getIdentifier())
                            .get();

            AppFunctionRuntimeMetadata newMetadata =
                    new AppFunctionRuntimeMetadata.Builder(existingMetadata)
                            .setEnabled(requestedRuntimeState)
                            .build();
            AppSearchBatchResult<String, Void> putDocumentBatchResult =
                    runtimeMetadataSearchSession
                            .put(
                                    new PutDocumentsRequest.Builder()
                                            .addGenericDocuments(newMetadata)
                                            .build())
                            .get();
            if (!putDocumentBatchResult.isSuccess()) {
                throw new IllegalStateException(
                        "Failed writing updated doc to AppSearch due to " + putDocumentBatchResult);
            }

            boolean isNewMetadataEffectivelyEnabled =
                    isAppFunctionEnabledInternal2(
                                    newMetadata,
                                    perUserAppSearchManager,
                                    userHandle.getIdentifier())
                            .get();

            try {
                if (isExistingMetadataEffectivelyEnabled != isNewMetadataEffectivelyEnabled) {
                    mAppFunctionMetadataObserver.onEnabledStatesChanged(
                            userHandle,
                            Set.of(new AppFunctionName(callingPackage, functionIdentifier)));
                }
            } catch (Exception e) {
                Slog.w(TAG, "Failed to report enabled state change.", e);
            }
        }
    }

    /**
     * Returns true if the given {@link AppFunctionRuntimeMetadata} is enabled.
     *
     * <p>This function makes use of an already obtained {@link AppFunctionRuntimeMetadata} to
     * optimize {@link #isAppFunctionEnabledInternal2}. If the runtimeMetadata has a non-default
     * enabled state, it returns it. Otherwise, it routes to {@link #isAppFunctionEnabledInternal2}.
     */
    private CompletableFuture<Boolean> isAppFunctionEnabledInternal2(
            @NonNull AppFunctionRuntimeMetadata runtimeMetadata,
            @NonNull AppSearchManager appSearchManager,
            int userId)
            throws Exception {
        if (runtimeMetadata.getEnabled() == APP_FUNCTION_STATE_DEFAULT) {
            return isAppFunctionEnabledInternal2(
                    runtimeMetadata.getFunctionId(),
                    runtimeMetadata.getPackageName(),
                    appSearchManager,
                    userId);
        } else {
            return AndroidFuture.completedFuture(
                    runtimeMetadata.getEnabled() == APP_FUNCTION_STATE_ENABLED);
        }
    }

    @WorkerThread
    @NonNull
    private AppFunctionRuntimeMetadata getRuntimeMetadataGenericDocument(
            @NonNull String packageName,
            @NonNull String functionId,
            @NonNull FutureAppSearchSession runtimeMetadataSearchSession)
            throws Exception {
        String documentId =
                AppFunctionRuntimeMetadata.getDocumentIdForAppFunction(packageName, functionId);
        GetByDocumentIdRequest request =
                new GetByDocumentIdRequest.Builder(APP_FUNCTION_RUNTIME_NAMESPACE)
                        .addIds(documentId)
                        .build();
        AppSearchBatchResult<String, GenericDocument> result =
                runtimeMetadataSearchSession.getByDocumentId(request).get();
        if (result.isSuccess()) {
            return new AppFunctionRuntimeMetadata((result.getSuccesses().get(documentId)));
        }
        throw new IllegalArgumentException("Function " + functionId + " does not exist");
    }

    private void bindAppFunctionServiceUnchecked(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            @NonNull Intent serviceIntent,
            @NonNull UserHandle targetUser,
            @NonNull ICancellationSignal cancellationSignalTransport,
            @NonNull SafeOneTimeExecuteAppFunctionCallback safeExecuteAppFunctionCallback,
            int bindFlags,
            @NonNull IBinder callerBinder,
            int callingUid) {
        CancellationSignal cancellationSignal =
                CancellationSignal.fromTransport(cancellationSignalTransport);
        ICancellationCallback cancellationCallback =
                new ICancellationCallback.Stub() {
                    @Override
                    public void sendCancellationTransport(
                            @NonNull ICancellationSignal cancellationTransport) {
                        cancellationSignal.setRemote(cancellationTransport);
                    }
                };
        boolean bindServiceResult =
                mRemoteServiceCaller.runServiceCall(
                        serviceIntent,
                        bindFlags,
                        targetUser,
                        mServiceConfig.getExecuteAppFunctionCancellationTimeoutMillis(),
                        cancellationSignal,
                        new RunAppFunctionServiceCallback(
                                requestInternal,
                                cancellationCallback,
                                safeExecuteAppFunctionCallback,
                                getPackageSigningInfo(
                                        targetUser,
                                        requestInternal.getCallingPackage(),
                                        callingUid)),
                        callerBinder);

        if (!bindServiceResult) {
            Slog.e(TAG, "Failed to bind to the AppFunctionService");
            safeExecuteAppFunctionCallback.onError(
                    new AppFunctionException(
                            ERROR_SYSTEM_ERROR, "Failed to bind the AppFunctionService."));
        }
    }

    @NonNull
    private SigningInfo getPackageSigningInfo(
            @NonNull UserHandle targetUser, @NonNull String packageName, int uid) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(targetUser);

        if (uid == Process.ROOT_UID) {
            // root is not a package. It does not have a signing info.
            return new SigningInfo();
        }
        PackageInfo packageInfo;
        packageInfo =
                Objects.requireNonNull(
                        mPackageManagerInternal.getPackageInfo(
                                packageName,
                                PackageManager.GET_SIGNING_CERTIFICATES,
                                uid,
                                targetUser.getIdentifier()));
        return Objects.requireNonNull(packageInfo.signingInfo);
    }

    private AppSearchManager getAppSearchManagerAsUser(@NonNull UserHandle userHandle) {
        return mContext.createContextAsUser(userHandle, /* flags= */ 0)
                .getSystemService(AppSearchManager.class);
    }

    private AppFunctionException mapExceptionToExecuteAppFunctionResponse(Throwable e) {
        if (e instanceof CompletionException) {
            e = e.getCause();
        }
        int resultCode = ERROR_SYSTEM_ERROR;
        if (e instanceof AppFunctionNotFoundException) {
            resultCode = AppFunctionException.ERROR_FUNCTION_NOT_FOUND;
        } else if (e instanceof AppSearchException appSearchException) {
            resultCode =
                    mapAppSearchResultFailureCodeToExecuteAppFunctionResponse(
                            appSearchException.getResultCode());
        } else if (e instanceof SecurityException) {
            resultCode = AppFunctionException.ERROR_DENIED;
        } else if (e instanceof DisabledAppFunctionException) {
            resultCode = AppFunctionException.ERROR_DISABLED;
        }
        return new AppFunctionException(resultCode, e.getMessage());
    }

    private int mapAppSearchResultFailureCodeToExecuteAppFunctionResponse(int resultCode) {
        if (resultCode == AppSearchResult.RESULT_OK) {
            throw new IllegalArgumentException(
                    "This method can only be used to convert failure result codes.");
        }

        switch (resultCode) {
            case AppSearchResult.RESULT_NOT_FOUND:
                return AppFunctionException.ERROR_FUNCTION_NOT_FOUND;
            case AppSearchResult.RESULT_INVALID_ARGUMENT:
            case AppSearchResult.RESULT_INTERNAL_ERROR:
            case AppSearchResult.RESULT_SECURITY_ERROR:
                // fall-through
        }
        return ERROR_SYSTEM_ERROR;
    }

    private void registerAppSearchObserver(@NonNull TargetUser user) {
        AppSearchManager perUserAppSearchManager =
                mContext.createContextAsUser(user.getUserHandle(), /* flags= */ 0)
                        .getSystemService(AppSearchManager.class);
        if (perUserAppSearchManager == null) {
            Slog.d(TAG, "AppSearch Manager not found for user: " + user.getUserIdentifier());
            return;
        }
        FutureGlobalSearchSession futureGlobalSearchSession =
                new FutureGlobalSearchSession(perUserAppSearchManager, THREAD_POOL_EXECUTOR);
        AppFunctionMetadataObserverCallback appFunctionMetadataObserver =
                new AppFunctionMetadataObserverCallback(
                        user.getUserHandle(),
                        mContext.createContextAsUser(user.getUserHandle(), /* flags= */ 0));
        var unused =
                futureGlobalSearchSession
                        .registerObserverCallbackAsync(
                                "android",
                                new ObserverSpec.Builder().build(),
                                // AppFunctionMetadataObserver implements a simple callback that
                                // does not block and should be safe to run on any thread.
                                Runnable::run,
                                appFunctionMetadataObserver)
                        .whenComplete(
                                (voidResult, ex) -> {
                                    if (ex != null) {
                                        Slog.e(TAG, "Failed to register observer: ", ex);
                                    }
                                    futureGlobalSearchSession.close();
                                });
    }

    private void trySyncRuntimeMetadata(
            @NonNull TargetUser user, boolean shouldSetRuntimeMetadataSchemaUnconditionally) {
        MetadataSyncAdapter metadataSyncAdapter =
                MetadataSyncPerUser.getPerUserMetadataSyncAdapter(
                        user.getUserHandle(),
                        mContext.createContextAsUser(user.getUserHandle(), /* flags= */ 0));
        if (metadataSyncAdapter != null) {
            var unused =
                    metadataSyncAdapter
                            .submitSyncRequest(shouldSetRuntimeMetadataSchemaUnconditionally)
                            .whenComplete(
                                    (isSuccess, ex) -> {
                                        if (ex != null || !isSuccess) {
                                            Slog.e(TAG, "Sync was not successful");
                                        }
                                    });
        }
    }

    /**
     * Retrieves the lock object associated with the given package name.
     *
     * <p>This method returns the lock object from the {@code mLocks} map if it exists. If no lock
     * is found for the given package name, a new lock object is created, stored in the map, and
     * returned.
     */
    @VisibleForTesting
    @NonNull
    Object getLockForPackage(String callingPackage) {
        // Synchronized the access to mLocks to prevent race condition.
        synchronized (mLocks) {
            // By using a WeakHashMap, we allow the garbage collector to reclaim memory by removing
            // entries associated with unused callingPackage keys. Therefore, we remove the null
            // values before getting/computing a new value. The goal is to not let the size of this
            // map grow without an upper bound.
            mLocks.values().removeAll(Collections.singleton(null)); // Remove null values
            return mLocks.computeIfAbsent(callingPackage, k -> new Object());
        }
    }

    /**
     * Returns a new {@link SafeOneTimeExecuteAppFunctionCallback} initialized with a {@link
     * SafeOneTimeExecuteAppFunctionCallback.CompletionCallback} that logs the results and a {@link
     * SafeOneTimeExecuteAppFunctionCallback.BeforeCompletionCallback} that grants the temporary uri
     * permission to caller.
     */
    @VisibleForTesting
    SafeOneTimeExecuteAppFunctionCallback initializeSafeExecuteAppFunctionCallback(
            @NonNull ExecuteAppFunctionAidlRequest requestInternal,
            @NonNull IExecuteAppFunctionCallback executeAppFunctionCallback,
            int callingUid) {
        final boolean isDynamicAppFunction =
                android.app.appfunctions.flags.Flags.enableDynamicAppFunctions()
                        && mAppFunctionMetadataReader.isDynamicFunction(
                                requestInternal.getClientRequest().getTargetPackageName(),
                                requestInternal.getClientRequest().getFunctionIdentifier(),
                                requestInternal.getUserHandle());

        return new SafeOneTimeExecuteAppFunctionCallback(
                executeAppFunctionCallback,
                new SafeOneTimeExecuteAppFunctionCallback.BeforeCompletionCallback() {
                    @Override
                    public void beforeOnSuccess(@NonNull ExecuteAppFunctionResponse result) {
                        grantTemporaryUriPermissions(requestInternal, result, callingUid);
                    }
                },
                new SafeOneTimeExecuteAppFunctionCallback.CompletionCallback() {
                    @Override
                    public void finalizeOnSuccess(
                            @NonNull ExecuteAppFunctionResponse result,
                            long executionStartTimeMillis) {
                        logRequestResponse(requestInternal, result, /* exception= */ null);
                        mLoggerWrapper.logAppFunctionSuccess(
                                requestInternal,
                                result,
                                callingUid,
                                executionStartTimeMillis,
                                isDynamicAppFunction);
                        recordAppFunctionInteraction(requestInternal);
                    }

                    @Override
                    public void finalizeOnError(
                            @NonNull AppFunctionException error, long executionStartTimeMillis) {
                        logRequestResponse(requestInternal, /* response= */ null, error);
                        mLoggerWrapper.logAppFunctionError(
                                requestInternal,
                                error.getErrorCode(),
                                callingUid,
                                executionStartTimeMillis,
                                isDynamicAppFunction);
                        recordAppFunctionInteraction(requestInternal);
                    }
                });
    }

    /**
     * Logs the request and response to disk asynchronously.
     *
     * <p>This method is only executed for debuggable builds when the corresponding flag is enabled.
     */
    private void logRequestResponse(
            @NonNull ExecuteAppFunctionAidlRequest request,
            @Nullable ExecuteAppFunctionResponse response,
            @Nullable AppFunctionException exception) {
        if (!Build.isDebuggable()
                || !android.app.appfunctions.flags.Flags.enableRequestResponseLogging()) {
            return;
        }

        final AppFunctionRequestResponseLogger logger;
        synchronized (mRequestResponseLoggerPerUserLock) {
            logger = mRequestResponseLoggerPerUser.get(request.getUserHandle().getIdentifier());
        }
        if (logger != null) {
            IoThread.getExecutor()
                    .execute(() -> logger.log(request.getClientRequest(), response, exception));
        }
    }

    private void recordAppFunctionInteraction(@NonNull ExecuteAppFunctionAidlRequest aidlRequest) {
        if (!android.app.appfunctions.flags.Flags.enableAppInteractionApi()) return;
        Objects.requireNonNull(mAppInteractionService);

        final long accessTime = aidlRequest.getRequestWallTime();
        mAppInteractionService.noteAppInteraction(
                aidlRequest.getCallingPackage(),
                aidlRequest.getClientRequest().getTargetPackageName(),
                aidlRequest.getClientRequest().getAttribution(),
                accessTime,
                aidlRequest.getUserHandle().getIdentifier());
    }

    private static class AppFunctionMetadataObserverCallback implements ObserverCallback {
        @Nullable private final MetadataSyncAdapter mPerUserMetadataSyncAdapter;

        @NonNull UserHandle mUserHandle;

        AppFunctionMetadataObserverCallback(
                @NonNull UserHandle userHandle, @NonNull Context userContext) {
            mPerUserMetadataSyncAdapter =
                    MetadataSyncPerUser.getPerUserMetadataSyncAdapter(userHandle, userContext);
            mUserHandle = userHandle;
        }

        @Override
        public void onDocumentChanged(@NonNull DocumentChangeInfo documentChangeInfo) {
            if (mPerUserMetadataSyncAdapter == null) {
                return;
            }
            if (documentChangeInfo.getDatabaseName().equals(APP_FUNCTION_STATIC_METADATA_DB)
                    && documentChangeInfo.getNamespace().equals(APP_FUNCTION_STATIC_NAMESPACE)) {
                var unused =
                        mPerUserMetadataSyncAdapter.submitSyncRequest(
                                /* shouldSetRuntimeMetadataSchemaUnconditionally= */ false);
            }
        }

        @Override
        public void onSchemaChanged(@NonNull SchemaChangeInfo schemaChangeInfo) {
            if (mPerUserMetadataSyncAdapter == null) {
                return;
            }
            if (schemaChangeInfo.getDatabaseName().equals(APP_FUNCTION_STATIC_METADATA_DB)) {
                boolean shouldInitiateSync = false;
                for (String schemaName : schemaChangeInfo.getChangedSchemaNames()) {
                    if (schemaName.startsWith(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)) {
                        shouldInitiateSync = true;
                        break;
                    }
                }
                if (shouldInitiateSync) {
                    var unused =
                            mPerUserMetadataSyncAdapter.submitSyncRequest(
                                    /* shouldSetRuntimeMetadataSchemaUnconditionally= */ false);
                }
            }
        }
    }

    /** Throws when executing a disabled app function. */
    private static class DisabledAppFunctionException extends RuntimeException {
        private DisabledAppFunctionException(@NonNull String errorMessage) {
            super(errorMessage);
        }
    }
}
