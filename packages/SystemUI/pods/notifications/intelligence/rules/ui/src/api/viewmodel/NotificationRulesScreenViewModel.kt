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

import com.android.systemui.lifecycle.Activatable
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel

/** A view model for the notification rules screen. */
public interface NotificationRulesScreenViewModel : Activatable {
    /** The list of current saved rules for the user. Backed by snapshot state. */
    public val rules: List<RuleModel>

    /** The current state of the rules screen. Backed by snapshot state. */
    public var viewState: RulesScreenViewState

    /** Creates a new rule and adds it to the list of saved rules. */
    public fun createRule(newRule: RuleModel)

    /** Launches the rule edit screen for the given [draftRule]. */
    public fun launchEditRuleScreen(draftRule: DraftRuleModel)

    public interface Factory {
        public fun create(): NotificationRulesScreenViewModel
    }
}
