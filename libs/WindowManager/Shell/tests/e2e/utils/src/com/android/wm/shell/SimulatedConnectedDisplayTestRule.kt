/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell

import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A TestRule to manage multiple simulated connected overlay displays.
 */
class SimulatedConnectedDisplayTestRule : TestRule {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val addedDisplays = mutableListOf<Int>()

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } finally {
                    teardown()
                }
            }
        }

    private fun teardown() {
        cleanupTestDisplays()
    }

    /**
     * Adds multiple overlay displays with specified dimensions. Any existing overlay displays
     * will be removed before adding the new ones.
     *
     * @param displays A list of [Point] objects, where each [Point] represents the
     *                 width and height of a simulated display.
     * @return List of displayIds of added displays.
     */
    fun setupTestDisplays(displays: List<Point>): List<Int> = runBlocking {
        // Cleanup any existing overlay displays.
        cleanupTestDisplays()

        if (displays.isEmpty()) {
            Log.w(TAG, "setupTestDisplays called with an empty list. No displays created.")
            return@runBlocking emptyList()
        }

        val displayAddedFlow: Flow<Int> = callbackFlow {
            val listener = object : DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    trySend(displayId)
                }

                override fun onDisplayRemoved(displayId: Int) {}
                override fun onDisplayChanged(displayId: Int) {}
            }

            val handler = Handler(Looper.getMainLooper())
            displayManager.registerDisplayListener(listener, handler)

            awaitClose {
                displayManager.unregisterDisplayListener(listener)
            }
        }

        val displaySettings = displays.joinToString(separator = ";") { size ->
            "${size.x}x${size.y}/$DEFAULT_DENSITY"
        }

        // Add the overlay displays
        Settings.Global.putString(
            InstrumentationRegistry.getInstrumentation().context.contentResolver,
            Settings.Global.OVERLAY_DISPLAY_DEVICES, displaySettings
        )
        withTimeoutOrNull(TIMEOUT) {
            displayAddedFlow.take(displays.size).collect { displayId ->
                addedDisplays.add(displayId)
            }
        } ?: error("Timed out waiting for displays to be added.")
        addedDisplays
    }

    /**
     * Adds multiple overlay displays with default dimensions. Any existing overlay displays
     * will be removed before adding the new ones.
     *
     * @param count number of displays to add.
     * @return List of displayIds of added displays.
     */
    fun setupTestDisplays(count: Int): List<Int> {
        val displays = List(count) { Point(DEFAULT_WIDTH, DEFAULT_HEIGHT) }
        return setupTestDisplays(displays)
    }

    private fun cleanupTestDisplays() = runBlocking {
        if (addedDisplays.isEmpty()) {
            return@runBlocking
        }

        val displayRemovedFlow: Flow<Int> = callbackFlow {
            val listener = object : DisplayListener {
                override fun onDisplayAdded(displayId: Int) {}
                override fun onDisplayRemoved(displayId: Int) {
                    trySend(displayId)
                }

                override fun onDisplayChanged(displayId: Int) {}
            }
            val handler = Handler(Looper.getMainLooper())
            displayManager.registerDisplayListener(listener, handler)

            awaitClose {
                displayManager.unregisterDisplayListener(listener)
            }
        }

        // Remove overlay displays
        Settings.Global.putString(
            InstrumentationRegistry.getInstrumentation().context.contentResolver,
            Settings.Global.OVERLAY_DISPLAY_DEVICES, null)

        withTimeoutOrNull(TIMEOUT) {
            displayRemovedFlow.take(addedDisplays.size).collect { displayId ->
                addedDisplays.remove(displayId)
            }
        } ?: error("Timed out waiting for displays to be removed.")
    }

    private companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_DENSITY = 160
        const val TAG = "SimulatedConnectedDisplayTestRule"
        val TIMEOUT = 10.seconds
    }
}
