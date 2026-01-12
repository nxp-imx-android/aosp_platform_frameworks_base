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

import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.UserHandle;
import com.android.internal.annotations.GuardedBy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.app.privatecompute.IPccSandboxManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.PersistableBundle;
import android.os.Process;

import android.util.Log;
import com.android.internal.annotations.VisibleForTesting;

import com.android.server.LocalServices;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;

/**
 * Implementation of the {@link IPccSandboxManager} binder service.
 */
public class PccSandboxManagerServiceImpl extends IPccSandboxManager.Stub {

    private static final String TAG = "PccSandboxManagerServiceImpl";

    private final Context mContext;
    private final PackageManagerInternal mPackageManagerInternal;

    // Only instantiated when audit mode is enabled.
    @GuardedBy("mAuditLogLock")
    private @Nullable AuditModeContext mAuditModeContext = null;

    private final Object mAuditModeLock = new Object();
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    public PccSandboxManagerServiceImpl(Context context) {
        mContext = context;
        mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
    }

    public ExecutorService getExecutorService() {
        return mExecutorService;
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

    /** Internal method with feedback to the caller, for testing. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void writeToAuditLogInternal(@NonNull PersistableBundle bundle, @NonNull String packageName)
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
            // TODO: introduce a system property to toggle audit mode on/off.
            boolean auditModeEnabled = true;
            if (!auditModeEnabled) {
                // If audit mode was toggled off, clean up, including writing pending data to disk.
                if (mAuditModeContext != null) {
                    mAuditModeContext.stopAuditing();
                    mAuditModeContext = null;
                }
                return;
            }
            if (mAuditModeContext == null) {
                mAuditModeContext = AuditModeContext.create();
            }
            mAuditModeContext.writeToAuditLog(bundle, packageName);
        }
    }
}
