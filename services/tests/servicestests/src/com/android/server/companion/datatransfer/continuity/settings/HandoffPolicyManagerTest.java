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

package com.android.server.companion.datatransfer.continuity.settings;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import com.android.server.pm.UserManagerInternal;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffPolicyManagerTest {

    private static final int USER_ID = 0;

    private class FakeHandoffPolicyListener implements HandoffPolicyManager.Listener {
        private int mCallCount = 0;
        private int mUserId;

        @Override
        public void onHandoffPolicyChanged(int userId) {
            mCallCount++;
            mUserId = userId;
        }
    }

    @Mock private UserManagerInternal mMockUserManagerInternal;

    private HandoffPolicyManager mHandoffPolicyManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mHandoffPolicyManager = new HandoffPolicyManager(mMockUserManagerInternal);
    }

    @Test
    public void constructor_registersSelfAsUserRestrictionsListener() {
        verify(mMockUserManagerInternal).addUserRestrictionsListener(mHandoffPolicyManager);
    }

    @Test
    public void isHandoffAllowedForUser_callsUserManagerInternal() {
        when(mMockUserManagerInternal.getUserRestriction(
                        USER_ID, UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF))
                .thenReturn(false);
        assertThat(mHandoffPolicyManager.isHandoffAllowedForUser(USER_ID)).isTrue();
        verify(mMockUserManagerInternal)
                .getUserRestriction(USER_ID, UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF);
    }

    @Test
    public void onUserRestrictionsChanged_notifiesListeners() {
        FakeHandoffPolicyListener listener = new FakeHandoffPolicyListener();
        mHandoffPolicyManager.addListener(listener);
        Bundle prevRestrictions = new Bundle();
        prevRestrictions.putBoolean(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, true);
        Bundle newRestrictions = new Bundle();
        newRestrictions.putBoolean(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, false);
        mHandoffPolicyManager.onUserRestrictionsChanged(USER_ID, newRestrictions, prevRestrictions);
        assertThat(listener.mCallCount).isEqualTo(1);
        assertThat(listener.mUserId).isEqualTo(USER_ID);
    }

    @Test
    public void onUserRestrictionsChanged_doesNotNotifyListenersIfNoChange() {
        FakeHandoffPolicyListener listener = new FakeHandoffPolicyListener();
        mHandoffPolicyManager.addListener(listener);
        Bundle prevRestrictions = new Bundle();
        prevRestrictions.putBoolean(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, true);
        Bundle newRestrictions = new Bundle();
        newRestrictions.putBoolean(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, true);
        mHandoffPolicyManager.onUserRestrictionsChanged(USER_ID, newRestrictions, prevRestrictions);
        assertThat(listener.mCallCount).isEqualTo(0);
    }
}
