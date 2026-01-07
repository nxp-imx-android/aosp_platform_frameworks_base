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
import android.content.Context
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
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
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureUiComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.ui.viewmodel.ScreenCaptureUiViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ScreenCaptureUi
@AssistedInject
constructor(
    @Application private val appContext: Context,
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
    private val dialog: SystemUIDialog =
        dialogFactory
            .create(
                context =
                    appContext.createWindowContext(
                        display,
                        // TODO(b/427481098) Change to TYPE_SCREENSHOT
                        WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                        /* options */ null,
                    ),
                theme = R.style.Theme_SystemUI_Dialog_ScreenCapture,
                dialogDelegate = EdgeToEdgeDialogDelegate(),
                dismissOnDeviceLock = true,
                isTransient = true,
            ) { dialog: Dialog ->
                DialogContent(dialog.window!!)
            }
            .apply {
                setupWindow(window!!)
                setDismissOverride { visibleState.targetState = false }
            }
    private val visibleState = MutableTransitionState(false)

    private fun setupWindow(window: Window) {
        window.attributes =
            window.attributes.apply {
                title = "ScreenCaptureUi" // Not the same as Window#setTitle
            }
        with(window) {
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
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
        SideEffect {
            dialog.setCancelable(viewModel.cancelOnTouchOutside)
            dialog.setCanceledOnTouchOutside(viewModel.cancelOnTouchOutside)
        }

        if (!visibleState.targetState && visibleState.isIdle) {
            SideEffect {
                dialog.setDismissOverride(null)
                dialog.dismissWithoutAnimation()
            }
        }

        // Individual screen capture content control their own animation transitions.
        AnimatedVisibility(visibleState = visibleState, enter = EnterTransition.None) {
            val builder: ScreenCaptureUiComponent.Builder =
                componentBuilders.getValue(parameters.screenCaptureType)
            val coroutineScope = rememberCoroutineScope()
            val component =
                remember(parameters, coroutineScope) {
                    builder.setScope(coroutineScope).setDisplay(display).setWindow(window).build()
                }
            Box(modifier = Modifier.focusable()) { component.screenCaptureContent.Content() }
        }
    }

    /**
     * Shows the UI and suspends until it's is dismissed. Cancelling the suspension dismisses the UI
     */
    suspend fun show(): Unit = suspendCancellableCoroutine { invocation ->
        dialog.setOnDismissListener {
            dialog.dismiss()
            if (invocation.isActive) {
                invocation.resume(Unit)
            }
        }
        visibleState.targetState = true
        dialog.show()
        invocation.invokeOnCancellation { dialog.dismiss() }
    }

    @AssistedFactory
    interface Factory {

        fun create(display: Display, type: ScreenCaptureType): ScreenCaptureUi
    }
}
