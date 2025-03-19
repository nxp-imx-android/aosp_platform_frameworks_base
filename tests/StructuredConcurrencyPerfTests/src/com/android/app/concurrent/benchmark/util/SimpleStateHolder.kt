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
package com.android.app.concurrent.benchmark.util

import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.fail

class SimpleStateHolder<T>(initialValue: T) {

    fun interface Callback<T> {
        fun onChange(newValue: T)
    }

    private val listeners = mutableListOf<Pair<Executor, Callback<T>>>()

    fun addListener(
        executor: Executor,
        notifyInitial: Boolean = true,
        listener: Callback<T>,
    ): Pair<Executor, Callback<T>> {
        if (notifyInitial) {
            executor.execute { listener.onChange(value) }
        }
        val handle = Pair(executor, listener)
        synchronized(listeners) { listeners.add(Pair(executor, listener)) }
        return handle
    }

    fun removeListener(handle: Pair<Executor, Callback<T>>) {
        synchronized(listeners) { listeners.remove(handle) }
    }

    var value: T = initialValue
        set(value) {
            if (field == value) return
            field = value
            synchronized(listeners) {
                listeners.forEach { listener ->
                    listener.first.execute { listener.second.onChange(value) }
                }
            }
        }
}

fun <T, R> SimpleStateHolder<T>.mapSimpleState(
    executor: Executor,
    block: (T) -> R,
): SimpleStateHolder<R> {
    val mapped = SimpleStateHolder(block(value))
    addListener(executor, false) { mapped.value = block(it) }
    return mapped
}

fun <T, R> SimpleStateHolder<T>.flatMapLatest(
    executor: Executor,
    block: (T) -> SimpleStateHolder<R>,
): SimpleStateHolder<R> {
    var currentState = block(value)
    val flatMapped = SimpleStateHolder(currentState.value)
    var listenerHandle = currentState.addListener(executor, false) { flatMapped.value = it }
    addListener(executor, false) {
        val newStateHolder = block(it)
        if (currentState != newStateHolder) {
            currentState.removeListener(listenerHandle)
            currentState = newStateHolder
            listenerHandle = currentState.addListener(executor) { flatMapped.value = it }
        }
    }
    return flatMapped
}

fun <T1, T2, R> combineSimpleState(
    executor: Executor,
    a: SimpleStateHolder<T1>,
    b: SimpleStateHolder<T2>,
    block: (T1, T2) -> R,
): SimpleStateHolder<R> {
    val combined = SimpleStateHolder(block(a.value, b.value))
    a.addListener(executor, false) { combined.value = block(it, b.value) }
    b.addListener(executor, false) { combined.value = block(a.value, it) }
    return combined
}

fun <T1, T2, T3, R> combineSimpleState(
    executor: Executor,
    a: SimpleStateHolder<T1>,
    b: SimpleStateHolder<T2>,
    c: SimpleStateHolder<T3>,
    block: (T1, T2, T3) -> R,
): SimpleStateHolder<R> {
    val combined = SimpleStateHolder(block(a.value, b.value, c.value))
    a.addListener(executor, false) { combined.value = block(it, b.value, c.value) }
    b.addListener(executor, false) { combined.value = block(a.value, it, c.value) }
    c.addListener(executor, false) { combined.value = block(a.value, b.value, it) }
    return combined
}

interface SimpleSuspendableObserver<T> {
    suspend fun awaitNextValue(): T

    fun cancel()
}

fun <T> SimpleStateHolder<T>.asSuspendableObserver(
    executor: Executor
): SimpleSuspendableObserver<T> {
    var activeContinuation: Continuation<T>? = null
    addListener(executor, notifyInitial = false) { newValue ->
        val needsDispatch = activeContinuation
        activeContinuation = null
        needsDispatch?.resume(newValue)
    }
    return object : SimpleSuspendableObserver<T> {
        var initialValue: T? = value

        override suspend fun awaitNextValue(): T {
            // For the first call, report the initial value immediately:
            val v = initialValue
            return if (v != null) {
                initialValue = null
                // resume on designated thread:
                suspendCoroutine { c -> executor.execute { c.resume(v) } }
            } else {
                suspendCoroutine { c ->
                    if (activeContinuation != null) {
                        throw IllegalStateException("Only one awaiter permitted at a time.")
                    } else {
                        activeContinuation = c
                    }
                }
            }
        }

        override fun cancel() {
            activeContinuation?.resumeWithException(CancellationException())
        }
    }
}

// Similar concept to SynchronousQueue; can only pass one value at a time, and can only pass
// values if actively being listened to
class SimpleSynchronousState<T>() {
    var nextInput: Continuation<T>? = null

    fun putValueOrThrow(newValue: T) {
        val c = nextInput
        if (c != null) {
            nextInput = null
            c.resume(newValue)
        } else {
            fail("No one is awaiting. Can't send new value if there are no listeners.")
        }
    }

    suspend fun awaitValue(): T {
        return suspendCoroutine { continuation ->
            if (nextInput != null) {
                fail("Already awaiting. Can't override next continuation")
            }
            nextInput = continuation
        }
    }
}
