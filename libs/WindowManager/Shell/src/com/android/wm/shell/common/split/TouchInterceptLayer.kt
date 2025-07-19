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
package com.android.wm.shell.common.split

import android.app.TaskInfo
import android.content.Context
import android.graphics.PixelFormat
import android.os.Binder
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.WindowlessWindowManager

/**
 * Manages a touchable surface that is intended to intercept touches.
 */
class TouchInterceptLayer(
    val name: String = TAG
) {
    private var viewHost: SurfaceControlViewHost? = null
    private var leash: SurfaceControl? = null
    var rootView: View? = null

    /**
     * Creates a touch zone.
     */
    fun inflate(context: Context,
        rootLeash: SurfaceControl,
        rootTaskInfo: TaskInfo
    ) {
        rootView = View(context.createConfigurationContext(rootTaskInfo.configuration))

        // Set WM flags, tokens, and sizing on the touchable view. It will be the same size as its
        // parent
        // TODO (b/349828130): It's a bit wasteful to have the touch zone cover the whole app
        //  surface, even extending offscreen (keeps buffer active in memory), so can trim it down
        //  to the visible onscreen area in a future patch.
        val lp = WindowManager.LayoutParams(
            rootTaskInfo.configuration.windowConfiguration.bounds.width(),
            rootTaskInfo.configuration.windowConfiguration.bounds.height(),
            WindowManager.LayoutParams.TYPE_INPUT_CONSUMER,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSPARENT
        )
        lp.token = Binder()
        lp.setTitle(name)
        lp.privateFlags =
            lp.privateFlags or (WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION
                    or WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
        rootView?.setLayoutParams(lp)

        // Create a new leash under our stage leash.
        val builder = SurfaceControl.Builder()
            .setContainerLayer()
            .setName(name)
            .setCallsite(name + "[TouchInterceptLayer.inflate]")
        builder.setParent(rootLeash)
        leash = builder.build()

        // Create a ViewHost that will hold our view.
        val wwm = WindowlessWindowManager(rootTaskInfo.configuration, leash, null)
        viewHost = SurfaceControlViewHost(
            context, context.display, wwm,
            name + "[TouchInterceptLayer.inflate]"
        )
        viewHost!!.setView(rootView!!, lp)

        // Request a transparent region (and mark as will not draw to ensure the full view region)
        // as an optimization to SurfaceFlinger so we don't need to render the transparent surface
        rootView?.setWillNotDraw(true)
        rootView?.parent?.requestTransparentRegion(rootView)

        // Create a transaction so that we can activate and reposition our surface.
        val t = SurfaceControl.Transaction()
        // Set layer to maximum. We want this surface to be above the app layer, or else touches
        // will be blocked.
        t.setLayer(leash!!, SplitLayout.RESTING_TOUCH_LAYER)
        // Leash starts off hidden, show it.
        t.show(leash)
        t.apply()
    }

    /**
     * Releases the touch zone when it's no longer needed.
     */
    fun release() {
        if (viewHost != null) {
            viewHost!!.release()
        }
        if (leash != null) {
            val t = SurfaceControl.Transaction()
            t.remove(leash!!)
            t.apply()
            leash = null
        }
    }

    companion object {
        private const val TAG: String = "TouchInterceptLayer"
    }
}
