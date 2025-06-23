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
@file:OptIn(ExperimentalBlackHoleApi::class)

package com.android.app.concurrent.benchmark

import androidx.benchmark.BlackHole
import androidx.benchmark.ExperimentalBlackHoleApi
import com.android.app.concurrent.benchmark.base.BaseConcurrentBenchmark
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark.Companion.ExecutorThreadScopeBuilder
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark.Companion.HandlerThreadImmediateScopeBuilder
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark.Companion.HandlerThreadScopeBuilder
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark.Companion.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.base.BaseExecutorBenchmark.Companion.HandlerThreadBuilder
import com.android.app.concurrent.benchmark.util.SimpleStateHolder
import com.android.app.concurrent.benchmark.util.SimpleSynchronousState
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.concurrent.benchmark.util.asSuspendableObserver
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(JUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SingleThreadSumDoubleBaselineBenchmark() : BaseConcurrentBenchmark() {

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark { mainBlock { n -> sum += n.toDouble() } }
        BlackHole.consume(sum)
    }
}

@RunWith(JUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SingleThreadSum1xDoMathBaselineBenchmark() : BaseConcurrentBenchmark() {

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark { mainBlock { n -> sum += doMath(n) } }
        BlackHole.consume(sum)
    }
}

@RunWith(JUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SingleThreadSum2xDoMathBaselineBenchmark() : BaseConcurrentBenchmark() {

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark { mainBlock { n -> sum += doMath(doMath(n)) } }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
class ExecutorDispatchBaselineBenchmark(param: ThreadFactory<ExecutorService, Executor>) :
    BaseExecutorBenchmark(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder, HandlerThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark {
            onEachIteration(count = 1) { n, barrier ->
                val next = doMath(n)
                executor.execute {
                    sum += doMath(next)
                    barrier.countDown()
                }
            }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StartIntrinsicCoroutineBaselineBenchmark(param: ThreadFactory<ExecutorService, Executor>) :
    BaseExecutorBenchmark(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder, HandlerThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark {
            onEachIteration(count = 1) { n, barrier ->
                suspend {
                        val next = doMath(n)
                        suspendCoroutine { continuation: Continuation<Double> ->
                            executor.execute {
                                continuation.resume(doMath(next))
                                barrier.countDown()
                            }
                        }
                    }
                    .startCoroutine(
                        Continuation(
                            context = EmptyCoroutineContext,
                            resumeWith = { r: Result<Double> ->
                                if (r.isSuccess) {
                                    sum += r.getOrNull()!!
                                } else {
                                    error(r.exceptionOrNull()!!)
                                }
                            },
                        )
                    )
            }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ResumeIntrinsicCoroutineBaselineBenchmark(param: ThreadFactory<ExecutorService, Executor>) :
    BaseExecutorBenchmark(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() = listOf(ExecutorThreadBuilder, HandlerThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        val nextInput = SimpleSynchronousState<Double>()
        benchmarkRule.runBenchmark {
            beforeFirstIteration(count = 1) { barrier ->
                suspend {
                        while (true) {
                            val next = nextInput.awaitValue()
                            sum += suspendCoroutine { continuation: Continuation<Double> ->
                                executor.execute {
                                    continuation.resume(doMath(next))
                                    barrier.countDown()
                                }
                            }
                        }
                    }
                    .startCoroutine(
                        Continuation(
                            context = EmptyCoroutineContext,
                            resumeWith = { r: Result<Unit> ->
                                r.exceptionOrNull()?.let { error(it) }
                            },
                        )
                    )
                nextInput.putValueOrThrow(0.00)
            }
            mainBlock { n -> nextInput.putValueOrThrow(doMath(n)) }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LaunchCoroutineBaselineBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseCoroutineBenchmark(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() =
            listOf(
                ExecutorThreadScopeBuilder,
                HandlerThreadScopeBuilder,
                HandlerThreadImmediateScopeBuilder,
            )
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        benchmarkRule.runBenchmark {
            onEachIteration(count = 1) { n, barrier ->
                val next = doMath(n)
                bgScope.launch {
                    sum += doMath(next)
                    barrier.countDown()
                }
            }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MutableStateFlowBaselineBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseCoroutineBenchmark(param) {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() =
            listOf(
                ExecutorThreadScopeBuilder,
                HandlerThreadScopeBuilder,
                HandlerThreadImmediateScopeBuilder,
            )
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        val state = MutableStateFlow(0.00)
        benchmarkRule.runBenchmark {
            beforeFirstIteration(count = 1) { barrier ->
                bgScope.launch {
                    state.collect { next ->
                        sum += doMath(next)
                        barrier.countDown()
                    }
                }
            }
            mainBlock { n -> state.value = doMath(n) }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StateHolderBaselineBenchmark(param: ThreadFactory<ExecutorService, Executor>) :
    BaseExecutorBenchmark(param) {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = listOf(ExecutorThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        val state = SimpleStateHolder(0.0)
        benchmarkRule.runBenchmark {
            beforeFirstIteration(count = 1) { barrier ->
                state.addListener(executor, notifyInitial = true) { next ->
                    sum += doMath(next)
                    barrier.countDown()
                }
            }
            mainBlock { n -> state.value = doMath(n) }
        }
        BlackHole.consume(sum)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StateHolderIntrinsicCoroutineBaselineBenchmark(
    param: ThreadFactory<ExecutorService, Executor>
) : BaseExecutorBenchmark(param) {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = listOf(ExecutorThreadBuilder)
    }

    @Test
    fun benchmark() {
        var sum = 0.0
        val state = SimpleStateHolder(0.00)
        val stateWatcher = state.asSuspendableObserver(executor)
        benchmarkRule.runBenchmark {
            beforeFirstIteration(count = 1) { barrier ->
                suspend fun collectLambda(): Nothing {
                    while (true) {
                        val next = stateWatcher.awaitNextValue()
                        sum += doMath(next)
                        barrier.countDown()
                    }
                }
                ::collectLambda.startCoroutine(
                    Continuation(context = EmptyCoroutineContext, resumeWith = {})
                )
            }
            mainBlock { n -> state.value = doMath(n) }
        }
        BlackHole.consume(sum)
    }
}

private fun doMath(num: Number): Double {
    val n = num.toDouble()
    var sum = 0.0
    repeat(100) {
        sum += kotlin.math.sqrt(n + it) / 1.2345
        sum /= kotlin.math.PI
        sum /= kotlin.math.sin(n)
        sum /= kotlin.math.cos(n)
    }
    return sum
}
