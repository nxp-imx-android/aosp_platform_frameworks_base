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
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.thenIf
import kotlinx.coroutines.launch

/** State for a [DismissibleHorizontalPager] */
class DismissibleHorizontalPagerState(
    val isDismissible: Boolean,
    val isScrollingEnabled: Boolean,
    val pagerState: PagerState,
    val offset: Animatable<Float, AnimationVector1D>,
)

/**
 * Returns a remembered [DismissibleHorizontalPagerState] that starts at [initialPage] and has
 * [pageCount] total pages.
 */
@Composable
fun rememberDismissibleHorizontalPagerState(
    isDismissible: Boolean = true,
    isScrollingEnabled: Boolean = true,
    initialPage: Int = 0,
    pageCount: () -> Int,
): DismissibleHorizontalPagerState {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = pageCount)
    val offset = remember { Animatable(0f) }

    return remember(isDismissible, isScrollingEnabled, pagerState, offset) {
        DismissibleHorizontalPagerState(
            isDismissible = isDismissible,
            isScrollingEnabled = isScrollingEnabled,
            pagerState = pagerState,
            offset = offset,
        )
    }
}

/**
 * A [HorizontalPager] that can be swiped-away to dismiss by the user when swiped farther left or
 * right once fully scrolled to the left-most or right-most page, respectively.
 */
@Composable
fun DismissibleHorizontalPager(
    state: DismissibleHorizontalPagerState,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    key: ((Int) -> Any)? = null,
    pageSpacing: Dp = 0.dp,
    isFalseTouchDetected: Boolean,
    indicator: @Composable BoxScope.() -> Unit,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (state.offset.value > 0f && available.x < 0f) {
                    scope.launch { state.offset.snapTo(state.offset.value + available.x) }
                    Offset(available.x, 0f)
                } else if (state.offset.value < 0f && available.x > 0f) {
                    scope.launch { state.offset.snapTo(state.offset.value + available.x) }
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
                    scope.launch { state.offset.snapTo(state.offset.value + available.x) }
                    Offset(available.x, 0f)
                } else if (available.x < 0f) {
                    scope.launch { state.offset.snapTo(state.offset.value + available.x) }
                    Offset(available.x, 0f)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                scope.launch {
                    state.offset.animateTo(
                        if (state.offset.value >= state.pagerState.layoutInfo.pageSize / 2f) {
                            state.pagerState.layoutInfo.pageSize * 2f
                        } else if (
                            state.offset.value <= -state.pagerState.layoutInfo.pageSize / 2f
                        ) {
                            -state.pagerState.layoutInfo.pageSize * 2f
                        } else {
                            0f
                        }
                    )
                    if (state.offset.value != 0f) {
                        onDismissed()
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(modifier = modifier) {
        HorizontalPager(
            state = state.pagerState,
            userScrollEnabled = state.isScrollingEnabled && !isFalseTouchDetected,
            key = key,
            pageSpacing = pageSpacing,
            pageContent = pageContent,
            modifier =
                Modifier.thenIf(state.isDismissible) {
                    Modifier.nestedScroll(nestedScrollConnection).graphicsLayer {
                        translationX = state.offset.value
                    }
                },
        )

        indicator()
    }
}
