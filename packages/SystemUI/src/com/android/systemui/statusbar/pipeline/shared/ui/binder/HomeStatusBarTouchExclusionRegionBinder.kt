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

package com.android.systemui.statusbar.pipeline.shared.ui.binder

import android.graphics.Rect
import android.graphics.Region
import android.window.DesktopExperienceFlags
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.setSnapshotBinding
import com.android.systemui.statusbar.phone.PhoneStatusBarView
import kotlinx.coroutines.awaitCancellation

/** Binds a [PhoneStatusBarView] to [AppHandlesViewModel]'s touch exclusion region. */
object HomeStatusBarTouchExclusionRegionBinder {

    /**
     * Reports the updated touchable region to the [PhoneStatusBarView] calculated from the touch
     * exclusion region provided.
     */
    fun bind(view: PhoneStatusBarView, touchExclusionRegion: Region) {
        if (!DesktopExperienceFlags.ENABLE_REMOVE_STATUS_BAR_INPUT_LAYER.isTrue) {
            return
        }
        view.repeatWhenAttached {
            view.setSnapshotBinding {
                view.updateTouchableRegion(calculateTouchableRegion(view, touchExclusionRegion))
            }
            awaitCancellation()
        }
    }

    private fun calculateTouchableRegion(
        view: PhoneStatusBarView,
        touchExclusionRegion: Region,
    ): Region {
        val outBounds = Rect()
        view.getBoundsOnScreen(outBounds)
        val touchableRegion =
            Region.obtain().apply {
                set(outBounds)
                op(touchExclusionRegion, android.graphics.Region.Op.DIFFERENCE)
            }
        return touchableRegion
    }
}
