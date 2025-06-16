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
@file:OptIn(ExperimentalKairosApi::class)

package com.android.app.concurrent.benchmark

import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark
import com.android.app.concurrent.benchmark.base.BaseCoroutineBenchmark.Companion.ExecutorThreadScopeBuilder
import com.android.app.concurrent.benchmark.base.StateCollectBenchmark
import com.android.app.concurrent.benchmark.base.StateCombineBenchmark
import com.android.app.concurrent.benchmark.base.times
import com.android.app.concurrent.benchmark.builder.BenchmarkWithStateProvider
import com.android.app.concurrent.benchmark.builder.KairosStateBuilder
import com.android.app.concurrent.benchmark.builder.StateBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.systemui.kairos.ExperimentalKairosApi
import kotlinx.coroutines.CoroutineScope
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@Ignore("ExperimentalKairosApi")
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KairosStateCombineBenchmark(threadParam: ThreadFactory<Any, CoroutineScope>) :
    BaseKairosStateBenchmark(threadParam), StateCombineBenchmark {

    companion object {
        @Parameters(name = "{0}") @JvmStatic fun getDispatchers() = threadBuilders
    }
}

@Ignore("ExperimentalKairosApi")
@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KairosStateCollectBenchmark(
    threadParam: ThreadFactory<Any, CoroutineScope>,
    override val producerCount: Int,
    override val consumerCount: Int,
) : BaseKairosStateBenchmark(threadParam), StateCollectBenchmark {

    companion object {
        @Parameters(name = "{0},{1},{2}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadScopeBuilder) *
                StateCollectBenchmark.PRODUCER_LIST *
                StateCollectBenchmark.CONSUMER_LIST
    }
}

abstract class BaseKairosStateBenchmark(threadParam: ThreadFactory<Any, CoroutineScope>) :
    BaseCoroutineBenchmark(threadParam), BenchmarkWithStateProvider {

    override fun <T> getStateBuilder(): StateBuilder<*, *, T> = KairosStateBuilder(bgScope)
}
