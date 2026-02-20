/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.Context
import android.media.session.MediaSession
import android.os.UserHandle
import android.view.View
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.logging.UiEventLogger
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.broadcast.BroadcastSender
import javax.inject.Inject

/** Manager to create and show a [MediaOutputDialog]. */
open class MediaOutputDialogManager
@Inject
constructor(
    private val context: Context,
    private val broadcastSender: BroadcastSender,
    private val uiEventLogger: UiEventLogger,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val mediaSwitchingControllerFactory: MediaSwitchingController.Factory,
) {
    companion object {
        const val INTERACTION_JANK_TAG = "media_output"
        var mediaOutputDialog: MediaOutputDialog? = null
    }

    /** Creates a [MediaOutputDialog] for the given package. */
    // TODO: b/321969740 - Make the userHandle non-optional, and place the parameter next to the
    // package name. The user handle is necessary to disambiguate the same package running on
    // different users.
    open fun createAndShow(
        packageName: String,
        aboveStatusBar: Boolean,
        view: View? = null,
        userHandle: UserHandle? = null,
        token: MediaSession.Token? = null,
        useSystemColors: Boolean = false,
    ) {
        createAndShowWithController(
            packageName,
            aboveStatusBar,
            controller =
                view?.let {
                    DialogTransitionAnimator.Controller.fromView(
                        it,
                        DialogCuj(
                            InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN,
                            INTERACTION_JANK_TAG,
                        ),
                    )
                },
            userHandle = userHandle,
            token = token,
            useSystemColors = useSystemColors,
        )
    }

    /** Creates a [MediaOutputDialog] for the given package. */
    // TODO: b/321969740 - Make the userHandle non-optional, and place the parameter next to the
    // package name. The user handle is necessary to disambiguate the same package running on
    // different users.
    open fun createAndShowWithController(
        packageName: String,
        aboveStatusBar: Boolean,
        controller: DialogTransitionAnimator.Controller?,
        userHandle: UserHandle? = null,
        token: MediaSession.Token? = null,
        onDialogEventListener: MediaOutputDialog.OnDialogEventListener? = null,
        mediaSwitchingType: MediaSwitchingType? = null,
        useSystemColors: Boolean = false,
    ) {
        createAndShow(
            packageName,
            aboveStatusBar,
            dialogTransitionAnimatorController = controller,
            includePlaybackAndAppMetadata = true,
            userHandle = userHandle,
            token = token,
            onDialogEventListener = onDialogEventListener,
            mediaSwitchingType = mediaSwitchingType,
            useSystemColors = useSystemColors,
        )
    }

    open fun createAndShowForSystemRouting(
        controller: DialogTransitionAnimator.Controller? = null,
        onDialogEventListener: MediaOutputDialog.OnDialogEventListener? = null,
        mediaSwitchingType: MediaSwitchingType? = null,
    ) {
        createAndShow(
            packageName = null,
            aboveStatusBar = false,
            dialogTransitionAnimatorController = controller,
            includePlaybackAndAppMetadata = false,
            userHandle = null,
            onDialogEventListener = onDialogEventListener,
            mediaSwitchingType = mediaSwitchingType,
            useSystemColors = true,
        )
    }

    // TODO: b/321969740 - Make the userHandle non-optional, and place the parameter next to the
    // package name. The user handle is necessary to disambiguate the same package running on
    // different users.
    private fun createAndShow(
        packageName: String?,
        aboveStatusBar: Boolean,
        dialogTransitionAnimatorController: DialogTransitionAnimator.Controller?,
        includePlaybackAndAppMetadata: Boolean = true,
        userHandle: UserHandle? = null,
        token: MediaSession.Token? = null,
        onDialogEventListener: MediaOutputDialog.OnDialogEventListener? = null,
        mediaSwitchingType: MediaSwitchingType? = null,
        useSystemColors: Boolean = false,
    ) {
        // Dismiss the previous dialog, if any.
        mediaOutputDialog?.dismiss()

        val controller =
            mediaSwitchingControllerFactory.create(
                packageName,
                userHandle,
                token,
                mediaSwitchingType,
            )

        val mediaOutputDialog =
            MediaOutputDialog(
                context,
                aboveStatusBar,
                broadcastSender,
                controller,
                dialogTransitionAnimator,
                uiEventLogger,
                includePlaybackAndAppMetadata,
                onDialogEventListener,
                useSystemColors,
            )

        // Show the dialog.
        if (dialogTransitionAnimatorController != null) {
            dialogTransitionAnimator.show(mediaOutputDialog, dialogTransitionAnimatorController)
        } else {
            mediaOutputDialog.show()
        }
    }

    /** dismiss [MediaOutputDialog] if exist. */
    open fun dismiss() {
        mediaOutputDialog?.dismiss()
        mediaOutputDialog = null
    }
}
