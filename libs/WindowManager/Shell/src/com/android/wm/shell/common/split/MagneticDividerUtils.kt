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

package com.android.wm.shell.common.split

import android.content.res.Resources
import androidx.compose.ui.unit.dp
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spec.builder.spatialDirectionalMotionSpec
import com.android.mechanics.spec.with
import com.android.mechanics.spring.SpringParameters
import com.android.mechanics.view.standardViewMotionBuilderContext
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget

/**
 * Utility class used to create a framework that enables the divider to snap magnetically to snap
 * points while the user is dragging it.
 */
class MagneticDividerUtils {
    companion object {
        /**
         * When the user moves the divider towards or away from a snap point, a magnetic spring
         * movement and haptic will take place at this distance.
         */
        private val DEFAULT_MAGNETIC_ATTACH_THRESHOLD = 56.dp
        /** The minimum spacing between snap zones, to prevent overlap on smaller displays. */
        private val MINIMUM_SPACE_BETWEEN_SNAP_ZONES = 4.dp
        /** The stiffness of the magnetic snap effect. */
        private const val ATTACH_STIFFNESS = 850f
        /** The damping ratio of the magnetic snap effect. */
        private const val ATTACH_DAMPING_RATIO = 0.95f
        /** The spring used for the magnetic snap effect. */
        private val MagneticSpring = SpringParameters(
            stiffness = ATTACH_STIFFNESS,
            dampingRatio = ATTACH_DAMPING_RATIO
        )
        /**
         * When inside the magnetic snap zone, the divider's movement is reduced by this amount.
         */
        private const val ATTACH_DETACH_SCALE = 0.5f
        /**
         * A key that can be passed into a MotionValue to retrieve the SnapPosition associated
         * with the current drag.
         */
        @JvmStatic val SNAP_POSITION_KEY = SemanticKey<Int?>()

        /**
         * Key used for identity regions which don't have drop zones associated with them.
         * Need to keep this key separate for the SemanticKeys we create with null values as it
         * seems like this overwrites the semantics created with real snapTarget values
         */
        @JvmStatic private val SNAP_POSITION_KEY_IDENTITY = SemanticKey<Int?>()

        /**
         * Create a MotionSpec that has "snap zones" for each of the SnapTargets provided.
         */
        @JvmStatic
        fun generateMotionSpec(targets: List<SnapTarget>, res: Resources): MotionSpec {
            // Create a new MotionSpec object and return it. A MotionSpec is composed of at least
            // one DirectionalMotionSpec, so below we will create a DirectionalMotionSpec and pass
            // it as the single argument to the MotionSpec constructor.
            return MotionSpec(
                // To do that, we first create a "context" object, which gives access to a
                // DirectionalMotionSpec builder and some convenience functions, like for converting
                // dp > px.
                with(standardViewMotionBuilderContext(res.displayMetrics.density)) {
                    // Inside of this "with" block, we can write code freely -- the final evaluated
                    // value of this block will be the value of the final expression we write. See
                    // Kotlin docs for more details.

                    // First, get the position of the left-most (or top-most) dismiss point.
                    val topLeftDismissTarget = targets.first()
                    val topLeftDismissPosition = topLeftDismissTarget.position.toFloat()
                    // Create a DirectionalMotionSpec using a pre-set builder method. We choose the
                    // "spatialDirectionalMotionSpec", which is meant for "spatial" movement (as
                    // opposed to "effects" movement).
                    spatialDirectionalMotionSpec(
                        initialMapping = Mapping.Fixed(topLeftDismissPosition),
                        semantics = listOf(SNAP_POSITION_KEY_IDENTITY with null),
                        defaultSpring = MagneticSpring
                    ) {
                        // NOTE: This block is a trailing lambda passed in as the "init" parameter.

                        // A DirectionalMotionSpec is essentially a number line from -infinity to
                        // infinity, with instructions on how to interpret the value at each point.
                        // We create each individual segment below to fill out our number line.

                        // Start by finding the smallest span between two targets and setting an
                        // appropriate magnetic snap threshold.
                        val smallestSpanBetweenTargets = targets.zipWithNext { t1, t2 ->
                            t2.position.toFloat() - t1.position.toFloat()
                        }.reduce { minSoFar, currentDiff ->
                            kotlin.math.min(minSoFar, currentDiff)
                        }
                        val availableSpaceForSnapZone = (smallestSpanBetweenTargets -
                                MINIMUM_SPACE_BETWEEN_SNAP_ZONES.toPx()) / 2f
                        val snapThreshold = kotlin.math.min(
                            DEFAULT_MAGNETIC_ATTACH_THRESHOLD.toPx(), availableSpaceForSnapZone)

                        // Our first breakpoint is located at topLeftDismissPosition. On the right
                        // side of this breakpoint, we'll use the "identity" instruction, which
                        // means values won't be converted.
                        identity(
                            breakpoint = topLeftDismissPosition,
                            semantics = listOf(SNAP_POSITION_KEY with null)
                        )

                        // We continue creating alternating zones of "identity" and
                        // "fractionalInputFromCurrent", which will give us the behavior we're
                        // looking for, where the divider can be dragged along normally in some
                        // areas (the identity zones) and resists the user's movement in some areas
                        // (the fractionalInputFromCurrent zones). The targets have to be created in
                        // ascending order.

                        // Iterating from the second target to the second-last target (EXCLUDING the
                        // first and last):
                        for (i in (1 until targets.size - 1)) {
                            val target = targets[i]
                            val targetPosition = target.position.toFloat()

                            // Create a fractionalInputFromCurrent zone.
                            fractionalInputFromCurrent(
                                breakpoint = targetPosition - snapThreshold,
                                // With every magnetic segment, we also pass in the associated
                                // snapPosition as a "semantic association", so we can later query
                                // the MotionValue for it.
                                semantics = listOf(SNAP_POSITION_KEY with target.snapPosition),
                                delta = snapThreshold * (1 - ATTACH_DETACH_SCALE),
                                fraction = ATTACH_DETACH_SCALE,
                            )

                            // Create another identity zone.
                            identity(
                                breakpoint = targetPosition + snapThreshold,
                                semantics = listOf(SNAP_POSITION_KEY_IDENTITY with null)
                            )
                        }

                        // Finally, create one last fixedValue zone, from the bottom/right
                        // dismiss point to infinity.
                        val bottomRightDismissTarget = targets.last()
                        val bottomRightDismissPosition = bottomRightDismissTarget.position.toFloat()
                        fixedValue(
                            breakpoint = bottomRightDismissPosition,
                            value = bottomRightDismissPosition,
                            semantics = listOf(SNAP_POSITION_KEY_IDENTITY with null)
                        )
                    }
                }
            )
        }
    }
}
