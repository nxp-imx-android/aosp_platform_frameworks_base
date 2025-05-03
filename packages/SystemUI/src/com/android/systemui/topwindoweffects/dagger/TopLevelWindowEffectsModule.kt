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

package com.android.systemui.topwindoweffects.dagger

import android.os.Handler
import android.os.Looper
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shared.Flags.enableLppAssistInvocationEffect
import com.android.systemui.topwindoweffects.TopLevelWindowEffects
import com.android.systemui.topwindoweffects.qualifiers.TopLevelWindowEffectsThread
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher

@Module
object TopLevelWindowEffectsModule {

    @Provides
    @IntoMap
    @ClassKey(TopLevelWindowEffects::class)
    fun provideTopLevelWindowEffectsCoreStartable(impl: TopLevelWindowEffects): CoreStartable {
        return if (enableLppAssistInvocationEffect()) {
            impl
        } else {
            CoreStartable {
                // empty, no-op
            }
        }
    }

    @Provides
    @SysUISingleton
    @TopLevelWindowEffectsThread
    fun provideTopLevelWindowEffectsHandler(@TopLevelWindowEffectsThread looper: Looper): Handler =
        Handler(looper)

    @Provides
    @SysUISingleton
    @TopLevelWindowEffectsThread
    fun provideTopLevelWindowEffectsScope(
        @TopLevelWindowEffectsThread dispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(dispatcher)

    @Provides
    @SysUISingleton
    @TopLevelWindowEffectsThread
    fun provideTopLevelWindowEffectsDispatcher(
        @TopLevelWindowEffectsThread executor: Executor
    ): CoroutineDispatcher = executor.asCoroutineDispatcher()

    @Provides
    @SysUISingleton
    @TopLevelWindowEffectsThread
    fun provideTopLevelWindowEffectsContext(
        @TopLevelWindowEffectsThread dispatcher: CoroutineDispatcher
    ): CoroutineContext = dispatcher
}
