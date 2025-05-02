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
package com.android.systemui.ambientcue.ui.view

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager.LayoutParams

object AmbientCueUtils {
    fun getAmbientCueLayoutParams(width: Int, height: Int, readyToShow: Boolean): LayoutParams {
        val touchFlag =
            if (readyToShow) {
                LayoutParams.FLAG_NOT_TOUCH_MODAL
            } else {
                LayoutParams.FLAG_NOT_TOUCHABLE
            }
        return LayoutParams(
                width,
                height,
                LayoutParams.TYPE_DRAG,
                LayoutParams.FLAG_NOT_FOCUSABLE or touchFlag,
                PixelFormat.TRANSLUCENT,
            )
            .apply {
                alpha = if (readyToShow) 1f else 0f
                gravity = Gravity.BOTTOM or Gravity.START
                fitInsetsTypes = 0
                isFitInsetsIgnoringVisibility = false
            }
    }
}
