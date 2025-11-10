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

package android.app.privatecompute;

import android.annotation.FlaggedApi;
import android.annotation.SuppressLint;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;

/**
 * Manager for interacting with the Private Compute Core (PCC) sandbox.
 */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
@SystemService(Context.PCC_SANDBOX_SERVICE)
public final class PccSandboxManager {

    private final IPccSandboxManager mService;
    private final Context mContext;

    /**
     * Creates an instance.
     *
     * @param service An interface to the backing service.
     * @param context A {@link Context}.
     * @hide
     */
    public PccSandboxManager(IPccSandboxManager service, Context context) {
        mService = service;
        mContext = context;
    }

    /**
     * Returns whether the given UID belongs to a Private Compute Services (PCS) package.
     * These are packages that hold the {@link
     * android.Manifest.permission#PROVIDE_PRIVATE_COMPUTE_SERVICES}.
     *
     * @param uid The UID to check.
     * @return {@code true} if the UID belongs to a PCS package, {@code false} otherwise.
     */
    @SuppressLint("RequiresPermission") // Method call doesn't require permission.
    public boolean isPrivateComputeServicesUid(int uid) {
        try {
            return mService.isPrivateComputeServicesUid(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}

