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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.SystemService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersonalContextManagerServiceTest {
    private static final int USER_ID_1 = 10;
    private static final int USER_ID_2 = 11;

    private static final UserInfo USER_INFO_1 = new UserInfo(USER_ID_1, "user1", 0);
    private static final UserInfo USER_INFO_2 = new UserInfo(USER_ID_2, "user2", 0);
    private static final UserInfo SYSTEM_USER_INFO =
            new UserInfo(UserHandle.USER_SYSTEM, "system", 0);

    @Mock private Context mMockContext;
    @Mock private PackageManager mPackageManager;

    private PersonalContextManagerService mService;
    private SystemService.TargetUser mUser1;
    private SystemService.TargetUser mUser2;
    private SystemService.TargetUser mSystemUser;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mMockContext.getPackageName()).thenReturn("android");

        mockUserContext(UserHandle.of(USER_ID_1));
        mockUserContext(UserHandle.of(USER_ID_2));
        mockUserContext(UserHandle.SYSTEM);

        mService = spy(new PersonalContextManagerService(mMockContext));

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
    public void testOnUserUnlocked_registersComponentsAndMonitor() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        ContextComponentManager user1Manager =
                mService.getComponentManagerForUser(USER_ID_1);
        assertThat(user1Manager).isNotNull();

        // Verify that it tried to register components.
        verify(mPackageManager, atLeast(4)).queryIntentServices(any(), anyInt());
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

        ContextComponentManager user1Manager =
                mService.getComponentManagerForUser(USER_ID_1);
        assertThat(user1Manager).isNotNull();
    }

    @Test
    public void testOnUserStopping_cleansUp() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();

        mService.onUserStopping(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNull();
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

    private void mockUserContext(UserHandle userHandle) {
        Context userContext = mock(Context.class);
        when(mMockContext.createContextAsUser(eq(userHandle), anyInt())).thenReturn(userContext);
        when(userContext.getPackageManager()).thenReturn(mPackageManager);
        when(userContext.getUser()).thenReturn(userHandle);
    }
}
