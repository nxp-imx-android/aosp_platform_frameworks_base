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

package com.android.systemui.screencapture.ui

import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.content.Intent
import android.media.projection.IMediaProjection
import android.media.projection.IMediaProjectionManager.EXTRA_USER_REVIEW_GRANTED_CONSENT
import android.media.projection.MediaProjectionManager.EXTRA_MEDIA_PROJECTION
import android.media.projection.ReviewGrantedConsentResult.RECORD_CONTENT_TASK
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.mediaprojection.MediaProjectionServiceHelper
import com.android.systemui.screencapture.common.ScreenCaptureComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.util.AsyncActivityLauncher
import javax.inject.Inject

/**
 * An activity that hosts the pre screen share UI, started from MediaProjectionPermissionActivity.
 */
class ShareScreenActivity
@Inject
constructor(
    private val builder: ScreenCaptureComponent.Builder,
    private val mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
    private val asyncActivityLauncher: AsyncActivityLauncher,
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = intent.getIntExtra(EXTRA_HOST_APP_UID, -1)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val reviewGrantedConsentRequired =
            intent.getBooleanExtra(EXTRA_USER_REVIEW_GRANTED_CONSENT, false)
        val hostUserHandle: UserHandle? = intent.getParcelableExtra(EXTRA_HOST_APP_USER_HANDLE)

        if (uid == -1 || hostUserHandle == null || packageName == null) {
            Log.d(
                TAG,
                "Invalid intent extras: uid=$uid, hostUserHandle=$hostUserHandle, packageName=$packageName",
            )
            finish()
            return
        }

        mediaProjectionMetricsLogger.notifyPermissionRequestDisplayed(uid)

        val parameters =
            ScreenCaptureUiParameters.ShareScreen(
                onApprovedCallback = { taskId ->
                    onTaskSelected(taskId, reviewGrantedConsentRequired, hostUserHandle)
                },
                hostAppUserHandle = hostUserHandle,
            )
        val component = builder.setScope(lifecycleScope).setParameters(parameters).build()

        setContent {
            PlatformTheme {
                val scope = rememberCoroutineScope()
                val uiComponent =
                    remember(scope, parameters) {
                        component
                            .uiComponentBuilders()[ScreenCaptureType.SHARE_SCREEN]
                            ?.setScope(scope)
                            ?.setDisplay(display)
                            ?.setWindow(window)
                            ?.build()
                            ?: error("No UI builder for ${ScreenCaptureType.SHARE_SCREEN}")
                    }

                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    uiComponent.screenCaptureContent.Content()
                }
            }
        }
    }

    private fun onTaskSelected(
        taskId: Int,
        reviewGrantedConsentRequired: Boolean,
        hostUserHandle: UserHandle,
    ) {
        try {
            // Get the task info for the selected task.
            val recentTasks = ActivityTaskManager.getInstance().getTasks(RUNNING_TASKS_NUM_MAX)
            val taskInfo = recentTasks.firstOrNull { it.taskId == taskId }

            if (taskInfo == null) {
                // The task is no longer running, so we can't share it.
                Log.w(TAG, "Task info not found for taskId: $taskId")
                finishAsCancelled()
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
                    val mediaProjectionBinder = intent.getIBinderExtra(EXTRA_MEDIA_PROJECTION)
                    val projection = IMediaProjection.Stub.asInterface(mediaProjectionBinder)

                    // Configure the projection to capture a specific task using the same
                    // cookie.
                    projection.setLaunchCookie(launchCookie)
                    projection.taskId = taskId
                    val intent = Intent()
                    intent.putExtra(EXTRA_MEDIA_PROJECTION, projection.asBinder())
                    setResult(RESULT_OK, intent)
                    setForceSendResultForMediaProjection()
                    MediaProjectionServiceHelper.setReviewedConsentIfNeeded(
                        RECORD_CONTENT_TASK,
                        reviewGrantedConsentRequired,
                        projection,
                    )
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Error granting projection permission for task", e)
                    finishAsCancelled()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error granting projection permission for task", e)
            finishAsCancelled()
        }
    }

    private fun finishAsCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_HOST_APP_UID = "launched_from_host_uid"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_HOST_APP_USER_HANDLE = "launched_from_user_handle"

        private const val TAG = "ShareScreenActivity"

        private const val RUNNING_TASKS_NUM_MAX = 100
        private const val MEDIA_PROJECTION_LAUNCH_TOKEN = "media_projection_launch_token"
    }
}
