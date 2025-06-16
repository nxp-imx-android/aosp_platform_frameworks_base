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

import com.android.app.concurrent.benchmark.util.SimpleStateHolder
import com.android.app.concurrent.benchmark.util.combineSimpleState
import com.android.app.concurrent.benchmark.util.mapSimpleState
import java.util.concurrent.Executor

class SimpleStateHolderBuilder<T>(val executor: Executor) :
    StateBuilder<SimpleStateHolder<T>, SimpleStateHolder<T>, T> {

    private val writerContext =
        object : StateBuilder.WriteContext<SimpleStateHolder<T>, T> {
            override fun SimpleStateHolder<T>.update(newValue: T) {
                value = newValue
            }
        }

    private val readerContext =
        object : StateBuilder.ReadContext<SimpleStateHolder<T>, T> {
            override fun SimpleStateHolder<T>.observe(callback: (T) -> Unit) {
                addListener(executor) { callback(it) }
            }
        }

    override fun createMutableState(initialValue: T): SimpleStateHolder<T> {
        return SimpleStateHolder(initialValue)
    }

    override fun writeScope(block: StateBuilder.WriteContext<SimpleStateHolder<T>, T>.() -> Unit) {
        with(writerContext) { block() }
    }

    override fun readScope(block: StateBuilder.ReadContext<SimpleStateHolder<T>, T>.() -> Unit) {
        with(readerContext) { block() }
    }

    override fun SimpleStateHolder<T>.mapState(transform: (T) -> T): SimpleStateHolder<T> {
        return mapSimpleState(executor) { transform(it) }
    }

    override fun combineState(
        a: SimpleStateHolder<T>,
        b: SimpleStateHolder<T>,
        transform: (T, T) -> T,
    ): SimpleStateHolder<T> {
        return combineSimpleState(executor, a, b) { aVal, bVal -> transform(aVal, bVal) }
    }

    override fun combineState(
        a: SimpleStateHolder<T>,
        b: SimpleStateHolder<T>,
        c: SimpleStateHolder<T>,
        transform: (T, T, T) -> T,
    ): SimpleStateHolder<T> {
        return combineSimpleState(executor, a, b, c) { aVal, bVal, cVal ->
            transform(aVal, bVal, cVal)
        }
    }
}
