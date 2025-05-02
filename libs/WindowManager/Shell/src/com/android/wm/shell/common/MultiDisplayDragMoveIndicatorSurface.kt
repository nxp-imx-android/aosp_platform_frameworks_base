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
package com.android.wm.shell.common

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.os.Trace
import android.view.Display
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL
import android.view.WindowlessWindowManager
import android.widget.ImageView
import android.window.TaskConstants
import androidx.compose.ui.graphics.toArgb
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.R as sharedR
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellDesktopThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.windowdecor.WindowDecoration
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents the indicator surface that visualizes the current position of a dragged window during
 * a multi-display drag operation.
 *
 * This class manages the creation, display, and manipulation of the [SurfaceControl]s that act as a
 * visual indicator, providing feedback to the user about the dragged window's location.
 */
@ShellDesktopThread
class MultiDisplayDragMoveIndicatorSurface(
    context: Context,
    taskInfo: RunningTaskInfo,
    display: Display,
    surfaceControlBuilderFactory: Factory.SurfaceControlBuilderFactory,
    taskResourceLoader: WindowDecorTaskResourceLoader,
    @ShellDesktopThread desktopDispatcher: CoroutineDispatcher,
    @ShellBackgroundThread bgScope: CoroutineScope,
    surfaceControlViewHostFactory: WindowDecoration.SurfaceControlViewHostFactory =
        object : WindowDecoration.SurfaceControlViewHostFactory {},
) {
    public enum class Visibility {
        INVISIBLE,
        TRANSLUCENT,
        VISIBLE,
    }

    private var visibility = Visibility.INVISIBLE

    // A container surface to host the veil background
    private var veilSurface: SurfaceControl? = null
    // A color surface for the veil background.
    private var backgroundSurface: SurfaceControl? = null
    // A surface that hosts a windowless window with the app icon.
    private var iconSurface: SurfaceControl? = null

    private var viewHost: SurfaceControlViewHost? = null
    private var loadAppInfoJob: Job? = null

    @VisibleForTesting var iconView: ImageView
    private var iconSize = 0

    private val decorThemeUtil = DecorThemeUtil(context)
    private val cornerRadius =
        context.resources
            .getDimensionPixelSize(sharedR.dimen.desktop_windowing_freeform_rounded_corner_radius)
            .toFloat()

    init {
        Trace.beginSection("DragIndicatorSurface#init")

        val displayId = display.displayId
        veilSurface =
            surfaceControlBuilderFactory
                .create("Drag indicator veil of Task=${taskInfo.taskId} Display=$displayId")
                .setColorLayer()
                .setCallsite("DragIndicatorSurface#init")
                .setHidden(true)
                .build()
        backgroundSurface =
            surfaceControlBuilderFactory
                .create("Drag indicator background of Task=${taskInfo.taskId} Display=$displayId")
                .setColorLayer()
                .setParent(veilSurface)
                .setCallsite("DragIndicatorSurface#init")
                .setHidden(true)
                .build()
        iconSurface =
            surfaceControlBuilderFactory
                .create("Drag indicator icon of Task=${taskInfo.taskId} Display=$displayId")
                .setContainerLayer()
                .setParent(veilSurface)
                .setCallsite("DragIndicatorSurface#init")
                .setHidden(true)
                .build()
        iconSize =
            context.resources.getDimensionPixelSize(R.dimen.desktop_mode_resize_veil_icon_size)
        val root =
            LayoutInflater.from(context)
                .inflate(R.layout.desktop_mode_resize_veil, /* root= */ null)
        iconView = root.requireViewById(R.id.veil_application_icon)
        val lp =
            WindowManager.LayoutParams(
                iconSize,
                iconSize,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT,
            )
        lp.title = "Drag indicator veil icon window of Task=${taskInfo.taskId} Display=$displayId"
        lp.inputFeatures = INPUT_FEATURE_NO_INPUT_CHANNEL
        lp.setTrustedOverlay()
        val wwm =
            WindowlessWindowManager(
                taskInfo.configuration,
                iconSurface,
                /* hostInputTransferToken = */ null,
            )
        viewHost =
            surfaceControlViewHostFactory.create(context, display, wwm, "DragIndicatorSurface")
        viewHost?.setView(root, lp)
        loadAppInfoJob =
            bgScope.launch {
                if (!isActive) return@launch
                val icon = taskResourceLoader.getVeilIcon(taskInfo)
                withContext(desktopDispatcher) {
                    if (!isActive) return@withContext
                    iconView.setImageBitmap(icon)
                }
            }

        Trace.endSection()
    }

    /** Disposes the viewHost and indicator surfaces using the provided [transaction]. */
    fun dispose(transaction: SurfaceControl.Transaction) {
        loadAppInfoJob?.cancel()
        viewHost?.release()
        viewHost = null

        backgroundSurface?.let { background -> transaction.remove(background) }
        backgroundSurface = null
        iconSurface?.let { icon -> transaction.remove(icon) }
        iconSurface = null
        veilSurface?.let { veil -> transaction.remove(veil) }
        veilSurface = null
    }

    /**
     * Shows the indicator surface at [bounds] on the specified display ([displayId]), visualizing
     * the drag of the [taskInfo]. The indicator surface is shown using [transaction], and the
     * [rootTaskDisplayAreaOrganizer] is used to reparent the surfaces.
     */
    fun show(
        transaction: SurfaceControl.Transaction,
        taskInfo: RunningTaskInfo,
        rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
        displayId: Int,
        bounds: Rect,
        visibility: Visibility,
    ) {
        val background = backgroundSurface
        val icon = iconSurface
        val veil = veilSurface
        if (veil == null || icon == null || background == null) {
            val nullSurfacesString = buildString {
                if (veil == null) append(" veilSurface")
                if (icon == null) append(" iconSurface")
                if (background == null) append(" backgroundSurface")
            }
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "Cannot show drag indicator for Task %d on Display %d because " +
                    "required surface(s) are null: %s",
                taskInfo.taskId,
                displayId,
                nullSurfacesString,
            )
            return
        }

        val backgroundColor = decorThemeUtil.getColorScheme(taskInfo).surfaceContainer

        rootTaskDisplayAreaOrganizer.reparentToDisplayArea(displayId, veil, transaction)
        relayout(bounds, transaction, visibility)
        transaction
            .show(veil)
            .show(background)
            .show(icon)
            .setColor(background, Color.valueOf(backgroundColor.toArgb()).components)
            .setLayer(veil, MOVE_INDICATOR_LAYER)
            .setLayer(icon, MOVE_INDICATOR_ICON_LAYER)
            .setLayer(background, MOVE_INDICATOR_BACKGROUND_LAYER)
        transaction.apply()
    }

    /**
     * Repositions and resizes the indicator surface based on [bounds] using [transaction]. The
     * [newVisibility] flag indicates whether the indicator is within the display after relayout.
     */
    fun relayout(bounds: Rect, transaction: SurfaceControl.Transaction, newVisibility: Visibility) {
        if (visibility == Visibility.INVISIBLE && newVisibility == Visibility.INVISIBLE) {
            // No need to relayout if the surface is already invisible and should not be visible.
            return
        }

        visibility = newVisibility
        val veil = veilSurface ?: return
        val icon = iconSurface ?: return
        val iconPosition = calculateAppIconPosition(bounds)
        transaction
            .setCrop(veil, bounds)
            .setCornerRadius(veil, cornerRadius)
            .setPosition(icon, iconPosition.x, iconPosition.y)
        when (visibility) {
            Visibility.VISIBLE ->
                transaction
                    .setAlpha(veil, ALPHA_FOR_MOVE_INDICATOR_ON_DISPLAY_WITH_CURSOR)
                    .setAlpha(icon, ALPHA_FOR_MOVE_INDICATOR_ON_DISPLAY_WITH_CURSOR)
            Visibility.TRANSLUCENT ->
                transaction
                    .setAlpha(veil, ALPHA_FOR_MOVE_INDICATOR_ON_NON_CURSOR_DISPLAY)
                    .setAlpha(icon, ALPHA_FOR_MOVE_INDICATOR_ON_NON_CURSOR_DISPLAY)
            Visibility.INVISIBLE -> {
                // Do nothing intentionally. Falling into this means the bounds is outside
                // of the display, so no need to hide the surface explicitly.
            }
        }
    }

    private fun calculateAppIconPosition(surfaceBounds: Rect): PointF {
        return PointF(
            surfaceBounds.left + surfaceBounds.width().toFloat() / 2 - iconSize.toFloat() / 2,
            surfaceBounds.top + surfaceBounds.height().toFloat() / 2 - iconSize.toFloat() / 2,
        )
    }

    /** Factory for creating [MultiDisplayDragMoveIndicatorSurface] instances with the [context]. */
    class Factory(
        private val taskResourceLoader: WindowDecorTaskResourceLoader,
        @ShellMainThread private val desktopDispatcher: CoroutineDispatcher,
        @ShellBackgroundThread private val bgScope: CoroutineScope,
    ) {
        private val surfaceControlBuilderFactory: SurfaceControlBuilderFactory =
            object : SurfaceControlBuilderFactory {}

        /**
         * Creates a new [MultiDisplayDragMoveIndicatorSurface] instance to visualize the drag
         * operation of the [taskInfo] on the given [display].
         */
        fun create(taskInfo: RunningTaskInfo, display: Display, displayContext: Context) =
            MultiDisplayDragMoveIndicatorSurface(
                displayContext,
                taskInfo,
                display,
                surfaceControlBuilderFactory,
                taskResourceLoader,
                desktopDispatcher,
                bgScope,
            )

        /**
         * Interface for creating [SurfaceControl.Builder] instances.
         *
         * This provides an abstraction over [SurfaceControl.Builder] creation for testing purposes.
         */
        interface SurfaceControlBuilderFactory {
            fun create(name: String): SurfaceControl.Builder {
                return SurfaceControl.Builder().setName(name)
            }
        }
    }

    companion object {
        private const val TAG = "MultiDisplayDragMoveIndicatorSurface"

        private const val MOVE_INDICATOR_LAYER = TaskConstants.TASK_CHILD_LAYER_RESIZE_VEIL

        // Layers for child surfaces within veilSurface. Higher values are drawn on top.
        /** Background layer (drawn first/bottom). */
        private const val MOVE_INDICATOR_BACKGROUND_LAYER = 0
        /** Icon layer (drawn second/top, above the background). */
        private const val MOVE_INDICATOR_ICON_LAYER = 1

        private const val ALPHA_FOR_MOVE_INDICATOR_ON_DISPLAY_WITH_CURSOR = 1.0f
        private const val ALPHA_FOR_MOVE_INDICATOR_ON_NON_CURSOR_DISPLAY = 0.8f
    }
}
