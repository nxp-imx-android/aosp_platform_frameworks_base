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
package com.android.wm.shell.hierarchy.transitions

import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import androidx.annotation.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.Flags
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.hierarchy.updates.HierarchyUpdater
import com.android.wm.shell.hierarchy.utils.HierarchyUtils.Companion.getMode
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MODES
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.AnimationPlan
import com.android.wm.shell.transition.ITransitionPlanner
import com.android.wm.shell.transition.Transitions

/**
 * Helper class to resolve the transitions to play for changes in the hierarchy. This class is
 * called only when Mixpatcher is enabled, and is called AFTER the container hierarchy has been
 * updated.
 */
class HierarchyTransitionPlanner(
    private val transitions: Transitions,
    private val hierarchy: ContainerHierarchy,
    private val updater: HierarchyUpdater,
    shellInit: ShellInit,
) {
    init {
        if (Flags.enableShellModes() && com.android.window.flags.Flags.transitMixpatcherBase()) {
            shellInit.addInitCallback(this::onInit, this)
        }
    }

    private fun onInit() {
        transitions.addPlanner(ContainerHierarchyMixpatcherHandler())
    }

    /**
     * Handles an incoming mixpatcher transition by giving affected modes a way to handle the
     * transition.
     *
     * This is handled in multiple phases:
     * 1) Detaching/preparation of containers in their previous modes to allow for animation
     * 2a) Resolving/creating removed container animations by their previous modes
     * 2b) Resolving/creating added/changed container animations by their target modes
     *
     * At each stage, we work from the most specific mode to the least specific mode (upwards) to
     * allow the modes to handle (aka. consume) the detaching or the creation of the animation
     * for containers in its subtree that are a part of the changes, until all changes are handled
     * (or we fall to the next handler).
     */
    @VisibleForTesting
    fun handleMixpatcherTransition(
        snapshot: HierarchySnapshot,
        plan: AnimationPlan,
        fullInfo: TransitionInfo,
        plannableInfo: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
    ) {
        ProtoLog.v(WM_SHELL_MODES, "Planning for transition: #%d", fullInfo.debugId)
        val animationContext = Mode.AnimationContext(startTransaction)

        // Get a set of all containers pre/post update by token
        val preUpdateContainers = snapshot.preUpdateContainers.toList()
        val preUpdateContainersSet = preUpdateContainers.toSet()
        val preUpdateContainersByToken = preUpdateContainers.associateBy { it.token }
        val postUpdateContainers = hierarchy.toContainerList()
        val postUpdateContainersSet = postUpdateContainers.toSet()
        val postUpdateContainersByToken = postUpdateContainers.associateBy { it.token }

        // Allow affected modes to detach pre-update containers that are in the full changes
        // This logic in a distinct map of Mode -> [Changed containers under that mode]
        val detachChanges = fullInfo.changes
            .filter {
                val token = it.container
                token != null && preUpdateContainersByToken.containsKey(token) &&
                        getMode(preUpdateContainersByToken.getValue(token)) != null
            }
            .map { preUpdateContainersByToken.getValue(it.container!!) }
            .groupBy { getMode(it)!! }
        for ((mode, containersWithChanges) in detachChanges) {
            val detachResults = mode.prepareForAnimation(containersWithChanges)
            ProtoLog.v(
                WM_SHELL_MODES, "\t\tNotifying %s to detach containers %s, handled=%b",
                mode.getId(), containersWithChanges.map { it.name }, detachResults != null
            )
            if (detachResults != null) {
                if (detachResults.size != containersWithChanges.size) {
                    throw IllegalStateException("Expected detach results for each container")
                }

                // The mode detached the containers, so update the plan
                for ((i, handledContainer) in containersWithChanges.withIndex()) {
                    val detachResult = detachResults[i]
                    if (detachResult.isDone) {
                        plan.detach(handledContainer.token, detachResult.get().first())
                    } else {
                        plan.detachAsync(handledContainer.token, detachResult)
                    }
                }
            }
        }

        // Allow previous modes to create animations for containers that were removed (have no
        // target mode)
        val removedContainers = (preUpdateContainersSet - postUpdateContainersSet).toList()
        val removedContainersByToken = removedContainers.associateBy { it.token }
        // This logic in a distinct map of Mode -> [Changed containers under that mode]
        val animateRemoved = plannableInfo.changes
            .filter {
                val token = it.container
                token != null && removedContainersByToken.containsKey(token) &&
                        getMode(removedContainersByToken.getValue(token)) != null
            }
            .map { removedContainersByToken.getValue(it.container!!) }
            .groupBy { getMode(it)!! }
        for ((mode, removedContainers) in animateRemoved) {
            val animation = mode.createAnimation(animationContext, removedContainers, snapshot)
            ProtoLog.v(
                WM_SHELL_MODES, "\t\tNotifying %s to animate containers %s, handled=%b",
                mode.getId(), removedContainers.map { it.name }, animation != null
            )
            if (animation != null) {
                // The mode created an animation, so update the plan
                for (handledContainer in removedContainers) {
                    plan.setAnimation(handledContainer.token, animation)
                }
            }
        }

        // Allow target modes to create animations for containers that were otherwise changed
        // This logic in a distinct map of Mode -> [Changed containers under that mode]
        val animateRemaining = plannableInfo.changes
            .filter {
                val token = it.container
                token != null && postUpdateContainersByToken.containsKey(token) &&
                        getMode(postUpdateContainersByToken.getValue(token)) != null
            }
            .map { postUpdateContainersByToken.getValue(it.container!!) }
            .groupBy { getMode(it)!! }
        for ((mode, containers) in animateRemaining) {
            val animation = mode.createAnimation(animationContext, containers, snapshot)
            ProtoLog.v(
                WM_SHELL_MODES, "\t\tNotifying %s to animate containers %s, handled=%b",
                mode.getId(), containers.map { it.name }, animation != null
            )
            if (animation != null) {
                // The mode created an animation, so update the plan
                for (handledContainer in containers) {
                    plan.setAnimation(handledContainer.token, animation)
                }
            }
        }
    }

    /**
     * Hooks into Mixpatcher transition planning.
     */
    inner class ContainerHierarchyMixpatcherHandler : ITransitionPlanner {
        override fun plan(
            plan: AnimationPlan,
            fullInfo: TransitionInfo,
            transition: IBinder,
            plannableInfo: TransitionInfo,
            startTransaction: SurfaceControl.Transaction
        ) {
            // Always update the hierarchy first before handling the transition
            val snapshot = updater.handleTransition(transition, fullInfo, startTransaction)
            handleMixpatcherTransition(
                snapshot,
                plan,
                fullInfo,
                plannableInfo,
                startTransaction
            )
        }

        override fun getDebugName(): String {
            return "ContainerHierarchy"
        }
    }
}