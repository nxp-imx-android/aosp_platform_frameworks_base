/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.keyguard.ui.composable.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.ElementKey

@Immutable
/** Factory to build composable lockscreen elements based on keys */
interface LockscreenElementFactory {
    /**
     * Finds and renders the composable element at the specified key.
     *
     * @return true if the element was found and rendered, otherwise false.
     */
    @Composable
    fun lockscreenElement(
        key: ElementKey,
        context: LockscreenElementContext,
        modifier: Modifier,
    ): Boolean

    companion object {
        /**
         * Finds and renders the composable at the specified key with the default modifier. This is
         * provided because compose doesn't seem to like interface methods with default arguments.
         */
        @Composable
        fun LockscreenElementFactory.lockscreenElement(
            key: ElementKey,
            context: LockscreenElementContext,
        ): Boolean {
            return lockscreenElement(key, context, Modifier)
        }
    }
}
