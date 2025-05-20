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

package com.android.systemui.ambientcue.ui.compose

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.compose.windowsizeclass.calculateWindowSizeClass
import com.android.systemui.ambientcue.ui.viewmodel.ActionViewModel
import com.android.systemui.ambientcue.ui.viewmodel.AmbientCueViewModel
import com.android.systemui.ambientcue.ui.viewmodel.PillStyleViewModel
import com.android.systemui.lifecycle.rememberViewModel

@Composable
fun AmbientCueContainer(
    modifier: Modifier = Modifier,
    ambientCueViewModelFactory: AmbientCueViewModel.Factory,
    onShouldInterceptTouches: (Boolean) -> Unit,
) {
    val viewModel = rememberViewModel("AmbientCueContainer") { ambientCueViewModelFactory.create() }

    val visible = viewModel.isVisible
    val expanded = viewModel.isExpanded
    val actions = viewModel.actions
    val pillStyle = viewModel.pillStyle

    LaunchedEffect(expanded) {
        onShouldInterceptTouches(expanded)
        if (expanded) {
            viewModel.cancelDeactivation()
        } else {
            viewModel.delayAndDeactivateCueBar()
        }
    }

    LaunchedEffect(actions) { viewModel.delayAndDeactivateCueBar() }

    Box(
        modifier.clickable(enabled = expanded, indication = null, interactionSource = null) {
            viewModel.collapse()
        }
    ) {
        when (pillStyle) {
            is PillStyleViewModel.NavBarPillStyle -> {
                NavBarAmbientCue(
                    viewModel = viewModel,
                    actions = actions,
                    visible = visible,
                    expanded = expanded,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            is PillStyleViewModel.ShortPillStyle -> {
                val pillCenterInWindow = pillStyle.position
                TaskBarAnd3ButtonAmbientCue(
                    viewModel = viewModel,
                    actions = actions,
                    visible = visible,
                    expanded = expanded,
                    pillCenterInWindow = pillCenterInWindow,
                    modifier =
                        if (pillCenterInWindow == null) {
                            Modifier.align(Alignment.BottomCenter)
                        } else {
                            Modifier
                        },
                )
            }
            is PillStyleViewModel.Uninitialized -> {}
        }
    }
}

@Composable
private fun TaskBarAnd3ButtonAmbientCue(
    viewModel: AmbientCueViewModel,
    actions: List<ActionViewModel>,
    visible: Boolean,
    expanded: Boolean,
    pillCenterInWindow: Rect?,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val portrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    var pillCenter by remember { mutableStateOf(Offset.Zero) }
    BackgroundGlow(
        visible = visible,
        expanded = expanded,
        collapsedOffset = IntOffset(0, 110),
        modifier = modifier.graphicsLayer { translationX = -size.width / 2 + pillCenter.x },
    )
    ShortPill(
        actions = actions,
        visible = visible,
        horizontal = portrait,
        expanded = expanded,
        modifier =
            if (pillCenterInWindow == null) {
                modifier.padding(bottom = 12.dp, end = 24.dp).onGloballyPositioned {
                    pillCenter = it.boundsInParent().center
                }
            } else {
                Modifier.graphicsLayer {
                    val center = pillCenterInWindow.center
                    translationX = center.x - size.width / 2
                    translationY = center.y - size.height / 2
                    pillCenter = center
                }
            },
        onClick = { viewModel.expand() },
        onCloseClick = { viewModel.hide() },
    )
    ActionList(
        actions = actions,
        visible = visible && expanded,
        horizontalAlignment = Alignment.End,
        modifier = modifier.padding(bottom = chipsBottomPadding, end = 24.dp),
    )
}

@Composable
private fun NavBarAmbientCue(
    viewModel: AmbientCueViewModel,
    actions: List<ActionViewModel>,
    visible: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val windowWidthSizeClass = calculateWindowSizeClass().widthSizeClass

    val navBarWidth =
        if (windowWidthSizeClass == WindowWidthSizeClass.Compact) NAV_BAR_WIDTH_DP.dp
        else NAV_BAR_LARGE_WIDTH_DP.dp

    BackgroundGlow(visible = visible, expanded = expanded, modifier = modifier)
    NavBarPill(
        actions = actions,
        navBarWidth = navBarWidth,
        visible = visible,
        expanded = expanded,
        modifier = modifier,
        onClick = { viewModel.expand() },
        onCloseClick = { viewModel.hide() },
    )
    ActionList(
        actions = actions,
        visible = visible && expanded,
        modifier = modifier.padding(bottom = chipsBottomPadding),
    )
}

private const val NAV_BAR_WIDTH_DP = 108 // R.dimen.taskbar_stashed_small_screen from Launcher
private const val NAV_BAR_LARGE_WIDTH_DP = 220 // R.dimen.taskbar_stashed_handle_width from Launcher

private const val NAV_BAR_HEIGHT_DP = 24 // R.dimen.taskbar_stashed_size from Launcher
private val chipsBottomPadding = NAV_BAR_HEIGHT_DP.dp + 22.dp
