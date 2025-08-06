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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.plugins.annotations.ProtectedInterface
import com.android.systemui.plugins.annotations.SimpleProperty
import com.android.systemui.plugins.annotations.ThrowsOnFailure

/** Element Composable together with some metadata about the function. */
@Stable
@ProtectedInterface
interface LockscreenElement {
    @get:SimpleProperty
    /** Key of identifying this lockscreen element */
    val key: ElementKey

    @get:SimpleProperty
    /** Context override for the composable */
    val context: Context

    @Composable
    @ThrowsOnFailure
    /** Compose function which renders this element */
    fun ContentScope.LockscreenElement(
        factory: LockscreenElementFactory,
        context: LockscreenElementContext,
    )
}
