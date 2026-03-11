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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleFreeformTextCreationViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R
import kotlinx.coroutines.launch

/** Screen allowing the user to input freeform text in order to generate a new notification rule. */
@Composable
fun FreeformTextRuleCreationScreen(
    viewModel: NotificationRuleFreeformTextCreationViewModel,
    onDismissRequest: () -> Unit,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onNavigateToEditScreen: (DraftRuleModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Header(
            stringResource(R.string.notification_rules_create_new_title),
            onDismissRequest = onDismissRequest,
        )

        FreeformTextArea(
            action = viewModel.selectedAction,
            textFieldState = viewModel.enteredText,
            onEnterEditField = onEnterEditField,
            onActionSaved = { viewModel.selectedAction = it },
            modifier =
                Modifier.background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                ),
        )

        NextButton(
            onClick = {
                scope.launch {
                    // Use scope so that we cancel the rule generation if the user ever leaves this
                    // page
                    val newDraftRule = viewModel.createDraftRuleFromFreeformText()
                    onNavigateToEditScreen(newDraftRule)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FreeformTextArea(
    action: ActionModel,
    textFieldState: TextFieldState,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onActionSaved: (ActionModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        EditableAction(
            action = action,
            onEnterEditField = onEnterEditField,
            onActionSaved = onActionSaved,
        )

        TextField(textFieldState, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f))

        // TODO: b/478225883 - Add the inline prompt suggestions.
    }
}

@Composable
private fun NextButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text(stringResource(R.string.notification_rules_next))
    }
}
