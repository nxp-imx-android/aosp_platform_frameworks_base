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

import static android.content.pm.PackageManager.MATCH_PCC_ONLY;

import android.annotation.RequiresNoPermission;
import android.app.privatecompute.IPccSandboxManager;
import android.app.privatecompute.PccEntity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the {@link IPccSandboxManager} interface. This service provides information
 * about entities configured to run in the Private Compute Core (PCC) sandbox.
 * The service should also manage interactions between regular apps and PCC entities.
 *
 * @see PccEntity
 */
public class PccSandboxManagerServiceImpl extends IPccSandboxManager.Stub {

    private final Context mContext;
    private final PackageManagerInternal mPackageManagerInternal;

    public PccSandboxManagerServiceImpl(Context context,
            PackageManagerInternal packageManagerInternal) {
        mContext = context;
        mPackageManagerInternal = packageManagerInternal;
    }

    @RequiresNoPermission
    @Override
    public List<PccEntity> getPccEntities() {
        List<ApplicationInfo> pccApps =
                mPackageManagerInternal.getInstalledApplications(/* flags= */ MATCH_PCC_ONLY,
                        UserHandle.getCallingUserId(), Binder.getCallingUid());
        if (pccApps == null) {
            return new ArrayList<>();
        }
        List<PccEntity> pccEntities = new ArrayList<>(pccApps.size());
        for (ApplicationInfo appInfo : pccApps) {
            pccEntities.add(new PccEntity(appInfo.packageName));
        }
        return pccEntities;
    }
}
