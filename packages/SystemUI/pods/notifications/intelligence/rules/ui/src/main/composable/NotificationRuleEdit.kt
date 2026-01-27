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

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel

/**
 * A composable rendering a page to edit a specific notification rule.
 *
 * This is still a work-in-progress.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationRuleEdit(rule: RuleModel, modifier: Modifier = Modifier) {
    var editDialogShowing: EditDialogType? by remember { mutableStateOf(null) }
    var selectedAction by remember { mutableStateOf(rule.action) }

    val text =
        remember(selectedAction) {
            buildAnnotatedText(
                selectedAction = selectedAction,
                changeEditDialog = { editDialogShowing = it },
            )
        }
    Box(modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.bodyLargeEmphasized)

        when (editDialogShowing) {
            EditDialogType.Action -> {
                ActionChoiceDialog(
                    onDismissRequest = { editDialogShowing = null },
                    onActionSelected = { action -> selectedAction = action },
                )
            }
            null -> {}
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
    changeEditDialog: (EditDialogType?) -> Unit,
): AnnotatedString {
    return buildAnnotatedString {
        clickableText(
            text = selectedAction.name,
            onClick = { changeEditDialog.invoke(EditDialogType.Action) },
        )
        append(" all Conversation notifications on weekdays")
    }
}

private enum class EditDialogType {
    Action
    // TODO: b/478225883 - Add more edit types.
}

/**
 * Renders a dropdown menu to choose one rule action.
 *
 * @param onActionSelected invoked when the user selects an action in the menu.
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
