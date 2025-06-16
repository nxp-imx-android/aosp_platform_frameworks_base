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
package com.android.app.concurrent.benchmark.base

import android.device.collectors.util.SendToInstrumentation
import android.os.Bundle
import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.PerfettoTraceRule
import androidx.benchmark.macro.runSingleSessionServer
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.benchmark.traceprocessor.PerfettoTrace
import androidx.benchmark.traceprocessor.Row
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.concurrent.benchmark.builder.ConcurrentBenchmarkBuilder
import com.android.app.concurrent.benchmark.builder.StateChecker
import com.android.app.concurrent.benchmark.util.BARRIER_TIMEOUT_MILLIS
import com.android.app.concurrent.benchmark.util.CsvMetricCollector
import com.android.app.concurrent.benchmark.util.CsvMetricCollector.Helper.getCurrentBgThreadName
import com.android.app.concurrent.benchmark.util.CyclicCountDownBarrier
import com.android.app.concurrent.benchmark.util.DEBUG
import com.android.app.concurrent.benchmark.util.PERFETTO_CONFIG
import com.android.app.concurrent.benchmark.util.PERFETTO_SQL_QUERY_FORMAT_STR
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

private const val TAG = "ConcurrentBenchmarkRule"

class ConcurrentBenchmarkRule() : TestRule {

    val benchmarkRule = BenchmarkRule()

    @OptIn(ExperimentalPerfettoCaptureApi::class)
    override fun apply(base: Statement, description: Description): Statement {
        val traceCallback: ((PerfettoTrace) -> Unit) = { trace ->
            TraceProcessor.runSingleSessionServer(trace.path) {
                if (DEBUG) return@runSingleSessionServer
                val rowSequence =
                    query(String.format(PERFETTO_SQL_QUERY_FORMAT_STR, getCurrentBgThreadName()))
                val row = rowSequence.firstOrNull() ?: return@runSingleSessionServer
                val results = Bundle()
                var allMetricsValid = true
                row.keys.forEach { key ->
                    allMetricsValid = putValueFromRow(results, row, key) && allMetricsValid
                }
                CsvMetricCollector.Helper.clearActiveName()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                SendToInstrumentation.sendBundle(instrumentation, results)
                if (!allMetricsValid) {
                    error(
                        "Trace has data loss or other errors. For more details, " +
                            "open the trace in Perfetto and view its info and stats."
                    )
                }
            }
        }
        return RuleChain.outerRule(::applyInternal)
            .around(benchmarkRule)
            .around(
                PerfettoTraceRule(
                    config = PerfettoConfig.Text(PERFETTO_CONFIG),
                    enableUserspaceTracing = false,
                    traceCallback = traceCallback,
                )
            )
            .apply(base, description)
    }

    private fun applyInternal(base: Statement, description: Description) =
        object : Statement() {
            override fun evaluate() {
                CsvMetricCollector.Helper.setActiveName(
                    description.testClass.simpleName,
                    description.methodName,
                )
                base.evaluate()
            }
        }

    fun measureRepeated(
        unsafeInitialSetup: List<() -> Unit>,
        synchronizedBgWork: List<CyclicCountDownBarrier.Builder> = listOf(),
        mainBlock: (Int) -> Unit,
        stateChecker: StateChecker = StateChecker.NoOpStateChecker,
        afterLastIteration: () -> Unit,
    ) {
        // Each thread should call `CyclicBarrier.await()` when all work it expected to do is
        // completed, including the main thread (named "BenchmarkRunner"), so create a
        // `CyclicBarrier` with a party count matching the number of threads.
        unsafeInitialSetup.forEach { it() }
        var n = 0
        val barrier = CyclicBarrier(synchronizedBgWork.size + 1) // +1 for the main thread
        val bgContexts =
            synchronizedBgWork.mapNotNull {
                val context = it.build(barrier)
                context.runOnce(n)
                if (it.runOnEachIteration) context else null
            }
        try {
            // wait for all bg setup to be completed
            barrier.await(BARRIER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            fail("Timeout while awaiting initial setup")
            throw e
        }

        // No need to set THREAD_PRIORITY_MOST_FAVORABLE here; it is already set by the benchmark
        // initialization in AndroidX
        val state = benchmarkRule.getState()
        while (state.keepRunning()) {
            Assert.assertEquals(
                "Barrier should have 0 parties awaiting before mainBlock runs",
                0,
                barrier.numberWaiting,
            )
            n++
            bgContexts.forEach { it.runOnce(n) }
            mainBlock(n)
            try {
                barrier.await(BARRIER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                fail("Timeout while awaiting iteration #$n")
                throw e
            }
            if (!stateChecker.isInExpectedState(n)) {
                var message = "Benchmark is not in expected state."
                message += " Expected (${stateChecker.expectedStr}) == true, "
                message += "but it evaluated to false instead (${stateChecker.expectedCalc(n)})."
                fail(message)
            }
        }
        afterLastIteration()
    }

    fun runBenchmark(build: ConcurrentBenchmarkBuilder.() -> Unit) {
        with(ConcurrentBenchmarkBuilder()) {
            build()
            measure()
        }
    }
}

private fun putValueFromRow(bundle: Bundle, row: Row, key: String): Boolean {
    // Key name for Perfetto metrics computed by looking at each measurement slice, e.g.
    // "measurement 0", "measurement 1", "measurement 2", etc.
    // mt = "measurement timeline"
    val metricName = "perfetto_mt_$key"
    val metricValue = row[key]
    val strValue =
        when (metricValue) {
            is String,
            is Int,
            is Long -> "$metricValue"
            is Float,
            is Double -> String.format("%.3f", metricValue)
            null -> {
                Log.w(TAG, "Metric not found for key: $key")
                null
            }
            else -> {
                Log.w(TAG, "Unsupported metric type: ${metricValue::class}")
                null
            }
        }
    if (strValue != null) {
        bundle.putString(metricName, strValue)
        CsvMetricCollector.Helper.putMetric(metricName, strValue)
    }
    return strValue != "=NA()"
}
