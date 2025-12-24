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

import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellController

/** Encapsulate all the logic related to Desks. */
class DesksController(
    private val shellController: ShellController,
    private val userRepositories: DesktopUserRepositories,
    private val desktopConfig: DesktopConfig,
    private val desktopState: DesktopState,
) {

    /** Returns whether the given display has an active desk. */
    @JvmOverloads
    fun isAnyDeskActive(displayId: Int, userId: Int = shellController.currentUserId): Boolean =
        userRepositories.getProfile(userId).isAnyDeskActive(displayId)

    /** Returns the id of the active desk in [displayId]. */
    @JvmOverloads
    fun getActiveDeskId(displayId: Int, userId: Int = shellController.currentUserId): Int? =
        userRepositories.getProfile(userId).getActiveDeskId(displayId)

    /** Returns whether the user can create new desks. */
    fun canCreateDesks(userId: Int = shellController.currentUserId): Boolean {
        val deskLimit = desktopConfig.maxDeskLimit
        val repository = userRepositories.getProfile(userId)
        return deskLimit == 0 || repository.getNumberOfDesks() < deskLimit
    }

    /**
     * Returns whether the user can create a new desk in the given display.
     *
     * @param enforceDeskLimit set to false to bypass the desk-limit verification.
     */
    fun canCreateDeskInDisplay(
        displayId: Int,
        userId: Int = shellController.currentUserId,
        enforceDeskLimit: Boolean = true,
    ): Boolean {
        if (!desktopState.isDesktopModeSupportedOnDisplay(displayId)) {
            return false
        }
        if (enforceDeskLimit && !canCreateDesks(userId)) {
            // At the limit, no-op.
            return false
        }
        return true
    }
}
