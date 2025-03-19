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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine as combineFlow
import kotlinx.coroutines.flow.map as mapFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MutableStateFlowBuilder<T>(val scope: CoroutineScope) :
    StateBuilder<MutableStateFlow<T>, StateFlow<T>, T> {

    private val stateWriter =
        object : StateBuilder.WriteContext<MutableStateFlow<T>, T> {
            override fun MutableStateFlow<T>.update(newValue: T) {
                value = newValue
            }
        }

    private val stateReader =
        object : StateBuilder.ReadContext<StateFlow<T>, T> {
            override fun StateFlow<T>.observe(callback: (T) -> Unit) {
                scope.launch { collect { callback(it) } }
            }
        }

    override fun createMutableState(initialValue: T): MutableStateFlow<T> {
        return MutableStateFlow(initialValue)
    }

    override fun writeScope(block: StateBuilder.WriteContext<MutableStateFlow<T>, T>.() -> Unit) {
        with(stateWriter) { block() }
    }

    override fun readScope(block: StateBuilder.ReadContext<StateFlow<T>, T>.() -> Unit) {
        with(stateReader) { block() }
    }

    override fun StateFlow<T>.mapState(transform: (T) -> T): StateFlow<T> {
        // To make this comparable with other `StateBuilder` implementations, call
        // StateIn. This also prevents operator fusion.
        val initialValue = transform(value)
        return mapFlow { transform(it) }.stateIn(scope, SharingStarted.Lazily, initialValue)
    }

    override fun combineState(
        a: StateFlow<T>,
        b: StateFlow<T>,
        transform: (T, T) -> T,
    ): StateFlow<T> {
        return combineFlow(a, b) { aVal, bVal -> transform(aVal, bVal) }
            .stateIn(scope, SharingStarted.Lazily, transform(a.value, b.value))
    }

    override fun combineState(
        a: StateFlow<T>,
        b: StateFlow<T>,
        c: StateFlow<T>,
        transform: (T, T, T) -> T,
    ): StateFlow<T> {
        return combineFlow(a, b, c) { aVal, bVal, cVal -> transform(aVal, bVal, cVal) }
            .stateIn(scope, SharingStarted.Lazily, transform(a.value, b.value, c.value))
    }
}
