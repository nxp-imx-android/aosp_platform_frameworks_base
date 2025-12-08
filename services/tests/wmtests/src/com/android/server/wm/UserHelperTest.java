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

import static android.app.ActivityManager.START_CLASS_NOT_FOUND;
import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_USER;
import static android.app.ActivityManager.START_SUCCESS;
import static android.os.UserHandle.USER_SYSTEM;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.pm.UserActivitiesAllowlist;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityStarter.Request;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class UserHelperTest {

    private static final @UserIdInt int USER_ID = 007;
    private static final ComponentName COMP_NAME =
            ComponentName.createRelative("The.name.is.Bond", "James.Bond");

    private static final boolean STARTED = true;
    private static final boolean NOT_STARTED = false;

    @Rule
    public final Expect expect = Expect.create();

    // TODO(b/456300837): switch back to MockitoRule once UserHelper caches the current user
    @Rule
    public final ExtendedMockitoRule extendedMockito =
            new ExtendedMockitoRule.Builder(this)
            .mockStatic(ActivityManager.class)
            .build();

    private final Request mRequest = new Request();

    @Mock
    private UserManagerInternal mMockUmi;

    @Mock
    private ActivityRecord mActivityRecord;

    private UserHelper mUserHelper;

    @Before
    public void setFixtures() {
        mUserHelper = createUserHelper(/* hsum= */ true);
        mRequest.intent = new Intent().setComponent(COMP_NAME);
        mRequest.activityInfo = new ActivityInfo();
        mRequest.activityInfo.applicationInfo = new ApplicationInfo();

        // Currently, it only checks for if the current user is the HSU, so we're setting a
        // different userId in the request to make sure it's ignored
        setUserIdOnRequest(USER_ID);

        // Mock as not allowlisted by default so it's simpler to test corner-case scenarios (like
        // null info)
        mockHsuActivityAllowlisted(false);
    }

    @Test
    public void testCheckRequest_nullInfo_failure() {
        mRequest.activityInfo = null;

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_CLASS_NOT_FOUND);

        verifyUmiNotNotifiedActivityBlockedOnHsu();
    }

    @Test
    public void testCheckRequest_nullComponentName_success() {
        mRequest.intent = new Intent();

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiNotNotifiedActivityBlockedOnHsu();
    }

    @Test
    public void testCheckRequest_allowListIsNull_success() {
        mockNoHsuActivitiesAllowlist();

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiNotNotifiedActivityBlockedOnHsu();
    }

    @Test
    public void testCheckRequest_notAllowlisted_failure() {
        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_NOT_ALLOWED_FOR_USER);

        verifyUmiNotifiedActivityBlockedOnHsu();
    }

    @Test
    public void testCheckRequest_notAllowlisted_successWhenDeviceIsNotHsum() {
        var userHelper = createUserHelper(/* hsum= */ false);

        expect.withMessage("checkRequest()")
                .that(userHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiNotNotifiedActivityBlockedOnHsu();
    }

    @Test
    public void testCheckRequest_notAllowlisted_successWhenCurrentUserIsNotHsu() {
        setCurrentUserId(USER_ID);

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiNotNotifiedActivityBlockedOnHsu();
    }

    @Test
    public void testCheckRequest_allowlisted_success() {
        mockHsuActivityAllowlisted(true);

        expect.withMessage("checkRequest()")
                .that(mUserHelper.checkRequest(mRequest))
                .isEqualTo(START_SUCCESS);

        verifyUmiNotNotifiedActivityBlockedOnHsu();
    }

    @Test
    public void testLogStarted_notStarted() {
        mUserHelper.logActivityStarted(mActivityRecord, NOT_STARTED);

        verifyUmiNotNotifiedActivityLaunchedOnHsu();
    }

    @Test
    public void testLogStarted_started_dontLogWhenNotHsum() {
        var userHelper = createUserHelper(/* hsum= */ false);

        userHelper.logActivityStarted(mActivityRecord, STARTED);

        verifyUmiNotNotifiedActivityLaunchedOnHsu();
    }

    @Test
    public void testLogStarted_started_dontLogWhenNotHsu() {
        mUserHelper.logActivityStarted(USER_ID, COMP_NAME);

        verifyUmiNotNotifiedActivityLaunchedOnHsu();
    }

    @Test
    public void testLogStarted_started_logWhenNotHsu() {
        mUserHelper.logActivityStarted(USER_SYSTEM, COMP_NAME);

        verifyUmiNotifiedActivityLaunchedOnHsu();
    }

    @Test
    public void testGetUserId_nullActivityInfo() {
        expect.withMessage("getUserId()").that(UserHelper.getUserId(null)).isEqualTo(USER_SYSTEM);
    }

    @Test
    public void testGetUserId_nullApplicationInfo() {
        ActivityInfo info = new ActivityInfo();

        expect.withMessage("getUserId()").that(UserHelper.getUserId(info)).isEqualTo(USER_SYSTEM);
    }

    @Test
    public void testGetUserId() {
        var aInfo = new ActivityInfo();
        aInfo.applicationInfo = new ApplicationInfo();
        setUserId(aInfo.applicationInfo, USER_ID);

        expect.withMessage("getUserId()").that(UserHelper.getUserId(aInfo)).isEqualTo(USER_ID);
    }

    @Test
    public void testDump_hsum() throws Exception {
        String dump = dump(mUserHelper, "...");

        expect.withMessage("dump()").that(dump)
                .isEqualTo("""
                      ...UserHelper:
                      ...  TAG=ActivityTaskManager
                      ...  mIsHeadlessSystemUserMode=true
                      ...  mActivityLaunchIntegrationStatus=1 (ENABLED)
                      """);
    }

    @Test
    public void testDump_nonHsum() throws Exception {
        var userHelper = createUserHelper(/* hsum= */ false);

        String dump = dump(userHelper, "...");

        expect.withMessage("dump()").that(dump)
                .isEqualTo("""
                      ...UserHelper:
                      ...  TAG=ActivityTaskManager
                      ...  mIsHeadlessSystemUserMode=false
                      ...  mActivityLaunchIntegrationStatus=-1 (DISABLED_NOT_HSUM)
                      """);
    }

    // TODO(b/455582152): test dump with other statuses (and current user id)

    private void setUserIdOnRequest(@UserIdInt int userId) {
        setUserId(mRequest.activityInfo.applicationInfo, userId);
    }

    private void setUserId(ApplicationInfo applicationInfo, @UserIdInt int userId) {
        applicationInfo.uid = userId * UserHandle.PER_USER_RANGE + 666;
    }

    private UserHelper createUserHelper(boolean hsum) {
        when(mMockUmi.isHeadlessSystemUserMode()).thenReturn(hsum);
        return new UserHelper(mMockUmi);
    }

    private void mockNoHsuActivitiesAllowlist() {
        when(mMockUmi.getActivitiesAllowlist(USER_SYSTEM)).thenReturn(null);
    }

    private void mockHsuActivityAllowlisted(boolean value) {
        var mockAllowlist = mock(UserActivitiesAllowlist.class);
        when(mockAllowlist.isAllowed(COMP_NAME)).thenReturn(value);
        when(mMockUmi.getActivitiesAllowlist(USER_SYSTEM)).thenReturn(mockAllowlist);
    }

    private void setCurrentUserId(@UserIdInt int userId) {
        doReturn(userId).when(ActivityManager::getCurrentUser);
    }

    private void verifyUmiNotifiedActivityBlockedOnHsu() {
        verify(mMockUmi).logBlockedHsuActivity(COMP_NAME);
    }

    private void verifyUmiNotNotifiedActivityBlockedOnHsu() {
        verify(mMockUmi, never()).logBlockedHsuActivity(any());
    }

    private void verifyUmiNotifiedActivityLaunchedOnHsu() {
        verify(mMockUmi).logLaunchedHsuActivity(COMP_NAME);
    }

    private void verifyUmiNotNotifiedActivityLaunchedOnHsu() {
        verify(mMockUmi, never()).logLaunchedHsuActivity(any());
    }

    private static String dump(UserHelper userHelper, String prefix) throws IOException {
        try (StringWriter sw = new StringWriter()) {
            userHelper.dump(new PrintWriter(sw), prefix);
            return sw.toString();
        }
    }
}
