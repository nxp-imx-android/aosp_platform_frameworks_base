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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.ui.composable.component.AppIcon
import com.android.systemui.notifications.ui.composable.component.CollapsedText
import com.android.systemui.notifications.ui.composable.component.ExpandedText
import com.android.systemui.notifications.ui.composable.component.Expander
import com.android.systemui.notifications.ui.composable.component.Title
import com.android.systemui.notifications.ui.viewmodel.NotificationViewModel

@Composable
public fun NotificationContent(viewModel: NotificationViewModel, modifier: Modifier = Modifier) {
    Row(modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
        AppIcon(viewModel.appIcon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Title(viewModel.title)
                Expander(expanded = viewModel.isExpanded)
            }
            MainContent(viewModel)
        }
    }
}

// TODO: b/431222735 - Consider making the various types of content subtypes of a sealed class.
@Composable
private fun MainContent(viewModel: NotificationViewModel) {
    return if (viewModel.isExpanded) {
        ExpandedText(viewModel.text)
    } else {
        CollapsedText(viewModel.text)
    }
}
