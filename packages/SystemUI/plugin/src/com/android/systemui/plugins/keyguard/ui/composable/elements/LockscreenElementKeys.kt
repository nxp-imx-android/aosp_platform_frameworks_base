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

package com.android.systemui.plugins.keyguard.ui.composable.elements

import com.android.compose.animation.scene.ElementKey

/**
 * Defines several compose element keys which are useful for sharing a composable between the host
 * process and the client. These are similar to the view ids used previously.
 */
object LockscreenElementKeys {
    /** Root element of the entire lockcsreen */
    val Root = ElementKey("LockscreenRoot")

    object Region {
        /** The upper region includes everything above the lock icon */
        val Upper = ElementKey("LockscreenUpperRegion")

        /** The lower region includes everything below the lock icon */
        val Lower = ElementKey("LockscreenLowerRegion")

        /** The clock regions include the clock, smartspace, and the date/weather view */
        object Clock {
            val Large = ElementKey("LargeClockRegion")
            val Small = ElementKey("SmallClockRegion")
        }
    }

    /** The UMO's lockscreen element */
    val MediaCarousel = ElementKey("LockscreenMediaCarousel")

    object Notifications {
        /** The notification stack display on lockscreen */
        val Stack = ElementKey("LockscreenNotificationStack")

        object AOD {
            /** Icon shelf for AOD display */
            val IconShelf = ElementKey("AODNotificationIconShelf")

            /** Notifications for the AOD Promoted Region */
            val Promoted = ElementKey("AODPromotedNotifications")
        }
    }

    /** Lock Icon / UDFPS */
    val LockIcon = ElementKey("LockIcon")

    val SettingsMenu = ElementKey("SettingsMenu")
    val StatusBar = ElementKey("LockscreenStatusBar")
    val IndicationArea = ElementKey("IndicationArea")
    val AmbientIndicationArea = ElementKey("AmbientIndicationArea")

    object Shortcuts {
        val Start = ElementKey("ShortcutStart")
        val End = ElementKey("ShortcutEnd")
    }

    /** Element Keys for composables which wrap clock views */
    object Clock {
        val Large = ElementKey("LargeClock")
        val Small = ElementKey("SmallClock")
    }

    /** Smartspace provided lockscreen elements */
    object Smartspace {
        /** The card view is the large smartspace view which shows contextual information. */
        val Cards = ElementKey("SmartspaceCards")

        /**
         * Elements for the current date view. The large and small clock version use distinct keys
         * so that we can differentiate between them for animations when they are both displayed.
         */
        object Date {
            val LargeClock = ElementKey("SmartspaceDate-LargeClock")
            val SmallClock = ElementKey("SmartspaceDate-SmallClock")
        }

        /**
         * Elements for the standalone current weather view. The large and small clock version use
         * distinct keys so that we can differentiate between them for animations when they are both
         * displayed.
         */
        object Weather {
            val LargeClock = ElementKey("SmartspaceWeather-LargeClock")
            val SmallClock = ElementKey("SmartspaceWeather-SmallClock")
        }
    }
}
