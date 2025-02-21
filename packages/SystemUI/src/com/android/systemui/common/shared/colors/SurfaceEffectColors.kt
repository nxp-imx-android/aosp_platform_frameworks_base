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

package com.android.systemui.common.shared.colors

import android.content.res.Resources

object SurfaceEffectColors {
    @JvmStatic
    fun surfaceEffect0(r: Resources): Int {
        return r.getColor(com.android.internal.R.color.surface_effect_0)
    }
    @JvmStatic
    fun surfaceEffect1(r: Resources): Int {
        return r.getColor(com.android.internal.R.color.surface_effect_1)
    }
    @JvmStatic
    fun surfaceEffect2(r: Resources): Int {
        return r.getColor(com.android.internal.R.color.surface_effect_2)
    }
    @JvmStatic
    fun surfaceEffect3(r: Resources): Int {
        return r.getColor(com.android.internal.R.color.surface_effect_3)
    }
}
