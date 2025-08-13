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

import static android.app.privatecompute.flags.Flags.enablePccFrameworkSupport;

import android.content.Context;
import android.content.pm.PackageManagerInternal;

import com.android.server.LocalServices;
import com.android.server.SystemService;

/**
 * Service that manages interactions with entities configured to run in the Private Compute Core
 * (PCC) sandbox.
 *
 * @see android.app.privatecompute.PccSandboxManager
 * @see android.app.privatecompute.PccEntity
 */
public class PccSandboxManagerService extends SystemService {
    private final PccSandboxManagerServiceImpl mServiceImpl;

    public PccSandboxManagerService(Context context) {
        super(context);
        mServiceImpl = new PccSandboxManagerServiceImpl(
                context, LocalServices.getService(PackageManagerInternal.class));
    }

    @Override
    public void onStart() {
        if (enablePccFrameworkSupport()) {
            publishBinderService(Context.PCC_SANDBOX_SERVICE, mServiceImpl);
        }
    }
}
