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

import android.content.applicationContext
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateListOf
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ContactsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.FilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationRulesScreenViewModelTest : SysuiTestCase() {
    val kosmos = testKosmosNew()
    val backStack = mutableStateListOf<RulesScreenViewState>(RulesScreenViewState.CurrentRules)

    val Kosmos.underTest by
        Kosmos.Fixture { notificationRulesScreenViewModelFactory.create(backStack) }

    @Test
    fun currentScreen_isUpdatedWithBackStack() =
        kosmos.runTest {
            assertThat(underTest.currentScreen).isEqualTo(RulesScreenViewState.CurrentRules)

            val draftRule =
                DraftRuleModel(
                    isNew = false,
                    action = ActionModel.Highlight,
                    contacts = null,
                    includedApps = null,
                )

            backStack.add(
                RulesScreenViewState.EditRule(
                    notificationRuleEditViewModelFactory.create(draftRule)
                )
            )

            assertThat(underTest.currentScreen)
                .isInstanceOf(RulesScreenViewState.EditRule::class.java)
            assertThat((underTest.currentScreen as RulesScreenViewState.EditRule).viewModel.rule)
                .isEqualTo(draftRule)
        }

    @Test
    fun buildRuleText_onlyAction() =
        kosmos.runTest {
            val rule =
                RuleModel(
                    action = ActionModel.Highlight,
                    filter = FilterModel(includedApps = null, contacts = null),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(1)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
        }

    @Test
    fun buildRuleText_singleContact() =
        kosmos.runTest {
            val contact =
                ContactModel(
                    lookupUri = "cat-uri".toUri(),
                    name = "Meowth",
                    photoUri = "cat-photo".toUri(),
                )
            val rule =
                RuleModel(
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(includedApps = null, contacts = ContactsModel(listOf(contact))),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val valueChunk = ruleDisplay.textChunks[2] as TextChunk.FieldValueText
            assertThat(valueChunk.text).isEqualTo("Meowth")
        }

    @Test
    fun buildRuleText_singleApp() =
        kosmos.runTest {
            val app =
                AppModel(
                    packageName = "fake.app.messaging.cat",
                    label = "Chat the Cat",
                    icon = mock<Drawable>(),
                    uid = 1000,
                )
            val rule =
                RuleModel(
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(includedApps = IncludedAppsModel(listOf(app)), contacts = null),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val valueChunk = ruleDisplay.textChunks[2] as TextChunk.FieldValueText
            assertThat(valueChunk.text).isEqualTo("Chat the Cat")
        }

    @Test
    fun buildRuleText_twoContacts() =
        kosmos.runTest {
            val contact =
                ContactModel(
                    lookupUri = "mom-uri".toUri(),
                    name = "Mom Cell",
                    photoUri = "mom-photo".toUri(),
                )
            val rule =
                RuleModel(
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(
                            includedApps = null,
                            contacts = ContactsModel(listOf(contact, CONTACT_CAT)),
                        ),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunk = ruleDisplay.textChunks[2] as TextChunk.FieldValueText
            assertThat(fieldValueChunk.text).isEqualTo("Mom Cell +1 more")
        }

    @Test
    fun buildRuleText_threeApps() =
        kosmos.runTest {
            val app =
                AppModel(
                    packageName = "fake.app.crossword",
                    label = "Puzzle the Cat",
                    icon = mock<Drawable>(),
                    uid = 2000,
                )
            val rule =
                RuleModel(
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(
                            includedApps =
                                IncludedAppsModel(listOf(app, APP_CHAT_CAT, APP_POST_CAT)),
                            contacts = null,
                        ),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunk = ruleDisplay.textChunks[2] as TextChunk.FieldValueText
            assertThat(fieldValueChunk.text).isEqualTo("Puzzle the Cat +2 more")
        }

    @Test
    fun buildRuleText_contactsAndApps() =
        kosmos.runTest {
            val contact =
                ContactModel(
                    lookupUri = "mom-uri".toUri(),
                    name = "Mom Cell",
                    photoUri = "mom-photo".toUri(),
                )
            val app =
                AppModel(
                    packageName = "fake.app.crossword",
                    label = "Puzzle the Cat",
                    icon = mock<Drawable>(),
                    uid = 2000,
                )
            val rule =
                RuleModel(
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(
                            includedApps =
                                IncludedAppsModel(listOf(app, APP_CHAT_CAT, APP_POST_CAT)),
                            contacts = ContactsModel(listOf(contact)),
                        ),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(5)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunkApps = ruleDisplay.textChunks[2] as TextChunk.FieldValueText
            assertThat(fieldValueChunkApps.text).isEqualTo("Puzzle the Cat +2 more")

            assertThat(ruleDisplay.textChunks[3]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[4]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunkContacts = ruleDisplay.textChunks[4] as TextChunk.FieldValueText
            assertThat(fieldValueChunkContacts.text).isEqualTo("Mom Cell")
        }

    companion object {
        private val CONTACT_CAT =
            ContactModel(
                lookupUri = "cat-uri".toUri(),
                name = "Meowth",
                photoUri = "cat-photo".toUri(),
            )

        private val APP_CHAT_CAT =
            AppModel(
                packageName = "fake.app.messaging.cat",
                label = "Chat the Cat",
                icon = mock<Drawable>(),
                uid = 1000,
            )
        private val APP_POST_CAT =
            AppModel(
                packageName = "fake.app.social",
                label = "Post the Cat",
                icon = mock<Drawable>(),
                uid = 1001,
            )
    }
}
