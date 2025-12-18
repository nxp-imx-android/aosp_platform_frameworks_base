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

import android.app.ActivityManager
import android.app.ActivityOptions
import android.media.projection.IMediaProjection
import android.media.projection.ReviewGrantedConsentResult
import android.os.UserHandle
import android.util.Log
import com.android.systemui.mediaprojection.MediaProjectionServiceHelper
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureRecentTaskInteractor
import com.android.systemui.util.AsyncActivityLauncher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

@ScreenCaptureUiScope
class ShareScreenUiInteractor
@Inject
constructor(
    private val recentTaskInteractor: ScreenCaptureRecentTaskInteractor,
    private val asyncActivityLauncher: AsyncActivityLauncher,
) {

    sealed class SharingState {
        object NotStarted : SharingState()

        data class Approved(val projection: IMediaProjection) : SharingState()

        object Denied : SharingState()
    }

    private val _sharingState = MutableStateFlow<SharingState>(SharingState.NotStarted)
    val sharingState = _sharingState.asStateFlow()

    private var reviewGrantedConsentRequired: Boolean = false
    private lateinit var hostUserHandle: UserHandle
    var uid: Int = -1
        private set

    private lateinit var packageName: String
    private var initialDisplayId: Int = -1

    fun initialize(
        reviewGrantedConsentRequired: Boolean,
        hostUserHandle: UserHandle,
        uid: Int,
        packageName: String,
        initialDisplayId: Int,
    ) {
        this.reviewGrantedConsentRequired = reviewGrantedConsentRequired
        this.hostUserHandle = hostUserHandle
        this.uid = uid
        this.packageName = packageName
        this.initialDisplayId = initialDisplayId
    }

    fun onAppContentSharingApproved(contentId: Int) {
        // TODO(b/423708479) Finish the flow to support app content sharing.
    }

    suspend fun onAppSharingApproved(taskId: Int) {
        try {
            val projection =
                MediaProjectionServiceHelper.createOrReuseProjection(
                    uid,
                    packageName,
                    reviewGrantedConsentRequired,
                    initialDisplayId,
                )

            val recentTasks = recentTaskInteractor.recentTasks.first()
            val task = recentTasks.firstOrNull { it.taskId == taskId }

            if (task == null || task.baseIntent == null) {
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
                task.baseIntent,
                hostUserHandle,
                options.toBundle(),
            ) { waitResult ->
                if (
                    waitResult.result == ActivityManager.START_SUCCESS ||
                        waitResult.result == ActivityManager.START_TASK_TO_FRONT
                ) {
                    try {
                        // Configure the projection to capture a specific task using the same
                        // cookie.
                        projection.setLaunchCookie(launchCookie)
                        projection.taskId = taskId
                        MediaProjectionServiceHelper.setReviewedConsentIfNeeded(
                            ReviewGrantedConsentResult.RECORD_CONTENT_TASK,
                            reviewGrantedConsentRequired,
                            projection,
                        )
                        _sharingState.value = SharingState.Approved(projection)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error granting projection permission for task", e)
                        _sharingState.value = SharingState.Denied
                    }
                } else {
                    Log.w(
                        TAG,
                        "Failed to launch activity for task: taskId=$taskId, result=" +
                            "${waitResult.result}",
                    )
                    _sharingState.value = SharingState.Denied
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error granting projection permission for task", e)
            _sharingState.value = SharingState.Denied
        }
    }

    fun onDisplaySharingApproved(displayId: Int) {
        val projection =
            MediaProjectionServiceHelper.createOrReuseProjection(
                uid,
                packageName,
                reviewGrantedConsentRequired,
                displayId,
            )
        _sharingState.value = SharingState.Approved(projection)
    }

    fun onClose() {
        _sharingState.value = SharingState.Denied
    }

    companion object {
        private const val TAG = "ShareScreenUiInteractor"
        private const val MEDIA_PROJECTION_LAUNCH_TOKEN = "media_projection_launch_token"
    }
}
