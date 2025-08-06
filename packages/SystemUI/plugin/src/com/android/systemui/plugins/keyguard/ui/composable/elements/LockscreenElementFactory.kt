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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.log.core.Logger
import com.android.systemui.log.core.MessageBuffer
import com.android.systemui.plugins.keyguard.VRectF
import kotlin.sequences.associateBy

@Immutable
/** Factory to build composable lockscreen elements based on keys */
class LockscreenElementFactory(
    private val elements: Map<ElementKey, LockscreenElement>,
    messageBuffer: MessageBuffer,
) {
    private val logger = Logger(messageBuffer, LockscreenElementFactory::class.simpleName!!)

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
        if (!context.history.add(key)) {
            // TODO(b/432451019): Remove when more stable or upgrade to wtf log
            // Prevent crashes when the same element is rendered in two locations
            logger.e({ "Lockscreen Element has already been rendered: $str1" }) { str1 = "$key" }
            return false
        }

        val element = elements[key]
        if (element == null) {
            logger.e({ "No lockscreen element available at key: $str1" }) { str1 = "$key" }
            return false
        }

        CompositionLocalProvider(LocalContext provides element.context) {
            with(context.scope) {
                Element(
                    key = key,
                    modifier =
                        modifier.onGloballyPositioned { coordinates ->
                            context.onElementPositioned(key, VRectF(coordinates.boundsInWindow()))
                        },
                ) {
                    with(element) { LockscreenElement(this@LockscreenElementFactory, context) }
                }
            }
        }
        return true
    }

    companion object {
        /** Convenience method for building an element factory */
        fun build(
            messageBuffer: MessageBuffer,
            builder: ((List<LockscreenElement>) -> Unit) -> Unit,
        ): LockscreenElementFactory {
            val map = mutableMapOf<ElementKey, LockscreenElement>()
            builder { map.putAll(it.associateBy { e -> e.key }) }
            return LockscreenElementFactory(map, messageBuffer)
        }
    }
}
