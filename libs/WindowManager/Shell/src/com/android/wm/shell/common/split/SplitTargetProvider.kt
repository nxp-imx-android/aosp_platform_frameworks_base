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

import androidx.annotation.IdRes
import com.android.wm.shell.shared.split.SplitScreenConstants

/**
 * Access point via [SplitState] to get targets for split screen in the device's current
 * configuration
 */
interface SplitTargetProvider {

    /**
     * @return a [List] of SplitTargets from leftToRight or topToBottom order.
     * If [includeDismissal] is true then returning list will have start and end dismiss targets
     */
    fun getTargets(includeDismissal: Boolean): List<SplitTarget>

    /**
     * NOTE: If you are relying on this value more than [getTargets] above, you are probably doing
     * something wrong!
     *
     * @return The snap mode type for this device that will determine which targets are created.
     */
    fun getSnapMode(): Int

    class SplitTarget(
        @param:SplitScreenConstants.SnapPosition val snapPosition: Int,
        @param:android.annotation.StringRes val a11yActionString: Int,
        @param:IdRes val a11yActionId: Int
    )
}