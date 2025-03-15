/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.content.pm;

import static android.content.pm.UserInfo.FLAG_DEMO;
import static android.content.pm.UserInfo.FLAG_FULL;
import static android.content.pm.UserInfo.FLAG_GUEST;
import static android.content.pm.UserInfo.FLAG_MAIN;
import static android.content.pm.UserInfo.FLAG_PROFILE;
import static android.content.pm.UserInfo.FLAG_SYSTEM;
import static android.os.UserManager.USER_TYPE_FULL_RESTRICTED;
import static android.os.UserManager.USER_TYPE_FULL_SYSTEM;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.UserInfo.UserInfoFlag;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInfoTest {

    @Rule
    public final SetFlagsRule flags =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    @Rule public final Expect expect = Expect.create();

    @Test
    public void testSimple() throws Exception {
        final UserInfo ui = new UserInfo(10, "Test", UserInfo.FLAG_GUEST);

        assertThat(ui.getUserHandle()).isEqualTo(UserHandle.of(10));
        assertThat(ui.name).isEqualTo("Test");

        // Derived based on userType field
        assertThat(ui.isManagedProfile()).isEqualTo(false);
        assertThat(ui.isGuest()).isEqualTo(true);
        assertThat(ui.isRestricted()).isEqualTo(false);
        assertThat(ui.isDemo()).isEqualTo(false);
        assertThat(ui.isCloneProfile()).isEqualTo(false);
        assertThat(ui.isCommunalProfile()).isEqualTo(false);
        assertThat(ui.isPrivateProfile()).isEqualTo(false);

        // Derived based on flags field
        assertThat(ui.isPrimary()).isEqualTo(false);
        assertThat(ui.isAdmin()).isEqualTo(false);
        assertThat(ui.isProfile()).isEqualTo(false);
        assertThat(ui.isEnabled()).isEqualTo(true);
        assertThat(ui.isQuietModeEnabled()).isEqualTo(false);
        assertThat(ui.isEphemeral()).isEqualTo(false);
        assertThat(ui.isForTesting()).isEqualTo(false);
        assertThat(ui.isInitialized()).isEqualTo(false);
        assertThat(ui.isFull()).isEqualTo(false);
        assertThat(ui.isMain()).isEqualTo(false);
    }

    @Test
    public void testDebug() throws Exception {
        final UserInfo ui = new UserInfo(10, "Test", UserInfo.FLAG_GUEST);

        assertThat(ui.toString()).isNotEmpty();
        assertThat(ui.toFullString()).isNotEmpty();
    }

    @Test
    @DisableFlags(android.multiuser.Flags.FLAG_PROFILES_FOR_ALL)
    public void testCanHaveProfile_flagProfilesForAllDisabled() {
        expectCannotHaveProfile("non-full user", createTestUserInfo(/* flags= */ 0));
        expectCannotHaveProfile("guest user", createTestUserInfo(FLAG_FULL | FLAG_GUEST));
        expectCanHaveProfile("main user", createTestUserInfo(FLAG_FULL | FLAG_MAIN));
        expectCannotHaveProfile("non-main user", createTestUserInfo(FLAG_FULL));
        expectCannotHaveProfile("demo user", createTestUserInfo(FLAG_FULL | FLAG_DEMO));
        expectCannotHaveProfile("restricted user",
                createTestUserInfo(USER_TYPE_FULL_RESTRICTED, FLAG_FULL));
        expectCannotHaveProfile("profile user", createTestUserInfo(FLAG_PROFILE));
        expectCanHaveProfile("(full) system user that's also main user",
                createTestUserInfo(USER_TYPE_FULL_SYSTEM, FLAG_FULL | FLAG_SYSTEM | FLAG_MAIN));
        expectCannotHaveProfile("headless system user that's not main user",
                createTestUserInfo(USER_TYPE_SYSTEM_HEADLESS, FLAG_SYSTEM));
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_PROFILES_FOR_ALL)
    public void testCanHaveProfile_flagProfilesForAllEnabled() {
        expectCannotHaveProfile("non-full user", createTestUserInfo(/* flags= */ 0));
        expectCannotHaveProfile("guest user", createTestUserInfo(FLAG_FULL | FLAG_GUEST));
        expectCanHaveProfile("main user", createTestUserInfo(FLAG_FULL | FLAG_MAIN));
        expectCanHaveProfile("non-main user", createTestUserInfo(FLAG_FULL));
        expectCannotHaveProfile("demo user", createTestUserInfo(FLAG_FULL | FLAG_DEMO));
        expectCannotHaveProfile("restricted user",
                createTestUserInfo(USER_TYPE_FULL_RESTRICTED, FLAG_FULL));
        expectCannotHaveProfile("profile user", createTestUserInfo(FLAG_PROFILE));
        expectCanHaveProfile("(full) system user that's also main user",
                createTestUserInfo(USER_TYPE_FULL_SYSTEM, FLAG_FULL | FLAG_SYSTEM | FLAG_MAIN));
        expectCannotHaveProfile("headless system user that's not main user",
                createTestUserInfo(USER_TYPE_SYSTEM_HEADLESS, FLAG_SYSTEM));
    }

    /**
     * Creates a new {@link UserInfo} with id {@code 10}, name {@code Test}, and the given
     * {@code flags}.
     */
    private UserInfo createTestUserInfo(@UserInfoFlag int flags) {
        return new UserInfo(10, "Test", flags);
    }

    /**
     * Creates a new {@link UserInfo} with id {@code 10}, name {@code Test}, and the given
     * {@code userType} and {@code flags}.
     */
    private UserInfo createTestUserInfo(String userType, @UserInfoFlag int flags) {
        return new UserInfo(10, "Test", /* iconPath= */ null, flags, userType);
    }

    private void expectCanHaveProfile(String description, UserInfo user) {
        expect.withMessage("canHaveProfile() on %s (%s)", description, user)
                .that(user.canHaveProfile()).isTrue();
    }

    private void expectCannotHaveProfile(String description, UserInfo user) {
        expect.withMessage("canHaveProfile() on %s (%s)", description, user)
                .that(user.canHaveProfile()).isFalse();
    }
}
