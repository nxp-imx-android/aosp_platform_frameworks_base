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
import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_HEADLESS_SYSTEM_USER;
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

import android.annotation.UserIdInt;
import android.app.IApplicationThread;
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
        ActivityStarter starter = createStarter();

        starter.setReason("testExecute_notifyWhenActivityIsLaunch").execute();

        verifyUmiNotifiedActivityLaunched();
    }

    @Test
    @DisableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_dontNotifyWhenFlagIsDisabled() {
        ActivityStarter starter = createStarter();

        starter.setReason("testExecute_dontNotifyWhenFlagIsDisabled").execute();

        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_dontNotifyWhenDeviceIsNotHsum() {
        ActivityStarter starter = createStarter(/* isHsum=*/ false);

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
        ActivityStarter starter = createStarter();
        spyOn(starter);
        doReturn(START_ABORTED).when(starter).isAllowedToStart(any(), anyBoolean(), any());

        starter.setReason("testExecute_dontNotifyWhenActivityDidntStart").execute();

        verifyUmiNotNotifiedActivityLaunched();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testExecute_blockedWhenNotAllowlisted() {
        ActivityStarter starter = createStarter();
        mockActivityAllowlistedForHsu(false);

        int result = starter.execute();

        assertWithMessage("result of execute()").that(result)
                .isEqualTo(START_NOT_ALLOWED_FOR_HEADLESS_SYSTEM_USER);
        verifyUmiNotifiedActivityBlocked();
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
        doReturn(value).when(mMockUmi)
                .isActivityAllowlistedForHsu(ActivityBuilder.getDefaultComponent());
    }

    // NOTE: Also calls verifyUmiNotNotifiedActivityLaunched() (as that's the opposite behavior)
    private void verifyUmiNotifiedActivityBlocked() {
        verify(mMockUmi).logBlockedHsuActivity(ActivityBuilder.getDefaultComponent());
        verifyUmiNotNotifiedActivityLaunched();
    }

    private void verifyUmiNotNotifiedActivityBlocked() {
        verify(mMockUmi, never()).logBlockedHsuActivity(any());
    }

    // NOTE: Also calls verifyUmiNotNotifiedActivityBlocked() (as that's the opposite behavior)
    private void verifyUmiNotifiedActivityLaunched() {
        verify(mMockUmi).logLaunchedHsuActivity(ActivityBuilder.getDefaultComponent());
        verifyUmiNotNotifiedActivityBlocked();
    }

    private void verifyUmiNotNotifiedActivityLaunched() {
        verify(mMockUmi, never()).logLaunchedHsuActivity(any());
    }
}
