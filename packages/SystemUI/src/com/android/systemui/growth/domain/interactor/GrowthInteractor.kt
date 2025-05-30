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

package com.android.systemui.growth.domain.interactor

import android.content.ComponentName
import android.content.Intent
import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.res.R
import dagger.Lazy
import javax.inject.Inject

/** Interactor to communicate with the growth app. */
@SysUISingleton
class GrowthInteractor
@Inject
constructor(
    @Main private val resources: Resources,
    private val deviceEntryInteractor: Lazy<DeviceEntryInteractor>,
    private val broadcastSender: BroadcastSender,
) : ExclusiveActivatable() {
    private val growthAppPackageName =
        resources.getString(R.string.config_growthAppPackageName)
    private val growthReceiverClassName =
        resources.getString(R.string.config_growthReceiverClassName)
    private val growthReceiverPermission =
        resources.getString(R.string.config_growthReceiverPermission)

    override suspend fun onActivated(): Nothing {
        deviceEntryInteractor.get().isDeviceEnteredDirectly.collect {
            if (it) {
                // Broadcast the device entered event to the growth app.
                val intent = Intent().apply { setAction(ACTION_DEVICE_ENTERED_DIRECTLY) }
                if (growthAppPackageName.isNotEmpty() && growthReceiverClassName.isNotEmpty()) {
                    intent.setPackage(growthAppPackageName)
                    intent.setComponent(ComponentName(growthAppPackageName, growthReceiverClassName))
                }

                if (growthReceiverPermission.isNotEmpty()) {
                    broadcastSender.sendBroadcast(intent, growthReceiverPermission)
                } else {
                    broadcastSender.sendBroadcast(intent)
                }
            }
        }
    }

    companion object {
        @VisibleForTesting
        const val ACTION_DEVICE_ENTERED_DIRECTLY =
            "com.android.systemui.growth.action.DEVICE_ENTERED_DIRECTLY"
    }
}