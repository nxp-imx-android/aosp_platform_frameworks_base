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

import android.app.NotificationManager
import android.app.NotificationRule
import android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK
import android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT_AND_ALERT
import android.app.NotificationRule.Action.PRIMARY_ACTION_LOW
import androidx.compose.runtime.mutableStateListOf
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.FilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class NotificationRulesRepositoryImpl
@Inject
constructor(
    private val notificationManager: NotificationManager,
    private val freeformRuleRepository: FreeformRuleRepository,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : NotificationRulesRepository, CoreStartable {
    override var rules = mutableStateListOf<RuleModel>()

    override fun start() {
        if (!NmContextualDisplayLaunch.isEnabled) {
            return
        }

        applicationScope.launch {
            val initialRules = withContext(backgroundDispatcher) { fetchInitialRules() }

            // Modify `rules` on the main thread so order is guaranteed.
            rules.clear()
            rules.addAll(initialRules)
        }
    }

    private fun fetchInitialRules(): List<RuleModel> {
        return notificationManager.notificationRules
            .filter {
                // TODO: b/478225883 - Log error if action isn't valid.
                validActions.contains(it.action.primaryAction)
            }
            .map {
                RuleModel(
                    id = it.id,
                    action = it.action.toInternalModel(),
                    // TODO: b/478225883 - Fill in the rest of the RuleModel.
                    filter = FilterModel(contacts = null, includedApps = null),
                )
            }
    }

    override suspend fun createDraftRuleFromFreeformText(
        action: ActionModel,
        text: String,
    ): ResponseModel<DraftRuleModel> {
        return freeformRuleRepository.createDraftRuleFromFreeformText(action, text)
    }

    override fun createRule(newRule: RuleModel) {
        NmContextualDisplayLaunch.expectInNewMode()
        // TODO: b/478225883 - Send rule to system_server for saving. Use an actor pattern with a
        // Channel to avoid blocking the main thread and guarantee order of operations.
        rules += newRule
    }

    private fun NotificationRule.Action.toInternalModel(): ActionModel {
        return when (this.primaryAction) {
            PRIMARY_ACTION_HIGHLIGHT_AND_ALERT -> ActionModel.HighlightAndAlert
            PRIMARY_ACTION_HIGHLIGHT -> ActionModel.Highlight
            PRIMARY_ACTION_LOW -> ActionModel.Silence
            PRIMARY_ACTION_BUNDLE -> ActionModel.Bundle
            PRIMARY_ACTION_BLOCK -> ActionModel.Block
            else ->
                throw IllegalStateException(
                    "Action $this should have been filtered out previously. " +
                        "Does validActions need to be updated?"
                )
        }
    }

    companion object {
        private val validActions =
            listOf(
                PRIMARY_ACTION_HIGHLIGHT_AND_ALERT,
                PRIMARY_ACTION_HIGHLIGHT,
                PRIMARY_ACTION_LOW,
                PRIMARY_ACTION_BUNDLE,
                PRIMARY_ACTION_BLOCK,
            )
    }
}
