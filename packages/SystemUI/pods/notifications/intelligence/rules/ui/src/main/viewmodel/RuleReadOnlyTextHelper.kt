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

import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel

/**
 * Transforms [rule] into a readable string. Because this is a read-only view, individual fields are
 * more visually prominent but not clickable.
 */
internal fun buildReadOnlyRuleText(rule: RuleModel): String {
    // TODO: b/478225883 - Internationalize this string when design is ready.
    // TODO: b/478225883 - Re-use text rendering from edit screen.
    val contactsList = rule.filter.contacts?.contacts
    val contactsString =
        if (contactsList != null) {
            " from ${contactsList.joinToString { it.name }} [TK]"
        } else {
            ""
        }

    val includedAppsList = rule.filter.includedApps?.apps
    val includedAppsString =
        if (includedAppsList != null) {
            " from ${includedAppsList.joinToString { it.label }} [TK]"
        } else {
            ""
        }

    return "Notifications$contactsString$includedAppsString [TK]"
}
