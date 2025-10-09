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

import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR
import com.android.systemui.Flags
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.common.ui.ConfigurationStateImpl
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Default
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAwareStatusBar
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.data.repository.StatusBarConfigurationController
import com.android.systemui.statusbar.disableflags.data.repository.DisableFlagsRepository
import com.android.systemui.statusbar.disableflags.data.repository.DisableFlagsRepositoryImpl
import com.android.systemui.statusbar.disableflags.domain.interactor.DisableFlagsInteractor
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractor
import com.android.systemui.statusbar.domain.interactor.StatusBarIconRefreshInteractorImpl
import com.android.systemui.statusbar.gesture.SwipeStatusBarAwayGestureHandler
import com.android.systemui.statusbar.layout.StatusBarContentInsetsProvider
import com.android.systemui.statusbar.layout.StatusBarContentInsetsProviderImpl
import com.android.systemui.statusbar.phone.ConfigurationControllerImpl
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.HomeStatusBarInteractor
import com.android.systemui.statusbar.ui.SystemBarUtilsState
import com.android.systemui.statusbar.window.StatusBarWindowStateController
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope

/**
 * Contains bindings that are [SystemUIDisplaySubcomponent.DisplayAware] related to the statusbar.
 */
@Module
interface PerDisplayStatusBarModule {

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun bindsStatusBarIconRefreshInteractor(
        impl: StatusBarIconRefreshInteractorImpl
    ): StatusBarIconRefreshInteractor

    @Binds
    @DisplayAware
    fun statusBarWindowStateController(
        controller: StatusBarWindowStateController
    ): StatusBarWindowStateController

    @Binds
    @DisplayAware
    fun statusBarContentInsetsProvider(
        impl: StatusBarContentInsetsProviderImpl
    ): StatusBarContentInsetsProvider

    @Binds
    @DisplayAware
    @IntoSet
    fun statusBarContentInsetsProviderAsLifecycleListener(
        impl: StatusBarContentInsetsProviderImpl
    ): SystemUIDisplaySubcomponent.LifecycleListener

    @Binds
    @PerDisplaySingleton
    @DisplayAware
    fun ongoingActivityChipsViewModel(
        impl: OngoingActivityChipsViewModel
    ): OngoingActivityChipsViewModel

    @Binds
    @DisplayAware
    fun homeStatusBarInteractor(interactor: HomeStatusBarInteractor): HomeStatusBarInteractor

    @Binds
    @DisplayAware
    fun swipeStatusBarAwayGestureHandler(
        impl: SwipeStatusBarAwayGestureHandler
    ): SwipeStatusBarAwayGestureHandler

    companion object {
        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun disableFlagsRepo(
            factory: DisableFlagsRepositoryImpl.Factory,
            @DisplayAware actualDisplayId: Int,
            @DisplayAware displayScope: CoroutineScope,
            @Default defaultRepositoryLazy: Lazy<DisableFlagsRepository>,
        ): DisableFlagsRepository {
            return if (Flags.disableFlagsPerDisplay()) {
                factory.create(actualDisplayId, displayScope)
            } else {
                defaultRepositoryLazy.get()
            }
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun disableFlagsInteractor(
            factory: DisableFlagsInteractor.Factory,
            @DisplayAware repo: DisableFlagsRepository,
            @Default defaultInteractorLazy: Lazy<DisableFlagsInteractor>,
        ): DisableFlagsInteractor {
            return if (Flags.disableFlagsPerDisplay()) {
                factory.create(repo)
            } else {
                defaultInteractorLazy.get()
            }
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun provideStatusBarConfigurationController(
            @DisplayAwareStatusBar context: Context,
            configurationControllerFactory: ConfigurationControllerImpl.Factory,
        ): StatusBarConfigurationController {
            return configurationControllerFactory.create(context)
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun systemBarUtilsState(
            @DisplayAware context: Context,
            @DisplayAware configurationController: StatusBarConfigurationController,
            factory: SystemBarUtilsState.Factory,
        ): SystemBarUtilsState {
            return factory.create(context, configurationController)
        }

        @Provides
        @PerDisplaySingleton
        @DisplayAware
        fun configurationState(
            configStateFactory: ConfigurationStateImpl.Factory,
            @DisplayAware configurationController: StatusBarConfigurationController,
            @DisplayAware context: Context,
        ): ConfigurationState {
            return configStateFactory.create(context, configurationController)
        }

        /**
         * Status Bar specific per display [Context]. For the default display it uses the default
         * application [Context], which is all that is needed.
         *
         * For external displays, it will be a [WindowContext], which is tied to the display and
         * also window type [TYPE_STATUS_BAR].
         */
        @Provides
        @PerDisplaySingleton
        @DisplayAwareStatusBar
        fun provideStatusBarWindowContext(
            display: Display,
            @Application context: Context,
        ): Context {
            return if (display.displayId == Display.DEFAULT_DISPLAY) {
                // No need to create a new context, if we already have one.
                context
            } else {
                context
                    .createWindowContext(display, TYPE_STATUS_BAR, /* options= */ Bundle.EMPTY)
                    .also { it.setTheme(R.style.Theme_SystemUI) }
            }
        }
    }
}
