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
 * Represents a **draft** of a notification rule. Notification rules control when and how
 * notifications are presented.
 *
 * This rule draft is being edited by a user and is not yet saved anywhere. And because it's a
 * draft, some values may be underspecified. Any underspecified values will use
 * [RuleValue.Ambiguous], and the user will have to specify the value in the UI before the rule is
 * saved.
 *
 * This is mostly an internal equivalent of [android.app.NotificationRule], but with ambiguous value
 * support as well.
 *
 * See also: [RuleModel] for rules that are already saved.
 */
public data class DraftRuleModel(
    /**
     * True if the rule being edited is completely new, and false if the rule already existed
     * previously.
     */
    val isNew: Boolean,
    /** The action to apply to the notification. See [android.app.NotificationRule.getAction]. */
    val action: ActionModel,
    /**
     * The contacts that this rule applies to. Null if contacts are not part of the rule filter. See
     * [android.app.NotificationRule.Filter.getContacts].
     */
    val contacts: RuleValue<ContactsModel>?,
    /**
     * The apps that this rule applies to. Null if included apps are not part of the rule filter.
     * [android.app.NotificationRule.Filter.getIncludedPackageUids].
     */
    val includedApps: RuleValue<IncludedAppsModel>?,
) {
    public companion object {
        /** Converts a rule to a draft version so it can be edited. */
        public fun RuleModel.toDraft(): DraftRuleModel {
            return DraftRuleModel(
                isNew = false,
                action = action,
                contacts = filter.contacts.toDraft(),
                includedApps = filter.includedApps.toDraft(),
            )
        }

        /** Converts a given type to a [RuleValue.Specified] version of that type. */
        private fun <T> T?.toDraft(): RuleValue<T>? {
            return if (this == null) {
                null
            } else {
                RuleValue.Specified(this)
            }
        }
    }
}

/** Represents various actions that a rule can apply to a notification. */
public enum class ActionModel {
    /** Highlight and also alert the user, disregarding DND & other modes. */
    HighlightAndAlert,
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT]. */
    Highlight,
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_LOW]. */
    Silence,
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE]. */
    Bundle,
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK]. */
    Block,
}

/** Represents a single aspect of a notification rule. */
public sealed interface RuleValue<T> {
    /** This aspect is fully specified and has the value [value]. */
    public data class Specified<T>(val value: T) : RuleValue<T>

    /** This aspect is underspecified and must be clarified by the user in the edit UI. */
    public data class Ambiguous<T>(
        /** The text to show in the UI before the user clarifies the value. */
        val placeholderText: String
    ) : RuleValue<T>
}
