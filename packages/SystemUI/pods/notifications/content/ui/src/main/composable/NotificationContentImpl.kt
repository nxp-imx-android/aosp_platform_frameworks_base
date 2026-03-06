/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.content.ui.composable

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.notifications.content.ui.composable.component.CollapsedText
import com.android.systemui.notifications.content.ui.composable.component.ExpandedText
import com.android.systemui.notifications.content.ui.composable.component.Title
import com.android.systemui.notifications.content.ui.composable.component.TopLineText
import com.android.systemui.notifications.content.ui.composable.content.NotificationRow
import com.android.systemui.notifications.content.ui.viewmodel.NotificationViewModel
import javax.inject.Inject

class NotificationContentImpl @Inject constructor() : NotificationContent {
    @Composable
    override fun Content(viewModelFactory: NotificationViewModel.Factory, modifier: Modifier) {
        val viewModel = rememberViewModel("Notification") { viewModelFactory.create() }

        if (!viewModel.isExpanded) {
            NotificationRow(
                viewModel,
                firstLine = {
                    TopLineText(
                        modifier = Modifier.padding(vertical = 2.dp),
                        title = viewModel.title,
                        // When collapsed, app name is only shown when there is no title
                        appNameText = if (viewModel.title == null) viewModel.appName else null,
                        headerTextSecondary = viewModel.headerTextSecondary,
                        headerText = viewModel.headerText,
                        // TODO: b/431222735 - Implement time/chronometer logic.
                        timeText = "now",
                        verificationText = viewModel.verificationText,
                    )
                },
                secondLine = { viewModel.text?.let { CollapsedText(it) } },
                modifier,
            )
        } else {
            NotificationRow(
                viewModel,
                firstLine = {
                    TopLineText(
                        modifier = Modifier.padding(vertical = 2.dp),
                        appNameText = viewModel.appName,
                        headerTextSecondary = viewModel.headerTextSecondary,
                        headerText = viewModel.headerText,
                        // TODO: b/431222735 - Implement time/chronometer logic.
                        timeText = "now",
                        verificationText = viewModel.verificationText,
                    )
                },
                // TODO: b/431222735 - Consider showing the expanded text here when there is no
                // title.
                //  this would require a mechanism for getting the text to wrap around the large
                // icon.
                secondLine = { Title(viewModel.title ?: "") },
                modifier,
            ) {
                viewModel.text?.let { ExpandedText(it, maxLines = viewModel.maxLinesWhenExpanded) }
            }
        }
    }
}
