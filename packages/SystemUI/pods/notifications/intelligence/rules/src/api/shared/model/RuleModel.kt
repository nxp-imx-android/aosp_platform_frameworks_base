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

package com.android.systemui.notifications.intelligence.rules.shared.model

/**
 * Represents a fully defined notification rule. Notification rules control when and how
 * notifications are presented. This is an internal equivalent of [android.app.NotificationRule].
 *
 * See also: [DraftRuleModel] for rules that are still being drafted.
 */
public data class RuleModel(
    /** The action to apply to the notification. See [android.app.NotificationRule.getAction]. */
    public val action: ActionModel,
    /**
     * The filter for which notifications this rule applies to.
     *
     * TODO: b/478225883 - Support a list of filters.
     */
    public val filter: FilterModel,
)

/** Represents a specific filter on a rule. See [android.app.NotificationRule.Filter]. */
public data class FilterModel(
    /**
     * The contacts that this rule applies to. Null if contacts are not part of the rule filter. See
     * [android.app.NotificationRule.Filter.getContacts].
     */
    public val contacts: ContactsModel?,
    /**
     * The apps that this rule applies to. Null if included apps are not part of the rule filter.
     * [android.app.NotificationRule.Filter.getIncludedPackageUids].
     */
    public val includedApps: IncludedAppsModel?,
)
