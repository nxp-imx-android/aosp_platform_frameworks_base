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

import android.content.Context
import android.graphics.PixelFormat
import android.view.ViewTreeObserver.InternalInsetsInfo
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import com.android.systemui.util.view.listenToComputeInternalInsets
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@ScreenCaptureScope
class ScreenCaptureOverlayUi
@Inject
constructor(@Application context: Context, dialogFactory: SystemUIDialogFactory) {

    private val dialog =
        dialogFactory
            .create(
                context = context,
                theme = R.style.Theme_SystemUI_Dialog_ScreenCapture,
                dialogDelegate = EdgeToEdgeDialogDelegate(),
                dismissOnDeviceLock = false,
            ) { dialog ->
                DialogContent(dialog.window!!)
            }
            .apply {
                setupWindow(window!!)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }

    private fun setupWindow(window: Window) {
        window.attributes =
            window.attributes.apply {
                type = WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY
                format = PixelFormat.TRANSLUCENT
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            }
        with(window) {
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setWindowAnimations(-1)
        }
    }

    @Composable
    private fun DialogContent(window: Window) {
        LaunchedEffect(window) {
            window.decorView.viewTreeObserver.listenToComputeInternalInsets {
                setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)),
        ) {}
    }

    /**
     * Shows the UI and suspends until it's is dismissed. Cancelling the suspension dismisses the UI
     */
    suspend fun show(): Unit = suspendCancellableCoroutine { invocation ->
        dialog.setOnDismissListener {
            if (invocation.isActive) {
                invocation.resume(Unit)
            }
        }
        dialog.show()
        invocation.invokeOnCancellation { hide() }
    }

    private fun hide() {
        dialog.dismissWithoutAnimation()
    }
}
