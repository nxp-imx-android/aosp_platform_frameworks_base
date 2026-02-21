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

package com.android.server.personalcontext;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.os.UserHandle;
import android.os.test.FakePermissionEnforcer;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.personalcontext.Flags;
import android.testing.TestableContext;
import android.util.ArrayMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.SystemService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersonalContextManagerServiceDisabledTest {
    private static final int USER_ID_1 = 10;
    private static final int USER_ID_2 = 11;
    private static final UserInfo USER_INFO_1 = new UserInfo(USER_ID_1, "user1", 0);
    private static final UserInfo USER_INFO_2 = new UserInfo(USER_ID_2, "user2", 0);
    private static final UserInfo SYSTEM_USER_INFO =
            new UserInfo(UserHandle.USER_SYSTEM, "system", 0);

    @Rule public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final PersonalContextTestableContext mContext =
            new PersonalContextTestableContext(getInstrumentation().getContext());

    @Mock private PackageManager mPackageManager;
    @Mock private ContentResolver mContentResolver;
    @Mock private PackageManagerInternal mPackageManagerInternal;
    private FakePermissionEnforcer mFakePermissionEnforcer;

    private PersonalContextManagerService mService;
    private SystemService.TargetUser mUser1;
    private SystemService.TargetUser mSystemUser;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLocalServiceKeeperRule.overrideLocalService(
                PackageManagerInternal.class, mPackageManagerInternal);

        mContext.setMockPackageManager(mPackageManager);

        mContext.addMockUserContext(UserHandle.of(USER_ID_1), mPackageManager);
        mContext.addMockUserContext(UserHandle.of(USER_ID_2), mPackageManager);
        mContext.addMockUserContext(UserHandle.SYSTEM, mPackageManager);

        mContext.getTestablePermissions()
                .setPermission(Manifest.permission.INTERACT_ACROSS_USERS, PERMISSION_GRANTED);
        mFakePermissionEnforcer = new FakePermissionEnforcer();
        mFakePermissionEnforcer.grant(Manifest.permission.CHANGE_PERSONAL_CONTEXT_MODE);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_READ_SETTINGS);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        mContext.addMockSystemService(Context.PERMISSION_ENFORCER_SERVICE, mFakePermissionEnforcer);

        mService = spy(new PersonalContextManagerService(mContext));

        mUser1 = new SystemService.TargetUser(USER_INFO_1);
        mSystemUser = new SystemService.TargetUser(SYSTEM_USER_INFO);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE_FEATURE)
    public void testOnUserStartingWhenDisabled_doesNotRegistersSettingContentObserver() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        verify(mContentResolver, never())
                .registerContentObserver(
                        eq(Settings.Secure.getUriFor(Settings.Secure.PERSONAL_CONTEXT_ENABLED)),
                        eq(false),
                        any(ContentObserver.class),
                        eq(UserHandle.USER_ALL));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE_FEATURE)
    public void testOnUserUnlocked_systemUser_doesNotregisterInternalComponents() {
        mService.onUserStarting(mSystemUser);
        mService.onUserUnlocked(mSystemUser);

        ContextComponentManager systemManager =
                mService.getComponentManagerForUser(UserHandle.USER_SYSTEM);
        assertThat(systemManager).isNull();
    }

    private final class PersonalContextTestableContext extends TestableContext {
        private final ArrayMap<UserHandle, Context> mMockUserContexts = new ArrayMap<>();

        PersonalContextTestableContext(Context base) {
            super(base);
        }

        private void addMockUserContext(UserHandle userHandle, PackageManager packageManager) {
            Context userContext = mock(Context.class);
            when(userContext.getPackageManager()).thenReturn(packageManager);
            when(userContext.getContentResolver()).thenReturn(mContentResolver);
            when(userContext.getUser()).thenReturn(userHandle);
            mMockUserContexts.put(userHandle, userContext);
        }

        @Override
        public Context createContextAsUser(UserHandle user, int flags) {
            return mMockUserContexts.get(user);
        }
    }
}
