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
import com.android.systemui.lowlight.LowLightBehaviorCoreStartable
import com.android.systemui.lowlight.data.repository.dagger.LowLightRepositoryModule
import android.content.res.Resources
import android.util.TypedValue
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.lowlight.data.repository.dagger.LowLightSettingsRepositoryModule
import com.android.systemui.lowlightclock.LowLightDisplayController
import com.android.systemui.res.R
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Named

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

    companion object {
        const val LIGHT_MODE_THRESHOLD = "light_mode_threshold"
        const val DARK_MODE_THRESHOLD = "dark_mode_threshold"
        const val LIGHT_MODE_SAMPLING_SPAN = "light_mode_sampling_span"
        const val DARK_MODE_SAMPLING_SPAN = "dark_mode_sampling_span"
        const val LIGHT_MODE_SAMPLING_FREQUENCY = "light_mode_sampling_frequency"
        const val DARK_MODE_SAMPLING_FREQUENCY = "dark_mode_sampling_frequency"

        @Provides
        @Named(LIGHT_MODE_THRESHOLD)
        fun providesLightModeThreshold(@Main res: Resources): Float {
            val outValue = TypedValue()
            res.getValue(R.dimen.config_ambientLightModeThreshold, outValue, true)
            return outValue.float
        }

        @Provides
        @Named(DARK_MODE_THRESHOLD)
        fun providesDarkModeThreshold(@Main res: Resources): Float {
            val outValue = TypedValue()
            res.getValue(R.dimen.config_ambientDarkModeThreshold, outValue, true)
            return outValue.float
        }

        @Provides
        @Named(LIGHT_MODE_SAMPLING_SPAN)
        fun providesLightModeSamplingSpan(@Main res: Resources): Int {
            return res.getInteger(R.integer.config_ambientLightModeSamplingSpanMillis)
        }

        @Provides
        @Named(DARK_MODE_SAMPLING_SPAN)
        fun providesDarkModeSamplingSpan(@Main res: Resources): Int {
            return res.getInteger(R.integer.config_ambientDarkModeSamplingSpanMillis)
        }

        @Provides
        @Named(LIGHT_MODE_SAMPLING_FREQUENCY)
        fun providesLightModeSamplingFrequency(@Main res: Resources): Int {
            return res.getInteger(R.integer.config_ambientLightModeSamplingFrequencyMillis)
        }

        @Provides
        @Named(DARK_MODE_SAMPLING_FREQUENCY)
        fun providesDarkModeSamplingFrequency(@Main res: Resources): Int {
            return res.getInteger(R.integer.config_ambientDarkModeSamplingFrequencyMillis)
        }
    }
}
