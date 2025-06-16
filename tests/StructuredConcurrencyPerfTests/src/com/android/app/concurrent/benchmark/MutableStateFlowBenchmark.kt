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

import androidx.benchmark.BlackHole
import androidx.benchmark.ExperimentalBlackHoleApi
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark.Companion.ExecutorThreadScopeBuilder
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark.Companion.UnconfinedExecutorThreadScopeBuilder
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark.Companion.UnsafeImmediateThreadScopeBuilder
import com.android.app.concurrent.benchmark.base.ChainedStateCollectBenchmark
import com.android.app.concurrent.benchmark.base.StateCollectBenchmark
import com.android.app.concurrent.benchmark.base.StateCombineBenchmark
import com.android.app.concurrent.benchmark.base.StateUnconfinedBenchmark
import com.android.app.concurrent.benchmark.base.times
import com.android.app.concurrent.benchmark.builder.BenchmarkWithStateProvider
import com.android.app.concurrent.benchmark.builder.MutableStateFlowBuilder
import com.android.app.concurrent.benchmark.builder.StateBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MutableStateFlowCombineBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseMutableStateFlowBenchmark(param), StateCombineBenchmark {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = threadBuilders
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MutableStateFlowCollectBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val producerCount: Int,
    override val consumerCount: Int,
) : BaseMutableStateFlowBenchmark(threadParam), StateCollectBenchmark {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadScopeBuilder) *
                StateCollectBenchmark.PRODUCER_LIST *
                StateCollectBenchmark.CONSUMER_LIST
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MutableStateFlowChainedCollectBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val chainLength: Int,
) : BaseMutableStateFlowBenchmark(threadParam), ChainedStateCollectBenchmark {

    companion object {
        @Parameters(name = "{0},{1}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadScopeBuilder) * ChainedStateCollectBenchmark.CHAIN_LENGTHS
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MutableStateFlowUnconfinedBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val producerCount: Int,
    override val consumerCount: Int,
) : BaseMutableStateFlowBenchmark(threadParam), StateUnconfinedBenchmark {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(UnconfinedExecutorThreadScopeBuilder, UnsafeImmediateThreadScopeBuilder) *
                StateUnconfinedBenchmark.PRODUCER_LIST *
                StateUnconfinedBenchmark.CONSUMER_LIST
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ChainedFlowBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    val chainLength: Int,
    val intermediateOperator: (CoroutineScope, Flow<Double>) -> Flow<Double>,
) : BaseCoroutineBenchmark(threadParam) {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadScopeBuilder) *
                listOf(1, 2, 5, 10, 25) *
                listOf(
                    object : (CoroutineScope, Flow<Double>) -> Flow<Double> {
                        override fun invoke(
                            scope: CoroutineScope,
                            upstream: Flow<Double>,
                        ): Flow<Double> {
                            return upstream
                        }

                        override fun toString(): String {
                            return "cold"
                        }
                    },
                    object : (CoroutineScope, Flow<Double>) -> Flow<Double> {
                        override fun invoke(
                            scope: CoroutineScope,
                            upstream: Flow<Double>,
                        ): Flow<Double> {
                            return upstream.stateIn(
                                scope,
                                started = SharingStarted.Eagerly,
                                initialValue = 0.0,
                            )
                        }

                        override fun toString(): String {
                            return "stateIn"
                        }
                    },
                    object : (CoroutineScope, Flow<Double>) -> Flow<Double> {
                        override fun invoke(
                            scope: CoroutineScope,
                            upstream: Flow<Double>,
                        ): Flow<Double> {
                            return upstream.conflate()
                        }

                        override fun toString(): String {
                            return "conflate"
                        }
                    },
                    object : (CoroutineScope, Flow<Double>) -> Flow<Double> {
                        override fun invoke(
                            scope: CoroutineScope,
                            upstream: Flow<Double>,
                        ): Flow<Double> {
                            return upstream.buffer(2)
                        }

                        override fun toString(): String {
                            return "buffer-2"
                        }
                    },
                )
    }

    @OptIn(ExperimentalBlackHoleApi::class)
    @Test
    fun benchmark() {
        val sourceState = MutableStateFlow(0.0)
        var receivedVal = 0.0
        val stateChain = mutableListOf<Flow<Double>>()
        repeat(chainLength) { i ->
            val upstream = if (i == 0) sourceState else stateChain.last()
            stateChain.add(intermediateOperator(bgScope, upstream.map { it + 1 }))
        }
        benchmarkRule.runBenchmark {
            beforeFirstIteration(count = 1) { barrier ->
                bgScope.launch {
                    stateChain.last().collect {
                        receivedVal = it
                        barrier.countDown()
                    }
                }
            }
            mainBlock { n -> sourceState.value = n.toDouble() }
            @OptIn(ExperimentalBlackHoleApi::class)
            afterLastIteration { BlackHole.consume(receivedVal) }
        }
    }
}

abstract class BaseMutableStateFlowBenchmark(threadParam: ThreadFactory<Any, CoroutineScope>) :
    BaseCoroutineBenchmark(threadParam), BenchmarkWithStateProvider {

    override fun <T> getStateBuilder(): StateBuilder<*, *, T> = MutableStateFlowBuilder(bgScope)
}
