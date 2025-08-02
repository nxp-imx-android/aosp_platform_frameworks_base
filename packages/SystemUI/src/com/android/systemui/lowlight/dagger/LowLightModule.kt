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

package com.android.systemui.lowlight.dagger

import com.android.systemui.CoreStartable
import com.android.systemui.lowlight.AmbientLightModeMonitor
import com.android.systemui.lowlight.LowLightBehaviorCoreStartable
import com.android.systemui.lowlight.data.repository.dagger.LowLightRepositoryModule
import com.android.systemui.lowlight.data.repository.dagger.LowLightSettingsRepositoryModule
import com.android.systemui.lowlight.shared.model.LightSensor
import com.android.systemui.lowlightclock.LowLightDisplayController
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.util.Optional
import javax.inject.Named
import javax.inject.Provider

@Module(
    includes = [LowLightSettingsRepositoryModule::class, LowLightRepositoryModule::class],
    subcomponents = [AmbientLightModeComponent::class],
)
abstract class LowLightModule {
    @Binds
    @IntoMap
    @ClassKey(LowLightBehaviorCoreStartable::class)
    abstract fun bindLowLightBehaviorCoreStartable(
        lowLightBehaviorCoreStartable: LowLightBehaviorCoreStartable
    ): CoreStartable

    @BindsOptionalOf abstract fun bindsLowLightDisplayController(): LowLightDisplayController

    @BindsOptionalOf @Named(LIGHT_SENSOR) abstract fun bindsLightSensor(): LightSensor

    companion object {
        const val LIGHT_SENSOR: String = "low_light_monitor_light_sensor"
        const val LOW_LIGHT_MONITOR: String = "low_light_monitor"

        @Provides
        @Named(LOW_LIGHT_MONITOR)
        fun providesLowLightMonitor(
            factory: AmbientLightModeComponent.Factory,
            @Named(LIGHT_SENSOR) sensor: Optional<Provider<LightSensor>>,
        ): AmbientLightModeMonitor? {
            return if (sensor.isEmpty) null
            else factory.create(sensor.get().get()).getAmbientLightModeMonitor()
        }
    }
}
