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

package com.android.systemui.effects;

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject

@SysUISingleton
class TopLevelWindowEffects @Inject constructor(
    @Application private val context: Context
) : CoreStartable {
    override fun start() {

    }

    private fun getWindowManagerLayoutParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT
        )

        lp.privateFlags = lp.privateFlags or
                (WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
                        or WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION
                        or WindowManager.LayoutParams.PRIVATE_FLAG_EDGE_TO_EDGE_ENFORCED
                        or WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED
                        or WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)

        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

        lp.title = "TopLevelWindowEffects"
        lp.fitInsetsTypes = WindowInsets.Type.systemOverlays()
        lp.gravity = Gravity.TOP

        return lp
    }
}
