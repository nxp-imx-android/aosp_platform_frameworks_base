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

package com.android.systemui.statusbar.data.repository

import android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR
import com.android.systemui.CoreStartable
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.common.ui.ConfigurationStateImpl
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.display.data.repository.DisplayWindowPropertiesRepository
import com.android.systemui.display.data.repository.PerDisplayStore
import com.android.systemui.display.data.repository.PerDisplayStoreImpl
import com.android.systemui.display.data.repository.SingleDisplayStore
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Status bar specific interface to disambiguate from the global [ConfigurationState]. */
interface StatusBarConfigurationState : ConfigurationState

/** Provides per display instances of [ConfigurationState], specifically for the Status Bar. */
interface StatusBarConfigurationStateStore : PerDisplayStore<StatusBarConfigurationState>

@SysUISingleton
class MultiDisplayStatusBarConfigurationStateStore
@Inject
constructor(
    @Background backgroundApplicationScope: CoroutineScope,
    displayRepository: DisplayRepository,
    private val displayWindowPropertiesRepository: DisplayWindowPropertiesRepository,
    private val statusBarConfigurationControllerStore: StatusBarConfigurationControllerStore,
    private val factory: ConfigurationStateImpl.Factory,
) :
    StatusBarConfigurationStateStore,
    PerDisplayStoreImpl<StatusBarConfigurationState>(
        backgroundApplicationScope,
        displayRepository,
    ) {

    init {
        StatusBarConnectedDisplays.unsafeAssertInNewMode()
    }

    override fun createInstanceForDisplay(displayId: Int): StatusBarConfigurationState? {
        val displayWindowProperties =
            displayWindowPropertiesRepository.get(displayId, TYPE_STATUS_BAR) ?: return null
        val configController =
            statusBarConfigurationControllerStore.forDisplay(displayId) ?: return null
        return factory.create(displayWindowProperties.context, configController)
    }

    override val instanceClass = StatusBarConfigurationState::class.java
}

@SysUISingleton
class SingleDisplayStatusBarConfigurationStateStore
@Inject
constructor(@Main globalConfigState: ConfigurationState) :
    StatusBarConfigurationStateStore,
    PerDisplayStore<StatusBarConfigurationState> by SingleDisplayStore(
        globalConfigState as StatusBarConfigurationState
    ) {

    init {
        StatusBarConnectedDisplays.assertInLegacyMode()
    }
}

@Module
object StatusBarConfigurationStateModule {

    @Provides
    @SysUISingleton
    fun store(
        singleDisplayLazy: Lazy<SingleDisplayStatusBarConfigurationStateStore>,
        multiDisplayLazy: Lazy<MultiDisplayStatusBarConfigurationStateStore>,
    ): StatusBarConfigurationStateStore {
        return if (StatusBarConnectedDisplays.isEnabled) {
            multiDisplayLazy.get()
        } else {
            singleDisplayLazy.get()
        }
    }

    @Provides
    @SysUISingleton
    @IntoMap
    @ClassKey(StatusBarConfigurationStateStore::class)
    fun storeAsCoreStartable(
        multiDisplayLazy: Lazy<MultiDisplayStatusBarConfigurationStateStore>
    ): CoreStartable {
        return if (StatusBarConnectedDisplays.isEnabled) {
            multiDisplayLazy.get()
        } else {
            CoreStartable.NOP
        }
    }
}
