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

import android.annotation.FlaggedApi
import android.annotation.SuppressLint
import android.app.NotificationRule
import android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK
import android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT_AND_ALERT
import android.app.NotificationRule.Action.PRIMARY_ACTION_LOW
import android.app.notificationManager
import android.graphics.drawable.ShapeDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.assertLogsWtf
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftFilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.FilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@FlaggedApi(NmContextualDisplayLaunch.FLAG_NAME)
@SuppressLint("RunBlockingUsage") // TODO: b/493190684 - Don't run linters on pod tests.
class NotificationRulesRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val underTest = kosmos.realNotificationRulesRepository

    @Before
    fun setUp() {
        // By default, return the rule that was passed in
        whenever(kosmos.notificationManager.addNotificationRule(any(), any())).thenAnswer {
            invocation ->
            invocation.arguments[0]
        }
        whenever(kosmos.notificationManager.updateNotificationRule(any())).thenAnswer { invocation
            ->
            invocation.arguments[0]
        }
    }

    @Test
    fun createDraftRuleFromFreeformText_isNewAndHasAction() =
        kosmos.runTest {
            val result =
                underTest.createDraftRuleFromFreeformText(
                    action = ActionModel.Block,
                    text = "sample text",
                )

            assertThat(result).isInstanceOf(ResponseModel.Success::class.java)
            assertThat((result as ResponseModel.Success<DraftRuleModel>).draftRule)
                .isInstanceOf(DraftRuleModel.New::class.java)
            assertThat((result as ResponseModel.Success<DraftRuleModel>).draftRule.action)
                .isEqualTo(ActionModel.Block)
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

    @Test
    @DisableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_flagDisabled_logsWtf() =
        kosmos.runTest {
            underTest.start()

            val draftRule =
                DraftRuleModel.New(action = ActionModel.Silence, filter = DraftFilterModel())

            assertLogsWtf { runBlocking { underTest.saveRule(draftRule) } }
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_savesAndAddsToList() =
        kosmos.runTest {
            underTest.start()

            val draftRule =
                DraftRuleModel.New(
                    action = ActionModel.Silence,
                    filter =
                        DraftFilterModel(
                            contacts =
                                RuleValue.Specified(
                                    ContactsModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2))
                                ),
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                        ),
                )

            testScope.launch { underTest.saveRule(draftRule) }

            val expectedRule =
                NotificationRule.Builder(
                        100,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addContact(CONTACT_1_URI)
                            .addContact(CONTACT_2_URI)
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .build()
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)

            assertThat(underTest.rules).hasSize(1)
            val savedRule = underTest.rules[0]
            assertThat(savedRule.id).isEqualTo(100)
            assertThat(savedRule.action).isEqualTo(ActionModel.Silence)
            assertThat(savedRule.filter.contacts)
                .isEqualTo(ContactsModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2)))
            assertThat(savedRule.filter.includedApps).isEqualTo(IncludedAppsModel(listOf(FAKE_APP)))
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_preExistingRule_updatesAndReplaces() =
        kosmos.runTest {
            putRulesIntoNotificationManager(listOf(createRule(id = 12), createRule(id = 34)))
            underTest.start()

            val updatedRuleDraft =
                DraftRuleModel.PreExisting(
                    id = 34,
                    action = ActionModel.Block,
                    filter =
                        DraftFilterModel(
                            contacts =
                                RuleValue.Specified(
                                    ContactsModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2))
                                ),
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                        ),
                )

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            val expectedRule =
                NotificationRule.Builder(
                        34,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addContact(CONTACT_1_URI)
                            .addContact(CONTACT_2_URI)
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .build()
                    )
                    .build()
            verify(notificationManager).updateNotificationRule(expectedRule)

            assertThat(underTest.rules).hasSize(2)
            assertThat(underTest.rules[0].id).isEqualTo(12)
            val savedRule = underTest.rules[1]
            assertThat(savedRule.id).isEqualTo(34)
            assertThat(savedRule.action).isEqualTo(ActionModel.Block)
            assertThat(savedRule.filter.contacts)
                .isEqualTo(ContactsModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2)))
            assertThat(savedRule.filter.includedApps).isEqualTo(IncludedAppsModel(listOf(FAKE_APP)))
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRules_addedToFront_withIncreasingIds() =
        kosmos.runTest {
            testScope.launch {
                underTest.saveRule(
                    DraftRuleModel.New(ActionModel.HighlightAndAlert, filter = DraftFilterModel())
                )
            }
            testScope.launch {
                underTest.saveRule(
                    DraftRuleModel.New(ActionModel.Highlight, filter = DraftFilterModel())
                )
            }
            testScope.launch {
                underTest.saveRule(
                    DraftRuleModel.New(ActionModel.Silence, filter = DraftFilterModel())
                )
            }

            verify(notificationManager)
                .addNotificationRule(
                    NotificationRule.Builder(
                            100,
                            NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT_AND_ALERT)
                                .build(),
                        )
                        .build(),
                    0,
                )
            verify(notificationManager)
                .addNotificationRule(
                    NotificationRule.Builder(
                            101,
                            NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
                        )
                        .build(),
                    0,
                )
            verify(notificationManager)
                .addNotificationRule(
                    NotificationRule.Builder(
                            102,
                            NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build(),
                        )
                        .build(),
                    0,
                )

            assertThat(underTest.rules).hasSize(3)
            assertThat(underTest.rules[0].id).isEqualTo(102)
            assertThat(underTest.rules[1].id).isEqualTo(101)
            assertThat(underTest.rules[2].id).isEqualTo(100)

            assertThat(underTest.rules[0].action).isEqualTo(ActionModel.Silence)
            assertThat(underTest.rules[1].action).isEqualTo(ActionModel.Highlight)
            assertThat(underTest.rules[2].action).isEqualTo(ActionModel.HighlightAndAlert)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_usesFirstAvailableId() =
        kosmos.runTest {
            putRulesIntoNotificationManager(
                listOf(createRule(id = 100), createRule(id = 101), createRule(id = 103))
            )
            underTest.start()

            val draftRule =
                DraftRuleModel.New(action = ActionModel.Silence, filter = DraftFilterModel())
            testScope.launch { underTest.saveRule(draftRule) }

            // 102 is the first available ID
            val expectedRule =
                NotificationRule.Builder(
                        102,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build(),
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)

            assertThat(underTest.rules).hasSize(4)
            assertThat(underTest.rules[0].id).isEqualTo(102)

            // Now 104 is the first available ID
            val secondDraftRule =
                DraftRuleModel.New(
                    action = ActionModel.HighlightAndAlert,
                    filter = DraftFilterModel(),
                )
            testScope.launch { underTest.saveRule(secondDraftRule) }

            val expectedSecondRule =
                NotificationRule.Builder(
                        104,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT_AND_ALERT).build(),
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedSecondRule, 0)

            assertThat(underTest.rules).hasSize(5)
            assertThat(underTest.rules[0].id).isEqualTo(104)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRuleSaveFails_nextNewRuleGetsFirstAvailableId() =
        kosmos.runTest {
            putRulesIntoNotificationManager(
                listOf(createRule(id = 100), createRule(id = 101), createRule(id = 105))
            )
            underTest.start()

            val draftRule =
                DraftRuleModel.New(action = ActionModel.Silence, filter = DraftFilterModel())

            // First attempt to save a draft rule: Fails
            whenever(notificationManager.addNotificationRule(any(), any())).thenReturn(null)
            testScope.launch { underTest.saveRule(draftRule) }
            assertThat(underTest.rules).hasSize(3)

            // Second attempt to save a draft rule: Succeeds
            val newDraftRule =
                DraftRuleModel.New(action = ActionModel.Bundle, filter = DraftFilterModel())
            whenever(kosmos.notificationManager.addNotificationRule(any(), any())).thenAnswer {
                invocation ->
                invocation.arguments[0]
            }
            testScope.launch { underTest.saveRule(newDraftRule) }

            // The second rule should still get ID 102 since the first rule never successfully saved
            val expectedRule =
                NotificationRule.Builder(
                        102,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE).build(),
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)

            assertThat(underTest.rules).hasSize(4)
            assertThat(underTest.rules[0].id).isEqualTo(102)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_allIdsTaken_throws() =
        kosmos.runTest {
            val rules = (100..125).map { createRule(id = it) }
            putRulesIntoNotificationManager(rules)
            underTest.start()

            val draftRule =
                DraftRuleModel.New(action = ActionModel.Silence, filter = DraftFilterModel())

            assertThrows(IllegalStateException::class.java) {
                runBlocking { underTest.saveRule(draftRule) }
            }
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_someNullFilterValues() =
        kosmos.runTest {
            putRulesIntoNotificationManager(listOf(createRule(id = 34)))
            underTest.start()

            val updatedRuleDraft =
                DraftRuleModel.PreExisting(
                    id = 34,
                    action = ActionModel.Silence,
                    filter =
                        DraftFilterModel(
                            contacts = null,
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                        ),
                )

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            val expectedRule =
                NotificationRule.Builder(
                        34,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .build()
                    )
                    .build()
            verify(notificationManager).updateNotificationRule(expectedRule)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_noFilterValues() =
        kosmos.runTest {
            underTest.start()

            val updatedRuleDraft =
                DraftRuleModel.New(
                    action = ActionModel.Highlight,
                    filter = DraftFilterModel(contacts = null, includedApps = null),
                )

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            val expectedRule =
                NotificationRule.Builder(
                        100,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_notificationManagerReturnsNull_notAdded() =
        kosmos.runTest {
            whenever(notificationManager.addNotificationRule(any(), any())).thenReturn(null)

            val draftRule =
                DraftRuleModel.New(
                    action = ActionModel.Silence,
                    filter =
                        DraftFilterModel(
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP)))
                        ),
                )

            testScope.launch { underTest.saveRule(draftRule) }

            // Verify the rule was sent to the manager, but we didn't update the internal list
            verify(notificationManager).addNotificationRule(any(), eq(0))
            assertThat(underTest.rules).isEmpty()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_preExistingRule_notificationManagerReturnsNull_notUpdated() =
        kosmos.runTest {
            whenever(notificationManager.updateNotificationRule(any())).thenReturn(null)

            putRulesIntoNotificationManager(listOf(createRule(id = 12), createRule(id = 34)))
            underTest.start()

            val updatedRuleDraft =
                DraftRuleModel.PreExisting(
                    id = 34,
                    action = ActionModel.Silence,
                    filter =
                        DraftFilterModel(
                            contacts =
                                RuleValue.Specified(
                                    ContactsModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2))
                                ),
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                        ),
                )

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            // Verify the rule was sent to the manager, but we didn't update the internal rule
            verify(notificationManager).updateNotificationRule(any())
            assertThat(underTest.rules).hasSize(2)
            assertThat(underTest.rules[1].filter)
                .isEqualTo(FilterModel(contacts = null, includedApps = null))
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
        private val CONTACT_1_URI = "key1".toUri()
        private val CONTACT_2_URI = "key2".toUri()
        private val FAKE_CONTACT_1 =
            ContactModel(lookupUri = CONTACT_1_URI, name = "name1", photoUri = "uri1".toUri())
        private val FAKE_CONTACT_2 =
            ContactModel(lookupUri = CONTACT_2_URI, name = "name2", photoUri = "uri2".toUri())

        private const val FAKE_APP_UID = 13
        private val FAKE_APP =
            AppModel(
                uid = FAKE_APP_UID,
                label = "app label",
                icon = ShapeDrawable(),
                packageName = "app.name",
            )
    }
}
