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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.ambientcue.ui.viewmodel.AmbientCueViewModel
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

    // TODO: b/414507396 - Replace with the height of the navbar
    val chipsBottomPadding = 46.dp

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
        BackgroundGlow(
            visible = visible,
            expanded = expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        NavBarPill(
            actions = actions,
            navBarWidth = 110.dp, // TODO: b/414507396 - Replace with the width of the navbar
            visible = visible,
            expanded = expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = { viewModel.expand() },
            onCloseClick = { viewModel.hide() },
        )
        ActionList(
            actions = actions,
            visible = visible && expanded,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = chipsBottomPadding),
        )
    }
}
