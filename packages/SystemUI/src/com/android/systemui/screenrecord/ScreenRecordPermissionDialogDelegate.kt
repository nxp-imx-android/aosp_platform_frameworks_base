/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.screenrecord

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.UserHandle
import android.view.View
import android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR
import androidx.annotation.StyleRes
import com.android.systemui.display.data.repository.DisplayWindowPropertiesRepository
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.mediaprojection.permission.BaseMediaProjectionPermissionContentManager
import com.android.systemui.mediaprojection.permission.BaseMediaProjectionPermissionDialogDelegate
import com.android.systemui.mediaprojection.permission.SINGLE_APP
import com.android.systemui.mediaprojection.permission.ScreenShareMode
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingStartStopInteractor
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.statusbar.phone.SystemUIDialog
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Dialog to select screen recording options */
class ScreenRecordPermissionDialogDelegate(
    private val hostUserHandle: UserHandle,
    private val hostUid: Int,
    private val controller: ScreenRecordUxController,
    private val activityStarter: ActivityStarter,
    private val onStartRecordingClicked: Runnable?,
    private val mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
    private val systemUIDialogFactory: SystemUIDialog.Factory,
    @ScreenShareMode defaultSelectedMode: Int,
    @StyleRes private val theme: Int,
    private val context: Context,
    private val displayManager: DisplayManager,
    private val displayWindowPropertiesRepository: Lazy<DisplayWindowPropertiesRepository>,
    private val screenRecordingStartStopInteractor: ScreenRecordingStartStopInteractor,
) :
    BaseMediaProjectionPermissionDialogDelegate<SystemUIDialog>(
        ScreenRecordPermissionContentManager.createOptionList(displayManager),
        appName = null,
        hostUid = hostUid,
        mediaProjectionMetricsLogger,
        R.drawable.ic_screenrecord,
        R.color.screenrecord_icon_color,
        defaultSelectedMode,
    ),
    SystemUIDialog.Delegate {
    @AssistedInject
    constructor(
        @Assisted hostUserHandle: UserHandle,
        @Assisted hostUid: Int,
        @Assisted controller: ScreenRecordUxController,
        activityStarter: ActivityStarter,
        @Assisted onStartRecordingClicked: Runnable?,
        mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
        systemUIDialogFactory: SystemUIDialog.Factory,
        @ShadeDisplayAware context: Context,
        displayManager: DisplayManager,
        displayWindowPropertiesRepository: Lazy<DisplayWindowPropertiesRepository>,
        screenRecordingStartStopInteractor: ScreenRecordingStartStopInteractor,
    ) : this(
        hostUserHandle,
        hostUid,
        controller,
        activityStarter,
        onStartRecordingClicked,
        mediaProjectionMetricsLogger,
        systemUIDialogFactory,
        defaultSelectedMode = SINGLE_APP,
        theme = SystemUIDialog.DEFAULT_THEME,
        context,
        displayManager,
        displayWindowPropertiesRepository,
        screenRecordingStartStopInteractor,
    )

    @AssistedFactory
    interface Factory {
        fun create(
            controller: ScreenRecordUxController,
            hostUserHandle: UserHandle,
            hostUid: Int,
            onStartRecordingClicked: Runnable?,
        ): ScreenRecordPermissionDialogDelegate
    }

    override fun createContentManager(): BaseMediaProjectionPermissionContentManager {
        return ScreenRecordPermissionContentManager(
            hostUserHandle,
            hostUid,
            mediaProjectionMetricsLogger,
            defaultSelectedMode,
            displayManager,
            controller,
            activityStarter,
            onStartRecordingClicked,
            screenRecordingStartStopInteractor,
        )
    }

    override fun createDialog(): SystemUIDialog {
        val displayContext =
            if (ShadeWindowGoesAround.isEnabled) {
                // This dialog needs to stay visible even if the notification shade closes,
                // for example, after unlocking the device. If we used the shade's own context, the
                // dialog would be tied to the shade and disappear when the shade collapses.
                // To avoid this, we get a context associated with the status bar on the current
                // display.  This ensures the dialog remains visible independently of the shade.
                displayWindowPropertiesRepository
                    .get()
                    .get(context.displayId, TYPE_STATUS_BAR)
                    ?.context
                    // falls back to the default context if the status bar context is not available
                    ?: context
            } else {
                context
            }
        return systemUIDialogFactory.create(this, displayContext, theme)
    }

    override fun onCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        super<BaseMediaProjectionPermissionDialogDelegate>.onCreate(dialog, savedInstanceState)
        setDialogTitle(R.string.screenrecord_permission_dialog_title)
        dialog.setTitle(R.string.screenrecord_title)
        setStartButtonOnClickListener { v: View? ->
            val screenRecordContentManager: ScreenRecordPermissionContentManager? =
                contentManager as ScreenRecordPermissionContentManager?
            screenRecordContentManager?.startButtonOnClicked()
            dialog.dismiss()
        }
        setCancelButtonOnClickListener { dialog.dismiss() }
    }
}
