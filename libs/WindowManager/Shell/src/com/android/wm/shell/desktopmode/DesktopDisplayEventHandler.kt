/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.content.Context
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.window.DesktopExperienceFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.desktopmode.multidesks.OnDeskDisplayChangeListener
import com.android.wm.shell.desktopmode.multidesks.OnDeskRemovedListener
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Handles display events in desktop mode */
class DesktopDisplayEventHandler(
    shellInit: ShellInit,
    private val mainScope: CoroutineScope,
    private val shellController: ShellController,
    private val displayController: DisplayController,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val desktopRepositoryInitializer: DesktopRepositoryInitializer,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopTasksController: DesktopTasksController,
    private val desktopDisplayModeController: DesktopDisplayModeController,
    private val desksTransitionObserver: DesksTransitionObserver,
    private val desktopState: DesktopState,
) : OnDisplaysChangedListener, OnDeskRemovedListener, OnDeskDisplayChangeListener {

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        displayController.addDisplayWindowListener(this)

        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            desktopTasksController.onDeskRemovedListener = this

            shellController.addUserChangeListener(
                object : UserChangeListener {
                    override fun onUserChanged(newUserId: Int, userContext: Context) {
                        val displayIds = rootTaskDisplayAreaOrganizer.displayIds
                        createDefaultDesksIfNeeded(displayIds.toSet(), newUserId)
                    }
                }
            )

            if (DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue) {
                desksTransitionObserver.deskDisplayChangeListener = this
            }
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        if (displayId != DEFAULT_DISPLAY) {
            desktopDisplayModeController.updateExternalDisplayWindowingMode(displayId)
            // The default display's windowing mode depends on the availability of the external
            // display. So updating the default display's windowing mode here.
            desktopDisplayModeController.updateDefaultDisplayWindowingMode()
        }

        createDefaultDesksIfNeeded(displayIds = setOf(displayId), userId = null)
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (displayId != DEFAULT_DISPLAY) {
            desktopDisplayModeController.updateDefaultDisplayWindowingMode()
        }
        // TODO(b/391652399): store a persisted DesktopDisplay in DesktopRepository
    }

    override fun onDesktopModeEligibleChanged(displayId: Int) {
        if (
            DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue &&
                displayId != DEFAULT_DISPLAY
        ) {
            desktopDisplayModeController.updateExternalDisplayWindowingMode(displayId)
            // The default display's windowing mode depends on the desktop eligibility of the
            // external display. So updating the default display's windowing mode here.
            desktopDisplayModeController.updateDefaultDisplayWindowingMode()
        }
    }

    override fun onDeskRemoved(lastDisplayId: Int, deskId: Int) {
        createDefaultDesksIfNeeded(setOf(lastDisplayId), userId = null)
    }

    private fun createDefaultDesksIfNeeded(displayIds: Set<Int>, userId: Int?) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        logV("createDefaultDesksIfNeeded displays=%s", displayIds)
        mainScope.launch {
            desktopRepositoryInitializer.isInitialized.collect { initialized ->
                if (!initialized) return@collect
                val repository =
                    userId?.let { desktopUserRepositories.getProfile(userId) }
                        ?: desktopUserRepositories.current
                displayIds
                    .filter { displayId -> displayId != Display.INVALID_DISPLAY }
                    .filter { displayId -> supportsDesks(displayId) }
                    .filter { displayId -> repository.getNumberOfDesks(displayId) == 0 }
                    .also { displaysNeedingDesk ->
                        logV(
                            "createDefaultDesksIfNeeded creating default desks in displays=%s",
                            displaysNeedingDesk,
                        )
                    }
                    .forEach { displayId ->
                        desktopTasksController.createDesk(
                            displayId,
                            repository.userId,
                            isDesktopFirstDisplay(displayId),
                        )
                    }
                cancel()
            }
        }
    }

    override fun onDeskDisplayChange(
        deskDisplayChanges: Set<OnDeskDisplayChangeListener.DeskDisplayChange>
    ) {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue()) return
        desktopTasksController.onDeskDisconnectTransition(deskDisplayChanges)
    }

    // TODO: b/393978539 - implement this
    private fun isDesktopFirstDisplay(displayId: Int): Boolean = displayId != DEFAULT_DISPLAY

    // TODO: b/362720497 - connected/projected display considerations.
    private fun supportsDesks(displayId: Int): Boolean =
        desktopState.isDesktopModeSupportedOnDisplay(displayId)

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopDisplayEventHandler"
    }
}
