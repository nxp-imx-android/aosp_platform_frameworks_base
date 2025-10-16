/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.animation

import android.content.ComponentName
import android.view.View

/**
 * A long-lived coordinator for a logical transition.
 *
 * This class manages a [TransitionSource] UI components that correspond to the same logical target.
 * Its primary role is to resolve the "best" active [TransitionSource] from its set when an
 * animation is requested.
 *
 * This object is designed to be long-lived (e.g., held in a ViewModel or Interactor) to survive
 * configuration changes, while the [TransitionSource] it manages are short-lived and tied to the
 * View/Composable lifecycle.
 */
class Expandable(
    // TODO(b/412899675) remove this once flag rolls out.
    val transitionSources: MutableSet<TransitionSource> = mutableSetOf()
) {

    constructor(transitionSource: TransitionSource) : this() {
        transitionSources.add(transitionSource)
    }

    /**
     * Create a [DialogTransitionAnimator.Controller] that can be used to expand this [Expandable]
     * into a Dialog, or return `null` if this [Expandable] should not be animated (e.g. if it is
     * currently not attached or visible).
     */
    fun dialogTransitionController(cuj: DialogCuj? = null): DialogTransitionAnimator.Controller? {
        return transitionSources.first().dialogTransitionController(cuj)
    }

    fun activityTransitionController(
        launchCujType: Int? = null
    ): ActivityTransitionAnimator.Controller? {
        return activityTransitionController(
            launchCujType,
            cookie = null,
            component = null,
            returnCujType = null,
            isEphemeral = true,
        )
    }

    /**
     * Create an [ActivityTransitionAnimator.Controller] that can be used to expand this
     * [Expandable] into an Activity, or return `null` if this [Expandable] should not be animated
     * (e.g. if it is currently not attached or visible).
     *
     * @param launchCujType The CUJ type from the [com.android.internal.jank.InteractionJankMonitor]
     *   associated to the launch that will use this controller.
     * @param cookie The unique cookie associated with the launch that will use this controller.
     *   This is required iff a return animation should be included.
     * @param component The name of the activity that will be launched by this controller. This is
     *   required for long-lived registrations only.
     * @param returnCujType The CUJ type from the [com.android.internal.jank.InteractionJankMonitor]
     *   associated to the return animation that will use this controller.
     */
    fun activityTransitionController(
        launchCujType: Int? = null,
        cookie: ActivityTransitionAnimator.TransitionCookie? = null,
        component: ComponentName? = null,
        returnCujType: Int? = null,
        isEphemeral: Boolean = true,
    ): ActivityTransitionAnimator.Controller? {
        return transitionSources
            .first()
            .activityTransitionController(
                launchCujType,
                cookie,
                component,
                returnCujType,
                isEphemeral,
            )
    }

    companion object {

        @JvmStatic
        fun fromView(view: View): Expandable {
            val transitionSource = TransitionSource.fromView(view)
            // TODO(b/412899675): make the addition lifecycle aware
            val expandable = Expandable(transitionSource)
            return expandable
        }
    }
}
