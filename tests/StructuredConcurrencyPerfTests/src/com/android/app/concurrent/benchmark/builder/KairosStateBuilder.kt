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

import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.MutableState
import com.android.systemui.kairos.State
import com.android.systemui.kairos.combine as combineKairosState
import com.android.systemui.kairos.launchKairosNetwork
import com.android.systemui.kairos.map as mapKairosState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ExperimentalKairosApi
class KairosStateBuilder<T>(val scope: CoroutineScope) :
    StateBuilder<MutableState<T>, State<T>, T> {

    val kairosNetwork = scope.launchKairosNetwork()

    private val stateWriter =
        object : StateBuilder.WriteContext<MutableState<T>, T> {
            override fun MutableState<T>.update(newValue: T) {
                setValue(newValue)
            }
        }

    override fun createMutableState(initialValue: T): MutableState<T> {
        return MutableState(kairosNetwork, initialValue)
    }

    override fun writeScope(block: StateBuilder.WriteContext<MutableState<T>, T>.() -> Unit) {
        scope.launch { kairosNetwork.transact { with(stateWriter) { block() } } }
    }

    override fun readScope(block: StateBuilder.ReadContext<State<T>, T>.() -> Unit) {
        scope.launch {
            kairosNetwork.activateSpec {
                with(
                    object : StateBuilder.ReadContext<State<T>, T> {
                        override fun State<T>.observe(callback: (T) -> Unit) {
                            observeSync { callback(it) }
                        }
                    }
                ) {
                    block()
                }
            }
        }
    }

    override fun State<T>.mapState(transform: (T) -> T): State<T> {
        return mapKairosState { transform(it) }
    }

    override fun combineState(a: State<T>, b: State<T>, transform: (T, T) -> T): State<T> {
        return combineKairosState(a, b) { aVal, bVal -> transform(aVal, bVal) }
    }

    override fun combineState(
        a: State<T>,
        b: State<T>,
        c: State<T>,
        transform: (T, T, T) -> T,
    ): State<T> {
        return combineKairosState(a, b, c) { aVal, bVal, cVal -> transform(aVal, bVal, cVal) }
    }
}
