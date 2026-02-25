/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.wm.shell.hierarchy.containers

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Binder
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.WindowlessWindowManager
import android.window.WindowContainerToken
import androidx.core.graphics.toRect
import com.android.wm.shell.hierarchy.utils.HierarchyUtils

typealias RootViewSupplier = (Context, ViewOverlayContainer) -> View

/**
 * A shell-only container in the ContainerHierarchy that be used to display an embedded view
 * hierarchy.
 *
 * Note: The bounds of this container are undefined until {@link #initialize()} is called.
 */
class ViewOverlayContainer(
    token: WindowContainerToken,
    private val rootViewSupplier: RootViewSupplier,
    private val overrideWidth: Int? = null,
    private val overrideHeight: Int? = null,
) : Container(token = token, name = token.asBinder().interfaceDescriptor) {
    // The view host for this container
    private var viewHost: SurfaceControlViewHost? = null

    // The root view of this container. This should only be used after 'initialize()' is called
    lateinit var rootView: View

    /**
     * Initializes the overlay container. The container must be in the hierarchy before this is
     * called.
     */
    fun initialize(context: Context) {
        val parent = this.parent!!

        val display = HierarchyUtils.getAncestorDisplay(this)!!
        val displayContext = display.displayProps().getDisplayContext(context)
        rootView =
            rootViewSupplier(displayContext.createConfigurationContext(parent.props.config), this)

        // Set WM flags, tokens, and sizing
        val width = overrideWidth ?: parent.props.bounds.width().toInt()
        val height = overrideHeight ?: parent.props.bounds.height().toInt()
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSPARENT
        )
        lp.token = Binder()
        lp.title = name
        lp.privateFlags =
            lp.privateFlags or (WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION
                    or WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
        rootView.layoutParams = lp

        // Create a new leash
        val builder = SurfaceControl.Builder()
            .setContainerLayer()
            .setName(name)
            .setCallsite(name)
        builder.setParent(parent.leash)
        leash = builder.build()

        // Create a new viewhost
        val wwm = WindowlessWindowManager(parent.props.config, leash, null)
        viewHost = SurfaceControlViewHost(
            displayContext, displayContext.display, wwm, name,
        )
        viewHost!!.setView(rootView, lp)

        // Update the container properties to match
        val relBounds = RectF(
            0f,
            0f,
            width.toFloat(),
            height.toFloat()
        )
        surface.updateReferenceFrame(relBounds)
        props.config.windowConfiguration.setBounds(relBounds.toRect())
        updateSurfaceFromPropertyChanges()
    }

    /**
     * Releases all surfaces & internal state associated with the overlay.
     * The caller must provide a SurfaceControl.Transaction to synchronize the removal of the
     * surface, and the caller must apply the provided transaction after this method is called.
     */
    fun release(tx: SurfaceControl.Transaction) {
        if (viewHost != null) {
            viewHost!!.release()
        }
        if (leash != NULL_SURFACE) {
            tx.remove(leash)
            leash = NULL_SURFACE
        }
    }
}