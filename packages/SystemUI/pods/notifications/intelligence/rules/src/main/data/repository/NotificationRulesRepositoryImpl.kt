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
import androidx.annotation.MainThread
import androidx.compose.runtime.mutableStateListOf
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRuleConversionHelper.toInternalModel
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRuleConversionHelper.validActionsMap
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRuleToExternalHelpers.toExternalRuleFormat
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toFullRule
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
    @Main private val mainDispatcher: CoroutineDispatcher,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : NotificationRulesRepository, CoreStartable {
    override var rules = mutableStateListOf<RuleModel>()

    private val availableRuleIds = mutableStateListOf<Int>().apply { addAll(RULE_ID_RANGE) }

    override fun start() {
        if (!NmContextualDisplayLaunch.isEnabled) {
            return
        }

        applicationScope.launch {
            val initialRules = withContext(backgroundDispatcher) { fetchInitialRules() }
            val initialRuleIds = initialRules.map { it.id }

            // Modify `rules` on the main thread so order is guaranteed.
            rules.clear()
            rules.addAll(initialRules)

            availableRuleIds.removeAll(initialRuleIds)
        }
    }

    private fun fetchInitialRules(): List<RuleModel> {
        return notificationManager.notificationRules
            .filter {
                // TODO: b/478225883 - Log error if action isn't valid.
                validActionsMap.containsKey(it.action.primaryAction)
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

    override suspend fun saveRule(rule: DraftRuleModel): Boolean {
        NmContextualDisplayLaunch.expectInNewMode()

        return when (rule) {
            is DraftRuleModel.New -> createNewRule(rule)
            is DraftRuleModel.PreExisting -> updateExistingRule(rule)
        }
    }

    private suspend fun createNewRule(newRule: DraftRuleModel.New): Boolean {
        val position = 0
        val formedRule = newRule.toFullRule(id = generateIdForNewRule())
        val externalRule = formedRule.toExternalRuleFormat()
        val savedRule =
            withContext(backgroundDispatcher) {
                val createdRule = notificationManager.addNotificationRule(externalRule, position)
                if (createdRule != null) {
                    // TODO: b/478225883 - Use the rule returned from NotificationManager as the
                    // official rule definition
                    formedRule
                } else {
                    null
                }
            }

        if (savedRule != null) {
            withContext(mainDispatcher) {
                // Always modify rules list & IDs list on main thread
                rules.add(position, savedRule)
                availableRuleIds.remove(savedRule.id)
            }
        }
        return savedRule != null
    }

    private suspend fun updateExistingRule(updatedRule: DraftRuleModel.PreExisting): Boolean {
        val formedRule = updatedRule.toFullRule()
        val externalRule = formedRule.toExternalRuleFormat()
        val savedRule =
            withContext(backgroundDispatcher) {
                val updatedRule = notificationManager.updateNotificationRule(externalRule)
                if (updatedRule != null) {
                    // TODO: b/478225883 - Use the rule returned from NotificationManager as the
                    // official rule definition
                    formedRule
                } else {
                    null
                }
            }

        if (savedRule != null) {
            withContext(mainDispatcher) {
                // Always modify rules list on main thread
                val existingRuleIndex = rules.indexOfFirst { it.id == savedRule.id }
                rules[existingRuleIndex] = savedRule
            }
        }
        return savedRule != null
    }

    @MainThread // Keep on the main thread so that two rules can't take the same ID
    private fun generateIdForNewRule(): Int {
        if (availableRuleIds.isEmpty()) {
            // TODO: b/478225883 - In the UI, don't allow a user to start creating a rule if they're
            //  already maxed out.
            throw IllegalStateException("All rule slots are already taken")
        }
        return availableRuleIds[0]
    }

    companion object {
        private val RULE_ID_RANGE = 100..125
    }
}
