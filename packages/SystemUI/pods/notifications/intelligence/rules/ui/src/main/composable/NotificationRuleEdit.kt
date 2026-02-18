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
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
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
    viewModel: NotificationRuleEditViewModel,
    dismissEditScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shownDialogType: EditDialogType by
        remember(viewModel) { mutableStateOf(EditDialogType.None) }

    // When the user edits a particular field, [draftRule] will be updated with the new value.
    var draftRule: DraftRuleModel by remember(viewModel) { mutableStateOf(viewModel.rule) }
    val selectedAction = draftRule.action
    val selectedContacts = draftRule.contacts
    val selectedIncludedApps = draftRule.includedApps
    val addFieldOptions: List<EditDialogType> = buildAddFieldOptions(draftRule)

    val textStyles = rememberTextStyles()
    val text =
        remember(
            selectedAction,
            selectedContacts,
            selectedIncludedApps,
            shownDialogType,
            textStyles,
        ) {
            buildAnnotatedText(
                selectedAction = selectedAction,
                selectedContacts = selectedContacts,
                selectedIncludedApps = selectedIncludedApps,
                shownDialogType = shownDialogType,
                changeEditDialog = { shownDialogType = it },
                textStyles = textStyles,
            )
        }

    BackHandler(enabled = true, onBack = dismissEditScreen)
    Column(modifier = modifier) {
        Button(onClick = dismissEditScreen) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                // TODO: b/478225883 - Translate content description (requires moving
                // resources to pods)
                contentDescription = "Back",
            )
        }

        Text(text = text, style = MaterialTheme.typography.titleLargeEmphasized)

        AddButton(
            addFieldOptions = addFieldOptions,
            currentEditDialogShowing = shownDialogType,
            changeEditDialog = { shownDialogType = it },
        )

        when (val dialogShowing = shownDialogType) {
            is EditDialogType.Action -> {
                ActionChoiceDialog(
                    onDismissRequest = { shownDialogType = EditDialogType.None },
                    onActionSelected = { action -> draftRule = draftRule.copy(action = action) },
                )
            }
            is EditDialogType.Contact -> {
                ContactChoiceDialog(
                    initialSearchQuery = dialogShowing.initialQuery,
                    initialSelection = dialogShowing.initialSelectedContacts,
                    onContactsSaved = { newContacts ->
                        draftRule =
                            draftRule.copy(
                                contacts =
                                    if (newContacts.isNotEmpty()) {
                                        RuleValue.Specified(ContactsModel(newContacts))
                                    } else {
                                        // Saving with no selected contacts is effectively removing
                                        // contacts from the filter.
                                        null
                                    }
                            )
                        shownDialogType = EditDialogType.None
                    },
                    viewModel = viewModel,
                )
            }
            is EditDialogType.IncludedApps -> {
                AppChoiceDialog(viewModel = viewModel)
            }
            is EditDialogType.AddField -> {
                AddFieldDialog(
                    options = dialogShowing.addFieldOptions,
                    onDismissRequest = { shownDialogType = EditDialogType.None },
                    onOptionSelected = { shownDialogType = it },
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
    selectedIncludedApps: RuleValue<IncludedAppsModel>?,
    shownDialogType: EditDialogType,
    changeEditDialog: (EditDialogType) -> Unit,
    textStyles: TextStyles,
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
            textStyles = textStyles,
        )

        append(" all Conversation notifications")

        selectedIncludedApps?.let {
            append(" from ")
            createIncludedAppsText(
                selectedIncludedApps = selectedIncludedApps,
                editDialogShowing = shownDialogType,
                changeEditDialog = changeEditDialog,
                textStyles = textStyles,
            )
        }

        selectedContacts?.let {
            append(" from ")
            createContactsText(
                selectedContacts = it,
                editDialogShowing = shownDialogType,
                changeEditDialog = changeEditDialog,
                textStyles = textStyles,
            )
        }

        append(" on weekdays")
    }
}

/** Renders a '+' button letting users add additional fields to the rule. */
@Composable
private fun AddButton(
    addFieldOptions: List<EditDialogType>,
    currentEditDialogShowing: EditDialogType,
    changeEditDialog: (EditDialogType) -> Unit,
) {
    if (addFieldOptions.isEmpty()) {
        return
    }

    Button(
        onClick = {
            toggleEditDialogShown(
                desiredType = EditDialogType.AddField(addFieldOptions),
                currentEditDialogShowing = currentEditDialogShowing,
                changeEditDialog = changeEditDialog,
            )
        }
    ) {
        Text("+ Add")
    }
}

/**
 * Builds a list of filter and condition fields that can be added to the rule. Only includes types
 * that *aren't* present in the rule yet. (Types that *are* present can be edited by clicking their
 * text.)
 */
private fun buildAddFieldOptions(draftRule: DraftRuleModel): List<EditDialogType> {
    return mutableListOf<EditDialogType>().apply {
        if (draftRule.contacts == null) {
            add(EditDialogType.Contact(initialSelectedContacts = emptyList()))
        }
        if (draftRule.includedApps == null) {
            add(EditDialogType.IncludedApps)
        }
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

/** Creates annotated text for the included apps filter field. */
private fun AnnotatedString.Builder.createIncludedAppsText(
    selectedIncludedApps: RuleValue<IncludedAppsModel>,
    editDialogShowing: EditDialogType,
    changeEditDialog: (EditDialogType) -> Unit,
    textStyles: TextStyles,
) {
    val text =
        when (selectedIncludedApps) {
            is RuleValue.Specified -> {
                val apps = selectedIncludedApps.value.apps
                check(apps.isNotEmpty()) { "IncludedAppsModel.apps must be non-empty" }
                val first = apps[0].label
                if (apps.size > 1) {
                    "$first +${apps.size - 1} more"
                } else {
                    first
                }
            }
            is RuleValue.Ambiguous -> {
                selectedIncludedApps.placeholderText
            }
        }
    clickableText(
        text = text,
        isAmbiguous = selectedIncludedApps is RuleValue.Ambiguous,
        onClick = {
            toggleEditDialogShown(
                desiredType = EditDialogType.IncludedApps,
                currentEditDialogShowing = editDialogShowing,
                changeEditDialog = changeEditDialog,
            )
        },
        textStyles = textStyles,
    )
}

/** Creates annotated text for the contacts filter field. */
private fun AnnotatedString.Builder.createContactsText(
    selectedContacts: RuleValue<ContactsModel>,
    editDialogShowing: EditDialogType,
    changeEditDialog: (EditDialogType) -> Unit,
    textStyles: TextStyles,
) {
    when (selectedContacts) {
        is RuleValue.Specified -> {
            val contacts = selectedContacts.value.contacts
            check(contacts.isNotEmpty()) { "ContactsModel.contacts must be non-empty" }

            val first = contacts[0].name
            val text =
                if (contacts.size > 1) {
                    "$first +${contacts.size - 1} more"
                } else {
                    first
                }

            clickableText(
                text = text,
                isAmbiguous = false,
                onClick = {
                    toggleEditDialogShown(
                        desiredType =
                            EditDialogType.Contact(
                                initialQuery = "",
                                initialSelectedContacts = contacts,
                            ),
                        currentEditDialogShowing = editDialogShowing,
                        changeEditDialog = changeEditDialog,
                    )
                },
                textStyles = textStyles,
            )
        }
        is RuleValue.Ambiguous -> {
            clickableText(
                text = selectedContacts.placeholderText,
                isAmbiguous = true,
                onClick = {
                    toggleEditDialogShown(
                        desiredType =
                            EditDialogType.Contact(
                                initialQuery = selectedContacts.placeholderText,
                                initialSelectedContacts = emptyList(),
                            ),
                        currentEditDialogShowing = editDialogShowing,
                        changeEditDialog = changeEditDialog,
                    )
                },
                textStyles = textStyles,
            )
        }
    }
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
    textStyles: TextStyles,
) {
    withLink(
        LinkAnnotation.Clickable(
            tag = text,
            styles =
                TextLinkStyles(
                    style = if (isAmbiguous) textStyles.ambiguous else textStyles.default
                ),
            linkInteractionListener = { onClick.invoke() },
        )
    ) {
        append(text)
    }
}

private data class TextStyles(val default: SpanStyle, val ambiguous: SpanStyle)

@Composable
private fun rememberTextStyles(): TextStyles {
    val baseStyle =
        SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)
    val defaultStyle = baseStyle.copy(color = MaterialTheme.colorScheme.primary)
    val ambiguousStyle = baseStyle.copy(color = MaterialTheme.colorScheme.error)

    return remember(defaultStyle, ambiguousStyle) {
        TextStyles(default = defaultStyle, ambiguous = ambiguousStyle)
    }
}
