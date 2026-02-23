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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toDraft
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModel
import kotlinx.coroutines.launch

@Composable
fun CurrentRulesScreen(
    viewModel: NotificationRulesScreenViewModel,
    dismissRulesScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = true, onBack = dismissRulesScreen)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Top),
        modifier = modifier,
    ) {
        item("Title") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = dismissRulesScreen, modifier = Modifier) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        // TODO: b/478225883 - Translate content description (requires moving
                        // resources to pods)
                        contentDescription = "Back",
                    )
                }
                Text(
                    text = "Notification Rules",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        viewModel.rules.forEach { rule ->
            item(rule.toString()) { CurrentRule(rule = rule, screenViewModel = viewModel) }
        }

        item("Create new rule") {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.launchEditRuleScreen(
                            DraftRuleModel(
                                action = ActionModel.Highlight,
                                contacts = null,
                                includedApps = null,
                            )
                        )
                    }
                }
            ) {
                Text("Create new rule")
            }
        }
    }
}

@Composable
private fun CurrentRule(rule: RuleModel, screenViewModel: NotificationRulesScreenViewModel) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                )
                .padding(8.dp)
                .clickable(onClick = { isExpanded = !isExpanded })
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp).padding(start = 4.dp),
            )
            Text(
                text = rule.toText(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }

        if (isExpanded) {
            Button(onClick = { screenViewModel.launchEditRuleScreen(rule.toDraft()) }) {
                Text("Edit")
            }
        }
    }
}

private fun RuleModel.toText(): String {
    // TODO: b/478225883 - Internationalize this string when design is ready.
    // TODO: b/478225883 - Re-use text rendering from edit screen.
    val contactsList = filter.contacts?.contacts
    val contactsString =
        if (contactsList != null) {
            " from ${contactsList.joinToString { it.name }}"
        } else {
            ""
        }

    val includedAppsList = filter.includedApps?.apps
    val includedAppsString =
        if (includedAppsList != null) {
            " from ${includedAppsList.joinToString { it.label }}"
        } else {
            ""
        }

    return "${action.name} notifications$contactsString$includedAppsString"
}
