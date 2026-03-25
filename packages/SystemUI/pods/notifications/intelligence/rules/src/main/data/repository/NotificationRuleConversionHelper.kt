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

import android.app.NotificationRule
import android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK
import android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT_AND_ALERT
import android.app.NotificationRule.Action.PRIMARY_ACTION_LOW
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel

/**
 * Helper object for converting between the external and internal model representations of a rule.
 */
object NotificationRuleConversionHelper {
    val validActionsMap: Map<Int, ActionModel> =
        mapOf(
            PRIMARY_ACTION_HIGHLIGHT_AND_ALERT to ActionModel.HighlightAndAlert,
            PRIMARY_ACTION_HIGHLIGHT to ActionModel.Highlight,
            PRIMARY_ACTION_LOW to ActionModel.Silence,
            PRIMARY_ACTION_BUNDLE to ActionModel.Bundle,
            PRIMARY_ACTION_BLOCK to ActionModel.Block,
        )

    fun ActionModel.toExternalModel(): NotificationRule.Action {
        val primaryAction =
            validActionsMap.entries.find { it.value == this }?.key
                ?: throw IllegalStateException(
                    "ActionModel doesn't have a corresponding entry in validActionsMap"
                )
        return NotificationRule.Action.Builder(primaryAction).build()
    }
}
