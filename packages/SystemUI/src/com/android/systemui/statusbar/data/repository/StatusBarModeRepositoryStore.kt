/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.data.repository

import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.DisplayId
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.display.data.repository.PerDisplayStore
import com.android.systemui.statusbar.core.StatusBarInitializer
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

interface StatusBarModeRepositoryStore : PerDisplayStore<StatusBarModePerDisplayRepository>

@SysUISingleton
class MultiDisplayStatusBarModeRepositoryStore
@Inject
constructor(
    @Background backgroundApplicationScope: CoroutineScope,
    private val factory: StatusBarModePerDisplayRepositoryFactory,
    displayRepository: DisplayRepository,
    private val displaySubcomponentRepo: PerDisplayRepository<SystemUIDisplaySubcomponent>,
) :
    StatusBarModeRepositoryStore,
    StatusBarPerDisplayStoreImpl<StatusBarModePerDisplayRepository>(
        backgroundApplicationScope,
        displayRepository,
    ) {

    override fun createInstanceForDisplay(displayId: Int): StatusBarModePerDisplayRepository? {
        val displaySubcomponent = displaySubcomponentRepo[displayId] ?: return null
        return factory.create(displaySubcomponent.displayCoroutineScope, displayId).also {
            it.start()
        }
    }

    override suspend fun onDisplayRemovalAction(instance: StatusBarModePerDisplayRepository) {
        instance.stop()
    }

    override val instanceClass = StatusBarModePerDisplayRepository::class.java
}

@SysUISingleton
class StatusBarModeRepositoryImpl
@Inject
constructor(
    @DisplayId private val displayId: Int,
    factory: StatusBarModePerDisplayRepositoryFactory,
    @Application applicationCoroutineScope: CoroutineScope,
) :
    StatusBarModeRepositoryStore,
    CoreStartable,
    StatusBarInitializer.StatusBarViewLifecycleListener {
    override val defaultDisplay = factory.create(applicationCoroutineScope, displayId)

    override fun forDisplay(displayId: Int) = defaultDisplay

    override fun start() {
        defaultDisplay.start()
    }

    override fun onStatusBarViewInitialized(component: HomeStatusBarComponent) {
        defaultDisplay.onStatusBarViewInitialized(component)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        defaultDisplay.dump(pw, args)
    }
}

@Module
interface StatusBarModeRepositoryModule {

    @Binds
    @SysUISingleton
    @IntoMap
    @ClassKey(StatusBarModeRepositoryStore::class)
    fun storeAsCoreStartable(store: MultiDisplayStatusBarModeRepositoryStore): CoreStartable

    @Binds
    @SysUISingleton
    fun store(store: MultiDisplayStatusBarModeRepositoryStore): StatusBarModeRepositoryStore
}
