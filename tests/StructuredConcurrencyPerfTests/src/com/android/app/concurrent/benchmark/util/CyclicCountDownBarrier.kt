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

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.fail

/**
 * Awaits on the given [barrier] when [CyclicCountDownBarrier.countDown] is called [count] times,
 * then resets.
 *
 * This class is NOT thread safe. It should only be called from one background thread.
 */
abstract class CyclicCountDownBarrier(private val count: Int) {
    interface Builder {
        val runOnEachIteration: Boolean

        fun build(barrier: CyclicBarrier): CyclicCountDownBarrier
    }

    private var assignedThread: Thread? = null

    abstract val barrier: CyclicBarrier

    private var currentCount = count

    /**
     * IMPORTANT: This should only be called from ONE thread.
     *
     * Each thread should have its own instance of [CyclicCountDownBarrier], if necessary, or it
     * should call await on the associated [CyclicBarrier] manually.
     */
    fun countDown() {
        val curThread = Thread.currentThread()
        if (assignedThread == null) {
            assignedThread = curThread
        }
        if (curThread != assignedThread) {
            fail(
                "CyclicCountDownBarrier.countDown() must only ever be called from one thread." +
                    " Was first called on Thread #${assignedThread?.threadId()}," +
                    " but was now called on Thread #${curThread.threadId()}"
            )
        }
        currentCount--
        if (currentCount == 0) {
            try {
                barrier.await(BARRIER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                fail("Timeout on bg thread while awaiting next iteration")
                throw e
            }
            currentCount = count
        }
    }

    abstract fun runOnce(n: Int)
}
