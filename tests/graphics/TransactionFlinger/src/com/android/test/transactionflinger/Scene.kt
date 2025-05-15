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
import android.graphics.Paint
import android.graphics.Rect
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
    private var drawFunctor: (Scene.(Choreographer.FrameData, Int, Int) -> RenderNode?)? = null
    private var propertiesFunctor: (Scene.(Choreographer.FrameData) -> Unit)? = null

    /** Radius of a blur applied to content behind this scene */
    var backgroundBlurRadius = 0

    /** Location of the top-left position in the x-direction of this scene, on a range of [0, 1] */
    var x = 0.0

    /** Location of the top-left position in the y-direction of this scene, on a range of [0, 1] */
    var y = 0.0

    /** Width of the scene normalized on [0, 1] */
    var width = 1.0

    /** Height of the scene normalized on [0, 1] */
    var height = 1.0
    var startTime = 0L
        private set

    val surfaceControl: SurfaceControl =
        SurfaceControl.Builder().setName("scene").setHidden(true).build()

    fun properties(functor: Scene.(Choreographer.FrameData) -> Unit) {
        propertiesFunctor = functor
    }

    /**
     * Adds a child scene
     */
    fun scene(init: Scene.() -> Unit): Scene {
        val scene = Scene()
        scene.init()
        children.add(scene)
        return scene
    }

    fun content(draw: Scene.(Choreographer.FrameData, Int, Int) -> RenderNode?) {
        drawFunctor = draw
    }

    fun drawAndSubmit(data: Choreographer.FrameData, width: Int, height: Int) {
        val transaction = SurfaceControl.Transaction()
        synchronized(transaction) {
            transaction.setVisibility(surfaceControl, true)
        }
        onDraw(data, transaction, width, height).get()
        synchronized(transaction) {
            transaction.apply()
        }
    }


    private fun onDraw(
        data: Choreographer.FrameData,
        transaction: SurfaceControl.Transaction,
        parentWidth: Int,
        parentHeight: Int
    ): CompletableFuture<Void> {
        if (startTime == 0L) {
            startTime = data.preferredFrameTimeline.deadlineNanos
            synchronized(transaction) {
                transaction.setPosition(
                    surfaceControl,
                    (parentWidth * x).toFloat(),
                    (parentHeight * y).toFloat()
                )
                for (child in children) {
                    transaction.reparent(child.surfaceControl, surfaceControl)
                }
            }
        }

        propertiesFunctor?.let {
            it.invoke(this@Scene, data)
            synchronized(transaction) {
                transaction.setBackgroundBlurRadius(surfaceControl, backgroundBlurRadius)
            }
        }

        val physicalWidth = (parentWidth * width).toInt()
        val physicalHeight = (parentHeight * height).toInt()
        val futuresList: MutableList<CompletableFuture<Void>> = mutableListOf()

        drawFunctor?.invoke(this@Scene, data, physicalWidth, physicalHeight)?.let { node ->
            val drawFuture = CompletableFuture<Void>()
            futuresList.add(drawFuture)
            val buffer = HardwareBuffer.create(
                physicalWidth, physicalHeight, HardwareBuffer.RGBA_8888, 1,
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
                    // We could instead wrap the rendering result into a payload that we then
                    // dispatch to the main thread, but a lock is easier to write :)
                    synchronized(transaction) {
                        transaction.setBuffer(surfaceControl, buffer, it.fence).setVisibility(
                            surfaceControl, true
                        )
                    }
                    drawFuture.complete(null)
                }
        }


        futuresList.addAll(children.asSequence()
            .map { it.onDraw(data, transaction, physicalWidth, physicalHeight) }
            .toList())

        return CompletableFuture<Void>.allOf(*futuresList.toTypedArray())
    }

    fun drawColor(color: Int, data: Choreographer.FrameData, width: Int, height: Int): RenderNode? {
        if (startTime < data.preferredFrameTimeline.deadlineNanos) {
            return null
        }
        val renderNode = RenderNode("cogsapp")
        renderNode.setPosition(Rect(0, 0, width, height))
        val paint = Paint()
        paint.color = color
        renderNode.beginRecording(width, height).drawPaint(paint)
        renderNode.endRecording()
        return renderNode
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