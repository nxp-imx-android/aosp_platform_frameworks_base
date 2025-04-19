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

import static com.android.window.flags.Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;

import org.junit.Test;
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

    @EnableFlags(FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testRestartMenuVisibility() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.checkRestartMenuVisibility(false);

            robot.activity().moveTaskToSecondaryDisplay();
            robot.checkRestartMenuVisibility(true);

            robot.activity().applyToTopActivity(ActivityRecord::restartProcessIfVisible);
            robot.checkRestartMenuVisibility(false);
        });
    }

    void runTestScenario(@NonNull Consumer<DisplayCompatRobotTest> consumer) {
        final DisplayCompatRobotTest robot = new DisplayCompatRobotTest(mWm, mAtm, mSupervisor);
        consumer.accept(robot);
    }

    private static class DisplayCompatRobotTest extends AppCompatRobotBase {

        DisplayCompatRobotTest(@NonNull WindowManagerService wm,
                @NonNull ActivityTaskManagerService atm,
                @NonNull ActivityTaskSupervisor supervisor) {
            super(wm, atm, supervisor);
        }

        void checkRestartMenuVisibility(boolean enabled) {
            activity().applyToTopActivity(activity -> assertEquals(enabled,
                    activity.getTask().getTaskInfo().appCompatTaskInfo
                            .isRestartMenuEnabledForDisplayMove()));
        }
    }
}
