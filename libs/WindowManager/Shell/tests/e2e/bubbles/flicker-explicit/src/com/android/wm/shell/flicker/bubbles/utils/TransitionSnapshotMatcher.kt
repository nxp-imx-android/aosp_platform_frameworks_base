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

package com.android.wm.shell.flicker.bubbles.utils

import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.surfaceflinger.Layer

/**
 * Matcher for transition snapshot layers.
 *
 * @param activityMatcher the activity that involves the transition snapshot.
 */
class TransitionSnapshotMatcher(private val activityMatcher: IComponentNameMatcher) :
    LayerMatcher() {

    override fun layerMatchesAnyOf(layers: Collection<Layer>): Boolean =
        layers.any {
            if (!it.name.contains("transition snapshot:")) {
                return@any false
            }
            return@any it.name.contains(activityMatcher.className)
        }

    override fun toLayerIdentifier(): String = "transition snapshot:[${activityMatcher.className}]"
}
