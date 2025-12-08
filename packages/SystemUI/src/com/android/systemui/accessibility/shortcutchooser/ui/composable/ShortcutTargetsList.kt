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

package com.android.systemui.accessibility.shortcutchooser.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel

/**
 * A list of accessibility targets.
 *
 * This list is vertically scrollable and will show a horizontal divider at the top or bottom if
 * there is more content in that direction.
 *
 * @param targets The list of targets to display
 * @param modifier The modifier to apply to the list
 * @param itemContent The content to display for each target
 */
@Composable
fun ShortcutTargetsList(
    targets: List<AccessibilityTargetModel>,
    modifier: Modifier = Modifier,
    itemContent: @Composable LazyItemScope.(AccessibilityTargetModel) -> Unit,
) {
    val listState = rememberLazyListState()
    val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }

    Box {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(targets, key = { target -> target.targetName }, itemContent = itemContent)
        }

        ScrollBorder(canScrollUp, Modifier.align(Alignment.TopCenter))
        ScrollBorder(canScrollDown, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ScrollBorder(visible: Boolean, modifier: Modifier) {
    if (visible) {
        HorizontalDivider(thickness = 1.dp, modifier = modifier.fillMaxWidth())
    }
}
