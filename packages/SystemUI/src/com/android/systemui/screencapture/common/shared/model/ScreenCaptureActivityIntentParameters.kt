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

package com.android.systemui.screencapture.common.shared.model

import android.content.Intent
import android.os.IBinder
import android.os.ResultReceiver
import android.os.UserHandle

private const val EXTRA_SCREEN_CAPTURE_TYPE = "screen_share_type"
private const val EXTRA_IS_USER_CONSENT_REQUIRED = "is_user_consent_required"
private const val EXTRA_MEDIA_PROJECTION = "media_projection"
private const val EXTRA_RESULT_RECEIVER = "result_receiver"
private const val EXTRA_HOST_APP_USER_HANDLE = "launched_from_user_handle"
private const val EXTRA_HOST_APP_UID = "launched_from_host_uid"

data class ScreenCaptureActivityIntentParameters(
    val screenCaptureType: ScreenCaptureType,
    val isUserConsentRequired: Boolean,
    val resultReceiver: ResultReceiver?,
    val mediaProjection: IBinder?,
    val hostAppUserHandle: UserHandle,
    val hostAppUid: Int,
) {

    fun fillIntent(intent: Intent) {
        with(intent) {
            putExtra(EXTRA_SCREEN_CAPTURE_TYPE, screenCaptureType)
            putExtra(EXTRA_IS_USER_CONSENT_REQUIRED, isUserConsentRequired)
            putExtra(EXTRA_RESULT_RECEIVER, resultReceiver)
            putExtra(EXTRA_MEDIA_PROJECTION, mediaProjection)
            putExtra(EXTRA_HOST_APP_USER_HANDLE, hostAppUserHandle)
            putExtra(EXTRA_HOST_APP_UID, hostAppUid)
        }
    }

    companion object {

        fun fromIntent(intent: Intent): ScreenCaptureActivityIntentParameters {
            val hostAppId =
                if (intent.hasExtra(EXTRA_HOST_APP_UID)) {
                    intent.getIntExtra(EXTRA_HOST_APP_UID, -1)
                } else {
                    error("EXTRA_HOST_APP_UID is missing")
                }
            return ScreenCaptureActivityIntentParameters(
                screenCaptureType =
                    intent.getSerializableExtra(EXTRA_SCREEN_CAPTURE_TYPE) as? ScreenCaptureType
                        ?: error("EXTRA_SCREEN_CAPTURE_TYPE is missing"),
                isUserConsentRequired =
                    intent.getBooleanExtra(EXTRA_IS_USER_CONSENT_REQUIRED, false),
                hostAppUserHandle =
                    intent.getParcelableExtra(EXTRA_HOST_APP_USER_HANDLE, UserHandle::class.java)
                        ?: error("EXTRA_HOST_APP_USER_HANDLE is missing"),
                hostAppUid = hostAppId,
                resultReceiver =
                    intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java),
                mediaProjection = intent.getIBinderExtra(EXTRA_MEDIA_PROJECTION),
            )
        }
    }
}
