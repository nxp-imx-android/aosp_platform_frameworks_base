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
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState

/**
 * A composable rendering a page to edit a specific notification rule.
 *
 * This is still a work-in-progress.
 *
 * @param onDismissRuleEditScreen invoked when the user dismisses this current screen.
 * @param onEnterEditField invoked when the user starts editing a particular field of the rule.
 * @param onExitEditField invoked when the user finishes editing a particular field of the rule.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationRuleEdit(
    viewModel: NotificationRuleEditViewModel,
    onDismissRuleEditScreen: () -> Unit,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onExitEditField: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addFieldOptions: List<RulesScreenViewState.EditField> =
        buildAddFieldOptions(viewModel, onExitEditField = onExitEditField)
    var isAddFieldDialogShowing by remember { mutableStateOf(false) }

    val textStyles = rememberTextStyles()
    val text =
        remember(viewModel, onEnterEditField, textStyles) {
            buildAnnotatedText(
                viewModel = viewModel,
                onEnterEditField = onEnterEditField,
                onExitEditField = onExitEditField,
                textStyles = textStyles,
            )
        }

    BackHandler(enabled = true, onBack = onDismissRuleEditScreen)
    Column(modifier = modifier) {
        Button(onClick = onDismissRuleEditScreen) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back [TK]",
            )
        }

        Text(text = text, style = MaterialTheme.typography.titleLargeEmphasized)

        AddButton(
            addFieldOptions = addFieldOptions,
            toggleAddFieldDialogShowing = { isAddFieldDialogShowing = !isAddFieldDialogShowing },
        )
        if (isAddFieldDialogShowing) {
            AddFieldDialog(
                options = addFieldOptions,
                onDismissRequest = { isAddFieldDialogShowing = false },
                onOptionSelected = { editField -> onEnterEditField(editField) },
            )
        }
    }
}

/**
 * Builds the text shown to the user, including clickable spans where the user can modify aspects of
 * the rule.
 */
private fun buildAnnotatedText(
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onExitEditField: () -> Unit,
    textStyles: TextStyles,
): AnnotatedString {
    return buildAnnotatedString {
        clickableText(
            text = viewModel.rule.action.name,
            isAmbiguous = false,
            onClick = {
                onEnterEditField(
                    RulesScreenViewState.EditField.Action(
                        onActionSaved = { newAction ->
                            viewModel.rule = viewModel.rule.copy(action = newAction)
                        }
                    )
                )
            },
            textStyles = textStyles,
        )

        append(" all Conversation notifications [TK]")

        viewModel.rule.includedApps?.let {
            append(" from [TK]")
            createIncludedAppsText(
                selectedIncludedApps = it,
                viewModel = viewModel,
                onEnterEditField = onEnterEditField,
                onExitEditField,
                textStyles = textStyles,
            )
        }

        viewModel.rule.contacts?.let {
            append(" from [TK]")
            createContactsText(
                selectedContacts = it,
                viewModel = viewModel,
                onEnterEditField = onEnterEditField,
                onExitEditField = onExitEditField,
                textStyles = textStyles,
            )
        }

        append(" on weekdays [TK]")
    }
}

/** Renders a '+' button letting users add additional fields to the rule. */
@Composable
private fun AddButton(
    addFieldOptions: List<RulesScreenViewState.EditField>,
    toggleAddFieldDialogShowing: () -> Unit,
) {
    if (addFieldOptions.isEmpty()) {
        return
    }

    Button(onClick = { toggleAddFieldDialogShowing() }) { Text("+ Add [TK]") }
}

/**
 * Builds a list of filter and condition fields that can be added to the rule. Only includes types
 * that *aren't* present in the rule yet. (Types that *are* present can be edited by clicking their
 * text.)
 */
private fun buildAddFieldOptions(
    viewModel: NotificationRuleEditViewModel,
    onExitEditField: () -> Unit,
): List<RulesScreenViewState.EditField> {
    return mutableListOf<RulesScreenViewState.EditField>().apply {
        if (viewModel.rule.contacts == null) {
            add(
                RulesScreenViewState.EditField.Contacts(
                    onContactsSaved = { newContacts ->
                        onContactsSaved(newContacts, viewModel, onExitEditField)
                    },
                    viewModel = viewModel,
                )
            )
        }
        if (viewModel.rule.includedApps == null) {
            add(
                RulesScreenViewState.EditField.Apps(
                    viewModel = viewModel,
                    onAppsSaved = { newApps -> onAppsSaved(newApps, viewModel, onExitEditField) },
                )
            )
        }
    }
}

/** Creates annotated text for the included apps filter field. */
private fun AnnotatedString.Builder.createIncludedAppsText(
    selectedIncludedApps: RuleValue<IncludedAppsModel>,
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onExitEditField: () -> Unit,
    textStyles: TextStyles,
) {
    val text =
        when (selectedIncludedApps) {
            is RuleValue.Specified -> {
                val apps = selectedIncludedApps.value.apps
                check(apps.isNotEmpty()) { "IncludedAppsModel.apps must be non-empty" }
                val first = apps[0].label
                if (apps.size > 1) {
                    "$first +${apps.size - 1} more [TK]"
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
            onEnterEditField(
                RulesScreenViewState.EditField.Apps(
                    viewModel = viewModel,
                    onAppsSaved = { newApps -> onAppsSaved(newApps, viewModel, onExitEditField) },
                )
            )
        },
        textStyles = textStyles,
    )
}

/** Creates annotated text for the contacts filter field. */
private fun AnnotatedString.Builder.createContactsText(
    selectedContacts: RuleValue<ContactsModel>,
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onExitEditField: () -> Unit,
    textStyles: TextStyles,
) {
    val onClick: () -> Unit = {
        onEnterEditField(
            RulesScreenViewState.EditField.Contacts(
                onContactsSaved = { newContacts ->
                    onContactsSaved(newContacts, viewModel, onExitEditField)
                },
                viewModel = viewModel,
            )
        )
    }
    when (selectedContacts) {
        is RuleValue.Specified -> {
            val contacts = selectedContacts.value.contacts
            check(contacts.isNotEmpty()) { "ContactsModel.contacts must be non-empty" }

            val first = contacts[0].name
            val text =
                if (contacts.size > 1) {
                    "$first +${contacts.size - 1} more [TK]"
                } else {
                    first
                }

            clickableText(
                text = text,
                isAmbiguous = false,
                onClick = onClick,
                textStyles = textStyles,
            )
        }
        is RuleValue.Ambiguous -> {
            clickableText(
                text = selectedContacts.placeholderText,
                isAmbiguous = true,
                onClick = onClick,
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

private fun onContactsSaved(
    newContacts: List<ContactModel>,
    viewModel: NotificationRuleEditViewModel,
    onExitEditField: () -> Unit,
) {
    viewModel.rule =
        viewModel.rule.copy(
            contacts =
                if (newContacts.isNotEmpty()) {
                    RuleValue.Specified(ContactsModel(newContacts))
                } else {
                    // Saving with no selected contacts is effectively removing contacts from the
                    // filter.
                    null
                }
        )
    onExitEditField()
}

private fun onAppsSaved(
    newApps: List<AppModel>,
    viewModel: NotificationRuleEditViewModel,
    onExitEditField: () -> Unit,
) {
    viewModel.rule =
        viewModel.rule.copy(
            includedApps =
                if (newApps.isNotEmpty()) {
                    RuleValue.Specified(IncludedAppsModel(newApps))
                } else {
                    // Saving with no selected apps is effectively removing apps from the filter.
                    null
                }
        )
    onExitEditField()
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
