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

package com.android.systemui.screencapture.ui

import android.app.Dialog
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.thenIf
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureUiComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.ui.viewmodel.ScreenCaptureUiViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private val scaleTransformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)

class ScreenCaptureUi
@AssistedInject
constructor(
    @Assisted private val display: Display,
    @Assisted private val type: ScreenCaptureType,
    private val viewModelFactory: ScreenCaptureUiViewModel.Factory,
    private val componentBuilders:
        Map<
            @JvmSuppressWildcards
            ScreenCaptureType,
            @JvmSuppressWildcards
            ScreenCaptureUiComponent.Builder,
        >,
    dialogFactory: SystemUIDialogFactory,
) {

    private val dialog =
        dialogFactory
            .create(
                theme = R.style.Theme_SystemUI_Dialog_ScreenCapture,
                dialogDelegate = EdgeToEdgeDialogDelegate(),
                dismissOnDeviceLock = true,
            ) { dialog: Dialog ->
                DialogContent(dialog.window!!)
            }
            .apply {
                setupWindow(window!!)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }
    private val visibleState = MutableTransitionState(false)

    private fun setupWindow(window: Window) {
        window.attributes =
            window.attributes.apply {
                title = "ScreenCaptureUi" // Not the same as Window#setTitle
            }
        with(window) {
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            // TODO(b/427481098) Change to TYPE_SCREENSHOT
            setType(WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL)
            setWindowAnimations(-1)
        }
    }

    @Composable
    private fun DialogContent(window: Window) {
        val viewModel =
            rememberViewModel("ScreenCaptureUi#viewModel") { viewModelFactory.create(type) }
        var parametersState: ScreenCaptureUiParameters? by remember { mutableStateOf(null) }
        LaunchedEffect(viewModel.state) {
            (viewModel.state as? ScreenCaptureUiState.Visible)?.parameters?.let {
                parametersState = it
            }
        }
        // Wait until parameters are passed down to Compose
        val parameters = parametersState ?: return
        val isLargeScreen = viewModel.isLargeScreen ?: false

        if (!visibleState.targetState && visibleState.isIdle) {
            SideEffect { dialog.dismissWithoutAnimation() }
        }

        val useLargeScreenShareAnimations = isLargeScreen && type == ScreenCaptureType.SHARE_SCREEN
        val density = LocalDensity.current
        val emphasizedDecelerate = remember { CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f) }
        val standardEasing = remember { CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) }
        val initialOffsetPx = with(density) { 40.dp.roundToPx() }
        val standardAccelerate = remember { CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f) }
        val targetOffsetPx = with(density) { 20.dp.roundToPx() }

        AnimatedVisibility(
            visibleState = visibleState,
            // TODO(b/449826486): make each capture type (screenshots, recording, sharing) control
            // their own animations.
            enter =
                if (useLargeScreenShareAnimations) {
                    slideInVertically(
                        animationSpec = tween(durationMillis = 300, easing = emphasizedDecelerate),
                        initialOffsetY = { initialOffsetPx },
                    ) + fadeIn(animationSpec = tween(durationMillis = 300, easing = standardEasing))
                } else {
                    scaleIn(transformOrigin = scaleTransformOrigin) + slideInVertically()
                },
            exit =
                if (useLargeScreenShareAnimations) {
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 150, easing = standardAccelerate),
                        targetOffsetY = { targetOffsetPx },
                    ) +
                        fadeOut(
                            animationSpec = tween(durationMillis = 150, easing = standardAccelerate)
                        )
                } else {
                    scaleOut(transformOrigin = scaleTransformOrigin) + slideOutVertically()
                },
        ) {
            val builder: ScreenCaptureUiComponent.Builder =
                componentBuilders.getValue(parameters.screenCaptureType)
            val coroutineScope = rememberCoroutineScope()
            val component =
                remember(parameters, coroutineScope) {
                    builder.setScope(coroutineScope).setDisplay(display).setWindow(window).build()
                }
            Box(
                modifier =
                    Modifier.focusable().thenIf(!isLargeScreen) {
                        // On small screens, follow the design pattern of a dialog
                        Modifier.clickable(
                            onClick = { hide() },
                            indication = null,
                            interactionSource = null,
                        )
                    }
            ) {
                component.screenCaptureContent.Content()
            }
        }
    }

    /**
     * Shows the UI and suspends until it's is dismissed. Cancelling the suspension dismisses the UI
     */
    suspend fun show(): Unit = suspendCancellableCoroutine { invocation ->
        dialog.setOnDismissListener {
            hide()
            if (invocation.isActive) {
                invocation.resume(Unit)
            }
        }
        visibleState.targetState = true
        dialog.show()
        invocation.invokeOnCancellation { hide() }
    }

    private fun hide() {
        visibleState.targetState = false
    }

    @AssistedFactory
    interface Factory {

        fun create(display: Display, type: ScreenCaptureType): ScreenCaptureUi
    }
}
