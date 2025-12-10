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

package com.android.systemui.personalcontext.dagger

import com.android.systemui.personalcontext.ComposeViewFactory
import com.android.systemui.personalcontext.ComposeViewFactoryImpl
import com.android.systemui.personalcontext.visualizer.session.VisualizerSessionFactory
import com.android.systemui.personalcontext.visualizer.session.VisualizerSessionFactoryImpl
import com.android.systemui.personalcontext.visualizer.templates.VisualizerTemplateFactory
import com.android.systemui.personalcontext.visualizer.templates.VisualizerTemplateFactoryImpl
import dagger.Lazy
import dagger.Module
import dagger.Provides

@Module
interface PersonalContextModule {
    companion object {
        @Provides
        fun provideVisualizerTemplateFactory(
            impl: Lazy<VisualizerTemplateFactoryImpl>
        ): VisualizerTemplateFactory {
            return impl.get()
        }

        @Provides
        fun provideVisualizerSessionFactory(
            impl: Lazy<VisualizerSessionFactoryImpl>
        ): VisualizerSessionFactory {
            return impl.get()
        }

        @Provides
        fun provideComposeViewFactory(impl: Lazy<ComposeViewFactoryImpl>): ComposeViewFactory {
            return impl.get()
        }
    }
}
