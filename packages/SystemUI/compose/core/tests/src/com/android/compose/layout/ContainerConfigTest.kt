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

package com.android.compose.layout

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ContainerConfigTest {

    companion object {
        val DEFAULT_CONTAINER_CONFIG =
            ContainerConfig(
                sizeFraction = 0.5f,
                minLongEdge = 900.dp,
                minShortEdge = 700.dp,
                maxLongEdge = 1200.dp,
                maxShortEdge = 850.dp,
            )

        val FIXED_SIZE_CONTAINER_CONFIG =
            ContainerConfig(
                sizeFraction = 0.5f,
                minLongEdge = 1000.dp,
                minShortEdge = 800.dp,
                maxLongEdge = 1000.dp,
                maxShortEdge = 800.dp,
            )

        val INFINITE_CONSTRAINTS =
            Constraints(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
    }

    // Use a density of 1.0f to make Dp to Px conversion straightforward (1.dp = 1px)
    val testDensity = Density(density = 1.0f)

    // --- Tests for ContainerLayout.HORIZONTAL ---

    @Test
    fun calculateContainerSize_horizontal_appliesFraction_withinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2000, 1600)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.HORIZONTAL)
        // Expected: width = 2000 * 0.5 = 1000 (within 900-1200), height = 1600 * 0.5 = 800 (within
        // 700-850)
        assertThat(result).isEqualTo(Constraints.fixed(1000, 800))
    }

    @Test
    fun calculateContainerSize_horizontal_coercesToMinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1400, 1000)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.HORIZONTAL)
        // Expected: width = 1400 * 0.5 = 700 -> coerced to 900 (minLong)
        // Expected: height = 1000 * 0.5 = 500 -> coerced to 700 (minShort)
        assertThat(result).isEqualTo(Constraints.fixed(900, 700))
    }

    @Test
    fun calculateContainerSize_horizontal_coercesToMaxBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2500, 1800)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.HORIZONTAL)
        // Expected: width = 2500 * 0.5 = 1250 -> coerced to 1200 (maxLong)
        // Expected: height = 1800 * 0.5 = 900 -> coerced to 850 (maxShort)
        assertThat(result).isEqualTo(Constraints.fixed(1200, 850))
    }

    @Test
    fun calculateContainerSize_horizontal_cappedByInputConstraints() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(800, 600)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.HORIZONTAL)
        // Min bounds (900, 700) are larger than input constraints, so result is capped by input.
        assertThat(result).isEqualTo(Constraints.fixed(800, 600))
    }

    @Test
    fun calculateContainerSize_horizontal_infiniteInputConstraints_coercesToMaxBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val result =
            config.calculateContainerSize(
                testDensity,
                INFINITE_CONSTRAINTS,
                ContainerLayout.HORIZONTAL,
            )
        assertThat(result).isEqualTo(Constraints.fixed(1200, 850))
    }

    @Test
    fun calculateContainerSize_horizontal_fixedSizeBounds() {
        val config = FIXED_SIZE_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1600, 1000)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.HORIZONTAL)
        assertThat(result).isEqualTo(Constraints.fixed(1000, 800))
    }

    // --- Tests for ContainerLayout.VERTICAL ---

    @Test
    fun calculateContainerSize_vertical_appliesFraction_withinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1600, 2000)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.VERTICAL)
        // Expected: width = 1600 * 0.5 = 800 (within 700-850), height = 2000 * 0.5 = 1000 (within
        // 900-1200)
        assertThat(result).isEqualTo(Constraints.fixed(800, 1000))
    }

    @Test
    fun calculateContainerSize_vertical_coercesToMinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1000, 1400)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.VERTICAL)
        // Expected: width = 1000 * 0.5 = 500 -> coerced to 700 (minShort)
        // Expected: height = 1400 * 0.5 = 700 -> coerced to 900 (minLong)
        assertThat(result).isEqualTo(Constraints.fixed(700, 900))
    }

    @Test
    fun calculateContainerSize_vertical_coercesToMaxBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1800, 2500)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.VERTICAL)
        // Expected: width = 1800 * 0.5 = 900 -> coerced to 850 (maxShort)
        // Expected: height = 2500 * 0.5 = 1250 -> coerced to 1200 (maxLong)
        assertThat(result).isEqualTo(Constraints.fixed(850, 1200))
    }

    @Test
    fun calculateContainerSize_vertical_cappedByInputConstraints() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(600, 800)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.VERTICAL)
        // Min bounds (700, 900) are larger than input constraints, so result is capped by input.
        assertThat(result).isEqualTo(Constraints.fixed(600, 800))
    }

    @Test
    fun calculateContainerSize_vertical_infiniteInputConstraints_coercesToMaxBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val result =
            config.calculateContainerSize(
                testDensity,
                INFINITE_CONSTRAINTS,
                ContainerLayout.VERTICAL,
            )
        assertThat(result).isEqualTo(Constraints.fixed(850, 1200))
    }

    @Test
    fun calculateContainerSize_vertical_fixedSizeBounds() {
        val config = FIXED_SIZE_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1000, 1600)
        val result =
            config.calculateContainerSize(testDensity, inputConstraints, ContainerLayout.VERTICAL)
        assertThat(result).isEqualTo(Constraints.fixed(800, 1000))
    }
}
