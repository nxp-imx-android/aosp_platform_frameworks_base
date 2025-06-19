/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.ambientcue.data.logger

import com.android.internal.util.FrameworkStatsLog
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject

/**
 * Data object corresponding to a AmbientCueEventReported atom.
 *
 * Fields must be kept in sync with stats/atoms/ambientcue/amibent_cue_extension_atoms.proto.
 */
data class AmbientCueEventReported(
    var displayDurationMillis: Long = 0L,
    var fulfilledWithMaIntentMillis: Long = 0L,
    var fulfilledWithMrIntentMillis: Long = 0L,
    var loseFocusMillis: Long = 0L,
    var maCount: Int = 0,
    var mrCount: Int = 0,
    var fulfilledWithMaIntent: Boolean = false,
    var fulfilledWithMrIntent: Boolean = false,
    var clickedCloseButton: Boolean = false,
    var reachedTimeout: Boolean = false,
)

/**
 * Interface for writing AmbientCueEventReported atoms to statsd log. Use AmbientCueLoggerImpl in
 * production.
 */
interface AmbientCueLogger {
    /**
     * Sets AmbientCue display events.
     *
     * @param maCount Number of ma actions generated and displayed.
     * @param mrCount Number of mr actions suggestions generated and displayed.
     */
    fun setAmbientCueDisplayStatus(maCount: Int, mrCount: Int)

    /**
     * Sets AmbientCue lose focus time.
     *
     * @param loseFocusMillis The time in milliseconds that the cue bar lost focus.
     */
    fun setLoseFocusMillis(loseFocusMillis: Long)

    /** Sets fulfilled with ma intent events. */
    fun setFulfilledWithMaStatus()

    /** Sets fulfilled with mr intent events. */
    fun setFulfilledWithMrStatus()

    /** Sets clicked close button events. */
    fun setClickedCloseButtonStatus()

    /** Sets reached timeout events. */
    fun setReachedTimeoutStatus()

    /** Flushes a AmbientCueEventReported atom. */
    fun flushAmbientCueEventReported()

    /** Clears all saved status. */
    fun clear()
}

/** Implementation for logging UI events related to controls. */
class AmbientCueLoggerImpl @Inject constructor(private val systemClock: SystemClock) :
    AmbientCueLogger {
    private var report = AmbientCueEventReported()
    private var displayTimeMillis: Long = 0L

    /** {@see AmbientCueLogger#setAmbientCueDisplayStatus} */
    override fun setAmbientCueDisplayStatus(maCount: Int, mrCount: Int) {
        this.displayTimeMillis = systemClock.currentTimeMillis()
        report.maCount = maCount
        report.mrCount = mrCount
    }

    /** {@see AmbientCueLogger#setLoseFocusMillis} */
    override fun setLoseFocusMillis(loseFocusMillis: Long) {
        // TODO(b/425279501): Count loseFocusMillis.
    }

    /** {@see AmbientCueLogger#setFulfilledWithMaStatus} */
    override fun setFulfilledWithMaStatus() {
        // TODO(b/425279501): Count fulfilledWithMaIntentMillis.
        report.fulfilledWithMaIntent = true
    }

    /** {@see AmbientCueLogger#setFulfilledWithMrStatus} */
    override fun setFulfilledWithMrStatus() {
        // TODO(b/425279501): Count fulfilledWithMrIntentMillis.
        report.fulfilledWithMrIntent = true
    }

    override fun setClickedCloseButtonStatus() {
        // TODO(b/425279501): Add logic to set clickedCloseButton
    }

    override fun setReachedTimeoutStatus() {
        // TODO(b/425279501): Add logic to set reachedTimeout
    }

    /** {@see AmbientCueLogger#flushAmbientCueEventReported} */
    override fun flushAmbientCueEventReported() {
        FrameworkStatsLog.write(
            FrameworkStatsLog.AMBIENT_CUE_EVENT_REPORTED,
            report.displayDurationMillis,
            report.fulfilledWithMaIntentMillis,
            report.fulfilledWithMrIntentMillis,
            report.loseFocusMillis,
            report.maCount,
            report.mrCount,
            report.fulfilledWithMaIntent,
            report.fulfilledWithMrIntent,
            report.clickedCloseButton,
            report.reachedTimeout,
        )
    }

    /** {@see AmbientCueLogger#clear} */
    override fun clear() {
        report = AmbientCueEventReported()
        displayTimeMillis = 0L
    }
}
