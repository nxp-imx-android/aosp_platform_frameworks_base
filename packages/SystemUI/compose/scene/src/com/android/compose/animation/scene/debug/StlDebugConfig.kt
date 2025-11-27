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

package com.android.compose.animation.scene.debug

import android.os.SystemProperties

internal object StlDebugConfig {
    const val DEBUG_STL = false

    // ====================== ELEMENT ======================
    /**
     * Shows borders around elements. The borders are inset on 3 lanes and dashed to reduce
     * overlapping and better visual. Filtered by [elementKeyFilter].
     */
    val showElementBorders = DEBUG_STL

    /** Show names of the elements within the element. Filtered by [elementKeyFilter]. */
    val showElementLabels = DEBUG_STL

    /**
     * Position of the element label within the element. This can be changed to reduce overlapping
     * of labels.
     */
    val elementLabelPosition = DebugLabelPosition.CenterTop

    /**
     * Log the element states of all elements on each transition changes. Filtered by
     * [elementKeyFilter] but can also used without filter if you want to understand the hierarchy.
     *
     * This is useful if you want to understand which contents currently hold which state and which
     * content is currently responsible for placing the element during a transition.
     */
    val logElements = DEBUG_STL

    /**
     * Log the verbose path that is taken during measure, layout and drawing of a specific element.
     * This is filtered by [elementKeyFilter] and will only log if a filter is set.
     *
     * This is useful if you want to debug the inner workings of STL.
     */
    val logElementsVerbose = DEBUG_STL

    /**
     * Limit visual debugging and logging to elements containing this String in the [key.debugName].
     *
     * SET: `adb shell setprop persist.debug.stl_element_key_filter YOUR_FILTER && adb reboot`
     * RESET: `adb shell setprop persist.debug.stl_element_key_filter \"\" && adb reboot`
     */
    val elementKeyFilter: String =
        SystemProperties.get("persist.debug.stl_element_key_filter") ?: ""

    // ====================== SCENE ======================

    /** Shows borders around scenes */
    val showSceneBorders = DEBUG_STL

    /** Show the scene name on a label */
    val showSceneLabels = DEBUG_STL

    /** Position of the scene label within the scene */
    val sceneLabelPosition = DebugLabelPosition.BottomRight

    // ====================== STL ======================

    /** Show a border around an STL */
    val showStlBorders = DEBUG_STL

    /** Show a label containing the STL name and the currently active transition */
    val showStlLabels = DEBUG_STL

    /** Position of the STL label */
    val stlLabelPosition = DebugLabelPosition.CenterLow

    enum class DebugLabelPosition {
        TopLeft,
        TopRight,
        BottomLeft,
        BottomRight,
        Center,
        CenterTop,
        CenterBottom,
        CenterHigh, // 1/3rd down
        CenterLow, // 2/3rds down
    }
}
