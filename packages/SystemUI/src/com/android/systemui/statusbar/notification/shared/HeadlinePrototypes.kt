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

package com.android.systemui.statusbar.notification.shared

import android.app.Flags
import android.os.SystemProperties
import com.android.systemui.flags.FlagToken
import com.android.systemui.flags.RefactorFlagUtils

/** Helper for reading or using the headline prototypes flag state. */
@Suppress("NOTHING_TO_INLINE")
object HeadlinePrototypes {
    /** The aconfig flag name */
    const val FLAG_NAME = Flags.FLAG_HEADLINE_PROTOTYPES

    /** A token used for dependency declaration */
    val token: FlagToken
        get() = FlagToken(FLAG_NAME, isEnabled)

    /** Is the refactor enabled */
    @JvmStatic
    inline val isEnabled
        get() = Flags.headlinePrototypes()

    /**
     * Prototype to always show Minimal HUN, even if the device is not in full screen.
     * Default is true.
     *
     * $ adb shell setprop persist.headline.always_show_minimal_hun [true|false]
     */
    @JvmStatic
    inline fun alwaysShowMinimalHun() =
        // NOTE: if we productionize this, we should use a secure setting.
        isEnabled && SystemProperties.getBoolean("persist.headline.always_show_minimal_hun", true)
}