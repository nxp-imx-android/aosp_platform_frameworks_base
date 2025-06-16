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
import com.android.app.concurrent.benchmark.util.SimpleStateHolder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Before

abstract class BaseExecutorBenchmark(val threadParam: ThreadFactory<Any, Executor>) :
    BaseConcurrentBenchmark() {

    companion object {
        object HandlerThreadBuilder :
            ThreadFactory<HandlerThread, Executor>(
                "Handler",
                startThread = ::startHandlerThread,
                getScheduler = { handlerThread -> handlerThread.threadExecutor },
                stopThread = { handlerThread -> handlerThread.quitSafely() },
            )

        object HandlerImmediateThreadBuilder :
            ThreadFactory<HandlerThread, Executor>(
                "Handler.immediate",
                startThread = ::startHandlerThread,
                getScheduler = { handlerThread ->
                    object : Executor {
                        val wrappedExecutor = handlerThread.threadExecutor

                        override fun execute(r: Runnable?) {
                            if (r != null && Looper.myLooper() == handlerThread.looper) {
                                r.run()
                            } else {
                                wrappedExecutor.execute(r)
                            }
                        }
                    }
                },
                stopThread = { handlerThread -> handlerThread.quitSafely() },
            )

        object ExecutorThreadBuilder :
            ThreadFactory<ExecutorService, Executor>(
                "Executor",
                startThread = ::startExecutorService,
                getScheduler = { it },
                stopThread = { stopExecutorService(it) },
            )

        /**
         * Executor that always runs on the current thread immediately, as if
         * [CoroutineDispatcher.isDispatchNeeded] returned `false`. This is as close to the
         * [Unconfined] as makes sense because the [SimpleStateHolder] cannot suspend and has no
         * equivalent of [yield].
         */
        object UnconfinedThreadBuilder :
            ThreadFactory<Unit, Executor>(
                "Unconfined",
                startThread = {},
                getScheduler = { Executor { r -> r.run() } },
                stopThread = {},
            )

        @JvmStatic
        val threadBuilders: List<ThreadFactory<Any, Executor>> =
            listOf(HandlerThreadBuilder, HandlerImmediateThreadBuilder, ExecutorThreadBuilder)
    }

    lateinit var executor: Executor

    @Before
    fun setup() {
        executor = threadParam.startThreadAndGetScheduler()
    }

    @After
    fun tearDown() {
        threadParam.stopThreadAndQuitScheduler()
    }
}
