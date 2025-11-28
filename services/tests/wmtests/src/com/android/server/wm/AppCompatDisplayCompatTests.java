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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.content.pm.ActivityInfo.CONFIG_COLOR_MODE;
import static android.content.pm.ActivityInfo.CONFIG_DENSITY;
import static android.content.pm.ActivityInfo.CONFIG_RESOURCES_UNUSED;
import static android.content.pm.ActivityInfo.CONFIG_TOUCHSCREEN;
import static android.content.pm.ActivityInfo.OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.window.flags.Flags.FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL;
import static com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_COMPAT_MODE;
import static com.android.window.flags.Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Tests for display related app-compat behavior.
 *
 * Build/Install/Run:
 * atest WmTests:AppCompatDisplayCompatTests
 */
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatDisplayCompatTests extends WindowTestsBase {

    private static final int CONFIG_MASK_FOR_DISPLAY_MOVE =
            ~(CONFIG_DENSITY | CONFIG_TOUCHSCREEN | CONFIG_COLOR_MODE | CONFIG_RESOURCES_UNUSED);

    private enum SelfKillType {
        FINISH_ACTIVITY,
        FINISH_AND_REMOVE_TASK,
        KILL_PROCESS
    }

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Before
    public void setUp() {
        doReturn(false).when(mDisplayContent).shouldSleep();
        mDisplayContent.wakeIfNeeded();
    }

    @EnableFlags({FLAG_ENABLE_DISPLAY_COMPAT_MODE, FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS})
    @Test
    public void testDisplayCompatMode_gameDoesNotRestartWithDisplayMove() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityGame(true);
            robot.activity().setTopActivityResumed();
            robot.activity().setTopActivityConfigChanges(CONFIG_MASK_FOR_DISPLAY_MOVE);
            robot.checkRestartMenuVisibility(false);
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskBetweenDisplays();
            robot.activity().checkTopActivityRelaunched(false);
            robot.checkRestartMenuVisibility(true);

            robot.activity().applyToTopActivity(ActivityRecord::restartProcessIfVisible);
            robot.checkRestartMenuVisibility(false);
        });
    }

    @Test
    public void testDisplayCompatMode_nonGameRestartsWithDisplayMove() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityResumed();
            robot.activity().setTopActivityConfigChanges(CONFIG_MASK_FOR_DISPLAY_MOVE);
            robot.checkRestartMenuVisibility(false);
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskBetweenDisplays();
            robot.activity().checkTopActivityRelaunched(true);
            robot.checkRestartMenuVisibility(false);
        });
    }

    @EnableFlags(FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testSizeCompatMode_sizeCompatModeAppHasRestartMenuWithDisplayMove() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityInSizeCompatMode(true);
            robot.activity().setShouldCreateCompatDisplayInsets(true);
            robot.activity().setTopActivityResumed();
            robot.checkRestartMenuVisibility(false);
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskBetweenDisplays();
            robot.activity().checkTopActivityRelaunched(false);
            robot.checkRestartMenuVisibility(true);

            robot.activity().applyToTopActivity(ActivityRecord::restartProcessIfVisible);
            robot.checkRestartMenuVisibility(false);
        });
    }

    @EnableCompatChanges(OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @Test
    public void testAutoRestartOnDisplayMove_enabled_restartsApp() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityResumed();
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskBetweenDisplays();

            robot.activity().checkTopActivityProcessRestarted(true);
        });
    }

    @DisableCompatChanges(OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @Test
    public void testAutoRestartOnDisplayMove_compatChangeDisabled_doesNotRestartApp() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityResumed();
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskBetweenDisplays();

            robot.activity().checkTopActivityProcessRestarted(false);
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_finishActivity() {
        testSelfKillOnDisplayMoveInner(true /* shouldRecoverFromSelfKill */,
                true /* moveDisplays */,
                false /* removeDisplay */,
                true /* relaunchActivity */,
                SelfKillType.FINISH_ACTIVITY);
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_disconnectDisplay() {
        testSelfKillOnDisplayMoveInner(false /* shouldRecoverFromSelfKill */,
                true /* moveDisplays */,
                true /* removeDisplay */,
                true /* relaunchActivity */,
                SelfKillType.FINISH_ACTIVITY);
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_finishAndRemoveTask() {
        testSelfKillOnDisplayMoveInner(true /* shouldRecoverFromSelfKill */,
                true /* moveDisplays */,
                false /* removeDisplay */,
                true /* relaunchActivity */,
                SelfKillType.FINISH_AND_REMOVE_TASK);
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_killProcess() {
        testSelfKillOnDisplayMoveInner(true /* shouldRecoverFromSelfKill */,
                true /* moveDisplays */,
                false /* removeDisplay */,
                true /* relaunchActivity */,
                SelfKillType.KILL_PROCESS /* selfKillType */);
    }

    @DisableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_flagDisabled() {
        testSelfKillOnDisplayMoveInner(false /* shouldRecoverFromSelfKill */,
                true /* moveDisplays */,
                false /* removeDisplay */,
                true /* relaunchActivity */,
                SelfKillType.FINISH_ACTIVITY);
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryNotOnDisplayMove() {
        testSelfKillOnDisplayMoveInner(false /* shouldRecoverFromSelfKill */,
                false /* moveDisplays */,
                false /* removeDisplay */,
                true /* relaunchActivity */,
                SelfKillType.FINISH_ACTIVITY);
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_noActivityRelaunch() {
        testSelfKillOnDisplayMoveInner(false /* shouldRecoverFromSelfKill */,
                true /* moveDisplays */,
                false /* removeDisplay */,
                false /* relaunchActivity */,
                SelfKillType.FINISH_ACTIVITY);
    }

    // TODO(b/463781180): Consider using the builder pattern.
    private void testSelfKillOnDisplayMoveInner(boolean shouldRecoverFromSelfKill,
            boolean moveDisplays, boolean removeDisplay, boolean relaunchActivity,
            SelfKillType selfKillType) {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponentInSecondaryDisplay();
            robot.activity().setTopActivityResumed();
            robot.activity().setTopActivityConfigChanges(
                    relaunchActivity ? 0 : CONFIG_RESOURCES_UNUSED);
            robot.activity().clearInvocationsForActivity();

            if (moveDisplays) {
                if (removeDisplay) {
                    robot.completeTransition();
                    robot.activity().top().mRootWindowContainer
                            .onDisplayRemoved(robot.activity().top().getDisplayId());
                }
                robot.activity().moveTaskBetweenDisplays();
            } else {
                robot.activity().setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
            }

            robot.emulateAndVerifySelfKill(selfKillType, shouldRecoverFromSelfKill);
        });
    }

    void runTestScenario(@NonNull Consumer<DisplayCompatRobotTest> consumer) {
        final DisplayCompatRobotTest robot = new DisplayCompatRobotTest(this);
        consumer.accept(robot);
    }

    private static class DisplayCompatRobotTest extends AppCompatRobotBase {

        DisplayCompatRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
            windowTestBase.registerTestTransitionPlayer();
        }

        @Override
        void onPostActivityCreation(@android.annotation.NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);

            final ActivityStartController startController =
                    activity.mAtmService.getActivityStartController();
            spyOn(startController);
            doReturn(0).when(startController).startActivityInPackage(anyInt(), anyInt(), anyInt(),
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), anyInt(),
                    any(), any(), anyBoolean(), any(), anyBoolean());
        }

        void checkRestartMenuVisibility(boolean enabled) {
            activity().applyToTopActivity(activity -> assertEquals(enabled,
                    activity.getTask().getTaskInfo().appCompatTaskInfo
                            .isRestartMenuEnabledForDisplayMove()));
        }

        void checkSelfKillRecoveryExecuted(boolean executed) {
            verify(activity().top().mAtmService.getActivityStartController(),
                    times(executed ? 1 : 0)).startActivityInPackage(anyInt(), anyInt(), anyInt(),
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), anyInt(),
                    any(), any(), anyBoolean(), any(), anyBoolean());
        }

        void emulateAndVerifySelfKill(SelfKillType selfKillType,
                boolean shouldRecoverFromSelfKill) {
            if (selfKillType == SelfKillType.KILL_PROCESS) {
                activity().exitAppProcess();
                completeTransition();
                Assert.assertFalse(activity().top().finishing);
            } else {
                if (selfKillType == SelfKillType.FINISH_ACTIVITY) {
                    activity().finishTopActivity();
                } else { // SelfKillType.FINISH_AND_REMOVE_TASK
                    activity().removeTopTask();
                }
                activity().top().mAppCompatController.getDisplayCompatModePolicy()
                        .onActivityFinishing();
                completeTransition();
                checkSelfKillRecoveryExecuted(shouldRecoverFromSelfKill);
            }
        }

        private void completeTransition() {
            final Transition transition =
                    activity().top().mTransitionController.getCollectingTransition();
            Assert.assertNotNull(transition);
            final ActionChain chain = ActionChain.testFinish(transition);
            activity().top().mWmService.mSyncEngine.abort(transition.getSyncId());
            transition.finishTransition(chain);
            testBase().waitHandlerIdle(activity().top().mWmService.mAtmService.mH);
        }
    }
}
