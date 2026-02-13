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
package com.android.wm.shell.hierarchy.properties

import android.view.Display.DEFAULT_DISPLAY
import android.window.TransitionInfo
import com.android.wm.shell.dagger.hierarchy.WmSyncedProperty
import com.android.wm.shell.hierarchy.updates.HierarchyChangeFlags
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_FOCUS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_ROOT_EXAMPLE_SHELL_PROPERTY
import com.android.wm.shell.transition.FocusTransitionObserver

/**
 * Tracks the currently focused state in the hierarchy.
 */
class FocusState {
    var globallyFocusedDisplayId: Int = DEFAULT_DISPLAY
    var globallyFocusedTaskId: Int = -1
    var perDisplayFocusedTaskId: MutableMap<Int, Int> = mutableMapOf()

    fun copyFrom(other: FocusState) {
        globallyFocusedDisplayId = other.globallyFocusedDisplayId
        globallyFocusedTaskId = other.globallyFocusedTaskId
        perDisplayFocusedTaskId.clear()
        perDisplayFocusedTaskId.putAll(other.perDisplayFocusedTaskId)
    }

    fun diff(other: FocusState, chgs: HierarchyChangeFlags) {
        chgs.compareAndSet(globallyFocusedDisplayId, other.globallyFocusedDisplayId, CHANGED_FOCUS)
        chgs.compareAndSet(globallyFocusedTaskId, other.globallyFocusedTaskId, CHANGED_FOCUS)
        chgs.compareAndSet(perDisplayFocusedTaskId, other.perDisplayFocusedTaskId, CHANGED_FOCUS)
    }

    fun propsToString(): String {
        return "focusedDisplay=$globallyFocusedDisplayId focusedTask=$globallyFocusedTaskId"
    }
}

/**
 * Properties for the root container in the hierarchy.
 */
class RootContainerProperties : ContainerProperties() {

    // The global focus state of the hierarchy
    @WmSyncedProperty
    val focusState = FocusState()

    // An example of a tracked shell state that modes can listen for
    var exampleTrackedShellOnlyState = false

    private val focusTransitionObserver = FocusTransitionObserver()

    /**
     * Updates the root of the hierarchy from any ongoing transition.
     */
    fun updateFromTransition(info: TransitionInfo) {
        focusTransitionObserver.updateFocusState(info)
        focusState.globallyFocusedDisplayId = focusTransitionObserver.globallyFocusedDisplayId
        focusState.globallyFocusedTaskId = focusTransitionObserver.globallyFocusedTaskId
        focusTransitionObserver.setFocusedTaskIdsPerDisplay(focusState.perDisplayFocusedTaskId)
    }

    /** @see ContainerProperties.copyFrom */
    override fun copyFrom(other: ContainerProperties) {
        val otherRoot = other as RootContainerProperties
        focusState.copyFrom(otherRoot.focusState)
        exampleTrackedShellOnlyState = otherRoot.exampleTrackedShellOnlyState
        super.copyFrom(other)
    }

    /** @see ContainerProperties.copy */
    override fun copy(): RootContainerProperties {
        return RootContainerProperties().apply {
            copyFrom(this@RootContainerProperties)
        }
    }

    /** @see ContainerProperties.propsToString */
    override fun diff(other: ContainerProperties, chgs: HierarchyChangeFlags) {
        super.diff(other, chgs)
        val otherRoot = other as RootContainerProperties
        focusState.diff(otherRoot.focusState, chgs)
        chgs.compareAndSet(
            exampleTrackedShellOnlyState, otherRoot.exampleTrackedShellOnlyState,
            CHANGED_ROOT_EXAMPLE_SHELL_PROPERTY
        )
    }

    override fun propsToString(): String {
        return focusState.propsToString() + " example=$exampleTrackedShellOnlyState " + super.propsToString()
    }
}