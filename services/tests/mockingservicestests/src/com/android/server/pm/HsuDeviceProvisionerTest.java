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
package com.android.server.pm;

import static android.os.UserHandle.USER_NULL;
import static android.os.UserHandle.USER_SYSTEM;
import static android.provider.Settings.Global.DEVICE_PROVISIONED;
import static android.provider.Settings.Secure.BUGREPORT_IN_POWER_MENU;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

public final class HsuDeviceProvisionerTest {

    private static final String SETUP_WIZARD_PKG = "com.google.android.setupwizard";
    private static final String SETUP_WIZARD_ACTIVITY = "SetupWizardActivity";

    private static final String TAG = HsuDeviceProvisionerTest.class.getSimpleName();

    @UserIdInt
    private static final int MAIN_USER_ID = 4;

    @Rule public final Expect expect = Expect.create();
    @Rule
    public final ExtendedMockitoRule extendedMockito =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(UserManager.class)
                    .spyStatic(Settings.Global.class)
                    .spyStatic(Settings.Secure.class)
                    .build();
    @Mock private ContentResolver mMockContentResolver;
    @Mock private UserManagerService mMockUms;
    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;

    private HsuDeviceProvisioner mFixture;

    @Before
    public void setFixtures() {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        mFixture =
                new HsuDeviceProvisioner(
                    mMockContext, new Handler(Looper.getMainLooper()), mMockContentResolver);
    }

    @Test
    public void testInit_provisioned() {
        mockIsDeviceProvisioned(true);

        mFixture.init();

        verifyContentObserverNeverRegistered();
        verifyNoSecureSettingsSet();
    }

    @Test
    public void testInit_notProvisioned_setObserver() {
        mockIsDeviceProvisioned(false);

        mFixture.init();

        var contentObserver = verifyContentObserverRegistered();
        expect.that(contentObserver).isSameInstanceAs(mFixture);
        verifyNoSecureSettingsSet();
    }

    @Test
    public void testOnChange_notProvisioned_dontSetAnything() {
        mockIsDeviceProvisioned(false);
        mockQuerySetupWizardHomeActivity();

        mFixture.onChange(true);

        verifyNoSecureSettingsSet();
        verifyDisableSuwNeverCalled();
    }

    @Test
    public void testOnChange_provisioned_setUserSetupComplete() {
        mockIsDeviceProvisioned(true);
        mockQuerySetupWizardHomeActivity();

        mFixture.onChange(true);

        verifySettingCopied(USER_SETUP_COMPLETE, 1);
        verifyDisableSuwCalled();
    }

    @Test
    public void testOnChange_provisioned_copyBugreportInPowerMenu_RealUser() {
        mockIsDeviceProvisioned(true);
        mFixture.setBootUser(MAIN_USER_ID);
        mockSettingValue(Settings.Secure.BUGREPORT_IN_POWER_MENU, 1, MAIN_USER_ID);

        mFixture.onChange(true);

        verifySettingCopiedForUser(Settings.Secure.BUGREPORT_IN_POWER_MENU, 1, USER_SYSTEM);
    }

    @Test
    public void testOnChange_provisioned_copyBugreportInPowerMenu_NoUser() {
        mockIsDeviceProvisioned(true);
        mFixture.setBootUser(USER_NULL);

        mFixture.onChange(true);

        verifySettingNotCopied(Settings.Secure.BUGREPORT_IN_POWER_MENU);
    }

    @Test
    public void testOnChange_provisioned_copyBugreportInPowerMenu_SystemUser() {
        mockIsDeviceProvisioned(true);
        mFixture.setBootUser(USER_SYSTEM);

        mFixture.onChange(true);

        verifySettingNotCopied(Settings.Secure.BUGREPORT_IN_POWER_MENU);
    }

    private void mockIsDeviceProvisioned(boolean value) {
        Log.v(TAG, "mockIsDeviceProvisioned(" + value + ")");
        doReturn(value ? 1 : 0).when(() -> Settings.Global.getInt(any(), eq(DEVICE_PROVISIONED)));
    }

    private void mockSettingValue(String settingName, int value, @UserIdInt int userId) {
        Log.d(TAG, "mockSettingValue(" + settingName + ", " + value + ", " + userId + ")");
        doReturn(value).when(() -> Settings.Secure.getIntForUser(
                                eq(mMockContentResolver), eq(settingName), anyInt(), eq(userId)));
    }

    private void verifyNoSecureSettingsSet() {
        try {
            verify(() -> Settings.Secure.putInt(any(), any(), anyInt()), never());
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("Settings.Secure.putInt() should not have been called, verify() "
                    + "failed: %s", t).fail();
        }
    }

    private void verifyContentObserverUnregistered(ContentObserver contentObserver) {
        try {
            verify(mMockContentResolver).unregisterContentObserver(contentObserver);
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("ContentResolver (%s) was not unregistered, verify() failed: %s",
                    contentObserver, t).fail();
        }
    }

    private void verifyContentObserverNeverRegistered() {
        try {
            verify(mMockContentResolver, never())
                    .registerContentObserver(any(), anyBoolean(), any());
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("should not have registered a content observer, verify() failed: %s",
                    t).fail();
        }
    }

    private ContentObserver verifyContentObserverRegistered() {
        ArgumentCaptor<ContentObserver> captor = ArgumentCaptor.forClass(ContentObserver.class);
        verify(mMockContentResolver)
                .registerContentObserver(
                        eq(Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED)),
                        eq(false),
                        captor.capture());
        return captor.getValue();
    }

    private ResolveInfo createFakeResolveInfo(String packageName, String activityName) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = packageName;
        resolveInfo.activityInfo.name = activityName;
        resolveInfo.activityInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.activityInfo.applicationInfo.packageName = packageName;
        return resolveInfo;
    }

    private void mockQuerySetupWizardHomeActivity() {
        List<ResolveInfo> matches = Collections.singletonList(
            createFakeResolveInfo(SETUP_WIZARD_PKG, SETUP_WIZARD_ACTIVITY)
        );
        when(mMockPackageManager.queryIntentActivities(
            any(Intent.class), anyInt())).thenReturn(matches);
    }

    private void verifyDisableSuwCalled() {
        ComponentName expectedComponent =
            new ComponentName(SETUP_WIZARD_PKG, SETUP_WIZARD_ACTIVITY);
        verify(mMockPackageManager).setComponentEnabledSetting(
            expectedComponent,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
    }

    private void verifyDisableSuwNeverCalled() {
        verify(mMockPackageManager, never()).setComponentEnabledSetting(any(), anyInt(), anyInt());
    }

    private void verifySettingNotCopied(String settingName) {
        try {
            verify(() -> Settings.Secure.putInt(any(), eq(settingName), anyInt()), never());
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("Setting %s should not have been copied: %s", settingName, t).fail();
        }
    }

    private void verifySettingCopied(String settingName, int value) {
        try {
            verify(() -> Settings.Secure.putInt(mMockContentResolver, settingName, value));
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("Settings should have been copied: %s", t).fail();
        }
    }

    private void verifySettingCopiedForUser(String settingName, int value, @UserIdInt int userId) {
        try {
            verify(() -> Settings.Secure.putIntForUser(
                                    mMockContentResolver, settingName, value, userId));
        } catch (Throwable t) {
            Log.e(TAG, "verify failure:", t);
            expect.withMessage("Settings should have been copied: %s", t).fail();
        }
    }
}
