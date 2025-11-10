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

package com.android.systemui.statusbar.phone.dagger

import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import com.android.systemui.statusbar.phone.ongoingcall.domain.interactor.OngoingCallStatusBarInteractor
import com.android.systemui.statusbar.phone.ongoingcall.shared.PerDisplayOngoingCallStatusBarVisibility
import com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarViewBinder
import com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarViewBinderImpl
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel.HomeStatusBarViewModelFactory
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModelImpl.HomeStatusBarViewModelFactoryImpl
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet

@Module(subcomponents = [HomeStatusBarComponent::class])
interface PerDisplayStatusBarReferenceModule {

    @Binds
    @DisplayAware
    fun homeStatusBarComponentFactory(
        factory: HomeStatusBarComponent.Factory
    ): HomeStatusBarComponent.Factory

    @Binds
    @DisplayAware
    fun homeStatusBarViewModelFactory(
        impl: HomeStatusBarViewModelFactoryImpl
    ): HomeStatusBarViewModelFactory

    @Binds
    @DisplayAware
    fun homeStatusBarViewBinder(impl: HomeStatusBarViewBinderImpl): HomeStatusBarViewBinder

    companion object {
        @Provides
        @ElementsIntoSet
        @DisplayAware
        fun ongoingCallStatusBarInteractorAsLifecycleListener(
            interactorLazy: Lazy<OngoingCallStatusBarInteractor>
        ): Set<SystemUIDisplaySubcomponent.LifecycleListener> {
            return if (PerDisplayOngoingCallStatusBarVisibility.isEnabled) {
                setOf(interactorLazy.get())
            } else {
                emptySet()
            }
        }
    }
}
