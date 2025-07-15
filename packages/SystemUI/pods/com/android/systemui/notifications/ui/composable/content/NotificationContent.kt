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

package com.android.systemui.notifications.ui.composable.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.ui.composable.component.AppIcon
import com.android.systemui.notifications.ui.composable.component.CollapsedText
import com.android.systemui.notifications.ui.composable.component.ExpandedText
import com.android.systemui.notifications.ui.composable.component.Expander
import com.android.systemui.notifications.ui.composable.component.LargeIcon
import com.android.systemui.notifications.ui.composable.component.Title
import com.android.systemui.notifications.ui.composable.component.TopLineText
import com.android.systemui.notifications.ui.viewmodel.NotificationViewModel

@Composable
public fun NotificationContent(viewModel: NotificationViewModel, modifier: Modifier = Modifier) {
    // TODO: b/431222735 - Handle transitions using STL.
    // TODO: b/431222735 - Handle empty text edge cases.
    if (!viewModel.isExpanded) {
        NotificationRow(
            viewModel,
            firstLine = { Title(viewModel.title) },
            secondLine = { CollapsedText(viewModel.text) },
            modifier,
        )
    } else {
        NotificationRow(
            viewModel,
            firstLine = {
                // TODO: b/431222735 - Implement the top line properly.
                TopLineText("Placeholder • Top Line Text", Modifier.padding(vertical = 2.dp))
            },
            secondLine = { Title(viewModel.title) },
            modifier,
        ) {
            ExpandedText(viewModel.text, maxLines = viewModel.maxLinesWhenExpanded)
        }
    }
}

@Composable
private fun NotificationRow(
    viewModel: NotificationViewModel,
    firstLine: @Composable () -> Unit,
    secondLine: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .heightIn(max = viewModel.maxHeightDp.dp)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(viewModel.appIcon, Modifier.padding(top = 4.dp, bottom = 4.dp, end = 16.dp))
        Column(Modifier.weight(1f)) {
            if (viewModel.largeIcon != null) {
                HeaderWithLargeIcon(viewModel, firstLine, secondLine)
            } else {
                HeaderWithoutLargeIcon(viewModel, firstLine, secondLine)
            }
            if (content != null) {
                Box(Modifier.padding(top = 4.dp)) { content() }
            }
        }
    }
}

/**
 * When the large icon is present, show the two lines of text, then the icon to the right of them,
 * then the expander.
 */
@Composable
private fun HeaderWithLargeIcon(
    viewModel: NotificationViewModel,
    firstLine: @Composable () -> Unit,
    secondLine: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.weight(1f).padding(top = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            firstLine()
            secondLine()
        }
        viewModel.largeIcon?.let { LargeIcon(it, Modifier.padding(start = 16.dp, end = 8.dp)) }
        Expander(expanded = viewModel.isExpanded, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * When the large icon is not present, the second line of text takes up all the available space
 * under the expander.
 */
@Composable
private fun HeaderWithoutLargeIcon(
    viewModel: NotificationViewModel,
    firstLine: @Composable () -> Unit,
    secondLine: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 4.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { firstLine() }
            Expander(expanded = viewModel.isExpanded)
        }
        secondLine()
    }
}
