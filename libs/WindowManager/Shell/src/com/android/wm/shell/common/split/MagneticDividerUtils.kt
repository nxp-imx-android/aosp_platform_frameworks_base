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
import com.android.mechanics.spec.Breakpoint
import com.android.mechanics.spec.Breakpoint.Companion.maxLimit
import com.android.mechanics.spec.Breakpoint.Companion.minLimit
import com.android.mechanics.spec.BreakpointKey
import com.android.mechanics.spec.DirectionalMotionSpec
import com.android.mechanics.spec.Guarantee
import com.android.mechanics.spec.Mapping
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spring.SpringParameters.Companion.Snap
import com.android.wm.shell.common.pip.PipUtils
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget

/**
 * Utility class used to create a framework that enables the divider to snap magnetically to snap
 * points while the user is dragging it.
 */
class MagneticDividerUtils {
    companion object {
        /**
         * The size of the "snap zone" (a zone around the snap point that attracts the divider.)
         * In dp.
         */
        private const val MAGNETIC_ZONE_SIZE = 30f

        @JvmStatic
        fun generateMotionSpec(targets: List<SnapTarget>, res: Resources): MotionSpec {
            val breakpoints: MutableList<Breakpoint> = ArrayList()
            val mappings: MutableList<Mapping> = ArrayList()

            // Add the "min" breakpoint, the "max" breakpoint, and 2 breakpoints for each snap point
            // (for a total of n breakpoints).
            // Add n-1 mappings that go between the breakpoints
            breakpoints.add(minLimit)
            mappings.add(Mapping.Identity)
            for (i in targets.indices) {
                val t: SnapTarget = targets[i]
                val halfZoneSizePx = PipUtils.dpToPx(MAGNETIC_ZONE_SIZE, res.displayMetrics) / 2f
                val startOfZone = t.position - halfZoneSizePx
                val endOfZone = t.position + halfZoneSizePx

                val startOfMagneticZone = Breakpoint(
                    BreakpointKey("snapzone$i::start", startOfZone),
                    startOfZone,
                    Snap,
                    Guarantee.None
                )
                val endOfMagneticZone = Breakpoint(
                    BreakpointKey("snapzone$i::end", endOfZone),
                    endOfZone,
                    Snap,
                    Guarantee.None
                )

                breakpoints.add(startOfMagneticZone)
                mappings.add(Mapping.Fixed(t.position.toFloat()))
                breakpoints.add(endOfMagneticZone)
                mappings.add(Mapping.Identity)
            }
            breakpoints.add(maxLimit)

            return MotionSpec(DirectionalMotionSpec(breakpoints, mappings))
        }
    }
}