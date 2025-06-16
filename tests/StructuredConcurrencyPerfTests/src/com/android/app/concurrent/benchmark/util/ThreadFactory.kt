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
package com.android.app.concurrent.benchmark.util

import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Least nice value for the highest priority among the SCHED_OTHER threads. This should match
 * priority used by AndroidX Microbenchmark for the BenchmarkRunner thread.
 */
const val THREAD_PRIORITY_MOST_FAVORABLE = -20

/**
 * Helper for naming JUnit parameters
 *
 * @param startThread start a new thread, and return a handle that can be used to later shutdown
 *   that thread
 * @param getScheduler using the thread handle, returns an object, such as a [CoroutineDispatcher]
 *   or [Executor], that can be used for scheduling work on the thread.
 * @param stopThread stop a thread using the handle
 */
open class ThreadFactory<out R : Any, out T : Any>(
    private val name: String,
    private val startThread: () -> R,
    private val getScheduler: (R) -> T,
    private val quitScheduler: (T) -> Unit = {},
    private val stopThread: (R) -> Unit,
) {
    private var thread: R? = null
    private var scheduler: T? = null

    fun startThreadAndGetScheduler(): T {
        if (thread != null) {
            throw IllegalStateException(
                "Attempting to start a new background thread before the prior one was terminated"
            )
        }
        thread = startThread()
        scheduler = getScheduler(thread!!)
        return scheduler!!
    }

    fun stopThreadAndQuitScheduler() {
        if (thread == null) {
            throw IllegalStateException("Attempting to shutdown thread before it was started")
        }
        quitScheduler(scheduler!!)
        scheduler = null
        stopThread(thread!!)
        thread = null
    }

    override fun toString(): String {
        return name
    }
}
