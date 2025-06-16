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

import android.util.Log
import androidx.benchmark.BlackHole
import androidx.benchmark.ExperimentalBlackHoleApi
import com.android.app.concurrent.benchmark.builder.BenchmarkWithStateProvider
import com.android.app.concurrent.benchmark.builder.ConcurrentBenchmarkBuilder
import com.android.app.concurrent.benchmark.builder.StateBenchmarkTask
import com.android.app.concurrent.benchmark.builder.StateBuilder
import com.android.app.concurrent.benchmark.builder.runBenchmark
import com.android.app.concurrent.benchmark.util.DEBUG
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

@OptIn(ExperimentalBlackHoleApi::class)
interface StateCombineBenchmark : BenchmarkWithStateProvider {

    val benchmarkRule: ConcurrentBenchmarkRule

    companion object {
        private const val TAG: String = "StateCombineBenchmark"
    }

    @Test
    fun benchmarkA_2statesWith1combinedCollector() {
        class Benchmark<M : R, R>(stateBuilder: StateBuilder<M, R, Int>) :
            StateBenchmarkTask<M, R, Int>(stateBuilder) {
            var combinedVal1 = 0
            val state1 = stateBuilder.createMutableState(0)
            val state2 = stateBuilder.createMutableState(0)
            val combined1 = stateBuilder.combineState(state1, state2) { a, b -> a + b }

            override fun ConcurrentBenchmarkBuilder.build() {
                beforeFirstIteration(1) { barrier ->
                    stateBuilder.readScope {
                        combined1.observe {
                            combinedVal1 = it
                            // We should only countDown() when it is an even number, which means we
                            // waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                    }
                }
                mainBlock { n ->
                    stateBuilder.writeScope {
                        state1.update(n)
                        state2.update(n)
                    }
                }
                stateChecker(
                    isInExpectedState = { n -> combinedVal1 == n * 2 },
                    expectedStr = "combinedVal1 == n * 2",
                    expectedCalc = { n -> "$combinedVal1 == $n * 2" },
                )
                afterLastIteration { BlackHole.consume(combinedVal1) }
            }
        }
        benchmarkRule.runBenchmark(Benchmark(getStateBuilder()))
    }

    @Test
    fun benchmarkB_1stateWith2CombineAnd2Collects() {
        class Benchmark<M : R, R>(stateBuilder: StateBuilder<M, R, Int>) :
            StateBenchmarkTask<M, R, Int>(stateBuilder) {
            var combinedVal1 = 0
            var combinedVal2 = 0

            val state1 = stateBuilder.createMutableState(0)
            val combined1 = stateBuilder.combineState(state1, state1) { a, b -> a + b }
            val combined2 = stateBuilder.combineState(state1, state1) { a, b -> a + b }

            override fun ConcurrentBenchmarkBuilder.build() {
                beforeFirstIteration(2) { barrier ->
                    stateBuilder.readScope {
                        combined1.observe {
                            combinedVal1 = it
                            // We should only countDown() when it is an even number, which means we
                            // waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                        combined2.observe {
                            combinedVal2 = it
                            // We should only countDown() when it is an even number, which means we
                            // waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                    }
                }
                mainBlock { n -> stateBuilder.writeScope { state1.update(n) } }

                stateChecker(
                    isInExpectedState = { n -> combinedVal1 == n * 2 && combinedVal2 == n * 2 },
                    expectedStr = "combinedVal1 == n * 2 && combinedVal2 == n * 2",
                    expectedCalc = { n -> "$combinedVal1 == $n * 2 && $combinedVal2 == $n * 2" },
                )

                afterLastIteration {
                    BlackHole.consume(combinedVal1)
                    BlackHole.consume(combinedVal2)
                }
            }
        }
        benchmarkRule.runBenchmark(Benchmark(getStateBuilder()))
    }

    @Test
    fun benchmarkC_2statesWith3Collectors() {
        class Benchmark<M : R, R>(stateBuilder: StateBuilder<M, R, Int>) :
            StateBenchmarkTask<M, R, Int>(stateBuilder) {
            var receivedVal1 = 0
            var receivedVal2 = 0
            var combinedVal1 = 0
            val state1 = stateBuilder.createMutableState(0)
            val state2 = stateBuilder.createMutableState(0)
            val combined1 = stateBuilder.combineState(state1, state2) { a, b -> a + b }

            override fun ConcurrentBenchmarkBuilder.build() {
                beforeFirstIteration(3) { barrier ->
                    stateBuilder.readScope {
                        state1.observe {
                            receivedVal1 = it
                            barrier.countDown()
                        }
                        state2.observe {
                            receivedVal2 = it
                            barrier.countDown()
                        }
                        combined1.observe {
                            combinedVal1 = it
                            // We should only countDown() when it is an even number, which means we
                            // waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                    }
                }
                mainBlock { n ->
                    stateBuilder.writeScope {
                        state1.update(n)
                        state2.update(n)
                    }
                }
                stateChecker(
                    isInExpectedState = { n ->
                        receivedVal1 == n && receivedVal2 == n && combinedVal1 == n * 2
                    },
                    expectedStr = "receivedVal1 == n && receivedVal2 == n && combinedVal1 == n * 2",
                    expectedCalc = { n ->
                        "$receivedVal1 == $n && $receivedVal2 == $n && $combinedVal1 == n * 2"
                    },
                )
                afterLastIteration {
                    BlackHole.consume(receivedVal1)
                    BlackHole.consume(receivedVal2)
                    BlackHole.consume(combinedVal1)
                }
            }
        }
        benchmarkRule.runBenchmark(Benchmark(getStateBuilder()))
    }

    @Test
    fun benchmarkD_2statesWith2Combine() {
        class Benchmark<M : R, R>(stateBuilder: StateBuilder<M, R, Int>) :
            StateBenchmarkTask<M, R, Int>(stateBuilder) {
            val state1 = stateBuilder.createMutableState(0)
            val state2 = stateBuilder.createMutableState(0)
            val combined1 = stateBuilder.combineState(state1, state2) { a, b -> a + b }
            val combined2 =
                stateBuilder.combineState(state1, state2, combined1) { a, b, c -> a - b + c + 7 }
            var receivedVal1 = 0
            var receivedVal2 = 0
            var combinedVal1 = 0

            override fun ConcurrentBenchmarkBuilder.build() {
                beforeFirstIteration(3) { barrier ->
                    stateBuilder.readScope {
                        state1.observe {
                            receivedVal1 = it
                            barrier.countDown()
                        }
                        state2.observe {
                            receivedVal2 = it
                            barrier.countDown()
                        }
                        combined1.observe {
                            combinedVal1 = it
                            // We should only countDown() when it is an even number, which means we
                            // waited for the state to settle.
                            if (it % 2 == 0) {
                                barrier.countDown()
                            }
                        }
                        combined2.observe {
                            // Since this is less predictable how often it is dispatched. Do not
                            // use the countDown() barrier, and do not use it in the assertion
                        }
                    }
                }
                mainBlock { n ->
                    stateBuilder.writeScope {
                        state1.update(n)
                        state2.update(n)
                    }
                }
                stateChecker(
                    isInExpectedState = { n ->
                        receivedVal1 == n && receivedVal2 == n && combinedVal1 == n * 2
                    },
                    expectedStr = "receivedVal1 == n && receivedVal2 == n && combinedVal1 == n * 2",
                    expectedCalc = { n ->
                        "$receivedVal1 == $n && $receivedVal2 == $n && $combinedVal1 == $n * 2"
                    },
                )
                afterLastIteration {
                    BlackHole.consume(receivedVal1)
                    BlackHole.consume(receivedVal2)
                    BlackHole.consume(combinedVal1)
                }
            }
        }
        benchmarkRule.runBenchmark(Benchmark(getStateBuilder()))
    }

    @Test
    fun benchmarkE_manyStringConcatFlows() {
        class Benchmark<M : R, R>(stateBuilder: StateBuilder<M, R, String>) :
            StateBenchmarkTask<M, R, String>(stateBuilder) {
            private val k = AtomicInteger()
            val stateA: M = stateBuilder.createMutableState("A:0") // A:$n
            val stateB: M = stateBuilder.createMutableState("B:0") // B:$n
            val stateC: M = stateBuilder.createMutableState("C:0") // C:$n
            val stateD: M = stateBuilder.createMutableState("D:0") // D:$n
            val stateE: M = stateBuilder.createMutableState("E:0") // E:$n

            // A:$n+B:$n
            val flowAB = stateBuilder.combineState(stateA, stateB) { a, b -> "$a+$b" }

            // --C:$n--
            val flowCm = with(stateBuilder) { stateC.mapState { "--$it--" } }

            // A:$n+D:$n
            val flowAD = stateBuilder.combineState(stateA, stateD) { a, d -> "$a+$d" }

            // ==E:$n==
            val flowEm = with(stateBuilder) { stateE.mapState { "==$it==" } }

            // ==E:$n==+B:$n
            val flowEmB = stateBuilder.combineState(flowEm, stateB) { eM, b -> "$eM+$b" }

            // C:$n+==E:$n==
            val flowCEm = stateBuilder.combineState(stateC, flowEm) { c, eM -> "$c+$eM" }

            // A:$n+B:$n+==E:$n==+==E:$n==+B:$n
            val flowABEmEmB =
                stateBuilder.combineState(flowAB, flowEm, flowEmB) { ab, eM, eMb -> "$ab+$eM+$eMb" }

            // A:$n+D:$n+==E:$n==+B:$n
            val flowADEmB = stateBuilder.combineState(flowAD, flowEmB) { ad, eMb -> "$ad+$eMb" }

            // --C:$n--+C:$n+==E:$n==
            val flowCmCEm = stateBuilder.combineState(flowCm, flowCEm) { cM, ceM -> "$cM+$ceM" }

            // ##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##
            val flowABEmEmBm = with(stateBuilder) { flowABEmEmB.mapState { "##$it##" } }

            // ##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##+A:$n+D:$n
            val flowABEmEmBmAD =
                stateBuilder.combineState(flowABEmEmBm, flowAD) { abeMeMbM, ad -> "$abeMeMbM+$ad" }

            // --C:$n--+C:$n+==E:$n==+A:$n+D:$n+==E:$n==+B:$n+A:$n+B:$n
            val flowCmCEmADEmBAB =
                stateBuilder.combineState(flowCmCEm, flowADEmB, flowAB) { cMceM, adeMb, ab ->
                    "$cMceM+$adeMb+$ab"
                }

            // --C:$n--+A:$n+B:$n+==E:$n==+==E:$n==+B:$n
            val flowCmABEmEmB =
                stateBuilder.combineState(flowCm, flowABEmEmB) { cM, abeMeMb -> "$cM+$abeMeMb" }

            var receivedVal1 = ""
            var receivedVal2 = ""
            var receivedVal3 = ""
            var receivedVal4 = ""

            override fun ConcurrentBenchmarkBuilder.build() {
                beforeFirstIteration(4) { barrier ->
                    stateBuilder.readScope {
                        flowABEmEmBm.observe {
                            val n = k.get()
                            receivedVal1 = it
                            if (DEBUG) Log.d(TAG, "receivedVal1=$receivedVal1")
                            if (receivedVal1 == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##") {
                                barrier.countDown()
                            }
                        }
                        flowABEmEmBmAD.observe {
                            val n = k.get()
                            receivedVal2 = it
                            if (DEBUG) Log.d(TAG, "receivedVal2=$receivedVal2")
                            if (receivedVal2 == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##+A:$n+D:$n") {
                                barrier.countDown()
                            }
                        }
                        flowCmCEmADEmBAB.observe {
                            val n = k.get()
                            receivedVal3 = it
                            if (DEBUG) Log.d(TAG, "receivedVal3=$receivedVal3")
                            if (
                                receivedVal3 ==
                                    "--C:$n--+C:$n+==E:$n==+A:$n+D:$n+==E:$n==+B:$n+A:$n+B:$n"
                            ) {
                                barrier.countDown()
                            }
                        }
                        flowCmABEmEmB.observe {
                            val n = k.get()
                            receivedVal4 = it
                            if (DEBUG) Log.d(TAG, "receivedVal4=$receivedVal4")
                            if (receivedVal4 == "--C:$n--+A:$n+B:$n+==E:$n==+==E:$n==+B:$n") {
                                barrier.countDown()
                            }
                        }
                    }
                }
                mainBlock { n ->
                    k.set(n)
                    stateBuilder.writeScope {
                        stateA.update("A:$n")
                        stateB.update("B:$n")
                        stateC.update("C:$n")
                        stateD.update("D:$n")
                        stateE.update("E:$n")
                    }
                }
                stateChecker(
                    isInExpectedState = { n ->
                        receivedVal1 == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##" &&
                            receivedVal2 == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##+A:$n+D:$n" &&
                            receivedVal3 ==
                                "--C:$n--+C:$n+==E:$n==+A:$n+D:$n+==E:$n==+B:$n+A:$n+B:$n" &&
                            receivedVal4 == "--C:$n--+A:$n+B:$n+==E:$n==+==E:$n==+B:$n"
                    },
                    expectedStr =
                        """
                        receivedVal1 == "##A:n+B:n+==E:n==+==E:n==+B:n##" &&
                            receivedVal2 == "##A:n+B:n+==E:n==+==E:n==+B:n##+A:n+D:n" &&
                            receivedVal3 == "--C:n--+C:n+==E:n==+A:n+D:n+==E:n==+B:n+A:n+B:n" &&
                            receivedVal4 == "--C:n--+A:n+B:n+==E:n==+==E:n==+B:n"
                """
                            .trimIndent(),
                    expectedCalc = { n ->
                        """
          $receivedVal1 == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##" &&
                    $receivedVal2 == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##+A:$n+D:$n" &&
                    $receivedVal3 == "--C:$n--+C:$n+==E:$n==+A:$n+D:$n+==E:$n==+B:$n+A:$n+B:$n" &&
                    $receivedVal4 == "--C:$n--+A:$n+B:$n+==E:$n==+==E:$n==+B:$n"
        """
                            .trimIndent()
                    },
                )
                afterLastIteration {
                    BlackHole.consume(receivedVal1)
                    BlackHole.consume(receivedVal2)
                    BlackHole.consume(receivedVal3)
                    BlackHole.consume(receivedVal4)
                }
            }
        }
        benchmarkRule.runBenchmark(Benchmark(getStateBuilder()))
    }
}
