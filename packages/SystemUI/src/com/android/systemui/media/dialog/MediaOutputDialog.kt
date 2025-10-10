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

package com.android.systemui.media.dialog

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import com.android.internal.logging.UiEventLogger
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.broadcast.BroadcastSender

/**
 * TODO: b/446971164 - Remove this class. It is temporary and exists only to preserve git history.
 */
open class MediaOutputDialog(
    context: Context,
    aboveStatusBar: Boolean,
    broadcastSender: BroadcastSender,
    mediaSwitchingController: MediaSwitchingController,
    dialogTransitionAnimator: DialogTransitionAnimator,
    uiEventLogger: UiEventLogger,
    includePlaybackAndAppMetadata: Boolean,
    onDialogEventListener: OnDialogEventListener?
) :
    MediaOutputBaseDialog(
        context,
        aboveStatusBar,
        broadcastSender,
        mediaSwitchingController,
        dialogTransitionAnimator,
        uiEventLogger,
        includePlaybackAndAppMetadata,
        onDialogEventListener
    ) {

    /** Callback for configuration changes .  */
    interface OnDialogEventListener {
        /** Will be called inside onConfigurationChanged.  */
        fun onConfigurationChanged(dialog: Dialog, newConfig: Configuration)

        /** Will be called when the dialog is created.  */
        fun onCreate(dialog: Dialog)
    }
}
