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

import android.util.Log
import java.lang.System.identityHashCode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

open class SimpleParam(@JvmField val symbol: String) {
    override fun toString(): String = symbol
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified R> R.instanceName(): String {
    return if (VERBOSE_DEBUG) {
        "${this::class.java.name.substringAfterLast(".")}@${identityHashCode(this).toHexString()}"
    } else {
        ""
    }
}

inline fun <reified R : CoroutineContext> R.toDbgString(): String =
    "${this.instanceName()}[" +
        fold("") { acc, element ->
            if (acc.isEmpty()) "${element.key}=${element.instanceName()}" else "$acc, $element"
        } +
        "]"

@OptIn(ExperimentalContracts::class)
inline fun <reified R> R.dbg(tr: Throwable? = null, msg: () -> String) {
    contract { callsInPlace(msg, InvocationKind.AT_MOST_ONCE) }
    if (VERBOSE_DEBUG) {
        @OptIn(ExperimentalStdlibApi::class) Log.v(TAG, "${instanceName()}: ${msg()}", tr)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun dbg(tr: Throwable? = null, msg: () -> String) {
    contract { callsInPlace(msg, InvocationKind.AT_MOST_ONCE) }
    if (VERBOSE_DEBUG) {
        @OptIn(ExperimentalStdlibApi::class) Log.v(TAG, msg(), tr)
    }
}

/**
 * Compute the cartesian product of the left and right, useful for testing all combinations of
 * parameterized tests.
 */
operator fun <T1, T2> Iterable<T1>.times(other: Iterable<T2>): Iterable<Array<Any?>> {
    return flatMap { leftValue ->
        other.map { rightValue ->
            if (leftValue is Array<*>) {
                arrayOf(*leftValue, rightValue)
            } else {
                arrayOf(leftValue, rightValue)
            }
        }
    }
}

/** Helper class to prevent JUnit from adding commas to int param names */
class IntParam(val value: Int) {
    override fun toString(): String {
        return value.toString()
    }
}

internal val CONSUME_CPU_NONE = IntParam(0)
internal val CONSUME_CPU_SMALL = IntParam(250)
internal val CONSUME_CPU_MEDIUM = IntParam(5_000)
internal val CONSUME_CPU_LARGE = IntParam(10_000)
val allConsumeCpuParams =
    listOf(CONSUME_CPU_NONE, CONSUME_CPU_SMALL, CONSUME_CPU_MEDIUM, CONSUME_CPU_LARGE)

internal fun consumeCpu(iterations: IntParam): Double {
    var accumulator = 123456789.12345678
    repeat(iterations.value) {
        accumulator /= 1.000001
        accumulator += 0.000000001
    }
    return accumulator
}
