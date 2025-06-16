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
package com.android.app.concurrent.benchmark.base

import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.Process.SCHED_OTHER
import android.util.Log
import com.android.app.concurrent.benchmark.util.BARRIER_TIMEOUT_MILLIS
import com.android.app.concurrent.benchmark.util.CsvMetricCollector.Helper.getCurrentBgThreadName
import com.android.app.concurrent.benchmark.util.THREAD_PRIORITY_MOST_FAVORABLE
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.junit.Assert.assertEquals
import org.junit.Rule

abstract class BaseConcurrentBenchmark {
    protected companion object {
        /**
         * Creates a thread backed by a worker / ExecutorService that uses a
         * [java.util.concurrent.BlockingQueue] schedule work.
         */
        @JvmStatic
        protected fun startExecutorService(): ExecutorService {
            return Executors.newSingleThreadExecutor { runnable ->
                val t =
                    Thread(
                        {
                            Process.setThreadPriority(THREAD_PRIORITY_MOST_FAVORABLE)
                            assertEquals(SCHED_OTHER, Process.getThreadScheduler(Process.myTid()))
                            runnable.run()
                        },
                        getCurrentBgThreadName(),
                    )
                t.isDaemon = true
                t
            }
        }

        @JvmStatic
        protected fun stopExecutorService(thread: ExecutorService) {
            thread.shutdown()
            try {
                if (!thread.awaitTermination(BARRIER_TIMEOUT_MILLIS * 2, MILLISECONDS)) {
                    thread.shutdownNow()
                    if (!thread.awaitTermination(BARRIER_TIMEOUT_MILLIS * 2, MILLISECONDS)) {
                        Log.e("ConcurrentBenchmark", "Executor thread did not terminate")
                    }
                }
            } catch (_: InterruptedException) {
                thread.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }

        /**
         * Creates a thread backed by a [Looper] that uses [android.os.MessageQueue] to schedule
         * work.
         */
        @JvmStatic
        protected fun startHandlerThread(): HandlerThread {
            val handlerThread =
                object : HandlerThread(getCurrentBgThreadName(), THREAD_PRIORITY_MOST_FAVORABLE) {
                        override fun onLooperPrepared() {
                            assertEquals(SCHED_OTHER, Process.getThreadScheduler(Process.myTid()))
                        }
                    }
                    .apply {
                        isDaemon = true
                        start()
                    }
            return handlerThread
        }
    }

    @get:Rule val benchmarkRule = ConcurrentBenchmarkRule()
}

operator fun <T1, T2> Iterable<T1>.times(other: Iterable<T2>): Iterable<Array<Any?>> {
    return flatMap { leftValue ->
        other.map { rightValue ->
            if (leftValue is Array<*>) {
                arrayOf(*leftValue, rightValue)
            } else {
                arrayOf(leftValue, rightValue)
            }
        }
    }
}
