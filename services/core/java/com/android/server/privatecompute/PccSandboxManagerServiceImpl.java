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
import android.os.PersistableBundle;
import android.os.Process;

import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of the {@link IPccSandboxManager} binder service.
 */
public class PccSandboxManagerServiceImpl extends IPccSandboxManager.Stub {

    private static final String TAG = "PccSandboxManagerServiceImpl";

    private final Context mContext;

    // Only instantiated when audit mode is enabled.
    @GuardedBy("mAuditLogLock")
    private @Nullable AuditModeContext mAuditModeContext = null;

    private final Object mAuditModeLock = new Object();
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    public PccSandboxManagerServiceImpl(Context context) {
        mContext = context;
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
    public void writeToAuditLog(@NonNull PersistableBundle data) {
        synchronized (mAuditModeLock) {
            // TODO: introduce a system property to toggle audit mode on/off.
            boolean auditModeEnabled = true;
            if (!auditModeEnabled) {
                // If audit mode was toggled off, clean up, including writing pending data to disk.
                if(mAuditModeContext != null){
                    mAuditModeContext.stopAuditing();
                    mAuditModeContext = null;
                }
                return;
            }
            if (mAuditModeContext == null) {
                mAuditModeContext = AuditModeContext.create(Executors.newSingleThreadExecutor());
            }
            // TODO: Once ag/36599762 is merged, call sanitizeBundle(data) on the incoming data
            mAuditModeContext.writeToAuditLog(data);
        }
    }
}
