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

package com.android.server.pm;

import static android.os.UserHandle.USER_ALL;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.pm.MultiuserNonComplianceLogger.PROP_SHOW_HSU_NOTIFICATION_TITLE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.IndentingPrintWriter;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.testutils.TestHandler;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.StringWriter;

public final class MultiuserNonComplianceLoggerTest {

    @Rule
    public final Expect expect = Expect.create();

    @Rule
    public final ExtendedMockitoRule extendedMockito = new ExtendedMockitoRule.Builder(this)
            .mockStatic(SystemProperties.class)
            .build();

    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mMockPackageManager;

    private final Context mTargetContext = getInstrumentation().getTargetContext();
    private final TestHandler mHandler = new TestHandler(/* callback= */ null);
    private MultiuserNonComplianceLogger mLogger;

    @Before
    public void setFixtures() {
        mLogger = new MultiuserNonComplianceLogger(mMockContext, mHandler);

        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
    }

    @Test
    public void testEmptyDump() {
        assertPendingMessagesAndFlushHandler(0);

        expect.withMessage("dump() with no logs")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           0 notifications shown on HSU
                           """);
    }

    @Test
    public void testLogMainUserCalls() {
        // Simulate shared UIDs.
        mockPackagesForUid(1000, new String[]{"pkg1", "pkg2"});
        mockPackagesForUid(10001, new String[]{"pkg3"});

        // Simulate the case where the package isn't installed for user 0.
        mockPackagesForUid(10002, null);

        mLogger.logGetMainUserCall(1000);
        mLogger.logIsMainUserCall(1000);
        mLogger.logIsMainUserCall(10001);
        mLogger.logIsMainUserCall(10001);
        mLogger.logIsMainUserCall(10002);
        assertPendingMessagesAndFlushHandler(5);

        expect.withMessage("dump() after logging some main user calls")
                .that(dump(mLogger))
                .isEqualTo("""
                           1 apps called getMainUser()
                             UID 1000 (shared): 1 calls

                           3 apps called isMainUser()
                             UID 1000 (shared): 1 calls
                             UID 10001 (pkg3): 2 calls
                             UID 10002 (unknown): 1 calls

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           0 notifications shown on HSU
                           """);
    }

    @Test
    public void testLogBlockedHsuActivity() {
        mLogger.logBlockedHsuActivity(ComponentName.createRelative("some.pkg", ".SomeActivity"));
        assertPendingMessagesAndFlushHandler(1);

        expect.withMessage("dump() after logging a blocked activity on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           1 activities blocked on HSU
                             some.pkg/.SomeActivity

                           0 activities launched on HSU

                           0 notifications shown on HSU
                           """);
    }

    @Test
    public void testLogLaunchedHsuActivity() {
        mLogger.logLaunchedHsuActivity(ComponentName.createRelative("some.pkg", ".SomeActivity"));
        assertPendingMessagesAndFlushHandler(1);

        expect.withMessage("dump() after logging a launched activity on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           1 activities launched on HSU
                             some.pkg/.SomeActivity

                           0 notifications shown on HSU
                           """);
    }

    @Test
    public void testLogShownHsuNotification_nullTitle() {
        Notification notification = new Notification.Builder(mTargetContext, "TEST")
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        StatusBarNotification sbn = newStatusBarNotification("test.pkg", /* id= */ 42, "TestTag",
                notification, USER_ALL);

        mLogger.logShownHsuNotification(sbn);

        assertPendingMessagesAndFlushHandler(1);
        expect.withMessage("dump() after logging a notification on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           1 notifications shown on HSU
                             [pkg=test.pkg, tag=TestTag, id=42, targetUserId=-1, title=null, \
                           vis=PUBLIC, category=sys, channel=TEST]: 1 times
                           """);
    }

    @Test
    public void testLogShownHsuNotification_titleRedacted() {
        mockSetPropertyShowTitle(false);
        Notification notification = new Notification.Builder(mTargetContext, "TEST")
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        setTitle(notification, "Supreme Leader");

        StatusBarNotification sbn = newStatusBarNotification("test.pkg", /* id= */ 42, "TestTag",
                notification, USER_ALL);

        mLogger.logShownHsuNotification(sbn);

        assertPendingMessagesAndFlushHandler(1);
        assertWithMessage("dump() after logging a notification on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           1 notifications shown on HSU
                             [pkg=test.pkg, tag=TestTag, id=42, targetUserId=-1, title=14_chars \
                           (redacted), vis=PUBLIC, category=sys, channel=TEST]: 1 times
                           """);
    }

    @Test
    public void testLogShownHsuNotification_titleNotRedacted() {
        mockSetPropertyShowTitle(true);

        Notification notification = new Notification.Builder(mTargetContext, "TEST")
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        setTitle(notification, "Supreme Leader");

        StatusBarNotification sbn = newStatusBarNotification("test.pkg", /* id= */ 42, "TestTag",
                notification, USER_ALL);

        mLogger.logShownHsuNotification(sbn);

        assertPendingMessagesAndFlushHandler(1);
        assertWithMessage("dump() after logging a notification on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           1 notifications shown on HSU
                             [pkg=test.pkg, tag=TestTag, id=42, targetUserId=-1, \
                           title=\"Supreme Leader\", vis=PUBLIC, category=sys, channel=TEST]: \
                           1 times
                           """);
    }

    @Test
    public void testLogBlockedAndLaunchedHsuActivities() {
        mLogger.logBlockedHsuActivity(ComponentName.createRelative("some.pkg", ".SomeActivity"));
        mLogger.logLaunchedHsuActivity(
                ComponentName.createRelative("some.pkg", ".AwesomeActivity"));

        assertPendingMessagesAndFlushHandler(2);
        expect.withMessage("dump() after logging blocked and launched activities on HSU")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           1 activities blocked on HSU
                             some.pkg/.SomeActivity

                           1 activities launched on HSU
                             some.pkg/.AwesomeActivity

                           0 notifications shown on HSU
                           """);
    }

    @Test
    public void testReset() {
        mLogger.logGetMainUserCall(1000);
        mLogger.logIsMainUserCall(1000);

        assertPendingMessagesAndFlushHandler(2);

        mLogger.reset();

        assertPendingMessagesAndFlushHandler(0);
        expect.withMessage("dump() after reset()")
                .that(dump(mLogger))
                .isEqualTo("""
                           0 apps called getMainUser()

                           0 apps called isMainUser()

                           0 activities blocked on HSU

                           0 activities launched on HSU

                           0 notifications shown on HSU
                           """);
    }

    private String dump(MultiuserNonComplianceLogger logger) {
        try {
            try (StringWriter sw = new StringWriter()) {
                logger.dump(new IndentingPrintWriter(sw));
                return sw.toString();
            }
        } catch (IOException e) {
            fail("Failed to call dump() on logger: " + e);
            return "";
        }
    }

    private void mockPackagesForUid(int uid, @Nullable String[] packages) {
        when(mMockPackageManager.getPackagesForUid(uid)).thenReturn(packages);
    }

    private static void setTitle(Notification notification, CharSequence title) {
        notification.extras.putCharSequence(Notification.EXTRA_TITLE, title);
    }

    private static StatusBarNotification newStatusBarNotification(String pkg, int id, String tag,
            Notification notification, @UserIdInt int userId) {
        String opPkg = null;
        int uid = Process.myUid();
        int pid = Process.myPid();
        UserHandle user = UserHandle.of(userId);
        String overrideGroupKey = null;
        long postTime = System.currentTimeMillis();

        return new StatusBarNotification(pkg, opPkg, id, tag, uid, pid, notification, user,
                overrideGroupKey, postTime);
    }

    private static void mockGetSystemProperty(String name, boolean defaultValue,
            boolean mockedValue) {
        doReturn(mockedValue).when(() -> SystemProperties.getBoolean(name, defaultValue));
    }

    private static void mockSetPropertyShowTitle(boolean value) {
        // NOTE: it's explicitly passing the default value as false - instead of anyBoolean() - to
        // make sure it's refacting by default.
        mockGetSystemProperty(PROP_SHOW_HSU_NOTIFICATION_TITLE, /* def= */ false, value);
    }

    private void assertPendingMessagesAndFlushHandler(int expectedNumberOfMessages) {
        expect.withMessage("number of pending messages on handler")
                .that(mHandler.getPendingMessages())
                .hasSize(expectedNumberOfMessages);
        mHandler.flush();
    }
}
