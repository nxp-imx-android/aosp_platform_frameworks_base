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

package com.android.systemui.statusbar.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.android.systemui.res.R
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays

/**
 * Calculates the size of a status bar item in Dp.
 *
 * This Composable function determines the appropriate size for status bar icons. It considers a
 * scale factor defined in resources which is used to adjust the size for different screen
 * densities.
 */
@Composable
fun getStatusBarItemSize(): Dp {
    val scaleFactor = LocalResources.current.getFloat(R.dimen.status_bar_icon_scale_factor)
    val iconSize =
        with(LocalDensity.current) {
            if (StatusBarConnectedDisplays.isEnabled) {
                (13 * scaleFactor).sp.toDp()
            } else {
                13.sp.toDp()
            }
        }
    return iconSize
}
