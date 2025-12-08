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

package com.android.server.wm;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_APP_LOCK;

import android.content.Context;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.internal.protolog.ProtoLog;

/**
 * Manages the App Lock feature within {@link ActivityTaskManagerService} and
 * {@link WindowManagerService}.
 */
final class AppLockController {

    private final Context mContext;
    private final ActivityTaskManagerService mAtmService;

    private RecentTasks mRecentTasks;

    /**
     * Monitors package changes related to App Lock enabled state and notifies {@link RecentTasks}.
     */
    @VisibleForTesting
    final PackageMonitor mPackageMonitor = new PackageMonitor() {
        @Override
        public void onPackageAppLockEnabled(String packageName) {
            super.onPackageAppLockEnabled(packageName);

            final int userId = getChangingUserId();
            ProtoLog.d(WM_DEBUG_APP_LOCK, "onPackageAppLockEnabled: packageName=%s, userId=%d",
                    packageName, userId);
            getRecentTasks().onPackageAppLockEnabledChanged(packageName, userId,
                    /* enabled= */ true);
        }

        @Override
        public void onPackageAppLockDisabled(String packageName) {
            super.onPackageAppLockDisabled(packageName);

            final int userId = getChangingUserId();
            ProtoLog.d(WM_DEBUG_APP_LOCK, "onPackageAppLockDisabled: packageName=%s, userId=%d",
                    packageName, userId);
            getRecentTasks().onPackageAppLockEnabledChanged(packageName, userId,
                    /* enabled= */ false);
        }
    };

    AppLockController(WindowManagerService wmService) {
        mContext = wmService.mContext;
        mAtmService = wmService.mAtmService;
    }

    private RecentTasks getRecentTasks() {
        if (mRecentTasks == null) {
            mRecentTasks = mAtmService.getRecentTasks();
        }
        return mRecentTasks;
    }

    /**
     * Initializes {@link AppLockController}. Should be called after
     * {@link com.android.server.SystemService#PHASE_SYSTEM_SERVICES_READY}.
     */
    void systemReady() {
        mPackageMonitor.register(mContext, UserHandle.ALL, BackgroundThread.getHandler());
    }
}
