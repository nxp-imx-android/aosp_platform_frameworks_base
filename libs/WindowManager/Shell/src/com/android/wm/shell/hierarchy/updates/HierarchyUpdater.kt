/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.wm.shell.hierarchy.updates

import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.DisplayAreaOrganizer
import android.window.TaskAppearedInfo
import android.window.TransitionInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.modes.FormFactorModes
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.properties.ActivityContainerProperties
import com.android.wm.shell.hierarchy.properties.ContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.RootContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.properties.WallpaperContainerProperties
import com.android.wm.shell.hierarchy.utils.HierarchyDebugUtils
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MODES
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * Helper class to update the container hierarchy from transitions or task organizer (for root tasks
 * only).
 */
class HierarchyUpdater(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val transitions: Transitions,
    private val hierarchy: ContainerHierarchy,
    private val formFactorModes: FormFactorModes,
    shellInit: ShellInit,
) {
    // For testing use only
    var updaterTestHook: UpdaterTestHook? = null

    init {
        if (Flags.enableShellModes()) {
            shellInit.addInitCallback(this::onInit, this)
        }
    }

    private fun onInit() {
        shellTaskOrganizer.setContainerHierarchyCreateRootTaskListener(
            ContainerHierarchyRootTaskHook()
        )
        if (!com.android.window.flags.Flags.transitMixpatcherBase()) {
            // The planner will update the hierarchy in lieu of the observer with mixpatcher
            transitions.registerObserver(ContainerHierarchyTransitionObserver())
        }
    }

    /**
     * Common logic for notifying modes, and MUST be called after the hierarchy is updated.
     */
    fun notifyModes(
        updateContext: Mode.UpdateContext,
        snapshot: HierarchySnapshot,
    ) {
        updaterTestHook?.onHierarchyUpdated()

        val postUpdateContainers = hierarchy.toContainerList()
        val preUpdateDisplays =
            snapshot.preUpdateContainers.filter { it.props is DisplayContainerProperties }
        val postUpdateDisplays =
            postUpdateContainers.filter { it.props is DisplayContainerProperties }

        // Iterate the pre-existing containers from bottom-up to notify old modes that their
        // descendants have been removed
        for (container in snapshot.preUpdateContainers.asReversed()) {
            val oldMode = snapshot.snapshots[container]?.mode
            val mode = HierarchyUtils.getMode(container)
            if (oldMode != null && oldMode != mode) {
                ProtoLog.v(
                    WM_SHELL_MODES,
                    "\tDetaching container: %s to %s",
                    container.name,
                    oldMode.getId()
                )
                oldMode.detachFromContainer(updateContext, container)
            }
        }

        // After pre-existing containers' modes have been notified, notify the modes globally for
        // displays have been removed
        val removedDisplays = preUpdateDisplays - postUpdateDisplays
        for (display in removedDisplays) {
            val modes = formFactorModes.getAvailableModesForDisplay(
                display.props<DisplayContainerProperties>().displayId
            )
            for (mode in modes) {
                mode.cleanupForDisplay(updateContext, display)
            }
        }

        // Before new containers' modes  have been notified, notify the modes globally for displays
        // that have been added
        val addedDisplays = postUpdateDisplays - preUpdateDisplays
        for (display in addedDisplays) {
            val modes = formFactorModes.getAvailableModesForDisplay(
                display.props<DisplayContainerProperties>().displayId
            )
            for (mode in modes) {
                mode.prepareForDisplay(updateContext, display)
            }
        }

        // Iterate the current containers from top-down to notify new modes that they have new
        // descendants. We have to do this iteratively as modes can manipulate the hierarchy (only
        // descendants of notified containers) as they are notified.
        val postUpdateContainersList = mutableListOf(hierarchy.root)
        while (postUpdateContainersList.isNotEmpty()) {
            val container = postUpdateContainersList.removeFirst()
            val oldMode = snapshot.snapshots[container]?.mode
            val mode = HierarchyUtils.getMode(container)
            if (mode != null) {
                if (mode != oldMode) {
                    // This container has not been associated with the mode yet (either directly
                    // or indirectly)
                    ProtoLog.v(
                        WM_SHELL_MODES,
                        "\tAttaching container: %s to %s",
                        container.name, mode.getId()
                    )
                    mode.attachToContainer(updateContext, container, container.mode == mode)
                } else if (container.mode == mode) {
                    // This container is directly associated with the mode
                    if (snapshot.hasChangesIncludingAncestors(container)) {
                        ProtoLog.v(
                            WM_SHELL_MODES,
                            "\tContainer or ancestor has changed: %s in %s",
                            container.name, mode.getId()
                        )
                        mode.containerChanged(updateContext, container, snapshot)
                    }
                } else {
                    // This container is indirectly associated wit the mode
                    if (!snapshot.getChanges(container).isEmpty) {
                        // The container has still changed, so notify that mode
                        ProtoLog.v(
                            WM_SHELL_MODES,
                            "\tDescendant container has changed: %s in %s",
                            container.name, mode.getId()
                        )
                        mode.containerChanged(updateContext, container, snapshot)
                    }
                }
            }
            postUpdateContainersList.addAll(container.children)
        }
        updaterTestHook?.onModesNotified()

        // Dump the hierarchy
        HierarchyDebugUtils.dumpHierarchy(hierarchy, snapshot)
    }

    fun handleCreateRootTask(
        appearedInfo: TaskAppearedInfo,
        name: String
    ): Container {
        val token = appearedInfo.taskInfo.token
        val existingContainer = hierarchy.getContainer(token)
        if (existingContainer != null) {
            // Skip if we've already updated the hierarchy for this window container. This happens
            // if we've already updated the hierarchy when creating the root task.
            return existingContainer
        }
        ProtoLog.v(
            WM_SHELL_MODES,
            "Adding root task into hierarchy: %d",
            appearedInfo.taskInfo.taskId
        )

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // Create the task in the hierarchy
        val displayArea = hierarchy.getTaskDisplayArea(appearedInfo.taskInfo.displayId)
        val createdTask =
            Container(token, TaskContainerProperties(appearedInfo.taskInfo)).apply {
                this.name += " ($name)"
                parent = displayArea
                leash = appearedInfo.leash
                if (mode != null) {
                    this.mode = mode
                }
            }

        notifyModes(Mode.UpdateContext(), snapshot)
        return createdTask
    }

    fun handleRemoveRootTask(token: WindowContainerToken) {
        ProtoLog.v(
            WM_SHELL_MODES,
            "Removing root task from hierarchy: %s",
            token
        )

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // Remove the task from the hierarchy
        val container = hierarchy.getContainer(token)
        container?.parent = null

        notifyModes(Mode.UpdateContext(), snapshot)
    }

    fun handleTransition(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction
    ): HierarchySnapshot {
        ProtoLog.v(
            WM_SHELL_MODES,
            "Updating hierarchy from transition: %d",
            info.debugId
        )

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // Update the root first
        hierarchy.root.props<RootContainerProperties>().updateFromTransition(info)

        // Update the rest of the hierarchy
        // We iterate the changes in reverse order to handle parent containers first (especially
        // when new containers are being added)
        val reversedChanges = info.changes.reversed()
        for (change in reversedChanges) {
            updateHierarchyFromChange(change)
        }

        // Notify all the modes
        notifyModes(Mode.UpdateContext(startTransaction), snapshot)

        if (HierarchyUtils.hasTemporaryAnimatingContainers(hierarchy.root)) {
            // If there are temporary containers, then schedule them to be cleaned up after all
            // transitions finish playing
            transitions.runOnIdle {
                ProtoLog.v(
                    WM_SHELL_MODES,
                    "Cleaning up temporary animating conatiners from transition: %d",
                    info.debugId
                )
                val snapshot = HierarchySnapshot(hierarchy.toContainerList())
                HierarchyUtils.removeAllTemporaryAnimatingContainers(hierarchy.root)
                notifyModes(Mode.UpdateContext(), snapshot)
            }
        }
        return snapshot
    }

    /**
     * Updates the containers in the hierarchy based on changes in the given transition.
     */
    private fun updateHierarchyFromChange(
        change: TransitionInfo.Change,
    ) {
        // Validate the change
        val token = change.container
        if (token == null) {
            ProtoLog.v(WM_SHELL_MODES, "\tChange skipped (no window token): %s", change)
            return
        }

        var container = hierarchy.getContainer(token)

        // If the container is being removed, just remove it from the hierarchy without updating
        if (TransitionUtil.isClosingMode(change.mode)) {
            if (container != null && change.mode == WindowManager.TRANSIT_CLOSE) {
                container.parent = null
                ProtoLog.v(WM_SHELL_MODES, "\tRemoving: %s", container.name)
                return
            } else if (container == null) {
                // Already removed
                return
            }
        }

        // Create the container if necessary
        if (container == null) {
            container = createContainerFromChange(change)
            ProtoLog.v(WM_SHELL_MODES, "\tAdding: %s", container.name)
        }

        // Apply the change to the container
        container.updateFromWindowChange(hierarchy.root, change)

        when (change.mode) {
            WindowManager.TRANSIT_TO_FRONT -> {
                container.parent!!.reorderChild(container, /* toTop= */ true)
                ProtoLog.v(WM_SHELL_MODES, "\tMoving to front: %s", container.name)
            }

            WindowManager.TRANSIT_TO_BACK -> {
                container.parent!!.reorderChild(container, /* toTop= */ false)
                ProtoLog.v(WM_SHELL_MODES, "\tMoving to back: %s", container.name)
            }

            WindowManager.TRANSIT_CHANGE -> {
                ProtoLog.v(WM_SHELL_MODES, "\tChanging: %s", container.name)
            }
        }
    }

    /**
     * Creates a container for the given change.
     * Does NOT add the container to the hierarchy.
     * TODO: Directly map this to the container type once we have explicit types in the info
     */
    private fun createContainerFromChange(
        change: TransitionInfo.Change,
    ): Container {
        val displayId = change.endDisplayId
        if (change.hasFlags(TransitionInfo.FLAG_IS_DISPLAY)) {
            return Container(
                change.container!!,
                DisplayContainerProperties(displayId)
            )
        } else if (change.hasFlags(TransitionInfo.FLAG_IS_TASK_DISPLAY_AREA)) {
            return Container(
                change.container!!,
                DisplayAreaContainerProperties(
                    DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
                )
            )
        } else if (change.taskInfo != null) {
            return Container(
                change.container!!,
                TaskContainerProperties(change.taskInfo!!)
            )
        }

        // Other container types are temporary in the hierarchy
        // TODO: Certain containers (like wallpaper) don't have valid tokens yet, we should either
        //       fix that or map those specific containers by flag
        if ((change.flags and TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
            return Container(
                change.container!!,
                WallpaperContainerProperties()
            ).apply {
                isTemporaryAnimatingContainer = true
            }
        } else if (change.activityTransitionInfo != null) {
            return Container(
                change.container!!,
                ActivityContainerProperties(change.activityTransitionInfo!!.taskId)
            ).apply {
                isTemporaryAnimatingContainer = true
            }
        } else {
            return Container(
                change.container!!,
                ContainerProperties()
            ).apply {
                isTemporaryAnimatingContainer = true
            }
        }
    }

    /**
     * Updates the hierarchy from a display change signal to give modes an opportunity to update
     * their tasks in final state before the display change completes.
     *
     * The caller is responsible for verifying that there is a given display container for the
     * provided displayId.
     */
    fun updateDisplay(
        displayId: Int,
        displayLayout: DisplayLayout,
        outWct: WindowContainerTransaction
    ) {
        val display = hierarchy.getDisplay(displayId)!!
        ProtoLog.v(WM_SHELL_MODES, "Updating display: %s", display.name)

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // Apply the display changes to the hierarchy first
        val newDisplayProps = display.props<DisplayContainerProperties>().copy()
        newDisplayProps.updateFromDisplayLayout(displayLayout)

        // Notify the modes associated with this display (ancestors & descendants) of the change
        // FUTURE: This currently assumes the displays exist and are rooted to the hierarchy
        val containers = listOf(hierarchy.root) + HierarchyUtils.toContainersList(display)
        for (c in containers) {
            if (c.mode != null) {
                c.mode!!.displayChanging(
                    c,
                    display.props<DisplayContainerProperties>(),
                    newDisplayProps,
                    outWct
                )
            }
        }

        notifyModes(Mode.UpdateContext(), snapshot)
    }

    /**
     * Hooks into ShellTaskOrganizer to listen for changes to root tasks.
     */
    inner class ContainerHierarchyRootTaskHook
        : ShellTaskOrganizer.ContainerHierarchyRootTaskListener {
        override fun onRootTaskCreated(appearedInfo: TaskAppearedInfo, name: String) {
            handleCreateRootTask(appearedInfo, name)
        }

        override fun onRootTaskRemoved(token: WindowContainerToken) {
            handleRemoveRootTask(token)
        }
    }

    /**
     * Hooks into transitions to listen for, and update from, incoming transitions.
     */
    inner class ContainerHierarchyTransitionObserver : Transitions.TransitionObserver {
        override fun onTransitionReady(
            transition: IBinder,
            info: TransitionInfo,
            startTransaction: SurfaceControl.Transaction,
            finishTransaction: SurfaceControl.Transaction
        ) {
            handleTransition(transition, info, startTransaction)
        }
    }

    /**
     * Only for testing. Hooks into the update path to allow tests to validate the update after
     * specific stages.
     */
    interface UpdaterTestHook {
        fun onHierarchyUpdated() {}
        fun onModesNotified() {}
    }
}