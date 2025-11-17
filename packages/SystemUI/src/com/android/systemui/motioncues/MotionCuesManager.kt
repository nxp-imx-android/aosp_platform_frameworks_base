/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.motioncues

import android.app.motioncues.IMotionCuesCallback
import android.app.motioncues.MotionCuesData
import android.app.motioncues.MotionCuesService.EXTRA_API_CALLBACK
import android.app.motioncues.MotionCuesSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.android.systemui.statusbar.CommandQueue
import javax.inject.Inject

/**
 * Manages the connection to the MotionCuesService and implements the CommandQueue callbacks to
 * start and end the motion cues session.
 */
class MotionCuesManager @Inject constructor(private val context: Context) : CommandQueue.Callbacks {
    private var motionCuesSettings: MotionCuesSettings? = null
    private val motionCuesCallback = MotionCuesCallbackImpl()

    private val motionCuesConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                // Handle service connected
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // Handle service disconnected
            }
        }

    override fun startMotionCuesSession(
        componentName: ComponentName,
        motionCuesSettings: MotionCuesSettings,
    ) {
        this.motionCuesSettings = motionCuesSettings

        val motionCuesIntent =
            Intent().apply {
                component = componentName
                putExtra(EXTRA_API_CALLBACK, motionCuesCallback.asBinder())
            }

        context.bindService(motionCuesIntent, motionCuesConnection, Context.BIND_AUTO_CREATE)
    }

    override fun endMotionCuesSession() {
        // End drawing
        context.unbindService(motionCuesConnection)
    }

    private inner class MotionCuesCallbackImpl : IMotionCuesCallback.Stub() {
        override fun updateBubblePixelPos(dx: Float, dy: Float) {
            // TODO: Implement the logic to draw/update bubbles in SystemUI's overlay
        }

        override fun updateMotionCuesData(motionCuesData: MotionCuesData) {
            // TODO: Update bubble visuals based on MotionCuesData
        }
    }
}
