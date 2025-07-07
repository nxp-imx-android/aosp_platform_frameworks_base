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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import java.io.Closeable
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// NOTE: The Snapshot APIs used here would typically not be called directly. This benchmark is for
// stress testing snapshot updates and observations. It's not meant to portray a realistic scenario.

class SnapshotStateExecutorBuilder<T>(val executor: Executor) : SnapshotStateBuilder<T>() {
    override fun startObservation(block: () -> Unit): Closeable {
        return SnapshotStateExecutorObserver(executor, block).start()
    }
}

class SnapshotStateCoroutineBuilder<T>(val scope: CoroutineScope) : SnapshotStateBuilder<T>() {
    override fun startObservation(block: () -> Unit): Closeable {
        return SnapshotStateCoroutineObserver(scope) { with(stateReader) { block() } }.start()
    }
}

abstract class SnapshotStateBuilder<T>() : StateBuilder<MutableState<T>, State<T>, T> {

    val openResources = mutableListOf<Closeable>()

    private val stateWriter =
        object : StateBuilder.WriteContext<MutableState<T>, T> {
            override fun MutableState<T>.update(newValue: T) {
                value = newValue
            }
        }

    override fun createMutableState(initialValue: T): MutableState<T> {
        return mutableStateOf(initialValue)
    }

    override fun writeScope(block: StateBuilder.WriteContext<MutableState<T>, T>.() -> Unit) {
        Snapshot.withMutableSnapshot { with(stateWriter) { block() } }
    }

    val stateReader =
        object : StateBuilder.ReadContext<State<T>, T> {
            override fun State<T>.observe(callback: (T) -> Unit) {
                callback(value)
            }
        }

    abstract fun startObservation(block: () -> Unit): Closeable

    @OptIn(ExperimentalStdlibApi::class)
    override fun readScope(block: StateBuilder.ReadContext<State<T>, T>.() -> Unit) {
        synchronized(openResources) {
            openResources += startObservation { with(stateReader) { block() } }
        }
    }

    override fun State<T>.mapState(transform: (T) -> T): State<T> {
        TODO("Not yet implemented")
    }

    override fun combineState(a: State<T>, b: State<T>, transform: (T, T) -> T): State<T> {
        TODO("Not yet implemented")
    }

    override fun combineState(
        a: State<T>,
        b: State<T>,
        c: State<T>,
        transform: (T, T, T) -> T,
    ): State<T> {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        synchronized(openResources) {
            openResources.forEach { it.close() }
            openResources.clear()
        }
    }
}

private class SnapshotStateExecutorObserver(val executor: Executor, private val block: () -> Unit) {
    private val observer =
        SnapshotStateObserver(onChangedExecutor = { callback -> executor.execute(callback) })

    private val onValueChanged = { _: Unit -> observeBlock() }

    private fun observeBlock() {
        observer.observeReads(
            // Scope would only need to be used if we wanted to pass different data to
            // onValueChangedInBlock
            scope = Unit,
            onValueChangedForScope = onValueChanged,
            block = block,
        )
    }

    fun start(): Closeable {
        executor.execute {
            observer.start()
            observeBlock()
        }
        return Closeable { observer.stop() }
    }
}

private class SnapshotStateCoroutineObserver(
    val scope: CoroutineScope,
    private val block: () -> Unit,
) {
    private val changeCallbacks = Channel<() -> Unit>(Channel.UNLIMITED)

    private val observer = SnapshotStateObserver { callback -> changeCallbacks.trySend(callback) }

    private val onValueChanged = { _: Unit -> observeBlock() }

    private fun observeBlock() {
        observer.observeReads(
            // Scope would only need to be used if we wanted to pass different data to
            // onValueChangedInBlock
            scope = Unit,
            onValueChangedForScope = onValueChanged,
            block = block,
        )
    }

    fun start(): Closeable {
        val job =
            scope.launch {
                observer.start()
                try {
                    observeBlock()

                    // Process changes until cancelled:
                    for (callback in changeCallbacks) {
                        callback()
                    }
                } finally {
                    observer.stop()
                }
            }
        return Closeable { job.cancel() }
    }
}
