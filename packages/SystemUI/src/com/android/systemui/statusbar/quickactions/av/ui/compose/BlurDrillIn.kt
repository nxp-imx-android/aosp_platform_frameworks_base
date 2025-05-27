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

package com.android.systemui.statusbar.quickactions.av.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.BlurDrillInViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.ButtonViewModel
import kotlinx.coroutines.launch

/** A drill-in page for selecting the blur intensity. */
@Composable
fun BlurDrillIn(
    viewModelFactory: BlurDrillInViewModel.Factory,
    returnToMainPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        rememberViewModel("BlurDrillIn.viewModel", key = returnToMainPage) {
            viewModelFactory.create(returnToMainPage)
        }
    val buttons =
        listOf(viewModel.blurOffButton, viewModel.blurLightButton, viewModel.blurFullButton)
    DrillIn(
        drillInTitle = viewModel.drillInTitle,
        returnToMainPage = { viewModel.returnToMainPage() },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            buttons.forEachIndexed { index, button ->
                val precedingButton = if (index > 0) buttons[index - 1] else null
                val subsequentButton = if (index < buttons.size - 1) buttons[index + 1] else null
                BlurSelectionButton(
                    viewModel = button,
                    precedingButton = precedingButton,
                    subsequentButton = subsequentButton,
                )
            }
        }
    }
}

/** A button representing a specific blur option. */
@Composable
private fun BlurSelectionButton(
    viewModel: ButtonViewModel,
    precedingButton: ButtonViewModel? = null,
    subsequentButton: ButtonViewModel? = null,
) {
    val scope = rememberCoroutineScope()
    val bigRadius = 20.dp
    val smallRadius = 2.dp
    val topCornerRadius =
        if (viewModel.state.isEnabled || precedingButton == null || precedingButton.state.isEnabled)
            bigRadius
        else smallRadius
    val bottomCornerRadius =
        if (
            viewModel.state.isEnabled ||
                subsequentButton == null ||
                subsequentButton.state.isEnabled
        )
            bigRadius
        else smallRadius
    val shape =
        RoundedCornerShape(
            topStart = topCornerRadius,
            topEnd = topCornerRadius,
            bottomStart = bottomCornerRadius,
            bottomEnd = bottomCornerRadius,
        )
    if (precedingButton != null) {
        Spacer(modifier = Modifier.size(2.dp))
    }
    ListItem(
        leadingContent = {
            viewModel.state.image?.let {
                Icon(painter = painterResource(id = it), contentDescription = null)
            }
        },
        headlineContent = { viewModel.state.mainTitle?.let { Text(text = it) } },
        colors = itemColors(viewModel.state.isEnabled),
        modifier =
            Modifier.clip(shape = shape)
                .clickable(onClick = { scope.launch { viewModel.onClick() } }),
    )
}
