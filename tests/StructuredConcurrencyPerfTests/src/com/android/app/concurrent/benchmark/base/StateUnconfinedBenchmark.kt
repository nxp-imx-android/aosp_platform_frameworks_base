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

interface StateUnconfinedBenchmark : BenchmarkWithStateProvider {

    val benchmarkRule: ConcurrentBenchmarkRule

    val producerCount: Int

    val consumerCount: Int

    companion object {
        val PRODUCER_LIST = listOf(1, 2, 5, 10)
        val CONSUMER_LIST = listOf(1, 2, 5, 10, 20)
    }

    @Test
    fun benchmark_unconfinedListeners() {
        class Benchmark<M : R, R>(stateBuilder: StateBuilder<M, R, Int>) :
            StateBenchmarkTask<M, R, Int>(stateBuilder) {
            var receivedVal = Array(producerCount) { IntArray(consumerCount) }
            var producers = List(producerCount) { stateBuilder.createMutableState(0) }

            override fun ConcurrentBenchmarkBuilder.build() {
                if (consumerCount != 0) {
                    unsafeInitialSetup {
                        stateBuilder.readScope {
                            producers.forEachIndexed { producerIndex, state ->
                                repeat(consumerCount) { consumerIndex ->
                                    state.observe { newValue ->
                                        receivedVal[producerIndex][consumerIndex] = newValue
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
