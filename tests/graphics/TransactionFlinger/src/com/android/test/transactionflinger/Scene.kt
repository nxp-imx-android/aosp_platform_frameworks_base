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

package com.android.test.transactionflinger

import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.view.Choreographer
import android.view.SurfaceControl
import java.util.concurrent.CompletableFuture

/**
 * A scene of SurfaceControls!
 */
class Scene {
    private val children = mutableListOf<Scene>()
    private var drawFunctor: (Scene.(Choreographer.FrameData) -> RenderNode?)? = null

    /**
     * Time that we first started drawing the first frame of the scene
     * Typically this is for rolling your own animations
     */
    var startTime = 0L
        private set

    /**
     * SurfaceControl that will contain the content for this scene on the display
     */
    val surfaceControl: SurfaceControl =
        SurfaceControl.Builder().setName("scene").setHidden(true).build()

    /**
     * Width in pixels
     */
    var width = 0

    /**
     * Height in pixels
     */
    var height = 0

    /**
     * Adds a child scene
     */
    fun scene(init: Scene.() -> Unit): Scene {
        val scene = Scene()
        scene.init()
        children.add(scene)
        return scene
    }

    /**
     * Specifies a function that will instruct this scene node to draw content
     */
    fun content(draw: Scene.(Choreographer.FrameData) -> RenderNode?) {
        drawFunctor = draw
    }

    /**
     * Draw the scene and its children, and accumulate updates into the provided transaction
     */
    fun onDraw(
        data: Choreographer.FrameData,
        transaction: SurfaceControl.Transaction
    ): CompletableFuture<Void> {
        if (startTime == 0L) {
            startTime = data.preferredFrameTimeline.deadlineNanos
            synchronized(transaction) {
                for (child in children) {
                    transaction.reparent(child.surfaceControl, surfaceControl)
                }
            }
        }
        val futuresList: MutableList<CompletableFuture<Void>> = mutableListOf()
        drawFunctor?.invoke(this@Scene, data)?.let { node ->
            val drawFuture = CompletableFuture<Void>()
            futuresList.add(drawFuture)
            val buffer = HardwareBuffer.create(
                width, height, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_COMPOSER_OVERLAY or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                        or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
            )

            val renderer = HardwareBufferRenderer(buffer)
            renderer.setContentRoot(node)
            renderer.obtainRenderRequest()
                .setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                .draw(
                    Runnable::run
                ) {
                    synchronized(transaction) {
                        transaction.setBuffer(surfaceControl, buffer, it.fence).setVisibility(
                            surfaceControl, true
                        )
                    }
                    drawFuture.complete(null)
                }
        }
        futuresList.addAll(children.asSequence()
            .map { it.onDraw(data, transaction) }
            .toList())

        return CompletableFuture<Void>.allOf(*futuresList.toTypedArray())
    }
}

/**
 * Creates a root level Scene.
 * Oh no, a DSL.
 */
fun scene(init: Scene.() -> Unit): Scene {
    val scene = Scene()
    scene.init()
    return scene
}