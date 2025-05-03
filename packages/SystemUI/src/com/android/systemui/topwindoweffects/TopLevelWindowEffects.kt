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

package com.android.systemui.topwindoweffects

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.topui.TopUiController
import com.android.systemui.topui.TopUiControllerRefactor
import com.android.systemui.topwindoweffects.domain.interactor.SqueezeEffectInteractor
import com.android.systemui.topwindoweffects.qualifiers.TopLevelWindowEffectsThread
import com.android.systemui.topwindoweffects.ui.compose.EffectsWindowRoot
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectViewModel
import com.android.wm.shell.appzoomout.AppZoomOut
import java.util.Optional
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SysUISingleton
class TopLevelWindowEffects
@Inject
constructor(
    @Application private val context: Context,
    @Main private val mainExecutor: Executor,
    @TopLevelWindowEffectsThread private val topLevelWindowEffectsScope: CoroutineScope,
    private val windowManager: WindowManager,
    private val squeezeEffectInteractor: SqueezeEffectInteractor,
    private val viewModelFactory: SqueezeEffectViewModel.Factory,
    // TODO(b/409930584): make AppZoomOut non-optional
    private val appZoomOutOptional: Optional<AppZoomOut>,
    private val notificationShadeWindowController: NotificationShadeWindowController,
    private val topUiController: TopUiController,
    private val interactionJankMonitor: InteractionJankMonitor,
) : CoreStartable {

    private var root: EffectsWindowRoot? = null

    // TODO(b/414267753): Make cleanup of window logic more robust
    private var isInvocationEffectHappening = false

    override fun start() {
        topLevelWindowEffectsScope.launch {
            squeezeEffectInteractor.isSqueezeEffectEnabled.collectLatest { enabled ->
                if (enabled) {
                    squeezeEffectInteractor.isPowerButtonDownAsSingleKeyGesture.collectLatest { down
                        ->
                        if (down) {
                            val roundedCornerInfo =
                                squeezeEffectInteractor.getRoundedCornersResourceId()
                            delay(squeezeEffectInteractor.getInvocationEffectInitialDelayMs())
                            addWindow(
                                roundedCornerInfo.topResourceId,
                                roundedCornerInfo.bottomResourceId,
                                roundedCornerInfo.physicalPixelDisplaySizeRatio,
                            )
                        } else if (root != null && !isInvocationEffectHappening) {
                            removeWindow()
                        }
                    }
                }
            }
        }
    }

    private fun addWindow(
        @DrawableRes topRoundedCornerId: Int,
        @DrawableRes bottomRoundedCornerId: Int,
        physicalPixelDisplaySizeRatio: Float,
    ) {
        if (isInvocationEffectHappening) {
            return
        }

        if (root != null) {
            Log.i(TAG, "addWindow: remove previous window")
            removeWindow()
        }

        root =
            EffectsWindowRoot(
                    context = context,
                    viewModelFactory = viewModelFactory,
                    topRoundedCornerResourceId = topRoundedCornerId,
                    bottomRoundedCornerResourceId = bottomRoundedCornerId,
                    physicalPixelDisplaySizeRatio = physicalPixelDisplaySizeRatio,
                    onEffectFinished = ::removeWindow,
                    appZoomOutOptional = appZoomOutOptional,
                    interactionJankMonitor = interactionJankMonitor,
                    onEffectStarted = { isInvocationEffectHappening = true },
                )
                .apply { visibility = View.GONE }

        root?.let { rootView ->
            setRequestTopUi(true)
            windowManager.addView(rootView, getWindowManagerLayoutParams())
            rootView.post { rootView.visibility = View.VISIBLE }
        }
    }

    private fun removeWindow() {
        if (root != null) {
            windowManager.removeView(root)
            root = null
        }

        isInvocationEffectHappening = false

        setRequestTopUi(false)
    }

    private fun setRequestTopUi(requestTopUi: Boolean) {
        if (TopUiControllerRefactor.isEnabled) {
            topUiController.setRequestTopUi(requestTopUi, TAG)
        } else {
            mainExecutor.execute {
                notificationShadeWindowController.setRequestTopUi(requestTopUi, TAG)
            }
        }
    }

    private fun getWindowManagerLayoutParams(): WindowManager.LayoutParams {
        val lp =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSPARENT,
            )

        lp.privateFlags =
            lp.privateFlags or
                (WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS or
                    WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION or
                    WindowManager.LayoutParams.PRIVATE_FLAG_EDGE_TO_EDGE_ENFORCED or
                    WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)

        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

        lp.title = TAG
        lp.fitInsetsTypes = WindowInsets.Type.systemOverlays()
        lp.gravity = Gravity.TOP

        return lp
    }

    companion object {
        @VisibleForTesting const val TAG = "TopLevelWindowEffects"
    }
}
