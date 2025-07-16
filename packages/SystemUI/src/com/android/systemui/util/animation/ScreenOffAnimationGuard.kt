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

package com.android.systemui.util.animation

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.View
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.android.systemui.Flags.screenOffAnimationGuardEnabled
import com.android.systemui.res.R
import java.lang.ref.WeakReference

private const val LOG_TAG = "AnimationGuard"

/**
 * This observes a given animation view and reports a WTF if the animations are running while the
 * screen is off.
 */
fun LottieAnimationView.enableScreenOffAnimationGuard() {
    if (!(Build.IS_ENG || Build.IS_USERDEBUG)) {
        return
    }

    if (!screenOffAnimationGuardEnabled()) {
        return
    }

    val lottieDrawable = drawable as? LottieDrawable ?: return
    if (getTag(R.id.screen_off_animation_guard_set) == System.identityHashCode(lottieDrawable)) {
        return
    }

    val animationView = WeakReference(this)
    val screenOffListenerGuard: ValueAnimator.AnimatorUpdateListener =
        ValueAnimator.AnimatorUpdateListener {
            animationView.get()?.let { view ->
                if (view.getTag(R.id.screen_off_animation_guard_reported_wtf) == true) {
                    return@AnimatorUpdateListener
                }

                // Retrieve ID of the view rendering
                val viewIdName =
                    try {
                        if (view.id != View.NO_ID) {
                            view.resources.getResourceEntryName(view.id)
                        } else {
                            "no-id"
                        }
                    } catch (e: Resources.NotFoundException) {
                        view.id.toString()
                    }

                val isScreenOff = view.display?.state == Display.STATE_OFF
                if (isScreenOff) {
                    // These logs create Binder calls, so throttle them. One is enough.
                    Log.wtf(LOG_TAG, "Lottie view $viewIdName is running while screen is off")
                    view.setTag(R.id.screen_off_animation_guard_reported_wtf, true)
                } else if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                    Log.d(LOG_TAG, "Lottie view $viewIdName is running while screen is on")
                }
            }
        }

    setTag(R.id.screen_off_animation_guard_reported_wtf, false)
    lottieDrawable.addAnimatorUpdateListener(screenOffListenerGuard)
    setTag(R.id.screen_off_animation_guard_set, System.identityHashCode(lottieDrawable))
}

/**
 * Attaches a listener which will report a [Log.wtf] error if the animator is attempting to render
 * frames while the screen is off.
 */
fun ValueAnimator.enableScreenOffAnimationGuard(context: Context) {
    if (!(Build.IS_ENG || Build.IS_USERDEBUG)) {
        return
    }

    enableScreenOffAnimationGuard({ context.display.state == Display.STATE_OFF })
}

/** Attaches an animation guard listener to the given ValueAnimator. */
fun ValueAnimator.enableScreenOffAnimationGuard(isDisplayOffPredicate: () -> Boolean) {
    if (!screenOffAnimationGuardEnabled()) {
        return
    }

    val listener = ScreenOffAnimationGuardListener(isDisplayOffPredicate)
    this.addListener(listener)
    this.addUpdateListener(listener)
}

/**
 * Remembers the stack trace of started animation and then reports an error if it runs when screen
 * is off.
 */
private class ScreenOffAnimationGuardListener(private val isDisplayOffPredicate: () -> Boolean) :
    Animator.AnimatorListener, ValueAnimator.AnimatorUpdateListener {

    // Holds the exception stack trace for the report.
    var animationStartedStackTrace: Exception? = null
    var animationDuringScreenOffReported = false

    override fun onAnimationStart(animation: Animator) {
        // This captures the stack trace of the starter of this animation.
        animationStartedStackTrace =
            AnimationDuringScreenOffException("Animation running during screen off.")
        animationDuringScreenOffReported = false
    }

    override fun onAnimationEnd(animation: Animator) {
        animationStartedStackTrace = null
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        if (!animationDuringScreenOffReported && isDisplayOffPredicate()) {
            Log.wtf(LOG_TAG, "View animator running during screen off.", animationStartedStackTrace)
            animationDuringScreenOffReported = true
        }
    }

    override fun onAnimationCancel(animation: Animator) {}

    override fun onAnimationRepeat(animation: Animator) {}
}

/** Used to record the stack trace of animation starter. */
private class AnimationDuringScreenOffException(message: String) : RuntimeException(message)
