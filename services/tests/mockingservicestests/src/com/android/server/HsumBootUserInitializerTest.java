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

import static android.os.UserHandle.USER_NULL;

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
import android.os.UserManager;
import android.util.Log;

import com.android.server.am.ActivityManagerService;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.UserManagerInternal;
import com.android.server.utils.TimingsTraceAndSlog;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public final class HsumBootUserInitializerTest {

    private static final String TAG = HsumBootUserInitializerTest.class.getSimpleName();

    @UserIdInt
    private static final int NON_SYSTEM_USER_ID = 42;

    @Rule
    public final Expect expect = Expect.create();

    // NOTE: replace by ExtendedMockitoRule once it needs to mock UM.isHSUM() or other methods
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

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
    public void testInit_shouldAlwaysHaveMainUserTrue_noMainUser() throws Exception {
        mockGetMainUserId(USER_NULL);
        var initializer = createHsumBootUserInitializer(true);

        initializer.init(mTracer);

        expectMainUserCreated();
    }

    @Test
    public void testInit_shouldAlwaysHaveMainUserTrue_hasMainUser() throws Exception {
        mockGetMainUserId(NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(true);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    @Test
    public void testInit_shouldAlwaysHaveMainUserFalse_noMainUser() throws Exception {
        mockGetMainUserId(USER_NULL);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ false);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    @Test
    public void testInit_shouldAlwaysHaveMainUserFalse_hasMainUser() {
        mockGetMainUserId(NON_SYSTEM_USER_ID);
        var initializer = createHsumBootUserInitializer(/* shouldAlwaysHaveMainUser= */ false);

        initializer.init(mTracer);

        expectNoUserCreated();
    }

    private HsumBootUserInitializer createHsumBootUserInitializer(
            boolean shouldAlwaysHaveMainUser) {
        mTracer = new TimingsTraceAndSlog(TAG);

        return new HsumBootUserInitializer(mMockUmi, mMockAms, mMockPms, mMockContentResolver,
                shouldAlwaysHaveMainUser);
    }

    private void expectMainUserCreated() {
        try {
            verify(mMockUmi).createUserEvenWhenDisallowed(null,
                    UserManager.USER_TYPE_FULL_SECONDARY, UserInfo.FLAG_ADMIN | UserInfo.FLAG_MAIN,
                    null, null);
        } catch (Exception e) {
            String msg = "didn't create main user";
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
    }

    private void mockGetMainUserId(@CanBeNULL @UserIdInt int userId) {
        Log.d(TAG, "mockGetMainUserId(): " + userId);
        when(mMockUmi.getMainUserId()).thenReturn(userId);
    }
}
