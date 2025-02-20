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

package com.android.wm.shell.desktopmode.multidesks

/** Listener for desk display change transitions. */
interface OnDeskDisplayChangeListener {
    /**
     * Handle a display change transition.
     *
     * @param deskDisplayChanges a collection of individual desk display changes
     */
    fun onDeskDisplayChange(deskDisplayChanges: Set<DeskDisplayChange>)

    /**
     * A representation of a single desk display change.
     *
     * @param deskId the desk that was reparented due to the display change
     * @param destinationDisplayId the display that the desk was reparented to
     * @param toTop whether the desk was reparented to the top of the destination display, if false,
     *   it was reparented to the bottom
     */
    data class DeskDisplayChange(
        val deskId: Int,
        val destinationDisplayId: Int,
        val toTop: Boolean,
    )
}
