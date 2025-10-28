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
import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_USER;
import static android.app.ActivityManager.START_PERMISSION_DENIED;
import static android.app.ActivityManager.START_SUCCESS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;

import android.annotation.UserIdInt;
import android.app.IApplicationThread;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;

import com.android.server.pm.UserActivitiesAllowlist;
import com.android.server.wm.WindowTestsBase.ActivityBuilder;

import org.junit.Test;

/**
 * Tests integration with {@code UserManagerInternal} to block activities that should not be
 * launched when the user is the {@code HSU} (Headless System User).
 */
public final class ActivityStarterHsuAllowlistIntegrationTests extends ActivityStarterTestBase {

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_dontNotifyUmiWhenActivityDidntStart() {
        ActivityStarter starter = createStarter();
        spyOn(starter);
        doReturn(START_ABORTED).when(starter).isAllowedToStart(any(), anyBoolean(), any());

        int result = starter.execute();
        assertWithMessage("result of execute()").that(result).isEqualTo(START_ABORTED);

        verifyUmiNotNotifiedActivityBlocked();
        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_blockedWhenNotAllowlisted() {
        ActivityStarter starter = createStarter();
        mockActivityAllowlistedForHsu(false);

        int result = starter.execute();

        assertWithMessage("result of execute()").that(result).isEqualTo(START_NOT_ALLOWED_FOR_USER);
        verifyUmiNotifiedActivityBlocked();
        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @DisableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_allowedWhenFlagIsDisabled() {
        ActivityStarter starter = createStarter();

        int result = starter.execute();

        assertWithMessage("result of execute()").that(result).isEqualTo(START_SUCCESS);
        verifyUmiNotNotifiedActivityBlocked();
        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_allowedWhenAllowlistIsNotSet() {
        ActivityStarter starter = createStarter();
        // Don't need to mock - umi.getActivitiesAllowlist() will return null by default

        int result = starter.execute();

        assertWithMessage("result of execute()").that(result).isEqualTo(START_SUCCESS);
        verifyUmiNotNotifiedActivityBlocked();
        verifyUmiNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_allowedWhenDeviceIsNotHsum() {
        ActivityStarter starter = createStarter(/* isHsum=*/ false);

        int result = starter.execute();

        assertWithMessage("result of execute()").that(result).isEqualTo(START_SUCCESS);
        verifyUmiNotNotifiedActivityBlocked();
        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_allowedWhenUserIsNotHsu() {
        ActivityStarter starter = createStarterForUser(42);

        int result = starter.execute();

        assertWithMessage("result of execute()").that(result).isEqualTo(START_SUCCESS);
        verifyUmiNotNotifiedActivityBlocked();
        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_umiNotCalledWhenNotAllowed() {
        ActivityStarter starter = createStarter();
        starter.mRequest.caller = mock(IApplicationThread.class);

        int result = starter.execute();

        assertWithMessage("result of execute()").that(result).isEqualTo(START_PERMISSION_DENIED);
        verifyUmiNotNotifiedActivityBlocked();
        verifyUmiNotNotifiedActivityLaunched();
    }

    // Note: methods below are called createStarter() to avoid confusion with the prepareStarter()
    // methods defined in the superclass.

    /**
     * Creates a new {@link ActivityStarter}.
     *
     * <p>It also mocks the device as being {@code HSUM} and allows
     * {@code ActivityBuilder.getDefaultComponent()}.
     */
    private ActivityStarter createStarter() {
        return createStarter(/* isHsum=*/ true);
    }

    private ActivityStarter createStarter(boolean isHsum) {
        // Must mock it before creating the starter, as the starter constructor calls
        // umi.isHeadlessSystemUserMode()
        doReturn(isHsum).when(mMockUmi).isHeadlessSystemUserMode();
        ActivityStarter starter = prepareStarter(FLAG_ACTIVITY_NEW_TASK);

        starter.mRequest.activityInfo.applicationInfo.packageName = ActivityBuilder
                .getDefaultComponent().getPackageName();

        mockActivityAllowlistedForHsu(true);

        return starter;
    }

    private ActivityStarter createStarterForUser(@UserIdInt int userId) {
        ActivityStarter starter = createStarter();
        starter.mRequest.activityInfo.applicationInfo.uid = userId * UserHandle.PER_USER_RANGE;
        return starter;
    }

    private void mockActivityAllowlistedForHsu(boolean value) {
        var mockAllowlist = mock(UserActivitiesAllowlist.class);
        doReturn(value).when(mockAllowlist).isAllowed(ActivityBuilder.getDefaultComponent());
        doReturn(mockAllowlist).when(mMockUmi)
                    .getActivitiesAllowlist(UserManager.USER_TYPE_SYSTEM_HEADLESS);
    }

    private void verifyUmiNotifiedActivityBlocked() {
        verify(mMockUmi).logBlockedHsuActivity(ActivityBuilder.getDefaultComponent());
    }

    private void verifyUmiNotNotifiedActivityBlocked() {
        verify(mMockUmi, never()).logBlockedHsuActivity(any());
    }

    private void verifyUmiNotifiedActivityLaunched() {
        verify(mMockUmi).logLaunchedHsuActivity(ActivityBuilder.getDefaultComponent());
    }

    private void verifyUmiNotNotifiedActivityLaunched() {
        verify(mMockUmi, never()).logLaunchedHsuActivity(any());
    }
}
