/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.util.test;

import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.ContentProvider;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for FakeSettingsProvider.
 */
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = ContentProvider.class)
public class FakeSettingsProviderTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final String SYSTEM_SETTING = Settings.System.SCREEN_BRIGHTNESS;
    private static final String SECURE_SETTING = Settings.Secure.BLUETOOTH_NAME;

    private MockContentResolver mCr;

    @Before
    public void setUp() throws Exception {
        mCr = new MockContentResolver();
        mCr.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
    }

    @Test
    @SmallTest
    public void testBasicOperation() throws Exception {
        try {
            Settings.System.getInt(mCr, SYSTEM_SETTING);
            fail("FakeSettingsProvider should start off empty.");
        } catch (Settings.SettingNotFoundException expected) {
            // Expected behaviour.
        }

        // Check that fake settings can be written and read back.
        Settings.System.putInt(mCr, SYSTEM_SETTING, 123);
        assertEquals(123, Settings.System.getInt(mCr, SYSTEM_SETTING));
    }

    private void assertUserHandleUnsupported(UserHandle userHandle, String settingName) {
        try {
            Settings.Secure.putStringForUser(mCr, settingName, "currentUserSetting",
                    userHandle.getIdentifier());
            fail("UserHandle " + userHandle + " is unsupported and should throw");
        } catch (UnsupportedOperationException expected) {
            // Expected behaviour.
        }
    }

    @Test
    @SmallTest
    public void testMultiUserOperation() {
        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "user0Setting", 0);
        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "user1Setting", 1);
        assertEquals("user0Setting", Settings.Secure.getStringForUser(mCr, SECURE_SETTING, 0));
        assertEquals("user1Setting", Settings.Secure.getStringForUser(mCr, SECURE_SETTING, 1));
        assertNull(Settings.Secure.getStringForUser(mCr, SECURE_SETTING, 2));

        final int currentUserId = UserHandle.getUserId(Process.myUid());
        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "currentUserSetting", currentUserId);
        assertEquals("currentUserSetting", Settings.Secure.getString(mCr, SECURE_SETTING));

        Settings.Secure.putString(mCr, SECURE_SETTING, "newValue");
        assertEquals("newValue",
                Settings.Secure.getStringForUser(mCr, SECURE_SETTING, currentUserId));

        Settings.Secure.putString(mCr, SECURE_SETTING, "newValue2");
        assertEquals("newValue2",
                Settings.Secure.getStringForUser(mCr, SECURE_SETTING, UserHandle.USER_CURRENT));

        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "newValue3", UserHandle.USER_CURRENT);
        assertEquals("newValue3", Settings.Secure.getString(mCr, SECURE_SETTING));

        assertUserHandleUnsupported(UserHandle.ALL, SECURE_SETTING);
        assertUserHandleUnsupported(UserHandle.CURRENT_OR_SELF, SECURE_SETTING);
    }
}
