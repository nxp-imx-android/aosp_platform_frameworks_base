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
package com.android.wm.shell.windowdecor.caption

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.graphics.RegionIterator
import android.os.Binder
import android.os.Trace
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import android.window.DesktopModeFlags
import android.window.WindowContainerTransaction
import com.android.app.tracing.traceSection
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.windowdecor.HandleMenuController
import com.android.wm.shell.windowdecor.ManageWindowsMenuController
import com.android.wm.shell.windowdecor.MaximizeMenuController
import com.android.wm.shell.windowdecor.TaskFocusStateConsumer
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams.OccludingCaptionElement.Alignment
import com.android.wm.shell.windowdecor.WindowDecoration2.SurfaceControlViewHostFactory
import com.android.wm.shell.windowdecor.WindowDecorationInsets
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.extension.isRtl
import com.android.wm.shell.windowdecor.extension.isTransparentCaptionBarAppearance
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder


/**
 * Creates, updates, and removes the caption and its related menus based on [RunningTaskInfo]
 * changes and user interactions.
 *
 * @param <T> The type of the caption's root view
 */
abstract class CaptionController<T>(
    protected var taskInfo: RunningTaskInfo,
    private val windowDecorViewHostSupplier:
    WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val surfaceControlBuilderSupplier: () -> SurfaceControl.Builder =
        { SurfaceControl.Builder() },
    private val surfaceControlViewHostFactory: SurfaceControlViewHostFactory =
        object : SurfaceControlViewHostFactory {},
) where T : View, T : TaskFocusStateConsumer {

    private var captionInsets: WindowDecorationInsets? = null
    private val insetsOwner = Binder()
    private var captionViewHost: WindowDecorViewHost? = null
    private var windowDecorationViewHolder: WindowDecorationViewHolder<*>? = null
    protected lateinit var captionLayoutResult: CaptionRelayoutResult
    private lateinit var decorWindowContext: Context
    private var windowDecorationInsets: WindowDecorationInsets? = null

    protected var isCaptionVisible = false
    var isRecentsTransitionRunning = false
    var hasGlobalFocus = false
    var isDragging = false

    /** Controller for maximize menu or null if caption does not implement a maximize menu. */
    open val maximizeMenuController: MaximizeMenuController? = null
    /** Controller for handle menu or null if caption does not implement a handle menu. */
    open val handleMenuController: HandleMenuController? = null
    /**
     * Controller for manage windows menu or null if caption does not implement a manage windows
     * menu.
     */
    open val manageWindowsMenuController: ManageWindowsMenuController? = null

    /** Inflates the correct caption view and returns the view's view holder. */
    protected abstract fun createCaptionView(): WindowDecorationViewHolder<*>

    /** Type of caption.*/
    abstract val captionType: CaptionType

    /** Called when [WindowDecoration2] is closing. */
    abstract fun close()

    /**
     * Returns the caption height including any additional padding that will be added to the
     * caption.
     */
    abstract fun getCaptionHeight(): Int

    /** Returns the width of the caption. */
    protected abstract fun getCaptionWidth(): Int

    /** Returns the valid drag area for a task or null if task cannot be dragged. */
    open fun calculateValidDragArea(): Rect? = null

    /** Called when a task is animating after being repositioned or resized. */
    open fun onAnimatingTaskRepositioningOrResize(animatingTaskResizeOrReposition: Boolean) {}

    /**
     * Called by [WindowDecoration2] to trigger a new relayout to update the caption and its views.
     */
    open fun relayout(
        params: RelayoutParams,
        parentContainer: SurfaceControl,
        display: Display,
        decorWindowContext: Context,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
    ): CaptionRelayoutResult = traceSection(
        traceTag = Trace.TRACE_TAG_WINDOW_MANAGER,
        name = "CaptionController#relayout",
    ) {
        taskInfo = params.runningTaskInfo
        hasGlobalFocus = params.hasGlobalFocus
        this.decorWindowContext = decorWindowContext

        val viewHolder = getOrCreateViewHolder()
        isCaptionVisible = taskInfo.isVisible && params.isCaptionVisible
        val viewHost = getOrCreateViewHost(decorWindowContext, display)
        val taskBounds = taskInfo.getConfiguration().windowConfiguration.bounds
        val captionTopPadding = getCaptionTopPadding()
        val captionHeight = getCaptionHeight()
        val captionWidth = getCaptionWidth()
        val captionX = (taskBounds.width() - captionWidth) / 2
        val captionY = 0

        updateCaptionContainerSurface(
            parentContainer,
            startT,
            captionWidth,
            captionHeight,
            captionX
        )
        val customizableCaptionRegion =
            updateCaptionInsets(params, decorWindowContext, wct, captionHeight, taskBounds)
        logD(
            "relayout with taskBounds=%s captionSize=%dx%d captionTopPadding=%d " +
                    "captionX=%d captionY=%d customCaptionRegion=%s",
            taskBounds,
            captionHeight,
            captionWidth,
            captionTopPadding,
            captionX,
            captionY,
            customizableCaptionRegion.toReadableString(),
        )

        traceSection(
            traceTag = Trace.TRACE_TAG_WINDOW_MANAGER,
            name = "CaptionController#relayout-updateViewHost",
        ) {
            viewHolder.setTopPadding(captionTopPadding)
            viewHolder.setTaskFocusState(params.hasGlobalFocus)
            val localCaptionBounds = Rect(
                captionX,
                captionY,
                captionX + captionWidth,
                captionY + captionHeight
            )
            val touchableRegion = calculateLimitedTouchableRegion(
                params,
                decorWindowContext,
                localCaptionBounds
            )
            updateViewHierarchy(
                params,
                viewHost,
                viewHolder.rootView,
                captionWidth,
                captionHeight,
                startT,
                touchableRegion
            )
        }

        captionLayoutResult = CaptionRelayoutResult(
            captionHeight = captionHeight,
            captionWidth = captionWidth,
            captionX = captionX,
            captionY = captionY,
            captionTopPadding = captionTopPadding,
            customizableCaptionRegion = customizableCaptionRegion,
        )
        return captionLayoutResult
    }

    protected open fun getCaptionTopPadding(): Int = 0

    /** Updates the caption's view hierarchy. */
    private fun updateViewHierarchy(
        params: RelayoutParams,
        viewHost: WindowDecorViewHost,
        view: View,
        captionWidth: Int,
        captionHeight: Int,
        startT: SurfaceControl.Transaction,
        touchableRegion: Region?
    ) = traceSection(
        traceTag = Trace.TRACE_TAG_WINDOW_MANAGER,
        name = "CaptionController#updateViewHierarchy",
    ) {
        val lp = WindowManager.LayoutParams(
            captionWidth,
            captionHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSPARENT
        ).apply {
            title = "Caption of Task=" + taskInfo.taskId
            setTrustedOverlay()
            inputFeatures = params.inputFeatures
        }
        if (params.asyncViewHost) {
            require(!params.applyStartTransactionOnDraw) {
                "Cannot use sync draw tx with async relayout"
            }
            viewHost.updateViewAsync(
                view,
                lp,
                taskInfo.configuration,
                touchableRegion
            )
        } else {
            viewHost.updateView(
                view,
                lp,
                taskInfo.configuration,
                touchableRegion,
                if (params.applyStartTransactionOnDraw) startT else null
            )
        }
    }

    /**
     * Calculates the touchable region of the caption to only the areas where input should be
     * handled by  the system (i.e. non custom-excluded areas). The region will be calculated based
     * on occluding caption elements and exclusion areas reported by the app.
     *
     * If app is not requesting to customize caption bar, returns [null] signifying that the
     * touchable region is not limited.
     */
    private fun calculateLimitedTouchableRegion(
        params: RelayoutParams,
        decorWindowContext: Context,
        localCaptionBounds: Rect,
    ): Region? {
        // If app is not requesting custom caption, touchable region is not limited so return null
        if (!taskInfo.isTransparentCaptionBarAppearance ||
            !DesktopModeFlags.ENABLE_ACCESSIBLE_CUSTOM_HEADERS.isTrue
        ) {
            return null
        }

        val taskPositionInParent = taskInfo.positionInParent
        val captionBoundsInDisplay = Rect(localCaptionBounds).apply {
            offsetTo(taskPositionInParent.x, taskPositionInParent.y)
        }

        val boundingRects = calculateBoundingRectsRegion(
            params,
            decorWindowContext,
            captionBoundsInDisplay
        )

        val customizedRegion = Region.obtain().apply {
            set(captionBoundsInDisplay)
            op(boundingRects, Region.Op.DIFFERENCE)
            op(params.displayExclusionRegion, Region.Op.INTERSECT)
        }

        val touchableRegion = Region.obtain().apply {
            set(captionBoundsInDisplay)
            op(customizedRegion, Region.Op.DIFFERENCE)
            // Return resulting region back to window coordinates.
            translate(-taskPositionInParent.x, -taskPositionInParent.y)
        }

        boundingRects.recycle()
        customizedRegion.recycle()
        return touchableRegion
    }

    private fun calculateBoundingRectsRegion(
        params: RelayoutParams,
        decorWindowContext: Context,
        captionBoundsInDisplay: Rect,
    ): Region {
        val numOfElements = params.occludingCaptionElements.size
        val region = Region.obtain()
        if (numOfElements == 0) {
            // The entire caption is a bounding rect.
            region.set(captionBoundsInDisplay)
            return region
        }
        val resources = decorWindowContext.resources
        params.occludingCaptionElements.forEach { element ->
            val elementWidthPx = resources.getDimensionPixelSize(element.widthResId)
            val boundingRect = calculateBoundingRectLocal(
                element,
                elementWidthPx,
                captionBoundsInDisplay,
                decorWindowContext,
            )
            // Bounding rect is initially calculated relative to the caption, so offset it to make
            // it relative to the display.
            boundingRect.offset(captionBoundsInDisplay.left, captionBoundsInDisplay.top)
            region.union(boundingRect)
        }
        return region
    }

    private fun updateCaptionContainerSurface(
        parentContainer: SurfaceControl,
        startT: SurfaceControl.Transaction,
        captionWidth: Int,
        captionHeight: Int,
        captionX: Int
    ) {
        val captionSurface = captionViewHost?.surfaceControl ?: return
        startT.reparent(captionSurface, parentContainer)
            .setWindowCrop(captionSurface, captionWidth, captionHeight)
            .setPosition(captionSurface, captionX.toFloat(), /* y= */ 0f)
            .setLayer(captionSurface, CAPTION_LAYER_Z_ORDER)
            .show(captionSurface)
    }

    /**
     * Adds caption inset source to a WCT
     */
    fun addCaptionInset(wct: WindowContainerTransaction) {
        val captionHeight = getCaptionHeight()
        if (captionHeight == 0 || !isCaptionVisible) {
            return
        }

        val captionInsets = Rect(0, 0, 0, captionHeight)
        val newInsets = WindowDecorationInsets(
            token = taskInfo.token,
            owner = insetsOwner,
            frame = captionInsets,
            taskFrame = null,
            boundingRects = null,
            flags = 0,
            shouldAddCaptionInset = true,
            excludedFromAppBounds = false
        )
        if (newInsets != windowDecorationInsets) {
            windowDecorationInsets = newInsets.apply { update(wct) }
        }
    }

    private fun updateCaptionInsets(
        params: RelayoutParams,
        decorWindowContext: Context,
        wct: WindowContainerTransaction,
        captionHeight: Int,
        taskBounds: Rect
    ): Region {
        if (!isCaptionVisible || !params.isInsetSource) {
            captionInsets?.remove(wct)
            captionInsets = null
            return Region.obtain()
        }
        // Caption inset is the full width of the task with the |captionHeight| and
        // positioned at the top of the task bounds, also in absolute coordinates.
        // So just reuse the task bounds and adjust the bottom coordinate.
        val captionInsetsRect = Rect(taskBounds)
        captionInsetsRect.bottom = captionInsetsRect.top + captionHeight

        // Caption bounding rectangles: these are optional, and are used to present finer
        // insets than traditional |Insets| to apps about where their content is occluded.
        // These are also in absolute coordinates.
        val numOfElements = params.occludingCaptionElements.size
        val customizableCaptionRegion = Region.obtain()
        val boundingRects: Array<Rect>?
        if (numOfElements == 0) {
            boundingRects = null
        } else {
            // The customizable region can at most be equal to the caption bar.
            if (params.hasInputFeatureSpy()) {
                customizableCaptionRegion.set(captionInsetsRect)
            }
            val resources = decorWindowContext.resources
            boundingRects = Array(numOfElements) { Rect() }

            for (i in 0 until numOfElements) {
                val element = params.occludingCaptionElements[i]
                val elementWidthPx = resources.getDimensionPixelSize(element.widthResId)
                boundingRects[i].set(
                    calculateBoundingRectLocal(
                        element,
                        elementWidthPx,
                        captionInsetsRect,
                        decorWindowContext
                    )
                )
                // Subtract the regions used by the caption elements, the rest is
                // customizable.
                if (params.hasInputFeatureSpy()) {
                    customizableCaptionRegion.op(
                        boundingRects[i],
                        Region.Op.DIFFERENCE
                    )
                }
            }
        }

        val newInsets = WindowDecorationInsets(
            taskInfo.token,
            insetsOwner,
            captionInsetsRect,
            taskBounds,
            boundingRects,
            params.insetSourceFlags,
            params.isInsetSource,
            params.shouldSetAppBounds
        )
        if (newInsets != captionInsets) {
            // Add or update this caption as an insets source.
            captionInsets = newInsets
            newInsets.update(wct)
        }

        return customizableCaptionRegion
    }

    private fun calculateBoundingRectLocal(
        element: RelayoutParams.OccludingCaptionElement,
        elementWidthPx: Int,
        captionRect: Rect,
        decorWindowContext: Context,
    ): Rect {
        val isRtl = decorWindowContext.isRtl
        return when (element.alignment) {
            Alignment.START -> {
                if (isRtl) {
                    Rect(
                        captionRect.width() - elementWidthPx,
                        0,
                        captionRect.width(),
                        captionRect.height()
                    )
                } else {
                    Rect(0, 0, elementWidthPx, captionRect.height())
                }
            }

            Alignment.END -> {
                if (isRtl) {
                    Rect(0, 0, elementWidthPx, captionRect.height())
                } else {
                    Rect(
                        captionRect.width() - elementWidthPx,
                        0,
                        captionRect.width(),
                        captionRect.height()
                    )
                }
            }
        }
    }

    /**
     * Checks whether the touch event falls inside the customizable caption region.
     */
    fun checkTouchEventInCustomizableRegion(ev: MotionEvent): Boolean =
        captionLayoutResult.customizableCaptionRegion.contains(
            ev.rawX.toInt(),
            ev.rawY.toInt()
        )

    /**
     * Returns caption's view holder if not null. Otherwise, inflates caption view and returns new
     * view holder.
     */
    private fun getOrCreateViewHolder(): WindowDecorationViewHolder<*> {
        val viewHolder = windowDecorationViewHolder ?: createCaptionView()
        windowDecorationViewHolder = viewHolder
        return viewHolder
    }

    /** Releases all caption views. Returns true if caption view host is released. */
    open fun releaseViews(
        wct: WindowContainerTransaction,
        t: SurfaceControl.Transaction
    ): Boolean {
        captionInsets?.remove(wct)
        captionInsets = null

        val viewHost = captionViewHost ?: return false
        windowDecorViewHostSupplier.release(viewHost, t)
        captionViewHost = null
        return true
    }

    private fun getOrCreateViewHost(
        context: Context,
        display: Display
    ): WindowDecorViewHost = traceSection(
        traceTag = Trace.TRACE_TAG_WINDOW_MANAGER,
        name = "CaptionController#getOrCreateViewHost",
    ) {
        val viewHost = captionViewHost ?: windowDecorViewHostSupplier.acquire(context, display)
        captionViewHost = viewHost
        return viewHost
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_WINDOW_DECORATION, "%s: $msg", TAG, *arguments)
    }

    private fun Region.toReadableString(): String {
        val iterator = RegionIterator(this)
        val rect = Rect()
        val sb = StringBuilder()
        sb.append("Region[")
        var first = true
        while (iterator.next(rect)) {
            if (!first) {
                sb.append(", ")
            }
            sb.append(rect.toShortString())
            first = false
        }
        sb.append("]")
        return sb.toString()
    }

    /** Caption data calculated during [relayout]. */
    data class CaptionRelayoutResult(
        // The caption height with caption padding included
        val captionHeight: Int,
        val captionWidth: Int,
        val captionX: Int,
        val captionY: Int,
        val captionTopPadding: Int,
        val customizableCaptionRegion: Region,
    )

    /** The type of caption added by this controller. */
    enum class CaptionType {
        APP_HANDLE, APP_HEADER, NO_CAPTION
    }

    companion object {
        private const val TAG = "CaptionController"

        /**
         * The Z-order of the caption surface.
         *
         *
         * We use [decorationContainerSurface] to define input window for task resizing; by
         * layering it in front of the caption surface, we can allow it to handle input
         * prior to caption view itself, treating corner inputs as resize events rather than
         * repositioning.
         */
        private const val CAPTION_LAYER_Z_ORDER: Int = -1
    }
}
