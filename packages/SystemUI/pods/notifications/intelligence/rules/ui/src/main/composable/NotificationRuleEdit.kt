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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel

/**
 * A composable rendering a page to edit a specific notification rule.
 *
 * This is still a work-in-progress.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationRuleEdit(
    viewModelFactory: NotificationRuleEditViewModel.Factory,
    rule: RuleModel,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel("NotificationRuleEditViewModel") { viewModelFactory.create() }
    val scope = rememberCoroutineScope()

    var editDialogShowing: EditDialogType? by remember { mutableStateOf(null) }
    var selectedAction by remember { mutableStateOf(rule.action) }

    val text =
        remember(selectedAction, editDialogShowing) {
            buildAnnotatedText(
                selectedAction = selectedAction,
                editDialogShowing = editDialogShowing,
                changeEditDialog = { editDialogShowing = it },
            )
        }

    Column(modifier = modifier) {
        Row { Text(text = text, style = MaterialTheme.typography.bodyLargeEmphasized) }

        Row {
            when (editDialogShowing) {
                EditDialogType.Action -> {
                    ActionChoiceDialog(
                        onDismissRequest = { editDialogShowing = null },
                        onActionSelected = { action -> selectedAction = action },
                    )
                }
                EditDialogType.Contact -> {
                    ContactChoiceDialog(
                        viewModel = viewModel,
                        scope = scope,
                        context = LocalContext.current,
                    )
                }
                null -> {}
            }
        }
    }
}

/**
 * Builds the text shown to the user, including clickable spans where the user can modify aspects of
 * the rule.
 *
 * @param changeEditDialog invoked when a certain edit dialog should be shown or hidden based on
 *   what part of the rule the user clicked.
 */
private fun buildAnnotatedText(
    selectedAction: ActionModel,
    editDialogShowing: EditDialogType?,
    changeEditDialog: (EditDialogType?) -> Unit,
): AnnotatedString {
    return buildAnnotatedString {
        clickableText(
            text = selectedAction.name,
            onClick = {
                toggleEditDialogShown(
                    desiredType = EditDialogType.Action,
                    currentEditDialogShowing = editDialogShowing,
                    changeEditDialog = changeEditDialog,
                )
            },
        )
        append(" all Conversation notifications from ")
        clickableText(
            text = "{contact click placeholder}",
            onClick = {
                toggleEditDialogShown(
                    desiredType = EditDialogType.Contact,
                    currentEditDialogShowing = editDialogShowing,
                    changeEditDialog = changeEditDialog,
                )
            },
        )
        append(" on weekdays")
    }
}

/** Shows/hides the [desiredType] edit dialog, depending on whether it's currently open or not. */
private fun toggleEditDialogShown(
    desiredType: EditDialogType,
    currentEditDialogShowing: EditDialogType?,
    changeEditDialog: (EditDialogType?) -> Unit,
) {
    if (currentEditDialogShowing == desiredType) {
        changeEditDialog.invoke(null)
    } else {
        changeEditDialog.invoke(desiredType)
    }
}

private enum class EditDialogType {
    Action,
    Contact,
    // TODO: b/478225883 - Add more edit types.
}

/**
 * Renders a dropdown menu to choose one rule action.
 *
 * @param onActionSelected invoked when the user selects an action in the menu.
 *
 * TODO: b/478225883 - Move to a separate file.
 */
@Composable
private fun ActionChoiceDialog(
    onDismissRequest: () -> Unit,
    onActionSelected: (ActionModel) -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismissRequest) {
        for (action in ActionModel.entries) {
            DropdownMenuItem(
                // TODO: b/478225883 - Add translated strings describing the actions.
                text = { Text(text = action.name) },
                onClick = {
                    onActionSelected.invoke(action)
                    onDismissRequest.invoke()
                },
            )
        }
    }
}

private fun AnnotatedString.Builder.clickableText(text: String, onClick: () -> Unit) {
    withLink(
        LinkAnnotation.Clickable(
            tag = text,
            styles = TextLinkStyles(style = CLICKABLE_SPAN_STYLE),
            linkInteractionListener = { onClick.invoke() },
        )
    ) {
        append(text)
    }
}

private val CLICKABLE_SPAN_STYLE =
    SpanStyle(
        // TODO: b/478225883 - Use theme colors, and a different color for errors.
        color = Color.Magenta,
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Bold,
    )
