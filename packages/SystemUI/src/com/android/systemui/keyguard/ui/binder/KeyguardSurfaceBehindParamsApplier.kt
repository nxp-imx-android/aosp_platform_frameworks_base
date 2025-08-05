/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.binder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.util.Log
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SyncRtSurfaceTransactionApplier
import android.view.View
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.internal.R
import com.android.keyguard.KeyguardViewController
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.TAG
import com.android.systemui.keyguard.domain.interactor.KeyguardSurfaceBehindInteractor
import com.android.systemui.keyguard.shared.model.KeyguardSurfaceBehindModel
import com.android.wm.shell.shared.animation.Interpolators
import kotlin.math.max
import java.util.concurrent.Executor
import javax.inject.Inject

/** Damping ratio to use for animations resulting from touch gesture fling animation. */
private const val TOUCH_FLING_DAMPING_RATIO = 0.992f

/**
 * Fastest swipe velocity we'll use for the spring animations. Higher values will result in an
 * unreasonable amount of overshoot.
 */
private const val FASTEST_SWIPE_VELOCITY = -21500f

/**
 * Applies [KeyguardSurfaceBehindViewParams] to a RemoteAnimationTarget, starting and managing
 * animations as needed.
 */
@SysUISingleton
class KeyguardSurfaceBehindParamsApplier
@Inject
constructor(
    @Main private val executor: Executor,
    private val keyguardViewController: KeyguardViewController,
    private val interactor: KeyguardSurfaceBehindInteractor,
    context: Context,
) {
    private var surfaceBehind: RemoteAnimationTarget? = null
        set(value) {
            field = value
            interactor.setSurfaceRemoteAnimationTargetAvailable(value != null)
        }

    private val surfaceTransactionApplier: SyncRtSurfaceTransactionApplier
        get() = SyncRtSurfaceTransactionApplier(keyguardViewController.viewRootImpl.view)

    private val matrix = Matrix()
    private val tmpFloat = FloatArray(9)

    private val defaultSpring = SpringForce().apply { stiffness = 675f }

    /**
     * If the spring is running, the value we're animating from. This needs to be saved in case the
     * viewParams are updated while the spring is still running.
     */
    private var animatingFromTranslationY: Float = Float.NaN
    private var animatedTranslationY = FloatValueHolder()
    private val translateYSpring =
        SpringAnimation(animatedTranslationY).apply {
            spring = defaultSpring
            addUpdateListener { _, _, _ -> applyToSurfaceBehind() }
            addEndListener { _, _, _, _ ->
                try {
                    animatingFromTranslationY = Float.NaN
                    updateIsAnimatingSurface()
                } catch (e: NullPointerException) {
                    // TODO(b/291645410): Remove when we can isolate DynamicAnimations.
                    e.printStackTrace()
                }
            }
        }

    private var animatedAlpha = 0f
    private var alphaAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 125
            interpolator = Interpolators.ALPHA_IN
            addUpdateListener {
                animatedAlpha = it.animatedValue as Float
                applyToSurfaceBehind()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        updateIsAnimatingSurface()
                    }
                }
            )
        }

    /** Rounded corner radius to apply to the surface behind the keyguard. */
    private val roundedCornerRadius =
        context.resources.getDimensionPixelSize(R.dimen.rounded_corner_radius).toFloat()

    /**
     * ViewParams to apply to the surface provided to [applyParamsToSurface]. If the surface is null
     * these will be applied once someone gives us a surface via [applyParamsToSurface].
     */
    var viewParams: KeyguardSurfaceBehindModel = KeyguardSurfaceBehindModel()
        set(newParams) {
            field = newParams
            startOrUpdateAnimators()
            applyToSurfaceBehind()
        }

    /**
     * Provides us with a surface to animate. We'll apply the [viewParams] to this surface and start
     * any necessary animations.
     */
    fun applyParamsToSurface(surface: RemoteAnimationTarget) {
        this.surfaceBehind = surface
        startOrUpdateAnimators()
        applyToSurfaceBehind()
    }

    /**
     * Notifies us that the [RemoteAnimationTarget] has been released, one way or another.
     * Attempting to animate a released target will cause a crash.
     *
     * This can be called either because we finished animating the surface naturally, or by WM
     * because external factors cancelled the remote animation (timeout, re-lock, etc). If it's the
     * latter, cancel any outstanding animations we have.
     */
    fun notifySurfaceReleased() {
        surfaceBehind = null

        if (alphaAnimator.isRunning) {
            alphaAnimator.cancel()
        }

        if (translateYSpring.isRunning) {
            translateYSpring.cancel()
        }
    }

    private fun startOrUpdateAnimators() {
        if (surfaceBehind == null) {
            return
        }

        if (viewParams.willAnimateAlpha()) {
            var fromAlpha = viewParams.animateFromAlpha

            if (alphaAnimator.isRunning) {
                alphaAnimator.cancel()
                fromAlpha = animatedAlpha
            }

            alphaAnimator.setFloatValues(fromAlpha, viewParams.alpha)
            alphaAnimator.start()
        }

        if (viewParams.willAnimateTranslationY()) {
            if (!translateYSpring.isRunning) {
                // If the spring isn't running yet, set the start value. Otherwise, respect the
                // current position.
                animatedTranslationY.value = viewParams.animateFromTranslationY
                animatingFromTranslationY = viewParams.animateFromTranslationY
                translateYSpring.setStartVelocity(
                    max(FASTEST_SWIPE_VELOCITY, viewParams.startVelocity)
                )
                translateYSpring.setSpring(
                    defaultSpring.apply {
                        dampingRatio =
                            if (viewParams.startVelocity > 0f) TOUCH_FLING_DAMPING_RATIO else 1f
                    }
                )
            }

            translateYSpring.animateToFinalPosition(viewParams.translationY)
        }

        updateIsAnimatingSurface()
    }

    private fun updateIsAnimatingSurface() {
        interactor.setAnimatingSurface(translateYSpring.isRunning || alphaAnimator.isRunning)
    }

    private fun applyToSurfaceBehind() {
        surfaceBehind?.leash?.let { sc ->
            executor.execute {
                if (surfaceBehind == null) {
                    Log.d(
                        TAG,
                        "Attempting to modify params of surface that isn't " +
                            "animating. Ignoring.",
                    )
                    matrix.set(Matrix.IDENTITY_MATRIX)
                    return@execute
                }

                val translationY =
                    if (translateYSpring.isRunning) animatedTranslationY.value
                    else viewParams.translationY

                val alpha =
                    if (alphaAnimator.isRunning) {
                        animatedAlpha
                    } else {
                        viewParams.alpha
                    }

                if (
                    keyguardViewController.viewRootImpl.view?.visibility != View.VISIBLE &&
                        sc.isValid
                ) {
                    with(SurfaceControl.Transaction()) {
                        setMatrix(
                            sc,
                            matrix.apply {
                                setTranslate(/* dx= */ 0f, translationY)
                                var percentTranslated =
                                    1f - (animatedTranslationY.value / animatingFromTranslationY)
                                if (!percentTranslated.isFinite()) percentTranslated = 1f
                                setScale(
                                    95f + (5f * percentTranslated),
                                    95f + (5f * percentTranslated),
                                    surfaceBehind!!.screenSpaceBounds.width() / 2f,
                                    surfaceBehind!!.screenSpaceBounds.height() * .75f,
                                )
                            },
                            tmpFloat,
                        )
                        setAlpha(sc, alpha)
                        setCornerRadius(sc, roundedCornerRadius)
                        apply()
                    }
                } else {
                    var percentTranslated = 1f - (translationY / animatingFromTranslationY)
                    if (!percentTranslated.isFinite()) percentTranslated = 1f
                    surfaceTransactionApplier.scheduleApply(
                        SyncRtSurfaceTransactionApplier.SurfaceParams.Builder(sc)
                            .withMatrix(
                                matrix.apply {
                                    setScale(
                                        .93f + (.07f * percentTranslated),
                                        .93f + (.07f * percentTranslated),
                                        surfaceBehind!!.screenSpaceBounds.width() / 2f,
                                        surfaceBehind!!.screenSpaceBounds.height() * .66f,
                                    )
                                    postTranslate(/* dx= */ 0f, translationY)
                                }
                            )
                            .withAlpha(alpha)
                            .withCornerRadius(roundedCornerRadius)
                            .build()
                    )
                }
            }
        }
    }
}
