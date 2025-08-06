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

// TODO(b/432451019): move to .ui.composable subpackage
package com.android.systemui.plugins.clocks

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.plugins.annotations.GeneratedImport
import com.android.systemui.plugins.annotations.ProtectedInterface
import com.android.systemui.plugins.annotations.ProtectedReturn
import com.android.systemui.plugins.annotations.SimpleProperty
import kotlin.sequences.associateBy

/** Element Composable together with some metadata about the function. */
@Stable
// TODO(b/432451019): Protect interface elements
// @ProtectedInterface
interface LockscreenElement {
    @get:SimpleProperty
    /** Key of identifying this lockscreen element */
    val key: ElementKey

    @get:SimpleProperty
    /** Context override for the composable */
    val context: Context

    @Composable
    /** Compose function which renders this element */
    fun ContentScope.LockscreenElement(
        factory: LockscreenElementFactory,
        context: LockscreenElementContext,
    )
}

@Composable
/** Convenience method for building an element w/ the default Modifier */
fun ContentScope.Element(key: ElementKey, content: @Composable BoxScope.() -> Unit) {
    Element(key, Modifier, content)
}

/** Provides Lockscreen Elements for use by the lockscreen layout */
@ProtectedInterface
@GeneratedImport("java.util.ArrayList")
interface LockscreenElementProvider {
    @get:ProtectedReturn("return new ArrayList<LockscreenElement>();")
    /** Returns the clock views as a map of composable functions. */
    val elements: List<LockscreenElement>
}

/** Combined Element Composable argument class */
data class LockscreenElementContext(val scope: ContentScope, val burnInModifier: Modifier)

/** Factory to build composable lockscreen elements based on keys */
data class LockscreenElementFactory(
    // TODO(b/432451019): This may be better as an explicitly immutable map
    val elements: Map<ElementKey, LockscreenElement>
) {
    /**
     * Finds and renders the composable element at the specified key.
     *
     * @return true if the element was found and rendered, otherwise false.
     */
    @Composable
    fun lockscreenElement(
        key: ElementKey,
        context: LockscreenElementContext,
        modifier: Modifier = Modifier,
    ): Boolean {
        val element = elements[key]
        if (element == null) {
            return false
        }

        CompositionLocalProvider(LocalContext provides element.context) {
            with(context.scope) {
                Element(key = element.key, modifier = modifier) {
                    with(element) { LockscreenElement(this@LockscreenElementFactory, context) }
                }
            }
        }

        return true
    }

    companion object {
        /** Convenience method for building an element factory */
        fun build(builder: ((List<LockscreenElement>) -> Unit) -> Unit): LockscreenElementFactory {
            val map = mutableMapOf<ElementKey, LockscreenElement>()
            builder { map.putAll(it.associateBy { e -> e.key }) }
            return LockscreenElementFactory(map)
        }
    }
}
