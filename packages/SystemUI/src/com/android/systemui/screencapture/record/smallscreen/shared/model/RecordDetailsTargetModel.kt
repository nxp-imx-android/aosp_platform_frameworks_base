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

package com.android.systemui.screencapture.record.smallscreen.shared.model

import android.view.Display
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget

sealed interface RecordDetailsTargetModel {

    val labelRes: Int
    val isSelectable: Boolean
    val screenCaptureTarget: ScreenCaptureTarget?
    val canShowTouches: Boolean
    val canUseMarkup: Boolean
    val canUseCamera: Boolean
    val shouldShowAppSelector: Boolean

    data class EntireScreen(override val screenCaptureTarget: ScreenCaptureTarget) :
        RecordDetailsTargetModel {

        constructor(display: Display) : this(ScreenCaptureTarget.Fullscreen(display.displayId))

        override val labelRes: Int = R.string.screen_record_entire_screen
        override val isSelectable: Boolean = true
        override val canShowTouches: Boolean = true
        override val canUseMarkup: Boolean = true
        override val canUseCamera: Boolean = true
        override val shouldShowAppSelector: Boolean = false
    }

    data class SingleApp(val task: ScreenCaptureRecentTask, val appLabel: CharSequence?) :
        RecordDetailsTargetModel {

        override val screenCaptureTarget: ScreenCaptureTarget =
            ScreenCaptureTarget.App(displayId = task.displayId, taskId = task.taskId)

        override val labelRes: Int = R.string.screen_record_single_app
        override val isSelectable: Boolean = true
        override val canShowTouches: Boolean = false
        override val canUseMarkup: Boolean = false
        override val canUseCamera: Boolean = false
        override val shouldShowAppSelector: Boolean = true
    }

    data object SingleAppNoRecents : RecordDetailsTargetModel {

        override val labelRes: Int = R.string.screen_record_single_app_no_recents
        override val isSelectable: Boolean = false
        override val screenCaptureTarget: ScreenCaptureTarget? = null
        override val canShowTouches: Boolean = false
        override val canUseMarkup: Boolean = false
        override val canUseCamera: Boolean = false
        override val shouldShowAppSelector: Boolean = false
    }
}

val SmallScreenRecordTargetsModel.currentTargetModel: RecordDetailsTargetModel?
    get() = items.getOrNull(selectedIndex)
