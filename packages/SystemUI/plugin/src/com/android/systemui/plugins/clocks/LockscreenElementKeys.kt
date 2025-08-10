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

// TODO(b/432451019): move to .ui.composable subpackage
package com.android.systemui.plugins.clocks

import com.android.compose.animation.scene.ElementKey

/**
 * Defines several compose element keys which are useful for sharing a composable between the host
 * process and the client. These are similar to the view ids used previously.
 */
object LockscreenElementKeys {
    val Root = ElementKey("LockscreenRoot")

    // Element Keys for top level large/small composables
    val ClockLarge = ElementKey("LargeClock")
    val ClockSmall = ElementKey("SmallClock")

    // Clock Regions include smartspace & the date/weather view
    val ClockRegionLarge = ElementKey("LargeClockRegion")
    val ClockRegionSmall = ElementKey("SmallClockRegion")

    // Lockscreen Defined Elements - Smartspace
    val SmartspaceDate = ElementKey("SmartspaceDate")
    val SmartspaceWeather = ElementKey("SmartspaceWeather")
    val SmartspaceDateWeatherVertical = ElementKey("SmartspaceDateWeatherVertical")
    val SmartspaceDateWeatherHorizontal = ElementKey("SmartspaceDateWeatherHorizontal")
    val SmartspaceCards = ElementKey("SmartspaceCards")
}
