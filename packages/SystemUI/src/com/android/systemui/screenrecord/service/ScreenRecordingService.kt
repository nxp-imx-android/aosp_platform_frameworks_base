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

package com.android.systemui.screenrecord.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.android.systemui.screenrecord.RecordingServiceStrings
import com.android.systemui.screenrecord.notification.NotificationInteractor
import com.android.systemui.screenrecord.notification.ScreenRecordingServiceNotificationInteractor

private const val TAG = "ScreenRecordingService"
private const val CHANNEL_ID = "screen_record"

open class ScreenRecordingService(
    private val tag: String,
    private val createNotificationInteractor: Context.() -> NotificationInteractor,
    private val onRecordingSaved: ScreenRecordingService.() -> Unit,
) : ComponentService() {

    @Suppress("unused") // used by the system
    constructor() :
        this(
            tag = TAG,
            createNotificationInteractor = {
                ScreenRecordingServiceNotificationInteractor(
                    context = this,
                    notificationManager = getSystemService(NotificationManager::class.java)!!,
                    strings = RecordingServiceStrings(resources),
                    channelId = CHANNEL_ID,
                    tag = TAG,
                    serviceClass = ScreenRecordingService::class.java,
                )
            },
            onRecordingSaved = {},
        )

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    companion object {

        const val ACTION_STOP =
            "com.android.systemui.screenrecord.ScreenRecordingService.ACTION_STOP"
        const val ACTION_SHARE =
            "com.android.systemui.screenrecord.ScreenRecordingService.ACTION_SHARE"
        const val EXTRA_STOP_REASON =
            "com.android.systemui.screenrecord.ScreenRecordingService.EXTRA_STOP_REASON"
    }
}
