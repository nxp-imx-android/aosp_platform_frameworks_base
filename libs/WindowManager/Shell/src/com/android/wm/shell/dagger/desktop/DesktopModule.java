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

package com.android.wm.shell.dagger.desktop;

import android.annotation.NonNull;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.dagger.DynamicOverride;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.ShellDesktopState;
import com.android.wm.shell.desktopmode.SnapController;
import com.android.wm.shell.desktopmode.desktoptaskshandlers.DesktopTasksTransitionHandler;
import com.android.wm.shell.desktopmode.multidesks.DesksController;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.shared.desktopmode.DesktopConfig;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import dagger.Module;
import dagger.Provides;

import java.util.Optional;

/**
 * Provides Desktop Shell implementation components to Dagger dependency Graph.
 */
@Module
public class DesktopModule {

    @WMSingleton
    @Provides
    static DesksController provideDesksController(
            @NonNull ShellController shellController,
            @DynamicOverride @NonNull DesktopUserRepositories userRepositories,
            @NonNull DesktopConfig desktopConfig,
            @NonNull DesktopState desktopState,
            @NonNull DisplayController displayController,
            @DynamicOverride @NonNull DesksOrganizer desksOrganizer,
            @NonNull ShellTaskOrganizer shellTaskOrganizer
    ) {
        return new DesksController(shellController, userRepositories,
                desktopConfig, desktopState, displayController, desksOrganizer, shellTaskOrganizer);
    }

    @WMSingleton
    @Provides
    static SnapController provideSnapController() {
        return new SnapController();
    }

    @WMSingleton
    @Provides
    static DesktopTasksTransitionHandler provideDesktopTasksTransitionHandler(
            Transitions transitions,
            ShellInit shellInit,
            ShellDesktopState desktopState,
            Optional<DesktopTasksController> desktopTasksController) {
        return new DesktopTasksTransitionHandler(transitions, shellInit, desktopState,
                desktopTasksController);
    }
}
