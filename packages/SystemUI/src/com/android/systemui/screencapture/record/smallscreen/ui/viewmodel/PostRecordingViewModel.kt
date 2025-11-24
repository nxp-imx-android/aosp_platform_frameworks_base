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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.service.ActivityStartingReceiver
import com.android.systemui.screenrecord.shared.model.ScreenRecording
import com.android.systemui.settings.UserTracker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private const val MIME_TYPE = "video/mp4"

class PostRecordingViewModel
@AssistedInject
constructor(
    @Assisted val videoUri: Uri,
    private val context: Context,
    private val broadcastSender: BroadcastSender,
    private val userTracker: UserTracker,
    private val drawableLoaderViewModel: DrawableLoaderViewModel,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModel {

    val isVideoSaved: Boolean by
        screenRecordingServiceInteractor.screenRecordings
            .filter { it.uri == videoUri }
            .map { it is ScreenRecording.Saved }
            .hydratedStateOf("PostRecordingViewModel#screenRecording", false)

    fun retake() {
        screenCaptureUiInteractor.show(ScreenCaptureUiParameters.Record())
    }

    fun edit() {
        startVideoActivity(
            action = Intent.ACTION_EDIT,
            label = context.getString(R.string.screen_record_edit),
            shouldShowChooser = false,
        )
    }

    fun share() {
        startVideoActivity(
            action = Intent.ACTION_SEND,
            label = context.getString(R.string.screenrecord_share_label),
            shouldShowChooser = true,
        )
    }

    private fun startVideoActivity(action: String, label: String, shouldShowChooser: Boolean) {
        val intent =
            Intent(action)
                .setDataAndType(videoUri, MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .putExtra(Intent.EXTRA_STREAM, videoUri)
                .let { intent ->
                    if (shouldShowChooser) {
                        Intent.createChooser(intent, label)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    } else {
                        intent
                    }
                }

        broadcastSender.sendBroadcastAsUser(
            intent = ActivityStartingReceiver.wrapIntent(context, intent),
            userHandle = userTracker.userHandle,
        )
    }

    @AssistedFactory
    interface Factory {

        fun create(videoUri: Uri): PostRecordingViewModel
    }
}
