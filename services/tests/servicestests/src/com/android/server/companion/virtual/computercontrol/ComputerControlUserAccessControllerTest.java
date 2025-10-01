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

package com.android.server.companion.virtual.computercontrol;

import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;
import static android.os.UserManager.USER_TYPE_PROFILE_CLONE;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ComputerControlUserAccessControllerTest {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private UserManager mUserManager;
    private final Context mContext =
            spy(
                    new ContextWrapper(
                            InstrumentationRegistry.getInstrumentation().getTargetContext()));
    private final List<UserHandle> mAllUsers = new ArrayList<>();

    private ComputerControlUserAccessController mUserAccessController;

    @Before
    public void setUp() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mUserManager.getAllProfiles()).thenReturn(mAllUsers);

        mUserAccessController = new ComputerControlUserAccessController(mContext);
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_USER_RESTRICTION)
    public void validateAndGetAllowedUsers_normalUser_returnsCloneDescendants() {
        // Allowed:
        UserHandle callingUser = createUser(USER_TYPE_FULL_SECONDARY);
        UserHandle callingUserCloneChild = createUser(USER_TYPE_PROFILE_CLONE, callingUser);
        UserHandle callingUserCloneGrandchild =
                createUser(USER_TYPE_PROFILE_CLONE, callingUserCloneChild);
        // Not allowed:
        /* callingUserNormalChild= */ createUser(USER_TYPE_FULL_SECONDARY, callingUser);
        UserHandle anotherUser = createUser(USER_TYPE_FULL_SECONDARY);
        /* anotherUserClone= */ createUser(USER_TYPE_PROFILE_CLONE, anotherUser);

        assertThat(mUserAccessController.validateAndGetAllowedUsers(attributionSource(callingUser)))
                .containsExactly(callingUser, callingUserCloneChild, callingUserCloneGrandchild);
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_USER_RESTRICTION)
    public void validateAndGetAllowedUsers_cloneUser_returnsRootCloneDescendants() {
        // Allowed:
        UserHandle callingUserParent = createUser(USER_TYPE_FULL_SECONDARY);
        UserHandle callingUser = createUser(USER_TYPE_PROFILE_CLONE, callingUserParent);
        UserHandle callingUserClone = createUser(USER_TYPE_PROFILE_CLONE, callingUser);
        UserHandle callingUserCloneSibling = createUser(USER_TYPE_PROFILE_CLONE, callingUserParent);
        // Not allowed:
        /* callingUserNormalSibling= */ createUser(USER_TYPE_FULL_SECONDARY, callingUserParent);
        UserHandle anotherUser = createUser(USER_TYPE_FULL_SECONDARY);
        /* anotherUserClone= */ createUser(USER_TYPE_PROFILE_CLONE, anotherUser);

        assertThat(mUserAccessController.validateAndGetAllowedUsers(attributionSource(callingUser)))
                .containsExactly(
                        callingUser, callingUserParent, callingUserClone, callingUserCloneSibling);
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_USER_RESTRICTION)
    public void validateAndGetAllowedUsers_normalChildUser_returnsOwnCloneDescendants() {
        // Not allowed:
        UserHandle callingUserParent = createUser(USER_TYPE_FULL_SECONDARY);
        /* callingUserCloneSibling= */ createUser(USER_TYPE_PROFILE_CLONE, callingUserParent);
        // Allowed:
        UserHandle callingUser = createUser(USER_TYPE_FULL_SECONDARY, callingUserParent);
        UserHandle callingUserClone = createUser(USER_TYPE_PROFILE_CLONE, callingUser);

        assertThat(mUserAccessController.validateAndGetAllowedUsers(attributionSource(callingUser)))
                .containsExactly(callingUser, callingUserClone);
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_USER_RESTRICTION)
    public void validateAndGetAllowedUsers_managedUser_throwsSecurityException() {
        UserHandle callingUser = createUser(USER_TYPE_PROFILE_MANAGED);

        assertThrows(
                SecurityException.class,
                () ->
                        mUserAccessController.validateAndGetAllowedUsers(
                                attributionSource(callingUser)));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_USER_RESTRICTION)
    public void validateAndGetAllowedUsers_managedUserClone_throwsSecurityException() {
        UserHandle callingUserParent = createUser(USER_TYPE_PROFILE_MANAGED);
        UserHandle callingUser = createUser(USER_TYPE_PROFILE_CLONE, callingUserParent);

        assertThrows(
                SecurityException.class,
                () ->
                        mUserAccessController.validateAndGetAllowedUsers(
                                attributionSource(callingUser)));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_USER_RESTRICTION)
    public void validateAndGetAllowedUsers_flagDisabledNormalUser_returnsEmpty() {
        UserHandle callingUser = createUser(USER_TYPE_FULL_SECONDARY);

        assertThat(mUserAccessController.validateAndGetAllowedUsers(attributionSource(callingUser)))
                .isEmpty();
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_USER_RESTRICTION)
    public void validateAndGetAllowedUsers_flagDisabledManagedUser_doesNotThrow() {
        UserHandle callingUser = createUser(USER_TYPE_PROFILE_MANAGED);

        assertThat(mUserAccessController.validateAndGetAllowedUsers(attributionSource(callingUser)))
                .isEmpty();
    }

    private UserHandle createUser(String userType) {
        UserHandle user = UserHandle.of(mAllUsers.size() + 100);
        mAllUsers.add(user);
        when(mUserManager.getUserInfo(user.getIdentifier()))
                .thenReturn(
                        new UserInfo(
                                user.getIdentifier(), "name", "icon", /* flags= */ 0, userType));
        return user;
    }

    private UserHandle createUser(String userType, UserHandle parent) {
        UserHandle user = createUser(userType);
        when(mUserManager.getProfileParent(user)).thenReturn(parent);
        return user;
    }

    private static AttributionSource attributionSource(UserHandle callingUser) {
        return new AttributionSource(callingUser.getUid(0), "com.package", "tag");
    }
}
