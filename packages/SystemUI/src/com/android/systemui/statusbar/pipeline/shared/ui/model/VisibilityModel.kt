/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pipeline.shared.ui.model

import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableRowLogger

/** The three visibility states required by Views. */
enum class VisibilityState {
    /** The view is visible. */
    VISIBLE,
    /** The view is invisible, but still takes up space. */
    INVISIBLE,
    /** The view is gone, and takes up no space. */
    GONE,
}

/** Models the current visibility for a specific child of the status bar. */
data class VisibilityModel(
    val visibility: VisibilityState,
    /** True if a visibility change should be animated. */
    val shouldAnimateChange: Boolean,
) : Diffable<VisibilityModel> {
    override fun logDiffs(prevVal: VisibilityModel, row: TableRowLogger) {
        if (visibility != prevVal.visibility) {
            row.logChange(COL_VIS, visibility.name)
        }
        // Purposefully don't log `shouldAnimate`, since it adds noise to logs without being useful
    }

    override fun logFull(row: TableRowLogger) {
        row.logChange(COL_VIS, visibility.name)
    }

    companion object {
        const val COL_VIS = "vis"
    }
}
