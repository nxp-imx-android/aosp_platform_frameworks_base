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

package com.android.systemui.statusbar.dagger

import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.statusbar.data.repository.StatusBarConfigurationController
import com.android.systemui.statusbar.data.repository.StatusBarConfigurationControllerStore
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractor
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractorImpl
import dagger.Binds
import dagger.Module
import dagger.Provides

/**
 * Contains bindings that are [SystemUIDisplaySubcomponent.DisplayAware] related to the statusbar.
 */
@Module
interface StatusBarPerDisplayModule {

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun bindsStatusBarIconRefreshInteractor(
        impl: StatusBarIconRefreshInteractorImpl
    ): StatusBarIconRefreshInteractor

    companion object {
        /**
         * Ideally StatusBarConfigurationControllerStore should be moved to [PerDisplaySingleton] in
         * the future, and the [StatusBarConfigurationControllerStore] return the instance from the
         * per-display component.
         *
         * Note that the error here will not cause SystemUI to crash, but just the subcomponent to
         * not be instantiated correctly and be null.
         */
        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun provideStatusBarConfigurationController(
            @SystemUIDisplaySubcomponent.DisplayAware displayId: Int,
            configurationControllerStore: StatusBarConfigurationControllerStore,
        ): StatusBarConfigurationController {
            return configurationControllerStore.forDisplay(displayId)
                ?: error("No configuration controller for display $displayId")
        }
    }
}
