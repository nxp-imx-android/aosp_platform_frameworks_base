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

import android.window.WindowAnimationState
import android.window.WindowContainerTransaction
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.updates.HierarchyChangeFlags
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.android.wm.shell.transition.DetachResult
import com.android.wm.shell.transition.ITransitionAnimation
import com.google.common.truth.Truth.assertThat
import org.mockito.kotlin.mock

/**
 * A test Mode for a container in the ContainerHierarchy.
 */
open class StubMode(
    private val name: String=""
) : Mode {
    val attachedRoots = mutableListOf<Container>()
    val attachedContainers = mutableListOf<Container>()
    val updates = mutableListOf<Pair<Container, HierarchyChangeFlags>>()
    val displayChanges = mutableListOf<DisplayContainerProperties>()

    // The containers to compare againest those provided in the next prepareForAnimation() call
    var expectedPrepareForAnimationContainers: List<Container>? = null
    // If true, will "consume" the next prepareForAnimation() call
    var consumePrepareForAnimation = false
    // The containers to compare againest those provided in the next createAnimation() call
    var expectedCreateAnimationContainers: List<Container>? = null
    // If true, will "consume" the next createAnimation() call
    var consumeCreateAnimation = false

    override fun getId(): String {
        return "TestMode_$name"
    }

    override fun attachToContainer(
        updateContext: Mode.UpdateContext,
        container: Container,
        isDirectlyAssigned: Boolean
    ) {
        if (isDirectlyAssigned) {
            attachedRoots.add(container)
        }
        attachedContainers.add(container)
    }

    override fun detachFromContainer(updateContext: Mode.UpdateContext, container: Container) {
        attachedRoots.remove(container)
        attachedContainers.remove(container)
    }

    override fun containerChanged(
        updateContext: Mode.UpdateContext,
        container: Container,
        snapshot: HierarchySnapshot
    ) {
        updates.add(container to snapshot.getChanges(container))
    }

    override fun requestUpdateForDisplayChange(
        directlyAssignedContainer: Container,
        curProps: DisplayContainerProperties,
        newProps: DisplayContainerProperties,
        wct: WindowContainerTransaction
    ) {
        displayChanges.add(newProps)
    }

    override fun prepareForAnimation(containers: List<Container>): List<DetachResult>? {
        val expectedContainers = expectedPrepareForAnimationContainers
        if (expectedContainers != null) {
            assertThat(expectedContainers.toSet() == containers.toSet()).isTrue()
        }
        if (consumePrepareForAnimation) {
            val results = mutableListOf<DetachResult>()
            for (container in containers) {
                results.add(DetachResult(listOf(WindowAnimationState())))
            }
            return results
        }
        return null
    }

    override fun createAnimation(
        animContext: Mode.AnimationContext,
        containers: List<Container>,
        snapshot: HierarchySnapshot
    ): ITransitionAnimation? {
        val expectedContainers = expectedCreateAnimationContainers
        if (expectedContainers != null) {
            assertThat(expectedContainers.toSet() == containers.toSet()).isTrue()
        }
        if (consumeCreateAnimation) {
            return mock<ITransitionAnimation>()
        }
        return null
    }
}