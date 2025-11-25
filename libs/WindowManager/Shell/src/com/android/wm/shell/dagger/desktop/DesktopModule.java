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

import com.android.wm.shell.dagger.DynamicOverride;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.desktopmode.DesksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.shared.desktopmode.DesktopConfig;
import com.android.wm.shell.sysui.ShellController;

import dagger.Module;
import dagger.Provides;

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
            @NonNull DesktopConfig desktopConfig
    ) {
        return new DesksController(shellController, userRepositories,
                desktopConfig);
    }
}
