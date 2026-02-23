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

import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel

/** The type of value being edited in the edit dialog. */
public sealed interface EditDialogType {
    /** No edit dialog is showing. */
    public data object None : EditDialogType

    /** The action is being edited. See [DraftRuleModel.action]. */
    public data object Action : EditDialogType

    /**
     * The contact list is being edited. See [DraftRuleModel.contacts].
     *
     * @param initialQuery the text to use as the beginning contact search query.
     * @param initialSelectedContacts the contacts currently selected as part of the rule.
     */
    public data class Contact(
        val initialQuery: String = "",
        val initialSelectedContacts: List<ContactModel>,
    ) : EditDialogType

    /** The included apps list is being edited. See [DraftRuleModel.includedApps]. */
    public data object IncludedApps : EditDialogType

    // TODO: b/478225883 - Add more edit types.

    /**
     * A "meta" edit dialog that lets users add an additional filter or condition to the rule.
     *
     * @param addFieldOptions the list of types that can be added to the rule.
     */
    public data class AddField(val addFieldOptions: List<EditDialogType>) : EditDialogType
}
