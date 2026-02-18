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

package com.android.server.privatecompute;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.app.privatecompute.DataMigrationToPccService;
import android.app.privatecompute.IDataMigrationToPccService;
import android.app.privatecompute.IMigrationRequestResultReceiver;
import android.app.privatecompute.IMigrationRequestResultSender;
import android.app.privatecompute.IPccSandboxManager;
import android.app.privatecompute.IPccSandboxManagerNative;
import android.app.privatecompute.MigrationException;
import android.app.privatecompute.MigrationRequestResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.ShellCommand;
import android.os.UserHandle;
import android.sysprop.PccProperties;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
  * Implementation of the {@link IPccSandboxManager} binder service.
  */
public class PccSandboxManagerServiceImpl extends IPccSandboxManager.Stub {

    private static final String TAG = "PccSandboxManagerServiceImpl";

    private final Context mContext;
    private final PackageManagerInternal mPackageManagerInternal;
    private final Injector mInjector;

    // Only instantiated when audit mode is enabled.
    @GuardedBy("mAuditLogLock")
    private @Nullable AuditModeContext mAuditModeContext = null;

    private final Object mAuditModeLock = new Object();
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private PccSandboxManagerInternal mInternal;
    private final PccSandboxManagerNativeImpl mNativeImpl = new PccSandboxManagerNativeImpl();

    public PccSandboxManagerServiceImpl(Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public PccSandboxManagerServiceImpl(Context context, Injector injector) {
        mContext = context;
        mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
        mInjector = injector;
    }

    @VisibleForTesting
    static class Injector {
        int getCallingUid() {
            return Binder.getCallingUid();
        }

        Handler getHandler(Looper looper) {
            return new Handler(looper);
        }

        boolean auditModeEnabled() {
            return PccProperties.audit_mode_enabled().orElse(false);
        }
    }

    public void setPccSandboxManagerInternal(PccSandboxManagerInternal internal) {
        mInternal = internal;
    }

    public ExecutorService getExecutorService() {
        return mExecutorService;
    }

    public IBinder getNativeBinder() {
        return mNativeImpl;
    }

    @Override
    @RequiresNoPermission
    public boolean isPrivateComputeServicesUid(int uid) {
        // Private Compute Services packages must be assigned from Application
        // UID range.
        if (!Process.isApplicationUid(uid)) {
            return false;
        }

        PackageManager pm = mContext.getPackageManager();
        final String[] packagesForUid = pm.getPackagesForUid(uid);
        if (packagesForUid == null || packagesForUid.length == 0) {
            return false;
        }

        for (String packageName : packagesForUid) {
            if (pm.checkPermission(
                    android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                    packageName) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }

        return false;
    }

    @Override
    @RequiresNoPermission
    public boolean isPccTrustedSystemComponent(int uid, String packageName) {
        if (mInternal == null) {
            return false;
        }
        return mInternal.isPccTrustedSystemComponent(uid, packageName);
    }

    @Override
    @RequiresNoPermission
    public void writeToAuditLog(@NonNull PersistableBundle bundle, @NonNull String packageName) {
        try {
            writeToAuditLogInternal(bundle, packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to write to audit log: " + e);
            // No feedback is given to the app.
        }
    }

    @Override
    @RequiresNoPermission
    public void batchWriteToAuditLog(
            @NonNull List<PersistableBundle> data, @NonNull String packageName) {
        try {
            writeToAuditLogInternal(data, packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to batch write to audit log: " + e);
            // No feedback is given to the app.
        }
    }

    /**
     * Internal method with feedback to the caller, for testing. Returns true if the write was
     * successfully scheduled.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    boolean writeToAuditLogInternal(@NonNull PersistableBundle bundle, @NonNull String packageName)
            throws SecurityException {
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(bundle);
        return writeToAuditLogInternal(data, packageName);
    }

    /**
     * Internal method with feedback to the caller, for testing. Returns true if the write was
     * successfully scheduled.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    boolean writeToAuditLogInternal(
            @NonNull List<PersistableBundle> data, @NonNull String packageName)
            throws SecurityException {
        final int callingUid = Binder.getCallingUid();
        if (!mPackageManagerInternal.isSameApp(
                packageName, callingUid, UserHandle.getUserId(callingUid))) {
            // We don't report the security exception to apps, but we log it.
            throw new SecurityException(
                    "Package name " + packageName + " does not match calling UID " + callingUid);
        }

        // Sanitize before locking.
        /* TODO: PccBundleSanitizationUtil should accept a PersistableBundle or BaseBundle
        try {
            PccBundleSanitizationUtil.sanitizeBundle(bundle);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Failed to sanitize bundle: " + e.getMessage());
        }
        */

        synchronized (mAuditModeLock) {
            if (!mInjector.auditModeEnabled()) {
                // If audit mode was toggled off, clean up, including writing pending data to disk.
                if (mAuditModeContext != null) {
                    mAuditModeContext.stopAuditing();
                    mAuditModeContext = null;
                }
                return false;
            }
            if (mAuditModeContext == null) {
                mAuditModeContext = AuditModeContext.create();
            }
            for (PersistableBundle bundle : data) {
                mAuditModeContext.writeToAuditLog(bundle, packageName);
            }
        }
        return true;
    }

    private class PccSandboxManagerNativeImpl extends IPccSandboxManagerNative.Stub {
        @Override
        @RequiresNoPermission
        public void writeToAuditLog(@NonNull PersistableBundle bundle) {
            String packageName = mContext.getPackageManager().getNameForUid(Binder.getCallingUid());
            writeToAuditLogInternal(bundle, packageName);
        }
    }

    @Override
    @RequiresNoPermission
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
            String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        (new Shell()).exec(this, in, out, err, args, callback, resultReceiver);
    }

    private class Shell extends ShellCommand {
        @Override
        public int onCommand(String cmd) {
            if (cmd == null) {
                return handleDefaultCommands(cmd);
            }
            final PrintWriter pw = getOutPrintWriter();
            switch (cmd) {
                case "add-allowed-package" -> {
                    final int callingUid = Binder.getCallingUid();
                    if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
                        pw.println("Error: must be root or shell to use this command");
                        return -1;
                    }
                    final String packageName = getNextArgRequired();
                    if (mInternal != null) {
                        mInternal.addTestAllowedPackage(packageName);
                        pw.println("Added " + packageName + " to allowed packages");
                    }
                    return 0;
                }
                case "remove-allowed-package" -> {
                    final int callingUid = Binder.getCallingUid();
                    if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
                        pw.println("Error: must be root or shell to use this command");
                        return -1;
                    }
                    final String packageName = getNextArgRequired();
                    if (mInternal != null) {
                        mInternal.removeTestAllowedPackage(packageName);
                        pw.println("Removed " + packageName + " from allowed packages");
                    }
                    return 0;
                }
                default -> {
                    return handleDefaultCommands(cmd);
                }
            }
        }

        @Override
        public void onHelp() {
            final PrintWriter pw = getOutPrintWriter();
            pw.println("PccSandboxManager commands:");
            pw.println("  help");
            pw.println("    Print this help text.");
            pw.println("  add-allowed-package PACKAGE");
            pw.println("    Add a package to the list of allowed PCC packages for testing.");
            pw.println("  remove-allowed-package PACKAGE");
            pw.println("    Remove a package from the list of allowed PCC packages for testing.");
        }
    }

    /**
     * Starts a non-PCC process for data migration.
     */
    @Override
    @RequiresNoPermission
    public void startNonPccProcessForDataMigration(IMigrationRequestResultReceiver callback) {
        final int callingUid = mInjector.getCallingUid();
        final PackageManager pm = mContext.getPackageManager();
        final String[] packages = pm.getPackagesForUid(callingUid);
        if (packages == null || packages.length == 0) {
            try {
                callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                        "Could not find package for calling UID " + callingUid);
            } catch (RemoteException e) {
                // Ignore
            }
            return;
        }

        Intent intent = new Intent(DataMigrationToPccService.SERVICE_INTERFACE);
        ResolveInfo resolvedService = null;
        String targetPackage = null;

        for (String pkg : packages) {
            intent.setPackage(pkg);
            ResolveInfo ri = pm.resolveService(intent, 0);
            if (ri != null && ri.serviceInfo != null) {
                resolvedService = ri;
                targetPackage = pkg;
                break;
            }
        }

        if (resolvedService == null) {
            try {
                callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                        "No data migration service found for calling package.");
            } catch (RemoteException e) {
                // Ignore
            }
            return;
        }

        // Only non-PCC to PCC data migration is supported.
        if (resolvedService.serviceInfo.shouldRunInPccSandbox()) {
            try {
                callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                        "Data migration service " + resolvedService.serviceInfo.name
                                + " is marked as a PCC component");
            } catch (RemoteException e) {
                // Ignore
            }
            return;
        }

        if (!android.Manifest.permission.BIND_DATA_MIGRATION_FOR_PRIVATECOMPUTE.equals(
                resolvedService.serviceInfo.permission)) {
            try {
                callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                        "Service " + resolvedService.serviceInfo.name + " does not require "
                                + "android.permission.BIND_DATA_MIGRATION_FOR_PRIVATECOMPUTE");
            } catch (RemoteException e) {
                // Ignore
            }
            return;
        }

        Intent bindIntent = new Intent(DataMigrationToPccService.SERVICE_INTERFACE);
        bindIntent.setComponent(new ComponentName(targetPackage, resolvedService.serviceInfo.name));

        final long token = Binder.clearCallingIdentity();
        try {
            boolean bound = mContext.bindServiceAsUser(bindIntent,
                    new MigrationServiceConnection(mContext, mInjector, callback),
                    Context.BIND_AUTO_CREATE, UserHandle.getUserHandleForUid(callingUid));

            if (!bound) {
                try {
                    callback.onError(MigrationException.ERROR_INVOCATION_FAILED,
                            "Failed to bind to service");
                } catch (RemoteException e) {
                    // Ignore
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private static class MigrationServiceConnection implements ServiceConnection {
        private final Context mContext;
        private final Injector mInjector;
        private final IMigrationRequestResultReceiver mCallback;
        private final AtomicBoolean mIsDone = new AtomicBoolean(false);
        private final Handler mHandler;
        private Runnable mTimeoutRunnable;

        MigrationServiceConnection(Context context, Injector injector,
                IMigrationRequestResultReceiver callback) {
            mContext = context;
            mInjector = injector;
            mCallback = callback;
            mHandler = mInjector.getHandler(BackgroundThread.get().getLooper());
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IDataMigrationToPccService migrationService =
                    IDataMigrationToPccService.Stub.asInterface(service);

            // Unbind after a timeout.
            mTimeoutRunnable = () -> reportError(MigrationException.ERROR_TIMEOUT,
                    "Migration timed out");
            mHandler.postDelayed(mTimeoutRunnable,
                    DataMigrationToPccService.MIGRATION_TIMEOUT_MS);

            try {
                migrationService.onMigrationRequested(new IMigrationRequestResultSender.Stub() {
                    @Override
                    @RequiresNoPermission
                    public void sendResult(MigrationRequestResult result) {
                        // TODO(): Call PccBundleSanitizationUtil.sanitizeBundle once it handles
                        // PersistableBundle for a depth check.
                        reportResult(result);
                    }
                });
            } catch (RemoteException e) {
                reportError(MigrationException.ERROR_INVOCATION_FAILED,
                        "RemoteException during migration request");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public void onBindingDied(ComponentName name) {
            reportError(MigrationException.ERROR_INVOCATION_FAILED, "Binding died");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            reportError(MigrationException.ERROR_INVOCATION_FAILED, "Null binding");
        }

        private void reportResult(MigrationRequestResult result) {
            if (mIsDone.compareAndSet(false, true)) {
                mHandler.removeCallbacks(mTimeoutRunnable);
                try {
                    mCallback.onResult(result);
                } catch (RemoteException e) {
                    // Ignore
                }
                unbind();
            }
        }

        private void reportError(int errorCode, String errorMessage) {
            if (mIsDone.compareAndSet(false, true)) {
                if (mTimeoutRunnable != null) {
                    mHandler.removeCallbacks(mTimeoutRunnable);
                }
                try {
                    mCallback.onError(errorCode, errorMessage);
                } catch (RemoteException e) {
                    // Ignore
                }
                unbind();
            }
        }

        private void unbind() {
            try {
                mContext.unbindService(this);
            } catch (IllegalArgumentException e) {
                // Ignore if already unbound
            }
        }
    }
}
