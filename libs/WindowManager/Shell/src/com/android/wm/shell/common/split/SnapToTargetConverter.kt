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
import com.android.wm.shell.Flags
import com.android.wm.shell.R
import com.android.wm.shell.common.split.SplitTargetProvider.SplitTarget
import com.android.wm.shell.shared.split.SplitScreenConstants

/**
 * For a given snap mode, based on current feature flags and resource config values, generates list
 * of viable snap targets which should be accessed via the [SplitTargetProvider] interface
 */
class SnapToTargetConverter(val res: Resources,
                            isMinimizedMode: Boolean) : SplitTargetProvider {

    private var snapMode: Int = 0

    init {
        snapMode = if (Flags.enableFlexibleTwoAppSplit()) {
            DividerSnapAlgorithm.SNAP_FLEXIBLE_HYBRID
        } else {
            if (isMinimizedMode)
                DividerSnapAlgorithm.SNAP_MODE_MINIMIZED
            else
                res.getInteger(com.android.internal.R.integer.config_dockedStackDividerSnapMode)
        }
    }

    /** See [SplitScreenUtils.isLeftRightSplit] */
    private fun isLeftRightSplit() : Boolean {
        val mAllowLeftRightSplitInPortrait = SplitScreenUtils.allowLeftRightSplitInPortrait(res)
        return SplitScreenUtils.isLeftRightSplit(
            mAllowLeftRightSplitInPortrait,
            res.configuration
        )
    }

    /**
     * @param includeDismissal if true then the returned list will include the start and end
     *                         dismiss targets at the start and end of the list, respectively.
     * @return A [List] of [SplitTarget] for the device's current [snapMode], by ordering of left
     *         to right based on the different snap positions
     */
    override fun getTargets(includeDismissal: Boolean): List<SplitTarget> {
        val targets: MutableList<SplitTarget> = ArrayList()
        val areOffscreenRatiosSupported = SplitScreenUtils.allowOffscreenRatios(res)
        val isLeftRightSplit = isLeftRightSplit()
        if (includeDismissal) {
            targets.add(
                SplitTarget(
                    SplitScreenConstants.SNAP_TO_START_AND_DISMISS,
                    if (isLeftRightSplit) R.string.accessibility_action_divider_right_full else
                        R.string.accessibility_action_divider_bottom_full,
                    R.id.action_move_tl_full
                )
            )
        }
        when (snapMode) {
            DividerSnapAlgorithm.SNAP_ONLY_1_1 -> targets.add(
                SplitTarget(
                    SplitScreenConstants.SNAP_TO_2_50_50,
                    if (isLeftRightSplit) R.string.accessibility_action_divider_left_50 else
                        R.string.accessibility_action_divider_top_50,
                    R.id.action_move_tl_50
                )
            )

            DividerSnapAlgorithm.SNAP_MODE_MINIMIZED -> targets.add(
                SplitTarget(
                    SplitScreenConstants.SNAP_TO_MINIMIZE,
                    if (isLeftRightSplit) R.string.accessibility_action_divider_left_full else
                        R.string.accessibility_action_divider_top_full,
                    R.id.action_move_tl_full
                )
            )

            DividerSnapAlgorithm.SNAP_MODE_16_9, DividerSnapAlgorithm.SNAP_FIXED_RATIO ->
                targets.addAll(
                    getOnscreenTargets(isLeftRightSplit)
                )

            DividerSnapAlgorithm.SNAP_FLEXIBLE_SPLIT -> {
                if (areOffscreenRatiosSupported) {
                    targets.add(
                        SplitTarget(
                            SplitScreenConstants.SNAP_TO_2_10_90,
                            if (isLeftRightSplit) R.string.accessibility_action_divider_left_10
                            else R.string.accessibility_action_divider_top_10,
                            R.id.action_move_tl_90
                        )
                    )
                    targets.add(
                        SplitTarget(
                            SplitScreenConstants.SNAP_TO_2_50_50,
                            if (isLeftRightSplit) R.string.accessibility_action_divider_left_50
                            else R.string.accessibility_action_divider_top_50,
                            R.id.action_move_tl_50
                        )
                    )
                    targets.add(
                        SplitTarget(
                            SplitScreenConstants.SNAP_TO_2_90_10,
                            if (isLeftRightSplit) R.string.accessibility_action_divider_left_90
                            else R.string.accessibility_action_divider_top_90,
                            R.id.action_move_tl_10
                        )
                    )
                } else {
                    targets.addAll(getOnscreenTargets(isLeftRightSplit))
                }
            }

            DividerSnapAlgorithm.SNAP_FLEXIBLE_HYBRID -> {
                if (areOffscreenRatiosSupported) {
                    targets.add(
                        SplitTarget(
                            SplitScreenConstants.SNAP_TO_2_10_90,
                            if (isLeftRightSplit) R.string.accessibility_action_divider_left_10
                            else R.string.accessibility_action_divider_top_10,
                            R.id.action_move_tl_90
                        )
                    )
                    targets.addAll(getOnscreenTargets(isLeftRightSplit))
                    targets.add(
                        SplitTarget(
                            SplitScreenConstants.SNAP_TO_2_90_10,
                            if (isLeftRightSplit) R.string.accessibility_action_divider_left_90
                            else R.string.accessibility_action_divider_top_90,
                            R.id.action_move_tl_10
                        )
                    )
                } else {
                    targets.addAll(getOnscreenTargets(isLeftRightSplit))
                }
            }
            else -> throw IllegalStateException("unrecognized snap mode")
        }

        if (includeDismissal) {
            targets.add(
                SplitTarget(
                    SplitScreenConstants.SNAP_TO_END_AND_DISMISS,
                    if (isLeftRightSplit) R.string.accessibility_action_divider_left_full else
                        R.string.accessibility_action_divider_top_full,
                    R.id.action_move_rb_full
                )
            )
        }
        return targets
    }

    /**
     * Current snap mode of the device. You really probably shouldn't be using this. Exposed only
     * for the few use cases that actually need it.
     */
    override fun getSnapMode(): Int {
        return snapMode
    }

    /**
     * @return Default, 3 non-flex split targets. 30/50/70, in that specific order.
     */
    private fun getOnscreenTargets(isLeftRightSplit: Boolean): List<SplitTarget> {
        val targets: MutableList<SplitTarget> = java.util.ArrayList(3)
        targets.add(
            SplitTarget(
                SplitScreenConstants.SNAP_TO_2_33_66,
                if (isLeftRightSplit) R.string.accessibility_action_divider_left_30 else
                    R.string.accessibility_action_divider_top_30,
                R.id.action_move_tl_30
            )
        )
        targets.add(
            SplitTarget(
                SplitScreenConstants.SNAP_TO_2_50_50,
                if (isLeftRightSplit) R.string.accessibility_action_divider_left_50 else
                    R.string.accessibility_action_divider_top_50,
                R.id.action_move_tl_50
            )
        )
        targets.add(
            SplitTarget(
                SplitScreenConstants.SNAP_TO_2_66_33,
                if (isLeftRightSplit) R.string.accessibility_action_divider_left_70 else
                    R.string.accessibility_action_divider_top_70,
                R.id.action_move_tl_70
            )
        )
        return targets
    }
}