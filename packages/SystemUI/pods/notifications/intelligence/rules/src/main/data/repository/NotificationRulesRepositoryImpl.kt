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

package com.android.systemui.notifications.intelligence.rules.data.repository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.FilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import javax.inject.Inject

@SysUISingleton
class NotificationRulesRepositoryImpl @Inject constructor() : NotificationRulesRepository {
    // TODO: b/478225883 - Fetch the user's actual rules.
    override var rules: SnapshotStateList<RuleModel> =
        mutableStateListOf(
            RuleModel(
                action = ActionModel.Bundle,
                filter = FilterModel(contacts = null, includedApps = null),
            ),
            RuleModel(
                action = ActionModel.Highlight,
                filter = FilterModel(contacts = null, includedApps = null),
            ),
        )
        private set

    override suspend fun createDraftRuleFromFreeformText(
        action: ActionModel,
        text: String,
    ): DraftRuleModel {
        // TODO: b/478225883 - Send freeform text for processing.
        return DraftRuleModel(
            isNew = true,
            action = action,
            contacts = RuleValue.Ambiguous(text),
            includedApps = null,
        )
    }

    override fun createRule(newRule: RuleModel) {
        NmContextualDisplayLaunch.expectInNewMode()
        // TODO: b/478225883 - Send rule to system_server for saving. Use an actor pattern with a
        // Channel to avoid blocking the main thread and guarantee order of operations.
        rules += newRule
    }
}
