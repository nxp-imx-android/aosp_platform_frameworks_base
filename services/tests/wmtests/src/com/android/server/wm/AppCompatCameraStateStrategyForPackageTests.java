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

import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX;

import static org.junit.Assert.assertEquals;

import android.annotation.NonNull;
import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatCameraStateStrategyForPackage}.
 * <p>
 * Build/Install/Run:
 * atest WmTests:AppCompatCameraStateStrategyForPackageTests
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatCameraStateStrategyForPackageTests extends WindowTestsBase {
    private static final String TEST_PACKAGE_1 = "com.android.frameworks.wmtests";
    private static final String CAMERA_ID_1 = "camera-1";

    @Rule
    public TestRule mCompatChangeRule = new PlatformCompatChangeRule();

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testTrackCameraOpened_returnsCorrectCameraAppInfo() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();

            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
        });
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testTrackCameraOpened_cameraNotYetOpened() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();

            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraOpened(false);
            robot.checkCameraOpenedCalledForCanClosePolicy(0);
        });
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testOnCameraOpened_notifiesPolicy() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            robot.checkCameraOpenedCalledForCanClosePolicy(1);
        });
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testOnCameraOpened_cameraIsOpened() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            robot.checkIsCameraOpened(true);
        });
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testTrackCameraClosed_returnsCorrectCameraAppInfo() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            robot.assertCorrectCameraAppInfoOnCameraClosed(CAMERA_ID_1);
        });
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testOnCameraClosed_policyCanCloseCamera_cameraIsClosed() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCanClose();
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            robot.assertCorrectCameraAppInfoOnCameraClosed(CAMERA_ID_1);

            robot.assertReportsCloseStatusOnCameraClose(CAMERA_ID_1);
        });
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX)
    public void testOnCameraClosed_activityCannotCloseCamera_returnsCorrectStatus() {
        runTestScenario((robot) -> {
            robot.addPolicyThatCannotCloseOnce();
            robot.assertCorrectCameraAppInfoOnCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.maybeNotifyPolicyCameraOpened(CAMERA_ID_1);

            robot.assertCorrectCameraAppInfoOnCameraClosed(CAMERA_ID_1);

            robot.assertReportsCloseStatusOnCameraClose(CAMERA_ID_1);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatCameraStateStrategyForPackageRobotTest>
            consumer) {
        final AppCompatCameraStateStrategyForPackageRobotTest robot =
                new AppCompatCameraStateStrategyForPackageRobotTest(mWm, mAtm, mSupervisor);
        consumer.accept(robot);
    }

    private static class AppCompatCameraStateStrategyForPackageRobotTest
            extends AppCompatRobotBase {
        private FakeAppCompatCameraStatePolicy mFakePolicyCannotCloseOnce;
        private FakeAppCompatCameraStatePolicy mFakePolicyCanClose;

        private Set<FakeAppCompatCameraStatePolicy> mRegisteredPolicies = new ArraySet<>();

        AppCompatCameraStateStrategyForPackageRobotTest(@NonNull WindowManagerService wm,
                @NonNull ActivityTaskManagerService atm,
                @NonNull ActivityTaskSupervisor supervisor) {
            super(wm, atm, supervisor);
            setupAppCompatConfiguration();
            configureActivityAndDisplay();
        }

        @Override
        void onPostDisplayContentCreation(@NonNull DisplayContent displayContent) {
            super.onPostDisplayContentCreation(displayContent);
            mRegisteredPolicies = new ArraySet<>();
            mFakePolicyCannotCloseOnce = new FakeAppCompatCameraStatePolicy(true);
            mFakePolicyCanClose = new FakeAppCompatCameraStatePolicy(false);
        }

        private void configureActivityAndDisplay() {
            applyOnActivity(a -> {
                a.createActivityWithComponentInNewTaskAndDisplay();
            });
        }

        private void setupAppCompatConfiguration() {
            applyOnConf((c) -> {
                c.enableCameraCompatTreatment(true);
                c.enableCameraCompatTreatmentAtBuildTime(true);
            });
        }

        private void addPolicyThatCanClose() {
            getAppCompatCameraStateSource().addCameraStatePolicy(mFakePolicyCanClose);
            mRegisteredPolicies.add(mFakePolicyCanClose);
        }

        private void addPolicyThatCannotCloseOnce() {
            getAppCompatCameraStateSource().addCameraStatePolicy(mFakePolicyCannotCloseOnce);
            mRegisteredPolicies.add(mFakePolicyCannotCloseOnce);
        }

        private AppCompatCameraStateSource getAppCompatCameraStateSource() {
            return (AppCompatCameraStateSource) activity().top().mDisplayContent
                    .mAppCompatCameraPolicy.mCameraStateMonitor.mAppCompatCameraStatePolicy;
        }

        void assertCorrectCameraAppInfoOnCameraOpened(@NonNull String cameraId,
                @NonNull String packageName) {
            final CameraAppInfo cameraAppInfo = trackCameraOpened(cameraId, packageName);
            assertEquals(cameraId, cameraAppInfo.mCameraId);
            assertEquals(packageName, cameraAppInfo.mPackageName);
            assertEquals(activity().top().getTask().mTaskId, cameraAppInfo.mTaskId);
            assertEquals(activity().top().app.getPid(), cameraAppInfo.mPid);
        }

        void assertCorrectCameraAppInfoOnCameraClosed(@NonNull String cameraId) {
            final CameraAppInfo cameraAppInfo = trackCameraClosed(cameraId);
            assertEquals(cameraId, cameraAppInfo.mCameraId);
            assertEquals(activity().top().packageName, cameraAppInfo.mPackageName);
            assertEquals(activity().top().getTask().mTaskId, cameraAppInfo.mTaskId);
            assertEquals(activity().top().app.getPid(), cameraAppInfo.mPid);
        }

        void checkIsCameraOpened(boolean expectedIsOpened) {
            assertEquals(expectedIsOpened, getCameraStateMonitor().mAppCompatCameraStateStrategy
                    .isCameraRunningForActivity(activity().top()));
        }

        private void checkCameraOpenedCalledForCanClosePolicy(int times) {
            assertEquals(times, mFakePolicyCanClose.mOnCameraOpenedCounter);
        }

        void assertReportsCloseStatusOnCameraClose(@NonNull String cameraId) {
            assertReportsCloseStatusOnCameraClose(getExpectedCameraAppInfo(cameraId));
        }

        void assertReportsCloseStatusOnCameraClose(@NonNull CameraAppInfo cameraAppInfo) {
            for (FakeAppCompatCameraStatePolicy policy : mRegisteredPolicies) {
                boolean simulateCannotClose = policy == mFakePolicyCannotCloseOnce;
                assertEquals(!simulateCannotClose, notifyPolicyCameraClosedIfNeeded(cameraAppInfo,
                        simulateCannotClose ? mFakePolicyCannotCloseOnce : mFakePolicyCanClose));
            }
        }

        private CameraAppInfo trackCameraOpened(@NonNull String cameraId,
                @NonNull String packageName) {
            return activity().displayContent().mAppCompatCameraPolicy.mCameraStateMonitor
                    .mAppCompatCameraStateStrategy.trackOnCameraOpened(cameraId, packageName);
        }

        private void maybeNotifyPolicyCameraOpened(@NonNull String cameraId) {
            for (FakeAppCompatCameraStatePolicy policy : mRegisteredPolicies) {
                notifyPolicyCameraOpenedIfNeeded(getExpectedCameraAppInfo(cameraId), policy);
            }
        }

        private void notifyPolicyCameraOpenedIfNeeded(@NonNull CameraAppInfo cameraAppInfo,
                @NonNull AppCompatCameraStatePolicy policy) {
            activity().displayContent().mAppCompatCameraPolicy.mCameraStateMonitor
                    .mAppCompatCameraStateStrategy.notifyPolicyCameraOpenedIfNeeded(cameraAppInfo,
                            policy);
        }

        private CameraAppInfo trackCameraClosed(@NonNull String cameraId) {
            return activity().displayContent().mAppCompatCameraPolicy.mCameraStateMonitor
                    .mAppCompatCameraStateStrategy.trackOnCameraClosed(cameraId);
        }

        private void notifyPolicyCameraClosedIfNeeded(@NonNull String cameraId) {
            for (FakeAppCompatCameraStatePolicy policy : mRegisteredPolicies) {
                notifyPolicyCameraClosedIfNeeded(getExpectedCameraAppInfo(cameraId),
                        policy);
            }
        }

        boolean notifyPolicyCameraClosedIfNeeded(@NonNull CameraAppInfo cameraAppInfo,
                @NonNull AppCompatCameraStatePolicy policy) {
            return activity().displayContent().mAppCompatCameraPolicy.mCameraStateMonitor
                    .mAppCompatCameraStateStrategy.notifyPolicyCameraClosedIfNeeded(cameraAppInfo,
                            policy);
        }

        private CameraAppInfo getExpectedCameraAppInfo(@NonNull String cameraId) {
            return new CameraAppInfo(cameraId,
                    activity().top().app.getPid(),
                    activity().top().getTask().mTaskId,
                    activity().top().packageName);
        }

        private CameraStateMonitor getCameraStateMonitor() {
            return activity().top().mDisplayContent.mAppCompatCameraPolicy.mCameraStateMonitor;
        }
    }
}
