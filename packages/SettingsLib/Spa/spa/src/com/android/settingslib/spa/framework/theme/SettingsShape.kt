/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.framework.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object SettingsShape {
    val CornerFull = CircleShape
    val CornerExtraSmall2 = RoundedCornerShape(4.dp)
    val CornerMedium = RoundedCornerShape(12.dp)
    val CornerLarge1 = RoundedCornerShape(16.dp)
    val CornerLarge2 = RoundedCornerShape(20.dp)
    val CornerExtraLarge1 = RoundedCornerShape(28.dp)

    // Legacy tokens below

    val TopCornerMedium2 =
        RoundedCornerShape(CornerSize(20.dp), CornerSize(20.dp), CornerSize(0), CornerSize(0))

    val BottomCornerMedium2 =
        RoundedCornerShape(CornerSize(0), CornerSize(0), CornerSize(20.dp), CornerSize(20.dp))
}
