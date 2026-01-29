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
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
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
    rule: DraftRuleModel,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel("NotificationRuleEditViewModel") { viewModelFactory.create() }
    val scope = rememberCoroutineScope()

    var shownDialogType: EditDialogType by remember(rule) { mutableStateOf(EditDialogType.None) }
    var selectedAction by remember(rule) { mutableStateOf(rule.action) }
    val selectedContacts by remember(rule) { mutableStateOf(rule.contacts) }

    val text =
        remember(selectedAction, selectedContacts, shownDialogType) {
            buildAnnotatedText(
                selectedAction = selectedAction,
                selectedContacts = selectedContacts,
                shownDialogType = shownDialogType,
                changeEditDialog = { shownDialogType = it },
            )
        }

    Column(modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.titleLargeEmphasized)

        when (val dialogShowing = shownDialogType) {
            is EditDialogType.Action -> {
                ActionChoiceDialog(
                    onDismissRequest = { shownDialogType = EditDialogType.None },
                    onActionSelected = { action -> selectedAction = action },
                )
            }
            is EditDialogType.Contact -> {
                ContactChoiceDialog(
                    initialSearchQuery = dialogShowing.initialQuery,
                    viewModel = viewModel,
                    scope = scope,
                    context = LocalContext.current,
                )
            }
            is EditDialogType.None -> {}
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
    selectedContacts: RuleValue<ContactsModel>?,
    shownDialogType: EditDialogType,
    changeEditDialog: (EditDialogType) -> Unit,
): AnnotatedString {
    return buildAnnotatedString {
        clickableText(
            text = selectedAction.name,
            isAmbiguous = false,
            onClick = {
                toggleEditDialogShown(
                    desiredType = EditDialogType.Action,
                    currentEditDialogShowing = shownDialogType,
                    changeEditDialog = changeEditDialog,
                )
            },
        )

        append(" all Conversation notifications")

        selectedContacts?.let {
            append(" from ")
            createContactsText(
                selectedContacts = it,
                editDialogShowing = shownDialogType,
                changeEditDialog = changeEditDialog,
            )
        }

        append(" on weekdays")
    }
}

/** Shows/hides the [desiredType] edit dialog, depending on whether it's currently open or not. */
private fun toggleEditDialogShown(
    desiredType: EditDialogType,
    currentEditDialogShowing: EditDialogType,
    changeEditDialog: (EditDialogType) -> Unit,
) {
    if (currentEditDialogShowing == desiredType) {
        changeEditDialog.invoke(EditDialogType.None)
    } else {
        changeEditDialog.invoke(desiredType)
    }
}

/** The type of value being edited in the edit dialog. */
private sealed interface EditDialogType {
    /** No edit dialog is showing. */
    data object None : EditDialogType

    /** The action is being edited. See [DraftRuleModel.action]. */
    data object Action : EditDialogType

    /**
     * The contact list is being edited. See [DraftRuleModel.contacts].
     *
     * @param initialQuery the text to use as the beginning contact search query.
     */
    data class Contact(val initialQuery: String = "") : EditDialogType

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

/** Creates annotated text for the contacts filter field. */
private fun AnnotatedString.Builder.createContactsText(
    selectedContacts: RuleValue<ContactsModel>,
    editDialogShowing: EditDialogType,
    changeEditDialog: (EditDialogType) -> Unit,
) {
    val text =
        when (selectedContacts) {
            is RuleValue.Specified -> {
                val contacts = selectedContacts.value.contacts
                check(contacts.isNotEmpty()) { "ContactsModel.contacts must be non-empty" }
                val first = contacts[0].name
                if (contacts.size > 1) {
                    "$first +${contacts.size - 1} more"
                } else {
                    first
                }
            }
            is RuleValue.Ambiguous -> {
                selectedContacts.placeholderText
            }
        }
    clickableText(
        text = text,
        isAmbiguous = selectedContacts is RuleValue.Ambiguous,
        onClick = {
            toggleEditDialogShown(
                desiredType = EditDialogType.Contact(initialQuery = text),
                currentEditDialogShowing = editDialogShowing,
                changeEditDialog = changeEditDialog,
            )
        },
    )
}

/**
 * Renders the given text as a clickable element.
 *
 * @param isAmbiguous true if the text represents an underspecified value. See
 *   [RuleValue.Ambiguous].
 */
private fun AnnotatedString.Builder.clickableText(
    text: String,
    isAmbiguous: Boolean,
    onClick: () -> Unit,
) {
    withLink(
        LinkAnnotation.Clickable(
            tag = text,
            styles =
                TextLinkStyles(
                    style = if (isAmbiguous) AMBIGUOUS_CLICKABLE_STYLE else CLICKABLE_STYLE
                ),
            linkInteractionListener = { onClick.invoke() },
        )
    ) {
        append(text)
    }
}

private val CLICKABLE_STYLE =
    SpanStyle(
        // TODO: b/478225883 - Use theme colors.
        color = Color.Blue,
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Bold,
    )

private val AMBIGUOUS_CLICKABLE_STYLE =
    CLICKABLE_STYLE.copy(
        // TODO: b/478225883 - Use theme colors.
        color = Color.Red
    )
