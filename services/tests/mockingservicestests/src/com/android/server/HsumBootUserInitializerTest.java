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
package com.android.server;

import static android.multiuser.Flags.FLAG_CREATE_INITIAL_USER;
import static android.os.UserHandle.USER_NULL;
import static android.os.UserHandle.USER_SYSTEM;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.annotation.SpecialUsers.CanBeNULL;
import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Log;

import com.android.server.am.ActivityManagerService;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.UserManagerInternal;
import com.android.server.utils.TimingsTraceAndSlog;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;

public final class HsumBootUserInitializerTest {

    private static final String TAG = HsumBootUserInitializerTest.class.getSimpleName();

    @UserIdInt
    private static final int NON_SYSTEM_USER_ID = 42;

    @Rule
    public final Expect expect = Expect.create();

    // NOTE: replace by ExtendedMockitoRule once it needs to mock UM.isHSUM() or other methods
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Rule
    public final SetFlagsRule setFlagsRule =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    @Mock
    private UserManagerInternal mMockUmi;
    @Mock
    private ActivityManagerService mMockAms;
    @Mock
    private PackageManagerService mMockPms;
    @Mock
    private ContentResolver mMockContentResolver;

    @Nullable // Must be created in the same thread that it's used
    private TimingsTraceAndSlog mTracer;

    @Before
    public void setDefaultExpectations() throws Exception {
        mockGetMainUserId(USER_NULL);
        mockGetUserIds(USER_SYSTEM);
        mockCreateNewUser(NON_SYSTEM_USER_ID);
    }

    @After
    public void expectAllTracingCallsAreFinished() {
        if (mTracer == null) {
            return;
        }
        var unfinished = mTracer.getUnfinishedTracesForDebug();
        if (!unfinished.isEmpty()) {
            expect.withMessage("%s unfinished tracing calls: %s", unfinished.size(), unfinished)
                    .fail();
        }
    }

    @Test
    @EnableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_shouldAlwaysHaveMainUserFalse_shouldCreateInitialUserFalse_noUsers_dontCreateUser() {
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ false,
                /* shouldCreateInitialUser= */ false);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    @Test
    @EnableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_shouldAlwaysHaveMainUserFalse_shouldCreateInitialUserFalse_hasUser_dontCreateUser() {
        mockGetUserIds(USER_SYSTEM, NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ false,
                /* shouldCreateInitialUser= */ false);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    @Test
    @EnableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_shouldAlwaysHaveMainUserFalse_shouldCreateInitialUserTrue_noUser_createsUser() {
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ false,
                /* shouldCreateInitialUser= */ true);

        initializer.init(mTracer);

        expectAdminUserCreated();
    }

    @Test
    @EnableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_shouldAlwaysHaveMainUserFalse_shouldCreateInitialUserTrue_hasUser_dontCreateUser() {
        mockGetUserIds(USER_SYSTEM, NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ false,
                /* shouldCreateInitialUser= */ true);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    @Test
    @EnableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_shouldAlwaysHaveMainUserTrue_shouldCreateInitialUserFalse_noMainUser_createsMainUser() {
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ true,
                /* shouldCreateInitialUser= */ false);

        initializer.init(mTracer);

        expectMainUserCreated();
    }

    @Test
    @EnableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_shouldAlwaysHaveMainUserTrue_shouldCreateInitialUserFalse_hasNonMainUser_createsMainUser() {
        mockGetUserIds(USER_SYSTEM, NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ true,
                /* shouldCreateInitialUser= */ false);

        initializer.init(mTracer);

        expectMainUserCreated();
    }

    @Test
    @EnableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_shouldAlwaysHaveMainUserTrue_shouldCreateInitialUserFalse_hasMainUser_dontCreateUser() {
        mockGetMainUserId(NON_SYSTEM_USER_ID);
        mockGetUserIds(USER_SYSTEM, NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ true,
                /* shouldCreateInitialUser= */ false);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    @Test
    @EnableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_shouldAlwaysHaveMainUserTrue_shouldCreateInitialUserTrue_hasNonMainUser_createsMainUser() {
        mockGetUserIds(USER_SYSTEM, NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ true,
                /* shouldCreateInitialUser= */ true);

        initializer.init(mTracer);

        expectMainUserCreated();
    }

    // TODO(b/409650316): remove tests below after flag's completely pushed

    @Test
    @DisableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_flagDisabled_shouldAlwaysHaveMainUserTrue_shouldCreateInitialUserTrue_noUser_createsMainUser() {
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ true,
                /* shouldCreateInitialUser= */ true);

        initializer.init(mTracer);

        expectMainUserCreated();
    }

    @Test
    @DisableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_flagDisabled_shouldAlwaysHaveMainUserTrue_shouldCreateInitialUserTrue_hasMainUser_dontCreateUser() {
        mockGetMainUserId(NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ true,
                /* shouldCreateInitialUser= */ true);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    @Test
    @DisableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_flagDisabled_shouldAlwaysHaveMainUserFalse_shouldCreateInitialUserTrue_noUser_createsAdminUser()
            throws Exception {
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ false,
                /* shouldCreateInitialUser= */ true);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    @Test
    @DisableFlags(FLAG_CREATE_INITIAL_USER)
    public void testInit_flagDisabled_shouldAlwaysHaveMainUserFalse_shouldCreateInitialUserTrue_hasUser_dontCreateUser() {
        mockGetUserIds(USER_SYSTEM, NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ false,
                /* shouldCreateInitialUser= */ true);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    private HsumBootUserInitializer createHsumBootUserInitializer(
            boolean shouldAlwaysHaveMainUser) {
        return createHsumBootUserInitializer(shouldAlwaysHaveMainUser,
                /* shouldCreateInitialUser= */ false);
    }

    private HsumBootUserInitializer createHsumBootUserInitializer(
            boolean shouldAlwaysHaveMainUser, boolean shouldCreateInitialUser) {
        mTracer = new TimingsTraceAndSlog(TAG);
        return new HsumBootUserInitializer(mMockUmi, mMockAms, mMockPms, mMockContentResolver,
                shouldAlwaysHaveMainUser, shouldCreateInitialUser);
    }

    private void expectMainUserCreated() {
        expectUserCreated(UserInfo.FLAG_ADMIN | UserInfo.FLAG_MAIN);
    }

    private void expectAdminUserCreated() {
        expectUserCreated(UserInfo.FLAG_ADMIN);
    }

    private void expectUserCreated(@UserInfoFlag int flags) {
        try {
            verify(mMockUmi).createUserEvenWhenDisallowed(null,
                    UserManager.USER_TYPE_FULL_SECONDARY, flags, null, null);
        } catch (Exception e) {
            String msg = "didn't create user with flags " + flags;
            Log.e(TAG, msg, e);
            expect.withMessage(msg).fail();
        }
    }

    private void expectNoUserCreated() {
        try {
            verify(mMockUmi, never()).createUserEvenWhenDisallowed(any(), any(), anyInt(), any(),
                    any());
        } catch (Exception e) {
            String msg = "shouldn't have created any user";
            Log.e(TAG, msg, e);
            expect.withMessage(msg).fail();
        }

        // Since the user was not created, we can automatically infer that the boot user should not
        // have been set as well
        expectSetBootUserIdNeverCalled();
    }

    private void expectSetBootUserId(@UserIdInt int userId) {
        try {
            verify(mMockUmi).setBootUserId(userId);
        } catch (Exception e) {
            String msg = "didn't call setBootUserId(" +  userId + ")";
            Log.e(TAG, msg, e);
            expect.withMessage(msg).fail();
        }
    }

    private void expectSetBootUserIdNeverCalled() {
        try {
            verify(mMockUmi, never()).setBootUserId(anyInt());
        } catch (Exception e) {
            String msg = "setBootUserId() should never be called";
            Log.e(TAG, msg, e);
            expect.withMessage(msg).fail();
        }
    }

    private void mockCreateNewUser(@UserIdInt int userId) throws Exception {
        @SuppressWarnings("deprecation")
        UserInfo userInfo = new UserInfo();
        userInfo.id = userId;
        Log.d(TAG, "createUserEvenWhenDisallowed() will return " + userInfo);
        when(mMockUmi.createUserEvenWhenDisallowed(any(), any(), anyInt(), any(), any()))
                .thenReturn(userInfo);
    }

    private void mockGetMainUserId(@CanBeNULL @UserIdInt int userId) {
        Log.d(TAG, "mockGetMainUserId(): " + userId);
        when(mMockUmi.getMainUserId()).thenReturn(userId);
    }

    private void mockGetUserIds(@UserIdInt int... userIds) {
        Log.d(TAG, "mockGetUserIds(): " + Arrays.toString(userIds));
        when(mMockUmi.getUserIds()).thenReturn(userIds);
    }
}
