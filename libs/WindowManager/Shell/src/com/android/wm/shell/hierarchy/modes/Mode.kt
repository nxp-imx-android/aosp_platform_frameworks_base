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
package com.android.wm.shell.hierarchy.modes

import android.view.SurfaceControl
import android.window.WindowContainerTransaction
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.transition.DetachResult
import com.android.wm.shell.transition.ITransitionAnimation
import java.io.PrintWriter

/**
 * Encapsulates logic for a particular container in the hierarchy.
 */
interface Mode {

    //
    // Updates (ie. for a mode to respond to changes in the hierarchy)
    //

    /**
     * Hook to create any static containers for the given display (if necessary).
     *
     * This is always called globally for all modes before a mode is associated with any descendant
     * of this new display in the hierarchy.
     *
     * FUTURE: This would be a good place to hook into to save/restore per-display & mode state
     */
    fun prepareForDisplay(updateContext: UpdateContext, display: Container) {}

    /**
     * Cleans up any static containers added in {#prepareForDisplay()}.
     *
     * This is always called after all descendants of this display in the hierarchy have been
     * disassociated from any given mode.
     */
    fun cleanupForDisplay(updateContext: UpdateContext, display: Container) {}

    /**
     * Associates this mode with the given container.
     *
     * If {@param isDirectlyAssigned} is 'true', then all of the container's descendants also be
     * associated with this mode (unless it has its own mode), and will be called with
     * isDirectlyAssigned=false.
     *
     * Modes should ONLY manipulate the descendants of the provided container, not siblings or
     * ancestors, or their lifecycles may not be correctly reported when updating.
     *
     * The container which has a directly assigned mode will receive 'attachToContainer()' before
     * any of its descendants.
     */
    fun attachToContainer(
        updateContext: UpdateContext,
        container: Container,
        isDirectlyAssigned: Boolean
    ) {}

    /**
     * This method is called in the following scenarios:
     *  - if a container that is directly assigned to this mode has changed (container=directly
     *    associated container)
     *  - if an ancestor of a container that is directly assigned to this mode has changed
     *    (container=directly associated container)
     *  - if a descendant of a container that is directly assigned to this mode has changed
     *    (container=changed descendant container)
     *
     * This ensures that the mode is notified of the changes in the ancestor chain, as well as the
     * subtree, so it can coordinate updates for global changes (ie. display) as well as
     * per-attached-container behavior (ie. per-attached-container overlays).
     *
     * The provided container is always the directly associated container or a descendant.
     *
     * This will only be called AFTER attachToContainer() and BEFORE detachFromContainer().
     */
    fun containerChanged(
        updateContext: UpdateContext,
        container: Container,
        snapshot: HierarchySnapshot,
    ) {}

    /**
     * Disassociates this mode from the given container.
     *
     * Modes should ONLY manipulate the descendants of the provided container, not siblings or
     * ancestors, or their lifecycles may not be correctly reported when updating.
     *
     * Descendants of the directly assigned container will receive 'detachFromContainer()' before
     * the directly assigned container.
     */
    fun detachFromContainer(
        updateContext: UpdateContext,
        container: Container
    ) {}

    //
    // Requests (ie. when someone needs to manipulate containers in the hierarchy and needs to know
    // how to move something into/out of a mode.
    //

    /**
     * Requests this mode to update the given {@param wct} to start managing the given
     * {@param task}. For example, this can mean reparenting the task into one of the root
     * containers that is associated with this mode.
     *
     * A mode implementing this method is not expected to request an update to the hierarchy, only
     * to update the given WindowContainerTransaction (ie. calling this method should have NO
     * side-effects, and subsequent handling of the request should be equivalent to a Core-initiated
     * change of the same type).
     *
     * The caller is expected to apply the transaction.
     *
     * If the mode supports a more specific request type, then it must validate the request given.
     */
    fun requestEnterMode(
        task: Container,
        request: EnterRequestContext,
        wct: WindowContainerTransaction
    ): Boolean {
        throw IllegalStateException("Not supported")
    }

    /**
     * Requests this mode to update the given {@param wct} to layout the containers under this mode
     * in the new display configuration. This happens prior to the display change being applied
     * to the hierarchy.
     *
     * The mode is NOT expected to apply the transaction.
     *
     * FUTURE: Revisit to see if we need intermediate containers to be updated as well (ie. if there
     *         are roots who are built within parent containers, updating the display may require
     *         the parents to be updated before the roots themselves can be properly/easily updated.
     */
    fun requestUpdateForDisplayChange(
        directlyAssignedContainer: Container,
        curDisplayProps: DisplayContainerProperties,
        newDisplayProps: DisplayContainerProperties,
        wct: WindowContainerTransaction
    ) {}

    //
    // Transitions (ie. post-update transition resolution for containers associated with this mode)
    //

    /**
     * Called for the set of descendant containers (of the "source" mode) that are participating in
     * the current transition, allowing the mode to "detach" the container and report the current
     * state of the container/surface to whatever animates the container next (ie. the target mode).
     *
     * If a mode is handling the "detaching" of the containers from the mode, then it must return
     * a list of DetachResults with one `WindowAnimationState` for each of the provided containers.
     * Doing so will also prevent these containers from being notified to other modes for detaching.
     *
     * TODO: Clarify about coordinate space for the handoff state
     */
    fun prepareForAnimation(
        containers: List<Container>,
    ): List<DetachResult>? {
        return null
    }

    /**
     * Called for the set of descendant containers (of the "target" mode) that are participating in
     * the current transition, allowing this mode to create an animation to be played for the given
     * containers to their final target state in this mode.
     *
     * If the container was previously in the hierarchy, then `prepareForAnimation` will have been
     * called first prior to this call.
     *
     * Returning a non-null animation indicates that this mode will animate ALL provided descendant
     * containers, and the animation assumes the responsibility for calling the provided finish
     * callback.
     *
     * NOTE: The container provided may not currently exist in the hierarchy (ie. it was removed in
     * this update).
     *
     * FUTURE: Make this work with Shell-only containers as well.
     */
    fun createAnimation(
        animContext: AnimationContext,
        containers: List<Container>,
        snapshot: HierarchySnapshot,
    ): ITransitionAnimation? {
        return null
    }

    //
    // Misc
    //

    /**
     * Returns an identifier that can be used to resolve the mode directly if needed (ie. from
     * shell commands)
     */
    open fun getId(): String {
        return this::class.simpleName!!
    }

    /**
     * Handles a shell command. This is primarily for debugging purposes and should not be used
     * in tests since it's not really "api".
     */
    fun onShellCommand(displayId: Int, args: MutableList<String>, pw: PrintWriter) {}

    //
    // Contexts
    //

    /**
     * Additional context for updates, including specific transition information that isn't
     * persisted in the updated hierarchy.
     */
    class UpdateContext(
        var reason: String = "No reason given",
        // The surface transactions that are to be applied if there is an associated transition
        // with the update, if there isn't then these will be null, and the logic doing the update
        // must apply and surface updates itself.
        val preTransitionTx: SurfaceControl.Transaction? = null,
        // The specific container to dump (otherwise the full hierarchy will be dumped)
        var dumpOnlyContainer: Container? = null,
    )

    /**
     * Additional data which is necessary when resolving how to move another task to be associated
     * with a mode. This can be extended by having per-mode types with additional info, but the
     * mode must validate the request type before using it.
     *
     * @see requestEnterMode
     */
    class EnterRequestContext(
        val displayId: Int
    )

    /**
     * Additional context for resolving transitions.
     */
    class AnimationContext(
        // The surface transactions applied before the transition starts playing.
        val preTransitionTx: SurfaceControl.Transaction,
    )
}