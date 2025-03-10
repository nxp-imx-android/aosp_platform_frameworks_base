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

package com.android.systemui.statusbar.notification.stack

import androidx.dynamicanimation.animation.SpringForce

/** A listener that responds to magnetic forces applied to an [ExpandableNotificationRow] */
interface MagneticRowListener {

    /**
     * Set a translation due to a magnetic attachment.
     *
     * The request to set a View's translation may occur while a magnetic animation is running. In
     * such a case, the [trackEagerly] argument will determine if we decide to eagerly track the
     * incoming translation or not. If true, the ongoing animation will update its target position
     * and continue to animate only if the incoming translation produces a delta higher than a touch
     * slop threshold. Otherwise, the animation will be cancelled and the View translation will be
     * set directly. If [trackEagerly] is false, we respect the animation and only update the
     * animated target.
     *
     * @param[translation] Incoming gesture translation.
     * @param[trackEagerly] Whether we eagerly track the incoming translation directly or not.
     */
    fun setMagneticTranslation(translation: Float, trackEagerly: Boolean = true)

    /**
     * Trigger the magnetic behavior when the row detaches or snaps back from its magnetic
     * couplings.
     *
     * @param[endTranslation] Translation that the row detaches to.
     * @param[springForce] A [SpringForce] that guides the dynamics of the behavior towards the end
     *   translation. This could be a detachment spring force or a snap-back spring force.
     * @param[startVelocity] A start velocity for the animation.
     */
    fun triggerMagneticForce(
        endTranslation: Float,
        springForce: SpringForce,
        startVelocity: Float = 0f,
    )

    /** Cancel any animations related to the magnetic interactions of the row */
    fun cancelMagneticAnimations()

    /** Cancel any other animations related to the row's translation */
    fun cancelTranslationAnimations()

    /** Can the row be dismissed. */
    fun canRowBeDismissed(): Boolean
}
