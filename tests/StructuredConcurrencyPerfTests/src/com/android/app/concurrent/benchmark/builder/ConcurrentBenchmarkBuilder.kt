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
package com.android.app.concurrent.benchmark.builder

import com.android.app.concurrent.benchmark.base.ConcurrentBenchmarkRule
import com.android.app.concurrent.benchmark.util.CyclicCountDownBarrier
import java.util.concurrent.CyclicBarrier

@DslMarker annotation class ConcurrentBuilderMarker

@ConcurrentBuilderMarker
class ConcurrentBenchmarkBuilder {
    // Initial setup that is NOT assigned a CyclicCountDownBarrier, does not await on the
    // shared `CyclicCount`
    private val _unsafeInitialSetup = mutableListOf<() -> Unit>()
    // Bg work that can either run once before the mainBlock, or both before the mainBlock
    // and once per iteration. This work is assigned a CyclicCountDownBarrier
    // Each item in the list increases the number of parties that await on the shared
    // CyclicBarrier by one.
    private val _synchronizedBgWork = mutableListOf<CyclicCountDownBarrier.Builder>()
    private var _mainBlockChanged = false
    private var _mainBlock: (Int) -> Unit = { /* do nothing */ }
        set(v) {
            if (_mainBlockChanged) {
                error("Cannot set mainBlock more than once")
            } else {
                _mainBlockChanged = true
                field = v
            }
        }

    private var _afterLastIterationChanged = false
    private var _afterLastIteration: () -> Unit = { /* do nothing */ }
        set(v) {
            if (_afterLastIterationChanged) {
                error("Cannot set afterLastIteration more than once")
            } else {
                _afterLastIterationChanged = true
                field = v
            }
        }

    private var _stateCheckerChanged = false
    // Checks whether we are in the expected state after all barrier parties await and the
    // synchronization point is reached
    private var _stateChecker: StateChecker = StateChecker.NoOpStateChecker
        set(v) {
            if (_stateCheckerChanged) {
                error("Cannot set stateChecker more than once")
            } else {
                _stateCheckerChanged = true
                field = v
            }
        }

    /**
     * Setup step that runs before the first iteration of [mainBlock], which will be assigned a
     * [CyclicCountDownBarrier] with the given [count].
     *
     * Internally, each call to [beforeFirstIteration] increments the party count for the sake of
     * the shared `CyclicBarrier`. This means [beforeFirstIteration] should only be called ONCE for
     * each bg thread in use for the test.
     */
    fun beforeFirstIteration(count: Int, block: (CyclicCountDownBarrier) -> Unit) {
        _synchronizedBgWork +=
            object : CyclicCountDownBarrier.Builder {
                override val runOnEachIteration: Boolean = false

                override fun build(barrier: CyclicBarrier): CyclicCountDownBarrier {
                    val bgContext =
                        object : CyclicCountDownBarrier(count) {
                            override val barrier: CyclicBarrier = barrier

                            override fun runOnce(n: Int) {
                                block(this)
                            }
                        }
                    return bgContext
                }
            }
    }

    /**
     * Same as [beforeFirstIteration], but subsequently runs on each iteration. The given block is
     * passed the current iteration count, starting with 0 for the initial setup that runs _before_
     * [mainBlock] is ever invoked.
     */
    fun onEachIteration(count: Int, block: (Int, CyclicCountDownBarrier) -> Unit) {
        _synchronizedBgWork +=
            object : CyclicCountDownBarrier.Builder {
                override val runOnEachIteration: Boolean = true

                override fun build(barrier: CyclicBarrier): CyclicCountDownBarrier {
                    val bgContext =
                        object : CyclicCountDownBarrier(count) {
                            override val barrier: CyclicBarrier = barrier

                            override fun runOnce(n: Int) {
                                block(n, this)
                            }
                        }
                    return bgContext
                }
            }
    }

    /**
     * Plain setup step that runs before the first iteration of [mainBlock]. This will not be
     * assigned a party for the sake of the `CyclicBarrier`. This also means that if this block
     * schedules bg work, the mainBlock may start before the initial bg work passed here is
     * completed.
     */
    fun unsafeInitialSetup(block: () -> Unit) {
        _unsafeInitialSetup += block
    }

    /**
     * Blocking work to run on the main thread. The given block is called once per iteration, and it
     * is passed the current iteration count starting from 1. The first iteration is called with
     * `n=1`, the second with `n=2`, etc.
     *
     * Internally, the main thread is implicitly assigned one party for the `CyclicBarrier`, which
     * is shared with any bg thread that registers itself with [beforeFirstIteration] or
     * [onEachIteration]. After the given block runs for an iteration, the benchmark test will call
     * `await` on the barrier. If no bg work is registered, the party count is `1`, meaning `await`
     * would return immediately.
     */
    fun mainBlock(block: (Int) -> Unit) {
        _mainBlock = { n -> block(n) }
    }

    fun stateChecker(stateChecker: StateChecker) {
        _stateChecker = stateChecker
    }

    fun afterLastIteration(block: () -> Unit) {
        _afterLastIteration = block
    }

    fun stateChecker(
        isInExpectedState: (Int) -> Boolean,
        expectedStr: String,
        expectedCalc: (Int) -> String,
    ) {
        stateChecker(StateChecker(isInExpectedState, expectedStr, expectedCalc))
    }

    fun ConcurrentBenchmarkRule.measure() {
        measureRepeated(
            unsafeInitialSetup = this@ConcurrentBenchmarkBuilder._unsafeInitialSetup,
            synchronizedBgWork = this@ConcurrentBenchmarkBuilder._synchronizedBgWork,
            mainBlock = { n -> this@ConcurrentBenchmarkBuilder._mainBlock(n) },
            stateChecker = this@ConcurrentBenchmarkBuilder._stateChecker,
            afterLastIteration = _afterLastIteration,
        )
    }
}

@ConcurrentBuilderMarker
open class StateChecker(
    val isInExpectedState: (Int) -> Boolean,
    val expectedStr: String,
    val expectedCalc: (Int) -> String,
) {
    object NoOpStateChecker :
        StateChecker(
            isInExpectedState = { n -> true },
            expectedStr = "",
            expectedCalc = { n -> "" },
        )
}
