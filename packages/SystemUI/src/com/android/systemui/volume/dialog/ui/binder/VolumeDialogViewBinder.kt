/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.volume.dialog.ui.binder

import android.app.Dialog
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import androidx.compose.ui.util.lerp
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.updatePadding
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.internal.view.RotationPolicy
import com.android.systemui.common.ui.view.onApplyWindowInsets
import com.android.systemui.res.R
import com.android.systemui.util.kotlin.awaitCancellationThenDispose
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.android.systemui.volume.dialog.ui.utils.JankListenerFactory
import com.android.systemui.volume.dialog.ui.utils.suspendAnimate
import com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel
import com.android.systemui.volume.dialog.utils.VolumeTracer
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val SPRING_STIFFNESS = 700f
private const val SPRING_DAMPING_RATIO = 0.9f

private const val FRACTION_HIDE = 0f
private const val FRACTION_SHOW = 1f
private const val ANIMATION_MINIMUM_VISIBLE_CHANGE = 0.01f

/** Binds the root view of the Volume Dialog. */
@OptIn(ExperimentalCoroutinesApi::class)
@VolumeDialogScope
class VolumeDialogViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogViewModel,
    private val jankListenerFactory: JankListenerFactory,
    private val tracer: VolumeTracer,
    private val viewBinders: List<@JvmSuppressWildcards ViewBinder>,
) {

    fun CoroutineScope.bind(dialog: Dialog) {
        val insets: MutableStateFlow<WindowInsets> =
            MutableStateFlow(WindowInsets.Builder().build())
        // Root view of the Volume Dialog.
        val root: MotionLayout = dialog.requireViewById(R.id.volume_dialog)

        animateVisibility(root, dialog, viewModel.dialogVisibilityModel)

        viewModel.dialogTitle.onEach { dialog.window?.setTitle(it) }.launchIn(this)
        viewModel.motionState
            .scan(0) { acc, motionState ->
                // don't animate the initial state
                root.transitionToState(motionState, animate = acc != 0)
                acc + 1
            }
            .launchIn(this)

        launch { root.viewTreeObserver.listenToComputeInternalInsets() }

        launch {
            root
                .onApplyWindowInsets { v, newInsets ->
                    val insetsValues = newInsets.getInsets(WindowInsets.Type.displayCutout())
                    v.updatePadding(
                        left = insetsValues.left,
                        top = insetsValues.top,
                        right = insetsValues.right,
                        bottom = insetsValues.bottom,
                    )
                    insets.value = newInsets
                    WindowInsets.CONSUMED
                }
                .awaitCancellationThenDispose()
        }

        for (viewBinder in viewBinders) {
            with(viewBinder) { bind(root) }
        }
    }

    private fun CoroutineScope.animateVisibility(
        view: View,
        dialog: Dialog,
        visibilityModel: Flow<VolumeDialogVisibilityModel>,
    ) {
        view.applyAnimationProgress(FRACTION_HIDE)
        val animationValueHolder = FloatValueHolder(FRACTION_HIDE)
        val animation: SpringAnimation =
            SpringAnimation(animationValueHolder)
                .setSpring(
                    SpringForce()
                        .setStiffness(SPRING_STIFFNESS)
                        .setDampingRatio(SPRING_DAMPING_RATIO)
                )
                .setMinimumVisibleChange(ANIMATION_MINIMUM_VISIBLE_CHANGE)
                .addUpdateListener { _, value, _ -> view.applyAnimationProgress(value) }
        var junkListener: DynamicAnimation.OnAnimationUpdateListener? = null

        visibilityModel
            .mapLatest {
                when (it) {
                    is VolumeDialogVisibilityModel.Visible -> {
                        tracer.traceVisibilityEnd(it)
                        junkListener?.let(animation::removeUpdateListener)
                        junkListener =
                            jankListenerFactory.show(view).also(animation::addUpdateListener)
                        animation.suspendAnimate(FRACTION_SHOW)
                    }
                    is VolumeDialogVisibilityModel.Dismissed -> {
                        tracer.traceVisibilityEnd(it)
                        junkListener?.let(animation::removeUpdateListener)
                        junkListener =
                            jankListenerFactory.dismiss(view).also(animation::addUpdateListener)
                        animation.suspendAnimate(FRACTION_HIDE)
                        dialog.dismiss()
                    }
                    is VolumeDialogVisibilityModel.Invisible -> {
                        // do nothing
                    }
                }
            }
            .launchIn(this)
    }

    /**
     * @param fraction in range [0, 1]. 0 corresponds to the dialog being hidden and 1 - visible.
     */
    private fun View.applyAnimationProgress(fraction: Float) {
        alpha = ceil(fraction)
        if (display.rotation == RotationPolicy.NATURAL_ROTATION) {
                if (isLayoutRtl) {
                    -1
                } else {
                    1
                } * width / 2f
            } else {
                null
            }
            ?.let { maxTranslationX -> translationX = lerp(maxTranslationX, 0f, fraction) }
    }

    private suspend fun ViewTreeObserver.listenToComputeInternalInsets() =
        suspendCancellableCoroutine<Unit> { continuation ->
            val listener =
                ViewTreeObserver.OnComputeInternalInsetsListener { inoutInfo ->
                    viewModel.fillTouchableBounds(inoutInfo)
                }
            addOnComputeInternalInsetsListener(listener)
            continuation.invokeOnCancellation { removeOnComputeInternalInsetsListener(listener) }
        }

    private fun MotionLayout.transitionToState(newState: Int, animate: Boolean) {
        if (animate) {
            transitionToState(newState)
        } else {
            jumpToState(newState)
        }
    }
}
