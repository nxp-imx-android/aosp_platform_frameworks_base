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

import android.content.res.Resources
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel

/**
 * Transforms [rule] into a readable string. Because this is a read-only view, individual fields are
 * more visually prominent but not editable.
 */
internal fun buildReadOnlyRuleText(rule: RuleModel, resources: Resources): RuleDisplayModel {
    val appsText: SingleFieldTextModel<AppModel>? =
        rule.filter.includedApps?.let {
            createReadOnlyIncludedAppsText(selectedIncludedApps = it, resources = resources)
        }

    val contactsText: SingleFieldTextModel<ContactModel>? =
        rule.filter.contacts?.let {
            createReadOnlyContactsText(selectedContacts = it, resources = resources)
        }
    return buildRuleText(appsText = appsText, contactsText = contactsText, resources = resources)
}

/** Creates text representation for the included apps filter field. */
private fun createReadOnlyIncludedAppsText(
    selectedIncludedApps: IncludedAppsModel,
    resources: Resources,
): SingleFieldTextModel<AppModel> {
    return createMultiItemText(
        items = selectedIncludedApps.apps,
        id = { it.uniqueId },
        label = { it.label },
        onClick = null,
        resources = resources,
    )
}

/** Creates text representation for the contacts filter field. */
private fun createReadOnlyContactsText(
    selectedContacts: ContactsModel,
    resources: Resources,
): SingleFieldTextModel<ContactModel> {
    return createMultiItemText(
        items = selectedContacts.contacts,
        id = { it.id },
        label = { it.displayLabel },
        onClick = null,
        resources = resources,
    )
}
