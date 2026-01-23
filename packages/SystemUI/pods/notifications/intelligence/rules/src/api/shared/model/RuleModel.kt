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

/** Represents a notification rule controlling when and how notifications are presented. */
public data class RuleModel(val action: ActionModel)

/** Represents various actions that a rule can apply to a notification. */
public enum class ActionModel {
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT]. */
    Highlight,
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_DEFAULT]. */
    Default,
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_LOW]. */
    Silence,
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE]. */
    Bundle,
    /** See [android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK]. */
    Block,
}
