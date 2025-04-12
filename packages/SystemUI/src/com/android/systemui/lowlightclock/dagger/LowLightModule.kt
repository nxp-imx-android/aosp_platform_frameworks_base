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
package com.android.systemui.lowlightclock.dagger

import android.content.res.Resources
import android.hardware.Sensor
import com.android.dream.lowlight.dagger.LowLightDreamModule
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.lowlightclock.AmbientLightModeMonitor
import com.android.systemui.lowlightclock.AmbientLightModeMonitor.DebounceAlgorithm
import com.android.systemui.lowlightclock.AmbientLightModeMonitorImpl
import com.android.systemui.lowlightclock.LowLightDisplayController
import com.android.systemui.lowlightclock.LowLightMonitor
import com.android.systemui.res.R
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Named

@Module(includes = [LowLightDreamModule::class])
abstract class LowLightModule {
    @BindsOptionalOf abstract fun bindsLowLightDisplayController(): LowLightDisplayController

    @BindsOptionalOf @Named(LIGHT_SENSOR) abstract fun bindsLightSensor(): Sensor

    @BindsOptionalOf abstract fun bindsDebounceAlgorithm(): DebounceAlgorithm

    @Binds
    abstract fun bindAmbientLightModeMonitor(
        impl: AmbientLightModeMonitorImpl
    ): AmbientLightModeMonitor

    /** Inject into LowLightMonitor. */
    @Binds
    @IntoMap
    @ClassKey(LowLightMonitor::class)
    abstract fun bindLowLightMonitor(lowLightMonitor: LowLightMonitor): CoreStartable

    companion object {
        const val Y_TRANSLATION_ANIMATION_OFFSET: String = "y_translation_animation_offset"
        const val Y_TRANSLATION_ANIMATION_DURATION_MILLIS: String =
            "y_translation_animation_duration_millis"
        const val ALPHA_ANIMATION_IN_START_DELAY_MILLIS: String =
            "alpha_animation_in_start_delay_millis"
        const val ALPHA_ANIMATION_DURATION_MILLIS: String = "alpha_animation_duration_millis"
        const val LIGHT_SENSOR: String = "low_light_monitor_light_sensor"

        /** Provides a [LogBuffer] for logs related to low-light features. */
        @JvmStatic
        @Provides
        @SysUISingleton
        @LowLightLog
        fun provideLowLightLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("LowLightLog", 250)
        }

        /**  */
        @JvmStatic
        @Provides
        @Named(Y_TRANSLATION_ANIMATION_OFFSET)
        fun providesAnimationInOffset(@Main resources: Resources): Int {
            return resources.getDimensionPixelOffset(
                R.dimen.low_light_clock_translate_animation_offset
            )
        }

        /**  */
        @JvmStatic
        @Provides
        @Named(Y_TRANSLATION_ANIMATION_DURATION_MILLIS)
        fun providesAnimationDurationMillis(@Main resources: Resources): Long {
            return resources
                .getInteger(R.integer.low_light_clock_translate_animation_duration_ms)
                .toLong()
        }

        /**  */
        @JvmStatic
        @Provides
        @Named(ALPHA_ANIMATION_IN_START_DELAY_MILLIS)
        fun providesAlphaAnimationInStartDelayMillis(@Main resources: Resources): Long {
            return resources
                .getInteger(R.integer.low_light_clock_alpha_animation_in_start_delay_ms)
                .toLong()
        }

        /**  */
        @JvmStatic
        @Provides
        @Named(ALPHA_ANIMATION_DURATION_MILLIS)
        fun providesAlphaAnimationDurationMillis(@Main resources: Resources): Long {
            return resources
                .getInteger(R.integer.low_light_clock_alpha_animation_duration_ms)
                .toLong()
        }
    }
}
