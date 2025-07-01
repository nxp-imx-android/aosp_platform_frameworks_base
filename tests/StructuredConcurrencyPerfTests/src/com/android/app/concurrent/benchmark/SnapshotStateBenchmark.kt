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
package com.android.app.concurrent.benchmark

import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark.Companion.ExecutorThreadScopeBuilder
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark.Companion.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.base.StateCollectBenchmark
import com.android.app.concurrent.benchmark.base.times
import com.android.app.concurrent.benchmark.builder.BenchmarkWithStateProvider
import com.android.app.concurrent.benchmark.builder.SnapshotStateCoroutineBuilder
import com.android.app.concurrent.benchmark.builder.SnapshotStateExecutorBuilder
import com.android.app.concurrent.benchmark.builder.StateBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SnapshotStateCollectExecutorBenchmark(
    threadParam: ThreadFactory<Any, Executor>,
    override val producerCount: Int,
    override val consumerCount: Int,
) : BaseSnapshotStateExecutorBenchmark(threadParam), StateCollectBenchmark {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadBuilder) *
                StateCollectBenchmark.PRODUCER_LIST *
                StateCollectBenchmark.CONSUMER_LIST
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SnapshotStateCollectCoroutineBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val producerCount: Int,
    override val consumerCount: Int,
) : BaseSnapshotStateCoroutineBenchmark(threadParam), StateCollectBenchmark {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadScopeBuilder) *
                StateCollectBenchmark.PRODUCER_LIST *
                StateCollectBenchmark.CONSUMER_LIST
    }
}

abstract class BaseSnapshotStateExecutorBenchmark(threadParam: ThreadFactory<Any, Executor>) :
    BaseExecutorBenchmark(threadParam), BenchmarkWithStateProvider {

    override fun <T> getStateBuilder(): StateBuilder<*, *, T> =
        SnapshotStateExecutorBuilder(executor)
}

abstract class BaseSnapshotStateCoroutineBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>
) : BaseCoroutineBenchmark(threadParam), BenchmarkWithStateProvider {

    override fun <T> getStateBuilder(): StateBuilder<*, *, T> =
        SnapshotStateCoroutineBuilder(bgScope)
}
