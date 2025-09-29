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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private fun DpSize.toFixedConstraints(density: Density): Constraints =
    with(density) { Constraints.fixed(width.roundToPx(), height.roundToPx()) }

@RunWith(JUnit4::class)
class ContainerConfigTest {

    companion object {
        val DEFAULT_CONTAINER_CONFIG =
            ContainerConfig(
                sizePercentage = 0.5f,
                minSize = DpSize(900.dp, 700.dp),
                maxSize = DpSize(1200.dp, 850.dp),
            )

        val FIXED_SIZE_CONTAINER_CONFIG =
            ContainerConfig(
                sizePercentage = 0.5f,
                minSize = DpSize(1000.dp, 800.dp),
                maxSize = DpSize(1000.dp, 800.dp),
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

    @Test
    fun calculateContainerSize_appliesPercentage_withinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2000, 1600)
        val result = config.calculateContainerSize(testDensity, inputConstraints)
        assertThat(result).isEqualTo(Constraints.fixed(1000, 800))
    }

    @Test
    fun calculateContainerSize_coercesToMinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1400, 1000)
        val result = config.calculateContainerSize(testDensity, inputConstraints)
        assertThat(result).isEqualTo(config.minSize.toFixedConstraints(testDensity))
    }

    @Test
    fun calculateContainerSize_coercesToMaxBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2400, 1800)
        val result = config.calculateContainerSize(testDensity, inputConstraints)
        assertThat(result).isEqualTo(config.maxSize.toFixedConstraints(testDensity))
    }

    @Test
    fun calculateContainerSize_cappedByInputConstraints() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(800, 600)
        val result = config.calculateContainerSize(testDensity, inputConstraints)
        assertThat(result).isEqualTo(Constraints.fixed(800, 600))
    }

    @Test
    fun calculateContainerSize_infiniteInputConstraints_coercesToMaxBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val result = config.calculateContainerSize(testDensity, INFINITE_CONSTRAINTS)
        assertThat(result).isEqualTo(config.maxSize.toFixedConstraints(testDensity))
    }

    @Test
    fun calculateContainerSize_fixedSizeBounds() {
        val config = FIXED_SIZE_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1600, 1000)
        val result = config.calculateContainerSize(testDensity, inputConstraints)
        assertThat(result).isEqualTo(config.maxSize.toFixedConstraints(testDensity))
    }
}
