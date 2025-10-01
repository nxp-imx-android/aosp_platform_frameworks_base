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
package com.android.server.wm;

import static android.app.ActivityManager.START_ABORTED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

import android.annotation.UserIdInt;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;

import com.android.server.wm.WindowTestsBase.ActivityBuilder;

import org.junit.Test;

/**
 * Tests integration with {@code UserManagerInternal} to block activities that should not be
 * launched when the useris the {@code HSU} (Headless System User).
 */
public final class ActivityStarterHsuAllowlistIntegrationTests extends ActivityStarterTestBase {

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_notifyWhenActivityIsLaunched() {
        ActivityStarter starter = prepareStarter();

        starter.setReason("testExecute_notifyWhenActivityIsLaunch").execute();

        verifyUmiNotifiedActivityLaunched();
    }

    @Test
    @DisableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_dontNotifyWhenFlagIsDisabled() {
        ActivityStarter starter = prepareStarter();

        starter.setReason("testExecute_dontNotifyWhenFlagIsDisabled").execute();

        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_dontNotifyWhenDeviceIsNotHsum() {
        ActivityStarter starter = prepareStarter();
        mockIsHsum(false);

        starter.setReason("testExecute_dontNotifyWhenDeviceIsNotHsum").execute();

        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_dontNotifyWhenUserIsNotHsu() {
        ActivityStarter starter = createStarterForUser(42);

        starter.execute();

        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_dontNotifyWhenActivityDidntStart() {
        ActivityStarter starter = prepareStarter();
        spyOn(starter);
        doReturn(START_ABORTED).when(starter).isAllowedToStart(any(), anyBoolean(), any());

        starter.setReason("testExecute_dontNotifyWhenActivityDidntStart").execute();

        verifyUmiNotNotifiedActivityLaunched();
    }

    /**
     * Creates a new {@link ActivityStarter}.
     *
     * <p>It also mocks the device as being {@code HSUM}.
     */
    private ActivityStarter prepareStarter() {
        ActivityStarter starter = prepareStarter(FLAG_ACTIVITY_NEW_TASK);
        mockIsHsum(true);

        return starter;
    }

    private ActivityStarter createStarterForUser(@UserIdInt int userId) {
        ActivityStarter starter = prepareStarter();
        starter.mRequest.activityInfo.applicationInfo.uid = userId * UserHandle.PER_USER_RANGE;
        return starter;
    }

    private void mockIsHsum(boolean value) {
        doReturn(value).when(mMockUmi).isHeadlessSystemUserMode();
    }

    private void verifyUmiNotifiedActivityLaunched() {
        verify(mMockUmi).logLaunchedHsuActivity(ActivityBuilder.getDefaultComponent());
    }

    private void verifyUmiNotNotifiedActivityLaunched() {
        verify(mMockUmi, never()).logLaunchedHsuActivity(any());
    }
}
