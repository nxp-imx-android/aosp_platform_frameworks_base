/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.locksettings;

import static com.android.internal.widget.LockscreenCredential.createPassword;

import com.android.internal.widget.LockscreenCredential;

import org.junit.Before;

/** Provides set-up methods for child profile lock migration tests. */
public abstract class BaseChildProfileLockMigrationTests extends BaseLockSettingsServiceTests {

    protected static final LockscreenCredential UNIFIED_PASSWORD = createPassword("unified");

    @Before
    public void setUp() {
        mService.initializeSyntheticPassword(PRIMARY_USER_ID);
        mService.initializeSyntheticPassword(MANAGED_PROFILE_USER_ID);
    }

    protected void setUpChildProfileLockFileIfNeeded(
            boolean hasChildProfileLockBefore, LockscreenCredential unifiedProfilePassword) {
        if (!hasChildProfileLockBefore) {
            mStorage.removeChildProfileLock(MANAGED_PROFILE_USER_ID);
        } else {
            if (!mStorage.hasChildProfileLock(MANAGED_PROFILE_USER_ID)) {
                mService.tieProfilePasswordToParent(
                        MANAGED_PROFILE_USER_ID, PRIMARY_USER_ID, unifiedProfilePassword);
            }
        }
    }

    protected void setUpSpProtectorPasswordIfNeeded(
            boolean hasSpProtectorPasswordBefore, LockscreenCredential unifiedProfilePassword) {
        if (hasSpProtectorPasswordBefore) {
            mSpManager.tieProtectorToParent(
                    mGateKeeperService,
                    MANAGED_PROFILE_USER_ID,
                    mService.getCurrentLskfBasedProtectorId(MANAGED_PROFILE_USER_ID),
                    PRIMARY_USER_ID,
                    unifiedProfilePassword);
        }
    }

    protected boolean hasSpProtectorPassword(int profileUserId) {
        return mSpManager.hasProfilePassword(
                profileUserId, mService.getCurrentLskfBasedProtectorId(profileUserId));
    }
}
