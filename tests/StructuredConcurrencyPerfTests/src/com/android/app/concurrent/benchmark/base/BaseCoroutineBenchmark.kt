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
import com.android.app.concurrent.benchmark.util.DEBUG
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before

private fun tracingContextIfDebug(): CoroutineContext {
    return if (DEBUG) {
        createCoroutineTracingContext("bg", walkStackForDefaultNames = true)
    } else {
        EmptyCoroutineContext
    }
}

abstract class BaseCoroutineBenchmark(val threadParam: ThreadFactory<Any, CoroutineScope>) :
    BaseConcurrentBenchmark() {

    companion object {
        object HandlerThreadScopeBuilder :
            ThreadFactory<HandlerThread, CoroutineScope>(
                "Handler",
                startThread = ::startHandlerThread,
                getScheduler = { handlerThread ->
                    CoroutineScope(
                        handlerThread.threadHandler.asCoroutineDispatcher() +
                            tracingContextIfDebug()
                    )
                },
                quitScheduler = { scope -> scope.cancel() },
                stopThread = { handlerThread -> handlerThread.quitSafely() },
            )

        object HandlerThreadImmediateScopeBuilder :
            ThreadFactory<HandlerThread, CoroutineScope>(
                "Handler.immediate",
                startThread = ::startHandlerThread,
                getScheduler = { handlerThread ->
                    CoroutineScope(
                        handlerThread.threadHandler.asCoroutineDispatcher().immediate +
                            tracingContextIfDebug()
                    )
                },
                quitScheduler = { scope -> scope.cancel() },
                stopThread = { handlerThread -> handlerThread.quitSafely() },
            )

        object ExecutorThreadScopeBuilder :
            ThreadFactory<ExecutorService, CoroutineScope>(
                "Executor",
                startThread = ::startExecutorService,
                getScheduler = { executorService ->
                    CoroutineScope(
                        executorService.asCoroutineDispatcher() + tracingContextIfDebug()
                    )
                },
                quitScheduler = { scope -> scope.cancel() },
                stopThread = { stopExecutorService(it) },
            )

        object UnconfinedExecutorThreadScopeBuilder :
            ThreadFactory<CoroutineDispatcher, CoroutineScope>(
                "Unconfined",
                startThread = { Dispatchers.Unconfined },
                getScheduler = { dispatcher ->
                    CoroutineScope(dispatcher + tracingContextIfDebug())
                },
                quitScheduler = { scope -> scope.cancel() },
                stopThread = {},
            )

        object UnsafeImmediateThreadScopeBuilder :
            ThreadFactory<CoroutineDispatcher, CoroutineScope>(
                "UnsafeImmediate",
                startThread = {
                    object : CoroutineDispatcher() {
                        override fun dispatch(context: CoroutineContext, block: Runnable) {
                            block.run()
                        }
                    }
                },
                getScheduler = { dispatcher ->
                    CoroutineScope(dispatcher + tracingContextIfDebug())
                },
                quitScheduler = { scope -> scope.cancel() },
                stopThread = {},
            )
    }

    protected lateinit var bgScope: CoroutineScope

    @Before
    fun setup() {
        bgScope = threadParam.startThreadAndGetScheduler()
    }

    @After
    fun tearDown() {
        threadParam.stopThreadAndQuitScheduler()
    }
}
