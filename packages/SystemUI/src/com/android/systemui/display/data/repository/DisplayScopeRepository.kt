/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.display.data.repository

import android.view.Display
import com.android.app.displaylib.PerDisplayInstanceProviderWithTeardown
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.coroutines.newTracingContext
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Provides per display instances of [CoroutineScope].
 *
 * This is used to create a [PerDisplayRepository] of [CoroutineScope]
 */
@SysUISingleton
class DisplayScopeRepositoryInstanceProvider
@Inject
constructor(
    @Background private val backgroundApplicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : PerDisplayInstanceProviderWithTeardown<CoroutineScope> {

    override fun createInstance(displayId: Int): CoroutineScope {
        return if (displayId == Display.DEFAULT_DISPLAY) {
            // The default display is connected all the time, therefore we can optimise by reusing
            // the application scope, and don't need to create a new scope.
            backgroundApplicationScope
        } else {
            CoroutineScope(backgroundDispatcher + newTracingContext("DisplayScope$displayId"))
        }
    }

    override fun destroyInstance(instance: CoroutineScope) {
        instance.cancel("DisplayContext has been cancelled.")
    }
}

@Module
object PerDisplayCoroutineScopeRepositoryModule {
    @SysUISingleton
    @Provides
    fun provideDisplayCoroutineScopeRepository(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<CoroutineScope>,
        instanceProvider: DisplayScopeRepositoryInstanceProvider,
    ): PerDisplayRepository<CoroutineScope> {
        return repositoryFactory.create(
            debugName = "CoroutineScopePerDisplayRepo",
            instanceProvider,
        )
    }
}
