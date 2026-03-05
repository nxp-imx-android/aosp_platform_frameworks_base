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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UriGrantsManager;
import android.app.appfunctions.AppFunctionAccessServiceInterface;
import android.app.appfunctions.AppFunctionManagerConfiguration;
import android.app.appfunctions.flags.Flags;
import android.content.Context;
import android.content.pm.PackageManagerInternal;

import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.appfunctions.allowlist.SystemAppFunctionAllowlistReader;
import com.android.server.appfunctions.dynamic.MultiUserDynamicAppFunctionRegistry;
import com.android.server.appinteraction.AppInteractionService;
import com.android.server.appinteraction.AppInteractionServiceImpl;
import com.android.server.uri.UriGrantsManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.util.Objects;

/** Service that manages app functions. */
public class AppFunctionManagerService extends SystemService {
    private final AppFunctionManagerServiceImpl mServiceImpl;

    @Nullable private AppInteractionService mAppInteractionService = null;

    public AppFunctionManagerService(Context context) {
        super(context);
        if (Flags.enableAppInteractionApi()) {
            mAppInteractionService = new AppInteractionServiceImpl(context);
        }
        mServiceImpl =
                new AppFunctionManagerServiceImpl(
                        context,
                        LocalServices.getService(PackageManagerInternal.class),
                        LocalServices.getService(AppFunctionAccessServiceInterface.class),
                        UriGrantsManager.getService(),
                        LocalServices.getService(UriGrantsManagerInternal.class),
                        new AppFunctionsLoggerWrapper(context),
                        MultiUserDynamicAppFunctionRegistry.getInstance(),
                        mAppInteractionService,
                        new AppFunctionMetadataReader(
                                MultiUserDynamicAppFunctionRegistry.getInstance(),
                                new AppFunctionsMetadataCache(context),
                                new ServiceConfigImpl()),
                        LocalServices.getService(ActivityTaskManagerInternal.class),
                        SystemAppFunctionAllowlistReader.getInstance());
    }

    @Override
    public void onStart() {
        if (AppFunctionManagerConfiguration.isSupported(getContext())) {
            publishBinderService(Context.APP_FUNCTION_SERVICE, mServiceImpl);
        }
        if (Flags.enableAppInteractionApi()) {
            publishLocalService(
                    AppInteractionService.class, Objects.requireNonNull(mAppInteractionService));
        }
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        mServiceImpl.onUserUnlocked(user);
    }

    @Override
    public void onUserStopping(@NonNull TargetUser user) {
        mServiceImpl.onUserStopping(user);
    }

    @Override
    public void onUserStopped(@NonNull TargetUser user) {
        mServiceImpl.onUserStopped(user);
    }

    @Override
    public void onUserStarting(@NonNull TargetUser user) {
        mServiceImpl.onUserStarting(user);
    }
}
