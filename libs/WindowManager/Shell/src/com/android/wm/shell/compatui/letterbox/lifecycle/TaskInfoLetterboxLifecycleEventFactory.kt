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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.graphics.Rect
import android.window.TransitionInfo.Change
import com.android.wm.shell.compatui.letterbox.config.LetterboxDependenciesHelper

/**
 * [LetterboxLifecycleEventFactory] implementation which creates a [LetterboxLifecycleEvent] from a
 * [TransitionInfo.Change] using a [TaskInfo] when present.
 */
class TaskInfoLetterboxLifecycleEventFactory(
    private val letterboxDependenciesHelper: LetterboxDependenciesHelper
) : LetterboxLifecycleEventFactory {
    override fun canHandle(change: Change): Boolean = change.taskInfo != null

    override fun createLifecycleEvent(change: Change): LetterboxLifecycleEvent? {
        change.taskInfo?.let { ti ->
            val isLetterboxed = ti.appCompatTaskInfo?.isTopActivityLetterboxed ?: false
            val taskBoundsAbs = change.endAbsBounds
            // The bounds are absolute to the screen but we need them relative to the Task.
            val taskBounds =
                Rect(taskBoundsAbs).apply { offset(-taskBoundsAbs.left, -taskBoundsAbs.top) }
            // Letterbox bounds are null when the activity is not letterboxed.
            val letterboxBoundsAbs =
                if (isLetterboxed) ti.appCompatTaskInfo?.topActivityLetterboxBounds else null
            val letterboxBounds =
                letterboxBoundsAbs?.let { absBounds ->
                    Rect(absBounds).apply { offset(-taskBoundsAbs.left, -taskBoundsAbs.top) }
                }
            return LetterboxLifecycleEvent(
                type = change.asLetterboxLifecycleEventType(),
                displayId = ti.displayId,
                taskId = ti.taskId,
                taskBounds = taskBounds,
                letterboxBounds = letterboxBounds,
                containerToken = ti.token,
                taskLeash = change.leash,
                isBubble = ti.isAppBubble,
                isTranslucent = change.isTranslucent(),
                supportsInput = letterboxDependenciesHelper.shouldSupportInputSurface(change),
            )
        }
        return null
    }
}
