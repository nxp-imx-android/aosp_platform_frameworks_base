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

import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark.Companion.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark.Companion.HandlerImmediateThreadBuilder
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark.Companion.HandlerThreadBuilder
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark.Companion.UnconfinedThreadBuilder
import com.android.app.concurrent.benchmark.base.ChainedStateCollectBenchmark
import com.android.app.concurrent.benchmark.base.StateCollectBenchmark
import com.android.app.concurrent.benchmark.base.StateCombineBenchmark
import com.android.app.concurrent.benchmark.base.StateUnconfinedBenchmark
import com.android.app.concurrent.benchmark.base.times
import com.android.app.concurrent.benchmark.builder.BenchmarkWithStateProvider
import com.android.app.concurrent.benchmark.builder.SimpleStateHolderBuilder
import com.android.app.concurrent.benchmark.builder.StateBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import java.util.concurrent.Executor
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleStateHolderCombineBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseSimpleStateHolderBenchmark(param), StateCombineBenchmark {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadBuilder, HandlerThreadBuilder, HandlerImmediateThreadBuilder)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleStateHolderCollectBenchmark(
    threadParam: ThreadFactory<Any, Executor>,
    override val producerCount: Int,
    override val consumerCount: Int,
) : BaseSimpleStateHolderBenchmark(threadParam), StateCollectBenchmark {

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
class SimpleStateHolderChainedCollectBenchmark(
    threadParam: ThreadFactory<Any, Executor>,
    override val chainLength: Int,
) : BaseSimpleStateHolderBenchmark(threadParam), ChainedStateCollectBenchmark {

    companion object {
        @Parameters(name = "{0},{1}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadBuilder) * ChainedStateCollectBenchmark.CHAIN_LENGTHS
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleStateHolderCollectUnconfinedBenchmark(
    threadParam: ThreadFactory<Any, Executor>,
    override val producerCount: Int,
    override val consumerCount: Int,
) : BaseSimpleStateHolderBenchmark(threadParam), StateUnconfinedBenchmark {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(UnconfinedThreadBuilder) *
                StateUnconfinedBenchmark.PRODUCER_LIST *
                StateUnconfinedBenchmark.CONSUMER_LIST
    }
}

abstract class BaseSimpleStateHolderBenchmark(threadParam: ThreadFactory<Any, Executor>) :
    BaseExecutorBenchmark(threadParam), BenchmarkWithStateProvider {

    override fun <T> getStateBuilder(): StateBuilder<*, *, T> = SimpleStateHolderBuilder(executor)
}
