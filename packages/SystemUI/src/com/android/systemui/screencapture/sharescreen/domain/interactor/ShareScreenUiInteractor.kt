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

package com.android.systemui.screencapture.sharescreen.domain.interactor

import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.media.projection.IMediaProjection
import android.media.projection.ReviewGrantedConsentResult.RECORD_CONTENT_TASK
import android.os.UserHandle
import android.util.Log
import com.android.systemui.mediaprojection.MediaProjectionServiceHelper
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.util.AsyncActivityLauncher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@ScreenCaptureUiScope
class ShareScreenUiInteractor
@Inject
constructor(
    private val activityTaskManager: ActivityTaskManager,
    private val asyncActivityLauncher: AsyncActivityLauncher,
) {

    enum class SharingState {
        NotStarted,
        Approved,
        Denied,
    }

    private val _sharingState = MutableStateFlow(SharingState.NotStarted)
    val sharingState = _sharingState.asStateFlow()

    private var mediaProjection: IMediaProjection? = null
    private var reviewGrantedConsentRequired: Boolean = false
    private lateinit var hostUserHandle: UserHandle

    fun initialize(
        mediaProjection: IMediaProjection?,
        reviewGrantedConsentRequired: Boolean,
        hostUserHandle: UserHandle,
    ) {
        this.mediaProjection = mediaProjection
        this.reviewGrantedConsentRequired = reviewGrantedConsentRequired
        this.hostUserHandle = hostUserHandle
    }

    fun onScreenSharingApproved(taskId: Int) {
        val projection = mediaProjection
        if (projection == null) {
            Log.e(TAG, "Media projection is null")
            _sharingState.value = SharingState.Denied
            return
        }

        try {
            // TODO(b/423708481) Check whether [ScreenCaptureRecentTaskInteractor] can be used
            // instead.
            // Get the task info for the selected task.
            val recentTasks = activityTaskManager.getTasks(RUNNING_TASKS_NUM_MAX)
            val taskInfo = recentTasks.firstOrNull { it.taskId == taskId }

            if (taskInfo == null) {
                // The task is no longer running, so we can't share it.
                Log.w(TAG, "Task info not found for taskId: $taskId")
                _sharingState.value = SharingState.Denied
                return
            }

            // Create a new LaunchCookie and ActivityOptions to perform the security handshake.
            val launchCookie = ActivityOptions.LaunchCookie(MEDIA_PROJECTION_LAUNCH_TOKEN)
            val options = ActivityOptions.makeBasic()
            options.launchCookie = launchCookie.binder

            // Bring the task to be captured to the front using the new cookie, and finish this
            // activity in the callback once the app is started.
            asyncActivityLauncher.startActivityAsUser(
                taskInfo.baseIntent,
                hostUserHandle,
                options.toBundle(),
            ) {
                try {
                    // Configure the projection to capture a specific task using the same
                    // cookie.
                    projection.setLaunchCookie(launchCookie)
                    projection.taskId = taskId
                    MediaProjectionServiceHelper.setReviewedConsentIfNeeded(
                        RECORD_CONTENT_TASK,
                        reviewGrantedConsentRequired,
                        projection,
                    )
                    _sharingState.value = SharingState.Approved
                } catch (e: Exception) {
                    Log.e(TAG, "Error granting projection permission for task", e)
                    _sharingState.value = SharingState.Denied
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error granting projection permission for task", e)
            _sharingState.value = SharingState.Denied
        }
    }

    fun onClose() {
        _sharingState.value = SharingState.Denied
    }

    companion object {
        private const val TAG = "ShareScreenUiInteractor"
        private const val RUNNING_TASKS_NUM_MAX = 100
        private const val MEDIA_PROJECTION_LAUNCH_TOKEN = "media_projection_launch_token"
    }
}
