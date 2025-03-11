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

package com.android.systemui.media.remedia.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastRoundToInt
import kotlinx.coroutines.launch

/** Swipe to dismiss that supports nested scrolling. */
@Composable
fun SwipeToDismiss(
    content: @Composable (overscrollEffect: OverscrollEffect?) -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetAnimatable = remember { Animatable(0f) }

    // This is the width of the revealed content UI box. It's not a state because it's not
    // observed in any composition and is an object with a value to avoid the extra cost
    // associated with boxing and unboxing an int.
    val revealedContentBoxWidth = remember {
        object {
            var value = 0
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (offsetAnimatable.value > 0f && available.x < 0f) {
                    scope.launch { offsetAnimatable.snapTo(offsetAnimatable.value + available.x) }
                    Offset(available.x, 0f)
                } else if (offsetAnimatable.value < 0f && available.x > 0f) {
                    scope.launch { offsetAnimatable.snapTo(offsetAnimatable.value + available.x) }
                    Offset(available.x, 0f)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                return if (available.x > 0f) {
                    scope.launch { offsetAnimatable.snapTo(offsetAnimatable.value + available.x) }
                    Offset(available.x, 0f)
                } else if (available.x < 0f) {
                    scope.launch { offsetAnimatable.snapTo(offsetAnimatable.value + available.x) }
                    Offset(available.x, 0f)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                scope.launch {
                    offsetAnimatable.animateTo(
                        if (offsetAnimatable.value >= revealedContentBoxWidth.value / 2f) {
                            revealedContentBoxWidth.value * 2f
                        } else if (offsetAnimatable.value <= -revealedContentBoxWidth.value / 2f) {
                            -revealedContentBoxWidth.value * 2f
                        } else {
                            0f
                        }
                    )
                    if (offsetAnimatable.value != 0f) {
                        onDismissed()
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { revealedContentBoxWidth.value = it.width }
                .nestedScroll(nestedScrollConnection)
                .offset { IntOffset(x = offsetAnimatable.value.fastRoundToInt(), y = 0) }
    ) {
        content(null)
    }
}
