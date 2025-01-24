/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.systemui.statusbar.notification

import android.util.Property
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.PhysicsPropertyAnimator.Companion.createDefaultSpring
import com.android.systemui.statusbar.notification.stack.AnimationProperties

/**
 * A physically animatable property of a view.
 *
 * @param tag the view tag to safe this property in
 * @param property the property to animate.
 */
data class PhysicsProperty(val tag: Int, val property: Property<View, Float>) {
    val offsetProperty =
        object : FloatPropertyCompat<View>(property.name) {
            override fun getValue(view: View): Float {
                return property.get(view)
            }

            override fun setValue(view: View, offset: Float) {
                val propertyData = view.getTag(tag) as PropertyData? ?: return
                propertyData.offset = offset
                property.set(view, propertyData.finalValue + offset)
            }
        }

    fun setFinalValue(view: View, finalValue: Float) {
        val propertyData = obtainPropertyData(view, this)
        val previousValue = propertyData.finalValue
        if (previousValue != finalValue) {
            propertyData.finalValue = finalValue
            property.set(view, propertyData.finalValue + propertyData.offset)
        }
    }
}

/** The propertyData associated with each animation running */
data class PropertyData(
    var finalValue: Float = 0f,
    var offset: Float = 0f,
    var animator: SpringAnimation? = null,
    var delayRunnable: Runnable? = null,
)

/**
 * A utility that can run physics based animations in a simple way. It properly handles overlapping
 * calls where sometimes a property can be set without animation, while also having instances where
 * it's supposed to start animations.
 *
 * This overall helps making sure that physics based animations complete and don't constantly start
 * new transitions which can lead to a feeling of lagging behind.
 *
 * Overall it is achieved by starting offset animations to an end value as soon as an animation is
 * requested and updating the end value immediately when no animation is needed. With the offset
 * always going to 0, this ensures that animations complete within a short time after an animation
 * has been requested.
 */
class PhysicsPropertyAnimator {
    companion object {
        @JvmField val TAG_ANIMATOR_TRANSLATION_Y = R.id.translation_y_animator_tag

        @JvmField
        val Y_TRANSLATION: PhysicsProperty =
            PhysicsProperty(TAG_ANIMATOR_TRANSLATION_Y, View.TRANSLATION_Y)

        // Uses the standard spatial material spring by default
        @JvmStatic
        fun createDefaultSpring(): SpringForce {
            return SpringForce()
                .setStiffness(380f) // MEDIUM LOW STIFFNESS
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY) // LOW BOUNCINESS
        }

        @JvmStatic
        @JvmOverloads
        /**
         * Set a property on a view, updating its value, even if it's already animating. The @param
         * animated can be used to request an animation. If the view isn't animated, this utility
         * will update the current animation if existent, such that the end value will point
         * to @param newEndValue or apply it directly if there's no animation.
         */
        fun setProperty(
            view: View,
            animatableProperty: PhysicsProperty,
            newEndValue: Float,
            properties: AnimationProperties? = null,
            animated: Boolean = false,
            endListener: DynamicAnimation.OnAnimationEndListener? = null,
        ) {
            if (animated) {
                startAnimation(view, animatableProperty, newEndValue, properties, endListener)
            } else {
                animatableProperty.setFinalValue(view, newEndValue)
            }
        }

        fun isAnimating(view: View, property: PhysicsProperty): Boolean {
            val (_, _, animator, _) = obtainPropertyData(view, property)
            return animator?.isRunning ?: false
        }
    }
}

private fun startAnimation(
    view: View,
    animatableProperty: PhysicsProperty,
    newEndValue: Float,
    properties: AnimationProperties?,
    endListener: DynamicAnimation.OnAnimationEndListener?,
) {
    val property = animatableProperty.property
    val propertyData = obtainPropertyData(view, animatableProperty)
    val previousEndValue = propertyData.finalValue
    if (previousEndValue == newEndValue) {
        return
    }
    propertyData.finalValue = newEndValue
    var animator = propertyData.animator
    if (animator == null) {
        animator = SpringAnimation(view, animatableProperty.offsetProperty)
        propertyData.animator = animator
        animator.setSpring(createDefaultSpring())
        val listener = properties?.getAnimationEndListener(animatableProperty.property)
        if (listener != null) {
            animator.addEndListener(listener)
            // We always notify things as started even if we have a delay
            properties.getAnimationStartListener(animatableProperty.property)?.accept(animator)
        }
        // remove the tag when the animation is finished
        animator.addEndListener { _, _, _, _ -> propertyData.animator = null }
    }
    // TODO(b/393581344): look at custom spring
    endListener?.let { animator.addEndListener(it) }
    val newOffset = previousEndValue - newEndValue + propertyData.offset

    // Immedialely set the new offset that compensates for the immediate end value change
    propertyData.offset = newOffset
    property.set(view, newEndValue + newOffset)

    // cancel previous starters still pending
    view.removeCallbacks(propertyData.delayRunnable)
    animator.setStartValue(newOffset)
    val startRunnable = Runnable {
        animator.animateToFinalPosition(0f)
        propertyData.delayRunnable = null
    }
    if (properties != null && properties.delay > 0 && !animator.isRunning) {
        propertyData.delayRunnable = startRunnable
        view.postDelayed(propertyData.delayRunnable, properties.delay)
    } else {
        startRunnable.run()
    }
}

private fun obtainPropertyData(view: View, animatableProperty: PhysicsProperty): PropertyData {
    var propertyData = view.getTag(animatableProperty.tag) as PropertyData?
    if (propertyData == null) {
        propertyData =
            PropertyData(finalValue = animatableProperty.property.get(view), offset = 0f, null)
        view.setTag(animatableProperty.tag, propertyData)
    }
    return propertyData
}
