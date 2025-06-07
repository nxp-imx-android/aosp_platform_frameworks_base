/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.test.transactionflinger.activities

import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.os.Bundle
import android.view.Choreographer
import android.view.Choreographer.VsyncCallback
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import android.view.WindowInsets
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Trivial activity. Not very interesting.
 */
class TrivialActivity : ComponentActivity(), SurfaceHolder.Callback, VsyncCallback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var sceneSurfaceControl: SurfaceControl
    private lateinit var choroegrapher: Choreographer
    private var startTime = 0L
    private var width = 0
    private var height = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the system bars. Ain't dealing with this when we actually start setting up a scene
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsets.Type.systemBars())
        actionBar?.hide()

        choroegrapher = Choreographer.getInstance()
        setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    surfaceView = SurfaceView(context).apply {
                        holder.addCallback(this@TrivialActivity)
                    }
                    surfaceView
                }
            )
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        this.width = width
        this.height = height
        sceneSurfaceControl = SurfaceControl.Builder().setBufferSize(width, height).setHidden(true)
            .setParent(surfaceView.surfaceControl).setName("cogsapp").build()
        choroegrapher.postVsyncCallback(this)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onVsync(data: Choreographer.FrameData) {
        if (startTime == 0L) {
            startTime = data.preferredFrameTimeline.deadlineNanos
        }

        val animationTime =
            ((data.preferredFrameTimeline.deadlineNanos - startTime) % 2.seconds.inWholeNanoseconds).nanoseconds

        val red = if (animationTime < 1.seconds) {
            (animationTime.inWholeMilliseconds * 255.0 / 1.seconds.inWholeMilliseconds).toInt()
        } else {
            ((2.seconds - animationTime).inWholeMilliseconds * 255.0 / 1.seconds.inWholeMilliseconds).toInt()
        }

        val renderNode = RenderNode("cogsapp")
        renderNode.setPosition(Rect(0, 0, width, height))
        val paint = Paint()
        paint.color = Color.argb(255, red, 0, 0)
        renderNode.beginRecording(width, height).drawPaint(paint)
        renderNode.endRecording()

        // TODO: use a pool of buffers
        val buffer = HardwareBuffer.create(
            width, height, HardwareBuffer.RGBA_8888, 1,
            HardwareBuffer.USAGE_COMPOSER_OVERLAY or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                    or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        )

        val renderer = HardwareBufferRenderer(buffer)
        renderer.setContentRoot(renderNode)
        renderer.obtainRenderRequest().setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB)).draw(
            Runnable::run
        ) {
            SurfaceControl.Transaction()
                .setBuffer(sceneSurfaceControl, buffer, it.fence)
                .setVisibility(
                    sceneSurfaceControl, true
                )
                .apply()
            choroegrapher.postVsyncCallback(this@TrivialActivity)
        }
    }
}