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

import static com.android.server.HsumBootUserInitializerTest.ExpectedResult.ADMIN_USER_CREATED;
import static com.android.server.HsumBootUserInitializerTest.ExpectedResult.MAIN_USER_CREATED;
import static com.android.server.HsumBootUserInitializerTest.ExpectedResult.NO_USER_CREATED;
import static com.android.server.HsumBootUserInitializerTest.InitialUsers.SYSTEM_AND_MAIN;
import static com.android.server.HsumBootUserInitializerTest.InitialUsers.SYSTEM_AND_NON_MAIN;
import static com.android.server.HsumBootUserInitializerTest.InitialUsers.SYSTEM_ONLY;

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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collection;

// NOTE: rename to HsumBootUserInitializerInitMethodTest if it needs to test other methods
@RunWith(Parameterized.class)
public final class HsumBootUserInitializerTest {

    private static final String TAG = HsumBootUserInitializerTest.class.getSimpleName();

    @UserIdInt
    private static final int NON_SYSTEM_USER_ID = 42;

    @UserIdInt
    private static final int MAIN_USER_ID = 108;

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

    private final boolean mShouldAlwaysHaveMainUser;
    private final boolean mShouldCreateInitialUser;
    private final InitialUsers mInitialUsers;
    private final ExpectedResult mExpectedResult;

    /** Useless javadoc to make checkstyle happy... */
    @Parameters(name = "{index}: hasMain={0},createInitial={1},initial={2},result={3}")
    public static Collection<Object[]> junitParametersPassedToConstructor() {
        return Arrays.asList(new Object[][] {
                // shouldAlwaysHaveMainUser false, shouldCreateInitialUser false
                { false, false, SYSTEM_ONLY, NO_USER_CREATED },
                { false, false, SYSTEM_AND_MAIN, NO_USER_CREATED },
                { false, false, SYSTEM_AND_NON_MAIN, NO_USER_CREATED },
                // shouldAlwaysHaveMainUser false, shouldCreateInitialUser true
                { false, true, SYSTEM_ONLY, ADMIN_USER_CREATED},
                { false, true, SYSTEM_AND_MAIN, NO_USER_CREATED },
                { false, true, SYSTEM_AND_NON_MAIN, NO_USER_CREATED},
                // shouldAlwaysHaveMainUser true, shouldCreateInitialUser false
                { true, false, SYSTEM_ONLY, MAIN_USER_CREATED},
                { true, false, SYSTEM_AND_MAIN, NO_USER_CREATED },
                { true, false, SYSTEM_AND_NON_MAIN, MAIN_USER_CREATED},
                // shouldAlwaysHaveMainUser true, shouldCreateInitialUser true
                { true, true, SYSTEM_ONLY, MAIN_USER_CREATED},
                { true, true, SYSTEM_AND_MAIN, NO_USER_CREATED },
                { true, true, SYSTEM_AND_NON_MAIN, MAIN_USER_CREATED}
        });
    }

    public HsumBootUserInitializerTest(boolean shouldAlwaysHaveMainUser,
            boolean shouldCreateInitialUser, InitialUsers initialUsers,
            ExpectedResult expectedResult) {
        mShouldAlwaysHaveMainUser = shouldAlwaysHaveMainUser;
        mShouldCreateInitialUser = shouldCreateInitialUser;
        mInitialUsers = initialUsers;
        mExpectedResult = expectedResult;
        Log.i(TAG, "Constructor: shouldAlwaysHaveMainUser=" + shouldAlwaysHaveMainUser
                + ", shouldCreateInitialUser=" + shouldCreateInitialUser
                + ", initialUsers=" + initialUsers + ",expectedResult=" + expectedResult);
    }

    @Before
    public void setDefaultExpectations() throws Exception {
        switch (mInitialUsers) {
            case SYSTEM_ONLY:
                mockGetUserIds(USER_SYSTEM);
                mockGetMainUserId(USER_NULL);
                break;
            case SYSTEM_AND_MAIN:
                mockGetUserIds(USER_SYSTEM, MAIN_USER_ID);
                mockGetMainUserId(MAIN_USER_ID);
                break;
            case SYSTEM_AND_NON_MAIN:
                mockGetUserIds(USER_SYSTEM, NON_SYSTEM_USER_ID);
                mockGetMainUserId(USER_NULL);
                break;
        }
        // NOTE: need to mock createNewUser() as the user id is used on Slog.
        switch (mExpectedResult) {
            case ADMIN_USER_CREATED:
                mockCreateNewUser(NON_SYSTEM_USER_ID);
                break;
            case MAIN_USER_CREATED:
                mockCreateNewUser(MAIN_USER_ID);
                break;
            default:
                // don't need to mock it
        }
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
    public void testFlagEnabled() {
        var initializer = createHsumBootUserInitializer(mShouldAlwaysHaveMainUser,
                mShouldCreateInitialUser);

        initializer.init(mTracer);

        switch (mExpectedResult) {
            case ADMIN_USER_CREATED:
                expectAdminUserCreated();
                expectSetBootUserId(NON_SYSTEM_USER_ID);
                break;
            case MAIN_USER_CREATED:
                expectMainUserCreated();
                expectSetBootUserId(MAIN_USER_ID);
                break;
            case NO_USER_CREATED:
                expectNoUserCreated();
                break;
        }
    }

    // TODO(b/409650316): remove tests below after flag's completely pushed
    @Test
    @DisableFlags(FLAG_CREATE_INITIAL_USER)
    public void testFlagDisabled() {
        var initializer =
                createHsumBootUserInitializer(mShouldAlwaysHaveMainUser, mShouldCreateInitialUser);

        initializer.init(mTracer);

        switch (mExpectedResult) {
            // When the flag is disabled, it shouldn't trigger the "create admin user" workflow
            case ADMIN_USER_CREATED:
            case NO_USER_CREATED:
                expectNoUserCreated();
                break;
            case MAIN_USER_CREATED:
                expectMainUserCreated();
                break;
        }
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

    // NOTE: enums below must be public to be static imported

    public enum InitialUsers {
        SYSTEM_ONLY,
        SYSTEM_AND_MAIN,
        SYSTEM_AND_NON_MAIN
    }

    public enum ExpectedResult {
        NO_USER_CREATED,
        MAIN_USER_CREATED,
        ADMIN_USER_CREATED
    }
}
