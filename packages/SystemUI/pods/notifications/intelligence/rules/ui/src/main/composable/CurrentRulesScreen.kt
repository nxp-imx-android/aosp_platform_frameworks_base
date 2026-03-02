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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toDraft
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.launch

@Composable
fun CurrentRulesScreen(
    viewModel: NotificationRulesScreenViewModel,
    onDismissCurrentRulesScreen: () -> Unit,
    onNavigateToEditScreen: (DraftRuleModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = true, onBack = onDismissCurrentRulesScreen)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Top),
        modifier = modifier,
    ) {
        item("Title") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onDismissCurrentRulesScreen, modifier = Modifier) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.accessibility_back),
                    )
                }
                Text(
                    text = stringResource(R.string.notification_rules_activity_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        viewModel.rules.forEach { rule ->
            item(rule.toString()) {
                CurrentRule(
                    rule = rule,
                    screenViewModel = viewModel,
                    onNavigateToEditScreen = onNavigateToEditScreen,
                )
            }
        }

        item("Create new rule") {
            Button(
                onClick = {
                    scope.launch {
                        onNavigateToEditScreen(
                            DraftRuleModel(
                                action = ActionModel.Highlight,
                                contacts = null,
                                includedApps = null,
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.notification_rules_create_new_rule))
            }
        }
    }
}

@Composable
private fun CurrentRule(
    screenViewModel: NotificationRulesScreenViewModel,
    rule: RuleModel,
    onNavigateToEditScreen: (DraftRuleModel) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                )
                .clickable(onClick = { isExpanded = !isExpanded })
                .padding(8.dp)
    ) {
        ReadOnlyAction(rule.action)
        Text(
            text = screenViewModel.buildRuleText(rule),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (isExpanded) {
            Button(onClick = { onNavigateToEditScreen(rule.toDraft()) }) {
                Text(stringResource(R.string.notification_rules_edit))
            }
        }
    }
}
