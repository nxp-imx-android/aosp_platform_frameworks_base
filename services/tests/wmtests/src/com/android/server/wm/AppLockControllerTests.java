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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;

import com.android.internal.os.BackgroundThread;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Build/Install/Run:
 * atest WmTests:AppLockControllerTests
 */
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppLockControllerTests extends WindowTestsBase {

    private static final String TEST_PACKAGE = "com.android.server.wm.testapp";
    private static final int TEST_USER_ID = 0;

    private AppLockController mAppLockController;
    private RecentTasks mRecentTasks;

    @Before
    public void setUp() throws Exception {
        mAppLockController = new AppLockController(mWm);
        spyOn(mAppLockController);

        mRecentTasks = mAtm.getRecentTasks();
        spyOn(mRecentTasks);
    }

    @Test
    public void testPackageMonitorOnPackageAppLockEnabled() {
        final Intent intent = createPackageAppLockBroadcast(TEST_PACKAGE, TEST_USER_ID,
                PackageManager.ACTION_PACKAGE_APP_LOCK_ENABLED_STATE_CHANGED, true);
        mAppLockController.mPackageMonitor.doHandlePackageEvent(intent);

        verify(mRecentTasks).onPackageAppLockEnabledChanged(TEST_PACKAGE, TEST_USER_ID, true);
    }

    @Test
    public void testPackageMonitorOnPackageAppLockDisabled() {
        final Intent intent = createPackageAppLockBroadcast(TEST_PACKAGE, TEST_USER_ID,
                PackageManager.ACTION_PACKAGE_APP_LOCK_ENABLED_STATE_CHANGED, false);
        mAppLockController.mPackageMonitor.doHandlePackageEvent(intent);

        verify(mRecentTasks).onPackageAppLockEnabledChanged(TEST_PACKAGE, TEST_USER_ID, false);
    }

    @Test
    public void testSystemReady() {
        spyOn(mAppLockController.mPackageMonitor);

        mAppLockController.systemReady();

        verify(mAppLockController.mPackageMonitor).register(eq(mWm.mContext), eq(UserHandle.ALL),
                eq(BackgroundThread.getHandler()));
    }

    private Intent createPackageAppLockBroadcast(String packageName, int userId, String action,
            boolean enabled) {
        Intent intent = new Intent(action, Uri.fromParts("package", packageName, null));
        intent.putExtra(Intent.EXTRA_USER_HANDLE, userId);
        final Bundle extras = new Bundle();
        extras.putBoolean(PackageManager.EXTRA_APP_LOCK_NEW_STATE, enabled);
        intent.putExtras(extras);
        return intent;
    }
}
