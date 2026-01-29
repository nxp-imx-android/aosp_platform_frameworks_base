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
import android.app.privatecompute.IPccSandboxManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.ShellCommand;
import android.os.UserHandle;
import android.sysprop.PccProperties;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    public PccSandboxManagerServiceImpl(Context context) {
        mContext = context;
        mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
        mInjector = new Injector();
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public PccSandboxManagerServiceImpl(Context context, Injector injector) {
        mContext = context;
        mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
        mInjector = injector;
    }

    public void setPccSandboxManagerInternal(PccSandboxManagerInternal internal) {
        mInternal = internal;
    }

    public ExecutorService getExecutorService() {
        return mExecutorService;
    }

    @VisibleForTesting
    static class Injector {
        boolean auditModeEnabled() {
            return PccProperties.audit_mode_enabled().orElse(false);
        }
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
    public void writeToAuditLog(@NonNull PersistableBundle bundle, @NonNull String packageName) {
        try {
            writeToAuditLogInternal(bundle, packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to write to audit log: " + e);
            // No feedback is given to the app.
        }
    }

    /** Internal method with feedback to the caller, for testing. Returns true if the write was
     * successfully scheduled.*/
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    boolean writeToAuditLogInternal(@NonNull PersistableBundle bundle, @NonNull String packageName)
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
            mAuditModeContext.writeToAuditLog(bundle, packageName);
        }
        return true;
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
}
