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

interface StateBuilder<M : R, R, T> {
    interface ReadContext<R, T> {
        fun R.observe(callback: (T) -> Unit)
    }

    interface WriteContext<R, T> {
        fun R.update(newValue: T)
    }

    fun createMutableState(initialValue: T): M

    fun writeScope(block: WriteContext<M, T>.() -> Unit)

    fun readScope(block: ReadContext<R, T>.() -> Unit)

    fun R.mapState(transform: (T) -> T): R

    fun combineState(a: R, b: R, transform: (T, T) -> T): R

    fun combineState(a: R, b: R, c: R, transform: (T, T, T) -> T): R

    fun dispose() {}
}

abstract class StateBenchmarkTask<M : R, R, T>(val stateBuilder: StateBuilder<M, R, T>) {
    abstract fun ConcurrentBenchmarkBuilder.build()

    fun dispose() {
        stateBuilder.dispose()
    }
}

fun <M : R, R, T> ConcurrentBenchmarkRule.runBenchmark(benchmark: StateBenchmarkTask<M, R, T>) {
    with(ConcurrentBenchmarkBuilder()) {
        with(benchmark) { build() }
        measure()
        benchmark.dispose()
    }
}

interface BenchmarkWithStateProvider {
    fun <T> getStateBuilder(): StateBuilder<*, *, T>
}
