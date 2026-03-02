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

package com.android.systemui.notifications.intelligence.rules.ui.viewmodel

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue

/**
 * Transforms [viewModel.rule] into a readable string. Also includes clickable spans where the user
 * can modify particular fields of the rule.
 */
internal fun buildEditableRuleText(
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onAppsSaved: (List<AppModel>) -> Unit,
    onContactsSaved: (List<ContactModel>) -> Unit,
    textStyles: TextStyles,
): AnnotatedString {
    return buildAnnotatedString {
        append("Notifications [TK]")

        viewModel.rule.includedApps?.let {
            append(" from [TK]")
            createIncludedAppsText(
                selectedIncludedApps = it,
                viewModel = viewModel,
                onEnterEditField = onEnterEditField,
                onAppsSaved = onAppsSaved,
                textStyles = textStyles,
            )
        }

        viewModel.rule.contacts?.let {
            append(" from [TK]")
            createContactsText(
                selectedContacts = it,
                viewModel = viewModel,
                onEnterEditField = onEnterEditField,
                onContactsSaved = onContactsSaved,
                textStyles = textStyles,
            )
        }
    }
}

/** Creates annotated text for the included apps filter field. */
private fun AnnotatedString.Builder.createIncludedAppsText(
    selectedIncludedApps: RuleValue<IncludedAppsModel>,
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onAppsSaved: ((List<AppModel>) -> Unit),
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
                    onAppsSaved = onAppsSaved,
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
    onContactsSaved: (List<ContactModel>) -> Unit,
    textStyles: TextStyles,
) {
    val onClick: () -> Unit = {
        onEnterEditField(
            RulesScreenViewState.EditField.Contacts(
                onContactsSaved = onContactsSaved,
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
                    style =
                        if (isAmbiguous) textStyles.ambiguousValueSpanStyle
                        else textStyles.specifiedValueSpanStyle
                ),
            linkInteractionListener = { onClick.invoke() },
        )
    ) {
        append(text)
    }
}
