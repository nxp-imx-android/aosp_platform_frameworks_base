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

package com.android.systemui.screencapture.record.shared.model

import android.graphics.Rect
import com.android.systemui.mediaprojection.MediaProjectionCaptureTarget

/** Models the target to be recorded. */
sealed interface RecordTargetModel {

    /** A single app should be recorded. */
    data class App(val target: MediaProjectionCaptureTarget) : RecordTargetModel

    /** The whole screen should be recorded. */
    data object WholeScreen : RecordTargetModel

    /** The region of the screen should be recorded. */
    data class Region(val rect: Rect) : RecordTargetModel
}
