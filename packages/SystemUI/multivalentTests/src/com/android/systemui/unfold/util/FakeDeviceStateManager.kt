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

package com.android.systemui.unfold.util

import android.hardware.devicestate.DeviceState
import android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY
import android.hardware.devicestate.DeviceStateManager
import android.hardware.devicestate.DeviceStateManager.DeviceStateCallback
import com.android.systemui.kosmos.Kosmos
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

val Kosmos.fakeDeviceStateManager by Kosmos.Fixture { FakeDeviceStateManager() }

class FakeDeviceStateManager {
    val deviceStateManager: DeviceStateManager = mock()

    private val listeners = arrayListOf<DeviceStateCallback>()

    init {
        whenever(deviceStateManager.registerCallback(any(), any())).thenAnswer {
            val deviceStateCallback =
                it.arguments[1] as? DeviceStateCallback ?: return@thenAnswer Unit
            listeners.add(deviceStateCallback)
        }
    }

    fun fold() {
        val foldDeviceStateConfiguration =
            DeviceState.Configuration.Builder(/* identifier= */ 0, "FOLDED")
                .setPhysicalProperties(setOf(PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY))
                .build()

        sendDeviceState(DeviceState(foldDeviceStateConfiguration))
    }

    fun sendDeviceState(deviceState: DeviceState) {
        listeners.forEach { it.onDeviceStateChanged(deviceState) }
    }
}
