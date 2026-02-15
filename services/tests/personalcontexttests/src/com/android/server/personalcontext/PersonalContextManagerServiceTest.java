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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.test.FakePermissionEnforcer;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersonalContextManagerServiceTest {
    private static final int USER_ID_1 = 10;
    private static final int USER_ID_2 = 11;

    private static final String TEST_PACKAGE_NAME = "test.package";

    private static final UserInfo USER_INFO_1 = new UserInfo(USER_ID_1, "user1", 0);
    private static final UserInfo USER_INFO_2 = new UserInfo(USER_ID_2, "user2", 0);
    private static final UserInfo SYSTEM_USER_INFO =
            new UserInfo(UserHandle.USER_SYSTEM, "system", 0);

    @Rule public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();

    @Rule
    public final PersonalContextTestableContext mContext =
            new PersonalContextTestableContext(getInstrumentation().getContext());

    @Mock private PackageManager mPackageManager;
    @Mock private ContentResolver mContentResolver;
    @Mock private PackageManagerInternal mPackageManagerInternal;
    private FakePermissionEnforcer mFakePermissionEnforcer;

    private PersonalContextManagerService mService;
    private PersonalContextManagerInternal mLocalService;
    private SystemService.TargetUser mUser1;
    private SystemService.TargetUser mUser2;
    private SystemService.TargetUser mSystemUser;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

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
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        mContext.addMockSystemService(Context.PERMISSION_ENFORCER_SERVICE, mFakePermissionEnforcer);

        mService = spy(new PersonalContextManagerService(mContext));
        mLocalService = mService.new LocalService();

        mUser1 = new SystemService.TargetUser(USER_INFO_1);
        mUser2 = new SystemService.TargetUser(USER_INFO_2);
        mSystemUser = new SystemService.TargetUser(SYSTEM_USER_INFO);
    }

    @Test
    public void testOnUserStarting_createsComponentManager() {
        mService.onUserStarting(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();
    }

    @Test
    public void testOnUserStarting_registersSettingContentObserver() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        verify(mContentResolver)
                .registerContentObserver(
                        eq(Settings.Secure.getUriFor(Settings.Secure.PERSONAL_CONTEXT_ENABLED)),
                        eq(false),
                        any(ContentObserver.class),
                        eq(UserHandle.USER_ALL));
    }

    @Test
    public void testOnUserUnlocked_registersComponentsAndMonitor() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        ContextComponentManager user1Manager = mService.getComponentManagerForUser(USER_ID_1);
        assertThat(user1Manager).isNotNull();

        // Verify that it tried to register components.
        verify(mPackageManager, atLeast(3)).queryIntentServices(any(), anyInt());
    }

    @Test
    public void testOnUserUnlocked_systemUser_registersInternalComponents() {
        mService.onUserStarting(mSystemUser);
        mService.onUserUnlocked(mSystemUser);

        ContextComponentManager systemManager =
                mService.getComponentManagerForUser(UserHandle.USER_SYSTEM);
        assertThat(systemManager).isNotNull();
        assertThat(systemManager.getRenderers()).isNotEmpty();
    }

    @Test
    public void testOnUserUnlocked_withoutStarting_isResilient() {
        // Should not crash, and should create the manager.
        mService.onUserUnlocked(mUser1);

        ContextComponentManager user1Manager = mService.getComponentManagerForUser(USER_ID_1);
        assertThat(user1Manager).isNotNull();
    }

    @Test
    public void testOnUserStopping_cleansUp() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();

        mService.onUserStopping(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNull();
        verify(mContentResolver).unregisterContentObserver(any(ContentObserver.class));
    }

    @Test
    public void testMultipleUserLifecycle() {
        // Start and unlock user 1
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();
        assertThat(mService.getComponentManagerForUser(USER_ID_2)).isNull();

        // Start and unlock user 2
        mService.onUserStarting(mUser2);
        mService.onUserUnlocked(mUser2);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();
        assertThat(mService.getComponentManagerForUser(USER_ID_2)).isNotNull();

        // Stop user 1
        mService.onUserStopping(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNull();
        assertThat(mService.getComponentManagerForUser(USER_ID_2)).isNotNull();

        // Stop user 2
        mService.onUserStopping(mUser2);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNull();
        assertThat(mService.getComponentManagerForUser(USER_ID_2)).isNull();
    }

    @Test
    public void testIsPersonalContextModeEnabled_modeUnset_returnsTrue() throws RemoteException {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        when(mPackageManagerInternal.getPersonalContextMode(any(), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_UNSET);

        boolean result = binderService.isPersonalContextModeEnabled(TEST_PACKAGE_NAME, USER_ID_1);
        assertThat(result).isTrue();

        verify(mPackageManagerInternal)
                .getPersonalContextMode(TEST_PACKAGE_NAME, Process.myUid(), USER_ID_1);
    }

    @Test
    public void testIsPersonalContextModeEnabled_modeOn_returnsTrue() throws RemoteException {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        when(mPackageManagerInternal.getPersonalContextMode(any(), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_USER_ON);

        boolean result = binderService.isPersonalContextModeEnabled(TEST_PACKAGE_NAME, USER_ID_1);
        assertThat(result).isTrue();

        verify(mPackageManagerInternal)
                .getPersonalContextMode(TEST_PACKAGE_NAME, Process.myUid(), USER_ID_1);
    }

    @Test
    public void testIsPersonalContextModeEnabled_modeOff_returnsFalse() throws RemoteException {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        when(mPackageManagerInternal.getPersonalContextMode(any(), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF);

        boolean result = binderService.isPersonalContextModeEnabled(TEST_PACKAGE_NAME, USER_ID_1);
        assertThat(result).isFalse();

        verify(mPackageManagerInternal)
                .getPersonalContextMode(TEST_PACKAGE_NAME, Process.myUid(), USER_ID_1);
    }

    @Test
    public void testSetPersonalContextModeEnabled_enabled_sendsOn() throws RemoteException {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        binderService.setPersonalContextModeEnabled(
                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ true);

        verify(mPackageManagerInternal)
                .setPersonalContextMode(
                        TEST_PACKAGE_NAME,
                        Process.myUid(),
                        USER_ID_1,
                        PackageManager.PERSONAL_CONTEXT_MODE_USER_ON);
    }

    @Test
    public void testSetPersonalContextModeEnabled_disabled_sendsOff() throws RemoteException {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        binderService.setPersonalContextModeEnabled(
                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ false);

        verify(mPackageManagerInternal)
                .setPersonalContextMode(
                        TEST_PACKAGE_NAME,
                        Process.myUid(),
                        USER_ID_1,
                        PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF);
    }

    @Test
    public void testSetPersonalContextModeEnabled_permissionsDenied_throwsSecurityException()
            throws RemoteException {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mFakePermissionEnforcer.revoke(Manifest.permission.CHANGE_PERSONAL_CONTEXT_MODE);

        assertThrows(
                SecurityException.class,
                () ->
                        binderService.setPersonalContextModeEnabled(
                                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ false));

        verifyNoInteractions(mPackageManagerInternal);
    }

    @Test
    @EnableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testPublishTriggeringHint_permissionDenied_throwsSecurityException() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);

        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);

        assertThrows(
                SecurityException.class,
                () -> binderService.publishTriggeringHint(hints, List.of(), List.of(), USER_ID_1));
    }

    @Test
    @DisableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testPublishTriggeringHint() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);
        binderService.publishTriggeringHint(hints, List.of(), List.of(), USER_ID_1);

        verify(mService)
                .startRefinerWorkflow(eq(USER_ID_1), anyInt(), eq(Set.of(hint)), any(), any());
    }

    @Test
    public void testPublishTriggeringHint_nullRenderTokens() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);
        binderService.publishTriggeringHint(hints, List.of(), List.of(), USER_ID_1);

        verify(mService)
                .startRefinerWorkflow(eq(USER_ID_1), anyInt(), eq(Set.of(hint)), any(), any());
    }

    @Test
    public void testLocalService_publishTriggeringHint() {
        Set<ContextHint> hints = new HashSet<>();
        hints.add(new BundleHint.Builder().build());
        mLocalService.publishTriggeringHint(hints, null, USER_ID_1);

        verify(mService).startRefinerWorkflow(eq(USER_ID_1), anyInt(), eq(hints), any(), any());
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
