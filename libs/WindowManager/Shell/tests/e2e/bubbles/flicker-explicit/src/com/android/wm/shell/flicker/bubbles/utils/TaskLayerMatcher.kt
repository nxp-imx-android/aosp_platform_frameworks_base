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

package com.android.wm.shell.flicker.bubbles.utils

import android.tools.traces.component.IComponentMatcher
import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.surfaceflinger.Layer
import android.tools.traces.wm.Activity
import android.tools.traces.wm.WindowContainer
import java.util.function.Predicate

/** Matches the Task layer that contains the given child activity. */
class TaskLayerMatcher(private val childActivityMatcher: IComponentNameMatcher) :
    IComponentMatcher {
    override fun windowMatchesAnyOf(windows: Collection<WindowContainer>): Boolean {
        error("Unimplemented - only for layer")
    }

    override fun activityMatchesAnyOf(activities: Collection<Activity>): Boolean {
        error("Unimplemented - only for layer")
    }

    override fun layerMatchesAnyOf(layers: Collection<Layer>): Boolean {
        return layers.any {
            if (!it.name.contains("Task=")) {
                return@any false
            }
            return@any it.children.stream().anyMatch {
                childActivityMatcher.activityRecordMatchesAnyOf(it)
            }
        }
    }

    override fun toActivityIdentifier(): String {
        error("Unimplemented - only for layer")
    }

    override fun toWindowIdentifier(): String {
        error("Unimplemented - only for layer")
    }

    override fun toLayerIdentifier(): String {
        return "Task[${childActivityMatcher.className}]"
    }

    override fun check(
        layers: Collection<Layer>,
        condition: Predicate<Collection<Layer>>
    ): Boolean {
        return condition.test(layers.filter { layerMatchesAnyOf(it) })
    }
}