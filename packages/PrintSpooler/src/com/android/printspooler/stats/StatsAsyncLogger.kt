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

package com.android.printspooler.stats

import android.annotation.UserIdInt
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * @hide
 */
object StatsAsyncLogger {
    private val TAG = StatsAsyncLogger::class.java.simpleName
    private val DEBUG = false

    @VisibleForTesting val EVENT_REPORTED_MIN_INTERVAL: Duration = 10.milliseconds
    @VisibleForTesting val MAX_EVENT_QUEUE = 150

    private var semaphore = Semaphore(MAX_EVENT_QUEUE)
    private lateinit var handlerThread: HandlerThread
    private lateinit var eventHandler: Handler
    private var nextAvailableTimeMillis = SystemClock.uptimeMillis()
    private var statsLogWrapper = StatsLogWrapper()
    private var logging = false

    @VisibleForTesting
    fun testSetStatsLogWrapper(wrapper: StatsLogWrapper) {
        statsLogWrapper = wrapper
    }

    @VisibleForTesting
    fun testSetSemaphore(s: Semaphore) {
        semaphore = s
    }

    @VisibleForTesting
    fun testSetHandler(handler: Handler) {
        eventHandler = handler
    }

    private fun getNextAvailableTimeMillis(): Long {
        return max(
            // Handles back to back records
            nextAvailableTimeMillis + EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds,
            // Updates next time to more recent value if it wasn't recently
            SystemClock.uptimeMillis() + EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds,
        )
    }

    // Initializes Async Logger for logging events. Returns true if
    // started logging and False if logging was already started.
    fun startLogging(): Boolean {
        if (logging) {
            return false
        }
        logging = true
        if (DEBUG) {
            Log.d(TAG, "Logging started")
        }
        semaphore = Semaphore(MAX_EVENT_QUEUE)
        handlerThread = HandlerThread("StatsEventLoggerWrapper").also { it.start() }
        eventHandler = Handler(handlerThread.getLooper())
        nextAvailableTimeMillis = SystemClock.uptimeMillis()
        return true
    }

    // Returns true if logging was started and the logger successfully
    // logged all pending events while ending. Returns false
    // otherwise.
    fun stopLogging(): Boolean {
        if (!logging) {
            return false
        }
        logging = false
        if (DEBUG) {
            Log.d(TAG, "Begin flushing events")
        }
        val acquired =
            semaphore.tryAcquire(
                MAX_EVENT_QUEUE,
                MAX_EVENT_QUEUE * EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
            )
        if (!acquired) {
            Log.w(TAG, "Time exceeded awaiting stats events")
        }
        if (DEBUG) {
            Log.d(TAG, "End flushing events")
        }
        handlerThread.quit()
        return acquired
    }

    fun AdvancedOptionsUiLaunched(@UserIdInt printServiceId: Int): Boolean {
        if (!logging) {
            return false
        }
        if (DEBUG) {
            Log.d(TAG, "Logging AdvancedOptionsUiLaunched event")
        }
        synchronized(semaphore) {
            if (!semaphore.tryAcquire()) {
                Log.w(TAG, "Logging too many events, dropping AdvancedOptionsUiLaunched event")
                return false
            }
            val result =
                eventHandler.postAtTime(
                    Runnable {
                        synchronized(semaphore) {
                            if (DEBUG) {
                                Log.d(TAG, "Async logging AdvancedOptionsUiLaunched event")
                            }
                            statsLogWrapper.internalAdvancedOptionsUiLaunched(printServiceId)
                            semaphore.release()
                        }
                    },
                    nextAvailableTimeMillis,
                )
            if (!result) {
                Log.e(TAG, "Could not log AdvancedOptionsUiLaunched event")
                semaphore.release()
                return false
            }
            nextAvailableTimeMillis = getNextAvailableTimeMillis()
        }
        return true
    }
}
