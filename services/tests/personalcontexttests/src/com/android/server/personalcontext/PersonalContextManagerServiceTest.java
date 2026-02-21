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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
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
import android.os.ParcelUuid;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.test.FakePermissionEnforcer;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.personalcontext.Flags;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.testing.TestableContext;
import android.util.ArrayMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.SystemService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE_FEATURE)
public class PersonalContextManagerServiceTest {
    private static final int USER_ID_1 = 10;
    private static final int USER_ID_2 = 11;

    private static final String TEST_PACKAGE_NAME = "test.package";
    private static final ParcelUuid TEST_COMPONENT_UUID = new ParcelUuid(UUID.randomUUID());

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
    private PersonalContextManagerInternal mLocalService;
    private SystemService.TargetUser mUser1;
    private SystemService.TargetUser mUser2;
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
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testRegisterInsightSurfaceClient_permissionsDenied_throwsSecurityException() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE);

        assertThrows(
                SecurityException.class,
                () ->
                        binderService.registerInsightSurfaceClient(
                                mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testRegisterInsightSurfaceClient_permissionFlagDisabled() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE);

        assertDoesNotThrow(
                () ->
                        binderService.registerInsightSurfaceClient(
                                mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testRegisterInsightSurfaceClient_flagDisabled_throwsSecurityException() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE);

        assertThrows(
                SecurityException.class,
                () ->
                        binderService.registerInsightSurfaceClient(
                                mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testIsEnabled_permissionsDenied_throwsSecurityException() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_READ_SETTINGS);

        assertThrows(
                SecurityException.class,
                () -> binderService.isEnabled(mContext.getUserId()));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testIsEnabled_permissionFlagDisabled() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_READ_SETTINGS);

        binderService.setEnabled(mContext.getUserId(), /* enabled= */ true);
        assertThat(binderService.isEnabled(mContext.getUserId())).isTrue();

        binderService.setEnabled(mContext.getUserId(), /* enabled= */ false);
        assertThat(binderService.isEnabled(mContext.getUserId())).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testIsEnabled_permissionFlagEnabled() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        // Read/write permissions were granted in setUp().

        binderService.setEnabled(mContext.getUserId(), /* enabled= */ true);
        assertThat(binderService.isEnabled(mContext.getUserId())).isTrue();

        binderService.setEnabled(mContext.getUserId(), /* enabled= */ false);
        assertThat(binderService.isEnabled(mContext.getUserId())).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testSetEnabled_permissionsDenied_throwsSecurityException() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        binderService.setEnabled(mContext.getUserId(), /* enabled= */ false);
        assertThat(binderService.isEnabled(mContext.getUserId())).isFalse();

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS);

        assertThrows(
                SecurityException.class,
                () -> binderService.setEnabled(mContext.getUserId(), /* enabled= */ true));

        assertThat(binderService.isEnabled(mContext.getUserId())).isFalse();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testSetEnabled_permissionFlagDisabled() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        binderService.setEnabled(mContext.getUserId(), /* enabled= */ false);
        assertThat(binderService.isEnabled(mContext.getUserId())).isFalse();

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS);

        assertDoesNotThrow(
                () -> binderService.setEnabled(mContext.getUserId(), /* enabled= */ true));

        assertThat(binderService.isEnabled(mContext.getUserId())).isTrue();
    }

    @Test
    public void testSetEnabled_enablesSetting() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        binderService.setEnabled(mContext.getUserId(), /* enabled= */ true);

        assertSecureSetting(mContext, Settings.Secure.PERSONAL_CONTEXT_ENABLED, 1);
    }

    @Test
    public void testSetEnabled_disablesSetting() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        binderService.setEnabled(mContext.getUserId(), /* enabled= */ false);

        assertSecureSetting(mContext, Settings.Secure.PERSONAL_CONTEXT_ENABLED, 0);
    }

    @Test
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
    @EnableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testPublishInsight_permissionDenied_throwsSecurityException() {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_INSIGHTS);

        BundleInsight insight = new BundleInsight.Builder().build();
        ContextInsightWrapper wrapper = new ContextInsightWrapper(insight);
        List<ContextInsightWrapper> insights = List.of(wrapper);

        assertThrows(
                SecurityException.class,
                () -> binderService.publishInsight(insights, TEST_COMPONENT_UUID, USER_ID_1));
    }

    @Test
    @DisableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testPublishInsight() throws RemoteException {
        PersonalContextManagerService.BinderService binderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        BundleInsight insight = new BundleInsight.Builder().build();
        ContextInsightWrapper wrapper = new ContextInsightWrapper(insight);
        List<ContextInsightWrapper> insights = List.of(wrapper);

        binderService.publishInsight(insights, TEST_COMPONENT_UUID, USER_ID_1);

        verify(mService)
                .startInsightWorkflow(
                        eq(USER_ID_1), eq(TEST_COMPONENT_UUID.getUuid()), eq(Set.of(insight)));
    }

    @Test
    public void testLocalService_publishTriggeringHint() {
        Set<ContextHint> hints = new HashSet<>();
        hints.add(new BundleHint.Builder().build());
        mLocalService.publishTriggeringHint(hints, null, USER_ID_1);

        verify(mService).startRefinerWorkflow(eq(USER_ID_1), anyInt(), eq(hints), any(), any());
    }

    private static void assertSecureSetting(Context context, String key, int value) {
        assertWithMessage("%s should be %s", key, value).that(Settings.Secure.getIntForUser(
                context.getContentResolver(),
                key,
                1, context.getUserId())).isEqualTo(value);
    }

    private static void assertDoesNotThrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            fail("Should not have thrown " + e);
        }
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
