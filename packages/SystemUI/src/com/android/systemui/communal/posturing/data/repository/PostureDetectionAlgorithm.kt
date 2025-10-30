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

package com.android.systemui.communal.posturing.data.repository

import android.hardware.Sensor
import android.hardware.SensorEvent
import com.android.systemui.communal.posturing.data.model.PositionState
import com.android.systemui.communal.posturing.shared.model.ConfidenceLevel
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.PostureDetectionLog
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.abs
import kotlin.math.cos
import kotlin.time.Duration.Companion.milliseconds

/**
 * Algorithm to determine if the device is "postured" (stationary and upright) from accelerometer
 * sensor events.
 *
 * The algorithm works by analyzing a stream of accelerometer events over a sliding window to
 * determine two key states: whether the device is stationary and whether it is in an upright
 * orientation. A device is considered "postured" only if it is both stationary and upright.
 *
 * ### How it works:
 * 1. **Data Collection:**
 *     - The algorithm collects accelerometer samples in a sliding window of
 *       [SAMPLE_WINDOW_DURATION_MS].
 *     - It requires a minimum number of samples ([MIN_SAMPLES]) within this window to ensure the
 *       data is reliable.
 * 2. **Stationary Detection (`computeStationaryConfidence`):**
 *     - The first step is to determine if the device is moving. This is done by calculating the
 *       variance of the accelerometer readings on each axis (x, y, z) over the collected samples.
 *     - A low variance indicates that the device is likely stationary. The total variance is
 *       compared against predefined thresholds to determine a confidence level. If the variance is
 *       high, the device is considered not stationary, and the algorithm concludes that it cannot
 *       be postured.
 *     - This step also computes the average acceleration vector, which, for a stationary device,
 *       primarily represents the direction and magnitude of Earth's gravity.
 * 3. **Posture Detection (`computeIsPostured`):**
 *     - This step is only performed if the device is determined to be stationary.
 *     - It uses the average acceleration (gravity) vector to determine the device's specific
 *       orientation by calling `computeOrientation`.
 *     - `computeOrientation` identifies which axis (X, Y, or Z) has the dominant acceleration
 *       component to determine if the device is face up/down, or resting on one of its edges.
 *     - `computeIsPostured` then checks if the orientation is one of the desired postured states:
 *       resting on its bottom, left, or right edge.
 *     - The final postured confidence is then set based on this check. If the device is in a
 *       postured orientation, the confidence level mirrors the stationary confidence. Otherwise, it
 *       is negative.
 *
 * ### Output:
 * The algorithm produces a [PositionState], which encapsulates the confidence levels for both the
 * stationary and upright states.
 */
class PostureDetectionAlgorithm
@Inject
constructor(
    private val clock: SystemClock,
    @Named(POSTURE_DETECTION_FLAT_ANGLE_TH) flatAngleThreshold: Int,
    @Named(POSTURE_DETECTION_UPRIGHT_ANGLE_TH) uprightAngleThreshold: Int,
    @PostureDetectionLog private val logBuffer: LogBuffer,
) {

    private val logger = Logger(logBuffer, TAG)

    private val samples = ArrayDeque<AccelSample>()

    private var prevLogInfo: PositionStateLogInfo? = null

    // Cosine of the angle threshold for flatness detection.
    private val flatAngleThresholdCos = cos(Math.toRadians(flatAngleThreshold.toDouble())).toFloat()

    // Minimum gravity on the Z-axis for flatness detection.
    private val flatGravityMin = GRAVITY * flatAngleThresholdCos

    // Maximum gravity on the Z-axis for flatness detection.
    private val flatGravityMax = GRAVITY * (1 + GRAVITY_HW_TOLERANCE)

    // Cosine of the angle threshold for upright detection.
    private val uprightAngleThresholdCos =
        cos(Math.toRadians(uprightAngleThreshold.toDouble())).toFloat()

    // Minimum gravity on the Y-axis for upright detection.
    private val uprightThresholdMin = GRAVITY * uprightAngleThresholdCos

    /**
     * Processes a new sensor event and returns the updated [PositionState] if a new state can be
     * determined.
     *
     * @param event The [SensorEvent] from the accelerometer.
     * @return A new [PositionState] or `null` if there is not enough data to make a determination.
     */
    fun onSensorChanged(event: SensorEvent?): PositionState? {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return null

        val now = clock.elapsedRealtime()
        samples.addLast(AccelSample(now, event.values[0], event.values[1], event.values[2]))

        // Remove old samples that are outside the sliding window.
        while (
            samples.isNotEmpty() &&
                (now - samples.first().timestamp > SAMPLE_WINDOW_DURATION.inWholeMilliseconds)
        ) {
            samples.removeFirst()
        }

        // We need a minimum number of samples to get an accurate reading.
        if (samples.size < MIN_SAMPLES) {
            return null
        }

        val (stationaryConfidence, avg, variance) = computeStationaryConfidence(samples)
        val (posturedConfidence, orientation) =
            if (stationaryConfidence is ConfidenceLevel.Positive) {
                computePosturedConfidence(avg, stationaryConfidence.confidence)
            } else {
                // If the device is moving, it cannot be postured.
                ConfidenceLevel.Negative(1.0f) to OrientationState.NONE
            }

        val positionState = PositionState(stationaryConfidence, posturedConfidence)
        val logInfo = PositionStateLogInfo(positionState, orientation)
        if (logInfo.isRelativelyDifferentFrom(prevLogInfo)) {
            logger.d({
                "stationary=$str1 postured=$str2 orientation=${OrientationState.entries[int1]} " +
                    "numSamples=$int2 $str3"
            }) {
                str1 = logInfo.positionState.stationary.toLogString()
                str2 = logInfo.positionState.postured.toLogString()
                // Storing avg and variance in one string since we're running out of slots
                str3 = "avg=$avg variance=$variance"
                int1 = logInfo.orientation.ordinal
                int2 = samples.size
            }
        }
        prevLogInfo = logInfo

        return positionState
    }

    /** Resets the internal state of the algorithm. */
    fun reset() {
        prevLogInfo = null
    }

    /**
     * Computes the confidence that the device is stationary.
     *
     * This function analyzes the variance of accelerometer readings over a window of samples. A
     * stationary device will have a constant acceleration vector (due to gravity), resulting in
     * very low variance. A moving or shaking device will exhibit high variance.
     *
     * The process is as follows:
     * 1. Calculate the mean and variance of the acceleration for each axis (x, y, z) across all
     *    samples.
     * 2. The total variance is compared against a series of thresholds:
     *     - If the variance on any single axis or the total variance is above
     *       [STATIONARY_ACCEL_VARIANCE_TH], the device is considered not stationary.
     *     - If the total variance is below [STATIONARY_ACCEL_VARIANCE_TH_HIGH_CONF], the device is
     *       considered stationary with high confidence.
     *     - If the total variance is below [STATIONARY_ACCEL_VARIANCE_TH_MED_CONF], the device is
     *       considered stationary with medium confidence.
     *     - Otherwise, it's considered stationary with low confidence.
     *
     * @param samples A list of [AccelSample] collected over a time window.
     * @return A [Pair] where the first element is the stationary [ConfidenceLevel] and the second
     *   is the average acceleration vector ([AccelVector]). The average vector is crucial for the
     *   subsequent orientation check, as it represents the direction of gravity when the device is
     *   stationary.
     */
    private fun computeStationaryConfidence(
        samples: List<AccelSample>
    ): Triple<ConfidenceLevel, AccelVector, AccelVector> {
        val numSamples = samples.size
        val sum = AccelVector.zero()
        val sumOfSquares = AccelVector.zero()
        val temp = AccelVector.zero()

        for (sample in samples) {
            // Avoid creating a new AccelVector for each iteration by reusing the same object.
            // This is a performance optimization since creating and garbage collecting a new
            // object is expensive in this tight loop.
            temp.set(sample.x, sample.y, sample.z)
            sum += temp
            sumOfSquares.addSquare(temp)
        }

        val avg = sum.copy()
        avg /= numSamples

        // Calculate variance in-place using the formula:
        // Var(X) = E[X^2] - (E[X])^2
        val variance = sumOfSquares
        variance /= numSamples

        // Reuse temp to calculate avg*avg and subtract from variance
        temp.set(avg.x, avg.y, avg.z)
        temp.componentwiseProductAssign(avg)
        variance -= temp

        val totalVariance = variance.sum()

        val stationaryConfidence =
            if (
                variance.x > STATIONARY_ACCEL_VARIANCE_TH ||
                    variance.y > STATIONARY_ACCEL_VARIANCE_TH ||
                    variance.z > STATIONARY_ACCEL_VARIANCE_TH ||
                    totalVariance > STATIONARY_ACCEL_VARIANCE_TH
            ) {
                ConfidenceLevel.Negative(1.0f)
            } else if (totalVariance < STATIONARY_ACCEL_VARIANCE_TH_HIGH_CONF) {
                ConfidenceLevel.Positive(1.0f)
            } else {
                // Linearly interpolate confidence between high confidence and negative thresholds.
                val confidence =
                    1.0f -
                        (totalVariance - STATIONARY_ACCEL_VARIANCE_TH_HIGH_CONF) /
                            (STATIONARY_ACCEL_VARIANCE_TH - STATIONARY_ACCEL_VARIANCE_TH_HIGH_CONF)
                ConfidenceLevel.Positive(confidence)
            }

        return Triple(stationaryConfidence, avg, variance)
    }

    /**
     * Determines if the device is in a postured state, which is defined as being upright resting on
     * its bottom, left, or right edge.
     *
     * @param avg The average acceleration vector.
     * @return A [Pair] where the first value is a boolean for whether the device is postured, and
     *   the second value is the [OrientationState].
     */
    private fun computeIsPostured(avg: AccelVector): Pair<Boolean, OrientationState> {
        val orientation = computeOrientation(avg)
        val isPostured =
            when (orientation) {
                OrientationState.POSTURED_BOTTOM_EDGE,
                OrientationState.POSTURED_LEFT_EDGE,
                OrientationState.POSTURED_RIGHT_EDGE -> true
                else -> false
            }
        return isPostured to orientation
    }

    /**
     * Computes the postured confidence and the device's orientation.
     *
     * This function first determines if the device is in a postured state (upright resting on its
     * bottom, left, or right edge) based on the average acceleration vector. If it is postured, the
     * confidence level mirrors the stationary confidence. Otherwise, it is negative.
     *
     * @param avg The average acceleration vector, representing the direction of gravity.
     * @param stationaryConfidence The confidence level that the device is stationary.
     * @return A [Pair] where the first element is the postured [ConfidenceLevel] and the second is
     *   the [OrientationState].
     */
    private fun computePosturedConfidence(
        avg: AccelVector,
        stationaryConfidence: Float,
    ): Pair<ConfidenceLevel, OrientationState> {
        val (isPostured, orientation) = computeIsPostured(avg)
        val posturedConfidence =
            if (isPostured) {
                ConfidenceLevel.Positive(stationaryConfidence)
            } else {
                ConfidenceLevel.Negative(stationaryConfidence)
            }
        return posturedConfidence to orientation
    }

    // Computes the orientation of the device based on the average acceleration vector.
    private fun computeOrientation(avg: AccelVector): OrientationState {
        val absX = abs(avg.x)
        val absY = abs(avg.y)
        val absZ = abs(avg.z)

        // If the device is within a certain range of gravity along the Z-axis, it is considered
        // face up or face down.
        if (absZ > flatGravityMin && absZ < flatGravityMax) {
            return if (avg.z > 0) OrientationState.FACE_UP else OrientationState.FACE_DOWN
        }

        // If the device is generally face down, it cannot be postured
        if (avg.z < 0) {
            return OrientationState.NONE
        }

        // If the device is not face up or face down, it is considered postured if it is resting
        // on one of its edges.
        return if (absX < 1.0f && absY > uprightThresholdMin) {
            if (avg.y > 0) OrientationState.POSTURED_BOTTOM_EDGE
            else OrientationState.POSTURED_TOP_EDGE
        } else if (absY < 1.0f && absX > uprightThresholdMin) {
            if (avg.x > 0) OrientationState.POSTURED_LEFT_EDGE
            else OrientationState.POSTURED_RIGHT_EDGE
        } else {
            OrientationState.NONE
        }
    }

    private data class AccelSample(val timestamp: Long, val x: Float, val y: Float, val z: Float)

    companion object {
        private const val TAG = "PostureDetectionAlgorithm"

        const val POSTURE_DETECTION_FLAT_ANGLE_TH = "posture_detection_flat_angle_th"

        const val POSTURE_DETECTION_UPRIGHT_ANGLE_TH = "posture_detection_upright_angle_th"

        /** Sensor sampling rate: 50Hz (20ms interval). */
        val SENSOR_SAMPLING_PERIOD_US = 20.milliseconds

        /** Duration of the sliding window for accelerometer samples (500ms). */
        private val SAMPLE_WINDOW_DURATION = 500.milliseconds

        /** Minimum number of samples required within the window to make a determination. */
        private const val MIN_SAMPLES = 10

        /**
         * Variance threshold for stationary detection. If the variance of acceleration exceeds
         * this, the device is considered to be moving. Based on `kStationaryAccelVarianceTh` in
         * `position_detector.h`.
         */
        private const val STATIONARY_ACCEL_VARIANCE_TH = 1.0f

        /** Variance threshold for high-confidence stationary state. */
        private const val STATIONARY_ACCEL_VARIANCE_TH_HIGH_CONF = 0.01f

        /** Approximate magnitude of Earth's gravity in m/s^2. */
        private const val GRAVITY = 9.8f

        /** Tolerance for gravity values when judging flatness. */
        private const val GRAVITY_HW_TOLERANCE = 0.1f
    }
}

/**
 * A an inline class that wraps a [FloatArray] of size 3 to represent a 3D vector. This is used to
 * avoid allocating new objects in performance-critical code.
 */
@JvmInline
private value class AccelVector(private val values: FloatArray) {
    /** The x component of the vector. */
    var x: Float
        get() = values[0]
        set(value) {
            values[0] = value
        }

    /** The y component of the vector. */
    var y: Float
        get() = values[1]
        set(value) {
            values[1] = value
        }

    /** The z component of the vector. */
    var z: Float
        get() = values[2]
        set(value) {
            values[2] = value
        }

    /** The sum of the components of the vector. */
    fun sum(): Float = x + y + z

    /** Sets the components of the vector. */
    fun set(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    /** Adds another vector to this vector. */
    operator fun plusAssign(other: AccelVector) {
        x += other.x
        y += other.y
        z += other.z
    }

    /** Subtracts another vector from this vector. */
    operator fun minusAssign(other: AccelVector) {
        x -= other.x
        y -= other.y
        z -= other.z
    }

    /** Divides this vector by a scalar. */
    operator fun divAssign(scalar: Int) {
        x /= scalar
        y /= scalar
        z /= scalar
    }

    /** Performs a component-wise multiplication of this vector with another vector. */
    fun componentwiseProductAssign(other: AccelVector) {
        x *= other.x
        y *= other.y
        z *= other.z
    }

    /** Adds the square of another vector to this vector. */
    fun addSquare(other: AccelVector) {
        x += other.x * other.x
        y += other.y * other.y
        z += other.z * other.z
    }

    /** Returns a copy of this vector. */
    fun copy() = AccelVector(values.clone())

    override fun toString(): String {
        return "($x, $y, $z)"
    }

    companion object {
        /** Returns a new vector with all components set to zero. */
        fun zero() = AccelVector(floatArrayOf(0f, 0f, 0f))
    }
}

/**
 * Contains logging information related to the device's position.
 *
 * @property orientation The orientation of the device.
 */
private data class PositionStateLogInfo(
    val positionState: PositionState,
    val orientation: OrientationState,
)

/** Represents the physical orientation of the device. */
private enum class OrientationState {
    /** The orientation could not be determined. */
    NONE,
    /** The device is lying flat on its back, screen facing up. */
    FACE_UP,
    /** The device is lying flat on its screen, screen facing down. */
    FACE_DOWN,
    /** The device is postured on its bottom edge. */
    POSTURED_BOTTOM_EDGE,
    /** The device is postured on its left edge. */
    POSTURED_LEFT_EDGE,
    /** The device is postured on its right edge. */
    POSTURED_RIGHT_EDGE,
    /** The device is postured on its top edge. */
    POSTURED_TOP_EDGE,
}

/** Returns true if the state has changed enough to warrant logging a new entry. */
private fun PositionState.isRelativelyDifferentFrom(other: PositionState): Boolean {
    return stationary.isRelativelyDifferentFrom(other.stationary) ||
        postured.isRelativelyDifferentFrom(other.postured)
}

/** Returns true if the confidence level is meaningfully different from [other]. */
private fun ConfidenceLevel.isRelativelyDifferentFrom(other: ConfidenceLevel): Boolean {
    return this is ConfidenceLevel.Positive != other is ConfidenceLevel.Positive ||
        abs(confidence - other.confidence) > 0.01f
}

/** Returns true if the debug info is meaningfully different from [other]. */
private fun PositionStateLogInfo.isRelativelyDifferentFrom(other: PositionStateLogInfo?): Boolean {
    return other == null ||
        orientation != other.orientation ||
        positionState.isRelativelyDifferentFrom(other.positionState)
}

/** Returns a simplified string representation of the confidence level for logging. */
private fun ConfidenceLevel.toLogString(): String {
    return "${this is ConfidenceLevel.Positive}($confidence)"
}
