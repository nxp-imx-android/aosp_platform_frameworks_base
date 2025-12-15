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

package com.android.systemui.screencapture.sharescreen.largescreen.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.TargetViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.TargetsViewModel

/**
 * A composable that displays a scrollable list of shareable content (e.g., recent apps).
 *
 * @param modifier The modifier to be applied to the composable.
 * @param viewModel The ViewModel that provides the list of tasks and manages selection state.
 */
@Composable
fun ShareContentList(modifier: Modifier = Modifier, viewModel: TargetsViewModel) {
    val targets by viewModel.targets
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = modifier.heightIn(min = 24.dp, max = 224.dp).width(286.dp),
    ) {
        LazyColumn(
            modifier = Modifier.testTag("ShareContentList"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            targets?.let { targets ->
                items(items = targets) { target ->
                    val targetViewModel =
                        rememberViewModel(
                            traceName = "ShareContentListItemViewModel#${target.traceTag}",
                            key = target,
                        ) {
                            viewModel.createViewModelFor(target)
                        }
                    val selectedModel by viewModel.selectedTarget
                    SelectorItem(
                        targetViewModel = targetViewModel,
                        isSelected = targetViewModel.model == selectedModel,
                        onItemSelected = { viewModel.setSelectedTarget(targetViewModel) },
                    )
                }
            }
        }
    }
}

/**
 * A composable that displays a single item in the share content list.
 *
 * @param targetViewModel The view model for this item.
 * @param isSelected Whether this item is selected.
 * @param onItemSelected The callback to be invoked when this item is clicked.
 */
@Composable
private fun SelectorItem(
    targetViewModel: TargetViewModel,
    isSelected: Boolean,
    onItemSelected: () -> Unit,
) {
    // Get the icon and label from the item's ViewModel.
    val icon = targetViewModel.icon?.getOrNull()
    val label = targetViewModel.label?.getOrNull()

    Surface(
        shape = if (isSelected) RoundedCornerShape(20.dp) else RoundedCornerShape(4.dp),
        color =
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier =
                Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                    .clickable(onClick = onItemSelected),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        ) {
            Box(
                modifier =
                    Modifier.size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (icon != null) {
                    Image(bitmap = icon.asImageBitmap(), contentDescription = label?.toString())
                }
            }
            if (label != null) {
                Text(
                    text = label.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
