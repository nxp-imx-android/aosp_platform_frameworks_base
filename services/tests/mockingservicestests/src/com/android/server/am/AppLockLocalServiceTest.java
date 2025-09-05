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

package com.android.server.am;

import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
import static android.app.ActivityManager.PROCESS_STATE_TOP;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import android.app.AppGlobals;
import android.app.AppLockInternal;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.permission.IPermissionManager;
import android.platform.test.annotations.Presubmit;
import android.util.SparseArray;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.appop.AppOpsService;
import com.android.server.wm.ActivityAssistInfo;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.ActivityTaskManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test class for {@link com.android.server.am.AppLockLocalService}.
 *
 * Build/Install/Run:
 * atest FrameworksMockingServicesTests:com.android.server.am.AppLockLocalServiceTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppLockLocalServiceTest {

    private static final String TEST_PACKAGE = "testpackage";
    private static final int TEST_USER_ID = 1234;
    private static final int TEST_UID = 12345;
    private static final ComponentName TEST_COMPONENT = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".Foo");
    private static final ComponentName TEST_COMPONENT_2 = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".FooBar");

    // TODO(b/302724778): Remove manual JNI load
    static {
        System.loadLibrary("mockingservicestestjni");
    }

    @Rule
    public final ApplicationExitInfoTest.ServiceThreadRule mServiceThreadRule =
            new ApplicationExitInfoTest.ServiceThreadRule();
    private final Context mContext = getInstrumentation().getTargetContext();
    private final TestPackageLockedStateListener mListener = new TestPackageLockedStateListener();
    private AppLockLocalService mAppLockLocalService;
    private TestHandler mTestHandler;
    private ActivityManagerService mAms;
    private MockitoSession mMockingSession;
    private AutoCloseable mCloseable;
    @Mock
    private AppOpsService mAppOpsService;
    @Mock
    private UserController mUserController;
    @Mock
    private IPackageManager mPackageManager;
    @Mock
    private IPermissionManager mPermissionManager;
    @Mock
    private PackageManagerInternal mPackageManagerInternal;
    @Mock
    private ActivityTaskManagerInternal mActivityTaskManagerInternal;
    @Mock
    private ActivityAssistInfo mActivityAssistInfo;
    @Mock
    private ActivityAssistInfo mActivityAssistInfo2;
    @Mock
    private ActivityInfo mActivityInfo;
    @Mock
    private ActivityInfo mActivityInfo2;

    @Before
    public void setUp() throws Exception {
        mMockingSession = mockitoSession().initMocks(this).mockStatic(AppGlobals.class).mockStatic(
                UserHandle.class).strictness(Strictness.LENIENT).startMocking();
        mCloseable = MockitoAnnotations.openMocks(this);

        // Add Local Services
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);
        LocalServices.addService(ActivityTaskManagerInternal.class, mActivityTaskManagerInternal);

        // Mock static and package manager calls
        when(AppGlobals.getPermissionManager()).thenReturn(mPermissionManager);
        when(AppGlobals.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackagesForUid(eq(Process.myUid()))).thenReturn(new String[]{""});
        when(UserHandle.getUserId(eq(TEST_UID))).thenReturn(TEST_USER_ID);

        // Set up ActivityManagerService
        TestActivityManagerServiceInjector amsInjector = new TestActivityManagerServiceInjector(
                mContext);
        mTestHandler = new TestHandler(mServiceThreadRule.getThread().getLooper());
        when(mUserController.isUserOrItsParentRunning(anyInt())).thenReturn(true);
        mAms = new ActivityManagerService(amsInjector, mServiceThreadRule.getThread(),
                mUserController);
        mAms.mActivityTaskManager = new ActivityTaskManagerService(mContext);
        mAms.mAtmInternal = mActivityTaskManagerInternal;
        mAms.mPackageManagerInt = mPackageManagerInternal;
        when(mPackageManagerInternal.getPackageUid(eq(TEST_PACKAGE), anyLong(),
                eq(TEST_USER_ID))).thenReturn(TEST_UID);
        when(mPackageManagerInternal.getSystemUiServiceComponent()).thenReturn(TEST_COMPONENT);

        // Set up AppLockLocalService
        TestInjector injector = new TestInjector();
        mAppLockLocalService = new AppLockLocalService(mAms, injector);
        LocalServices.addService(AppLockInternal.class, mAppLockLocalService);
        mAppLockLocalService.registerPackageLockedStateListener(mListener);

        when(mActivityAssistInfo.getUserId()).thenReturn(TEST_USER_ID);
        when(mActivityAssistInfo.getComponentName()).thenReturn(TEST_COMPONENT);
        when(mActivityAssistInfo2.getUserId()).thenReturn(TEST_USER_ID);
        when(mActivityAssistInfo2.getComponentName()).thenReturn(TEST_COMPONENT_2);

    }

    @After
    public void tearDown() throws Exception {
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityTaskManagerInternal.class);
        LocalServices.removeServiceForTest(AppLockInternal.class);
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
        mCloseable.close();
    }

    @Test
    public void getAppLockEnabledPackages_returnsCorrectPackages() {
        final String testPackage1 = "test.package.one";
        final String testPackage2 = "test.package.two";
        final String testPackage3 = "test.package.three";
        final int testUser1 = 10;
        final int testUser2 = 11;

        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(
                testPackage1, testUser1);
        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(
                testPackage2, testUser1);
        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(
                testPackage3, testUser2);

        SparseArray<Set<String>> enabledPackages =
                mAppLockLocalService.getAppLockEnabledPackages();

        assertThat(enabledPackages.size()).isEqualTo(2);

        Set<String> user1Packages = enabledPackages.get(testUser1);
        assertThat(user1Packages).isNotNull();
        assertThat(user1Packages).containsExactly(testPackage1,
                testPackage2);

        Set<String> user2Packages = enabledPackages.get(testUser2);
        assertThat(user2Packages).isNotNull();
        assertThat(user2Packages).containsExactly(testPackage3);
    }

    @Test
    public void isPackageLocked_neverAuthenticatedNoVisibleActivitiesAndInBackground_true()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        when(mPackageManagerInternal.getPackageUid(eq(TEST_PACKAGE), anyLong(),
                eq(TEST_USER_ID))).thenReturn(TEST_UID);
        when(mPackageManagerInternal.getSystemUiServiceComponent()).thenReturn(TEST_COMPONENT);

        setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isTrue();
    }

    @Test
    public void isPackageLocked_appLockNotEnabled_false() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(false);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isFalse();
    }

    @Test
    public void isPackageLocked_remoteExceptionInPackageManagerCall_false() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE), eq(TEST_USER_ID)))
                .thenThrow(new RemoteException("Package not found"));

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isFalse();
    }

    @Test
    public void isPackageLocked_withinAuthTimeout_false() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);
        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(TEST_PACKAGE,
                TEST_USER_ID);

        // Wait for just under the 5-second timeout.
        CountDownLatch latch = new CountDownLatch(1);
        // TODO(b/454308946): Update timeout to be configurable
        latch.await(4900, TimeUnit.MILLISECONDS);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isFalse();
    }

    @Test
    public void isPackageLocked_afterAuthTimeout_true() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);
        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(TEST_PACKAGE,
                TEST_USER_ID);

        // Wait for just under the 5-second timeout.
        CountDownLatch latch = new CountDownLatch(1);
        // TODO(b/454308946): Update timeout to be configurable
        latch.await(5100, TimeUnit.MILLISECONDS);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isTrue();
    }

    @Test
    public void isPackageLocked_activityHasVisibleTaskWithShowWhenLocked_true() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo));
        mActivityInfo.flags |= ActivityInfo.FLAG_SHOW_WHEN_LOCKED;
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID))).thenReturn(mActivityInfo);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isTrue();
    }

    @Test
    public void isPackageLocked_activityHasVisibleTaskWithoutShowWhenLocked_false()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo));
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID))).thenReturn(mActivityInfo);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isFalse();
    }

    @Test
    public void isPackageLocked_activityMultipleVisibleTasksWithoutShowWhenLocked_false()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo, mActivityAssistInfo));
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID))).thenReturn(mActivityInfo);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isFalse();
    }

    @Test
    public void isPackageLocked_activityMultipleVisibleTasksOneWithShowWhenLocked_false()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo, mActivityAssistInfo2));
        mActivityInfo2.flags |= ActivityInfo.FLAG_SHOW_WHEN_LOCKED;
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID))).thenReturn(mActivityInfo);
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT_2), anyLong(), anyInt(),
                eq(TEST_USER_ID))).thenReturn(mActivityInfo2);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isFalse();
    }

    @Test
    public void isPackageLocked_packageIsInForegroundWithoutVisibleTasks_false() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        setupProcessAndUidRecord(PROCESS_STATE_TOP);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isFalse();
    }

    @Test
    public void isPackageLocked_lockJobQueued_false() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        mAppLockLocalService.handleLockedStateLocked(TEST_PACKAGE, TEST_USER_ID);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE, TEST_USER_ID)).isFalse();
    }

    @Test
    public void setAppLockEnabledPackageSuccessfullyAuthenticated_listenersNotified()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(TEST_PACKAGE,
                TEST_USER_ID);
        mTestHandler.executeRunnables();

        assertThat(mListener.mLocked).isFalse();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID,
                TEST_PACKAGE)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_enqueuedChangedNotProcessRelevant_noUpdate()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_TOP);

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, 0, 0);

        assertThat(mListener.hasDefaultValues()).isTrue();
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID,
                TEST_PACKAGE)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_noPackagesWithAppLockEnabled_noUpdate() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(false);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_TOP);

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);

        assertThat(mListener.hasDefaultValues()).isTrue();
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID,
                TEST_PACKAGE)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_packageNewlyLocked_lockJobQueued() throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);
        mAppLockLocalService.handleUnlockedStateLocked(TEST_PACKAGE, TEST_USER_ID);
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(500, TimeUnit.MILLISECONDS);
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);

        assertThat(mListener.hasDefaultValues()).isTrue(); // Hasn't been sent yet
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID,
                TEST_PACKAGE)).isTrue();
    }

    @Test
    public void handleUidChangeLocked_packageGoesToBackThenFrontInGracePeriod_lockedJobCanceled()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);
        mAppLockLocalService.handleUnlockedStateLocked(TEST_PACKAGE, TEST_USER_ID);
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(500, TimeUnit.MILLISECONDS);
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);
        latch.await(500, TimeUnit.MILLISECONDS);
        UidRecord record2 = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        assertThat(mListener.hasDefaultValues()).isTrue(); // Never got sent to listener
        mAppLockLocalService.handleUidChangeLocked(record2, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_TOP);
    }

    @Test
    public void handleUidChangeLocked_packageNewlyUnlocked_listenerReceivedUnlockedUpdate()
            throws Exception {
        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(TEST_PACKAGE,
                TEST_USER_ID);
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_TOP);
        mTestHandler.executeRunnables();

        assertThat(mListener.mLocked).isFalse();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID,
                TEST_PACKAGE)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_packageMovesToBackground_listenerReceivedLockedUpdateDelayed()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE),
                eq(TEST_USER_ID))).thenReturn(true);
        mAppLockLocalService.handleUnlockedStateLocked(TEST_PACKAGE, TEST_USER_ID);
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(500, TimeUnit.MILLISECONDS);
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);
        latch.await(5500, TimeUnit.MILLISECONDS);
        mTestHandler.executeRunnables();

        assertThat(mListener.mLocked).isTrue();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID,
                TEST_PACKAGE)).isFalse();
    }

    private UidRecord setupProcessAndUidRecord(int processState) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE;
        info.processName = TEST_PACKAGE;
        info.uid = TEST_UID;
        final ProcessRecord appRec = new ProcessRecord(mAms, info, info.processName, TEST_UID);
        appRec.setCurProcState(processState);
        final UidRecord record = new UidRecord(TEST_UID, mAms);
        record.setCurProcState(processState);
        record.addProcess(appRec);
        mAms.mProcessList.addProcessNameLocked(appRec);
        mAms.mProcessList.mActiveUids.put(TEST_UID, record);

        return record;
    }

    private static class TestHandler extends Handler {
        private final Queue<Runnable> mQueue = new ArrayDeque<>();

        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            mQueue.add(msg.getCallback());
            return true;
        }

        void executeRunnables() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            while (!mQueue.isEmpty()) {
                mQueue.poll().run();
            }
            latch.await(500, TimeUnit.MILLISECONDS);
        }
    }

    private static class TestPackageLockedStateListener implements
            AppLockInternal.PackageLockedStateListener {
        @Nullable
        Boolean mLocked = null;
        String mPackageName = "";
        int mUserId = -1;

        @Override
        public void onPackageLockedStateChanged(String packageName, int userId, boolean locked) {
            this.mPackageName = packageName;
            this.mUserId = userId;
            this.mLocked = locked;
        }

        boolean hasDefaultValues() {
            return (mLocked == null) && mPackageName.isEmpty() && mUserId == -1;
        }
    }

    private class TestInjector implements AppLockLocalService.Injector {
        @Override
        public Handler getHandler() {
            return mTestHandler;
        }
    }

    private class TestActivityManagerServiceInjector extends ActivityManagerService.Injector {
        TestActivityManagerServiceInjector(Context context) {
            super(context);
        }

        @Override
        public AppOpsService getAppOpsService(Handler handler) {
            return mAppOpsService;
        }

        @Override
        public Handler getUiHandler(ActivityManagerService service) {
            return mTestHandler;
        }
    }
}
