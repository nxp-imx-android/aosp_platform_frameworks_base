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

import androidx.benchmark.BlackHole
import androidx.benchmark.ExperimentalBlackHoleApi
import com.android.app.concurrent.benchmark.builder.BenchmarkWithStateProvider
import com.android.app.concurrent.benchmark.builder.ConcurrentBenchmarkBuilder
import com.android.app.concurrent.benchmark.builder.StateBenchmarkTask
import com.android.app.concurrent.benchmark.builder.StateBuilder
import com.android.app.concurrent.benchmark.builder.runBenchmark
import org.junit.Test

interface StateCollectBenchmark : BenchmarkWithStateProvider {

    val benchmarkRule: ConcurrentBenchmarkRule

    val producerCount: Int

    val consumerCount: Int

    companion object {
        val PRODUCER_LIST = listOf(10, 25, 50)
        val CONSUMER_LIST = listOf(10, 25, 50)
    }

    @Test
    fun benchmark_stateObservers_shallow() {
        class Benchmark<M : R, R>(stateBuilder: StateBuilder<M, R, Int>) :
            StateBenchmarkTask<M, R, Int>(stateBuilder) {
            var receivedVal = Array(consumerCount) { IntArray(producerCount) }
            var producers = List(producerCount) { stateBuilder.createMutableState(0) }

            override fun ConcurrentBenchmarkBuilder.build() {
                if (consumerCount != 0) {
                    beforeFirstIteration(producerCount * consumerCount) { barrier ->
                        repeat(consumerCount) { consumerIndex ->
                            stateBuilder.readScope {
                                producers.forEachIndexed { producerIndex, state ->
                                    state.observe { newValue ->
                                        receivedVal[consumerIndex][producerIndex] = newValue
                                        barrier.countDown()
                                    }
                                }
                            }
                        }
                    }
                }
                mainBlock { n -> stateBuilder.writeScope { producers.forEach { it.update(n) } } }
                stateChecker(
                    isInExpectedState = expectedState@{ n ->
                            receivedVal.forEachIndexed { i, row ->
                                row.forEachIndexed { j, value ->
                                    if (value != n) {
                                        return@expectedState false
                                    }
                                }
                            }
                            return@expectedState true
                        },
                    expectedStr = "receivedVal[i][j] == n (for all i and j)",
                    expectedCalc = result@{ n ->
                            receivedVal.forEachIndexed { i, row ->
                                row.forEachIndexed { j, value ->
                                    if (value != n) {
                                        return@result "receivedVal[$i][$j] == $value"
                                    }
                                }
                            }
                            return@result "ok"
                        },
                )
                @OptIn(ExperimentalBlackHoleApi::class)
                afterLastIteration { BlackHole.consume(receivedVal) }
            }
        }
        benchmarkRule.runBenchmark(Benchmark(getStateBuilder()))
    }
}

interface ChainedStateCollectBenchmark : BenchmarkWithStateProvider {

    val benchmarkRule: ConcurrentBenchmarkRule

    val chainLength: Int

    companion object {
        val CHAIN_LENGTHS = listOf(1, 2, 5, 10, 25)
    }

    @Test
    fun benchmark_stateObservers_chained() {
        class Benchmark<M : R, R>(stateBuilder: StateBuilder<M, R, Int>) :
            StateBenchmarkTask<M, R, Int>(stateBuilder) {
            var receivedVal = 0
            var sourceState = stateBuilder.createMutableState(0)
            var stateChain = mutableListOf<R>()

            override fun ConcurrentBenchmarkBuilder.build() {
                repeat(chainLength) { i ->
                    val upstream =
                        if (i == 0) {
                            sourceState
                        } else {
                            stateChain.last()
                        }
                    stateChain.add(with(stateBuilder) { upstream.mapState { it + 1 } })
                }
                if (chainLength != 0) {
                    beforeFirstIteration(count = 1) { barrier ->
                        stateBuilder.readScope {
                            stateChain.last().observe { newValue ->
                                receivedVal = newValue
                                barrier.countDown()
                            }
                        }
                    }
                }
                mainBlock { n -> stateBuilder.writeScope { sourceState.update(n) } }
                stateChecker(
                    isInExpectedState = expectedState@{ n ->
                            return@expectedState receivedVal == n + chainLength
                        },
                    expectedStr = "receivedVal == n + chainLength",
                    expectedCalc = result@{ n ->
                            return@result "$receivedVal == $n + $chainLength"
                        },
                )
                @OptIn(ExperimentalBlackHoleApi::class)
                afterLastIteration { BlackHole.consume(receivedVal) }
            }
        }
        benchmarkRule.runBenchmark(Benchmark(getStateBuilder()))
    }
}
