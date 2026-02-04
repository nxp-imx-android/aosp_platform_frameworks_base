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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModel
import javax.inject.Inject

class NotificationRulesScreenImpl @Inject constructor() : NotificationRulesScreen {
    @Composable
    override fun Content(
        viewModelFactory: NotificationRulesScreenViewModel.Factory,
        dismissRulesScreen: () -> Unit,
        modifier: Modifier,
    ) {
        val viewModel = rememberViewModel("NotificationRulesScreen") { viewModelFactory.create() }

        Column(
            modifier = modifier.background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Demo notification rules screen",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(onClick = dismissRulesScreen) { Text("Back") }

            // TODO: b/478225883 - Implement the rules screen.
            viewModel.rules.forEach { Text(it.action.name) }
        }
    }
}
