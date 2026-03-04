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
import android.app.notificationManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationRulesRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val underTest = kosmos.realNotificationRulesRepository

    @Test
    fun createDraftRuleFromFreeformText_includesIsNewAndAction() =
        kosmos.runTest {
            val result =
                underTest.createDraftRuleFromFreeformText(
                    action = ActionModel.Block,
                    text = "sample text",
                )

            assertThat(result.isNew).isTrue()
            assertThat(result.action).isEqualTo(ActionModel.Block)
        }

    @Test
    @DisableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_flagOff_isEmpty() =
        kosmos.runTest {
            val rule = createRule(id = 23)
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).isEmpty()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_id() =
        kosmos.runTest {
            val rule = createRule(id = 23)
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo(23)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_unknownAction_filteredOut() =
        kosmos.runTest {
            val rule = createRule(action = NotificationRule.Action.Builder(100).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).isEmpty()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_highlightAndAlertAction() =
        kosmos.runTest {
            val rule =
                createRule(
                    action =
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT_AND_ALERT).build()
                )
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.HighlightAndAlert)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_highlightAction() =
        kosmos.runTest {
            val rule =
                createRule(
                    action = NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build()
                )
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.Highlight)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_silenceAction() =
        kosmos.runTest {
            val rule =
                createRule(action = NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.Silence)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_bundleAction() =
        kosmos.runTest {
            val rule =
                createRule(action = NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.Bundle)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_blockAction() =
        kosmos.runTest {
            val rule =
                createRule(action = NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.Block)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_multipleRules_inOrder() =
        kosmos.runTest {
            putRulesIntoNotificationManager(
                listOf(createRule(id = 12), createRule(id = 34), createRule(id = 56))
            )

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(3)
            assertThat(result[0].id).isEqualTo(12)
            assertThat(result[1].id).isEqualTo(34)
            assertThat(result[2].id).isEqualTo(56)
        }

    private fun createRule(
        id: Int = FAKE_ID,
        action: NotificationRule.Action =
            NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
    ): NotificationRule {
        return NotificationRule.Builder(id, action).build()
    }

    private fun Kosmos.putRulesIntoNotificationManager(rules: List<NotificationRule>) {
        whenever(notificationManager.notificationRules).thenReturn(rules)
    }

    companion object {
        private const val FAKE_ID = 101
    }
}
