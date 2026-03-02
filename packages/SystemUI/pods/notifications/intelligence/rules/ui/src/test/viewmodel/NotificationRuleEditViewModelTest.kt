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
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationRuleEditViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    val Kosmos.underTest by Kosmos.Fixture { notificationRuleEditViewModelFactory }

    @Test
    fun buildRuleText_onlyAction() =
        kosmos.runTest {
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = null,
                        includedApps = null,
                    )
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(1)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
        }

    @Test
    fun buildRuleText_singleContact() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val contact =
                ContactModel(
                    lookupUri = "cat-uri".toUri(),
                    name = "Meowth",
                    photoUri = "cat-photo".toUri(),
                )
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = RuleValue.Specified(ContactsModel(listOf(contact))),
                        includedApps = null,
                    )
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Meowth")
            assertThat(clickableChunk.isAmbiguous).isFalse()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.Contacts::class.java)
        }

    @Test
    fun buildRuleText_ambiguousContact() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = RuleValue.Ambiguous("who is it?"),
                        includedApps = null,
                    )
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("who is it?")
            assertThat(clickableChunk.isAmbiguous).isTrue()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.Contacts::class.java)
        }

    @Test
    fun buildRuleText_singleApp() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val app =
                AppModel(
                    packageName = "fake.app.messaging.cat",
                    label = "Chat the Cat",
                    icon = mock<Drawable>(),
                    uid = 1000,
                )
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = null,
                        includedApps = RuleValue.Specified(IncludedAppsModel(listOf(app))),
                    )
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Chat the Cat")
            assertThat(clickableChunk.isAmbiguous).isFalse()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.Apps::class.java)
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
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = RuleValue.Specified(ContactsModel(listOf(contact, CONTACT_CAT))),
                        includedApps = null,
                    )
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Mom Cell +1 more")
            assertThat(clickableChunk.isAmbiguous).isFalse()
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
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        includedApps =
                            RuleValue.Specified(
                                IncludedAppsModel(listOf(app, APP_CHAT_CAT, APP_POST_CAT))
                            ),
                        contacts = null,
                    )
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Puzzle the Cat +2 more")
            assertThat(clickableChunk.isAmbiguous).isFalse()
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
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = RuleValue.Specified(ContactsModel(listOf(contact))),
                        includedApps =
                            RuleValue.Specified(
                                IncludedAppsModel(listOf(app, APP_CHAT_CAT, APP_POST_CAT))
                            ),
                    )
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(5)
            assertThat(ruleDisplay.textChunks[0])
                .isEqualTo(TextChunk.BasicText("Notifications [TK]"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunkApps = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunkApps.text).isEqualTo("Puzzle the Cat +2 more")
            assertThat(clickableChunkApps.isAmbiguous).isFalse()

            assertThat(ruleDisplay.textChunks[3]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[4]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunkContacts = ruleDisplay.textChunks[4] as TextChunk.ClickableText
            assertThat(clickableChunkContacts.text).isEqualTo("Mom Cell")
            assertThat(clickableChunkContacts.isAmbiguous).isFalse()
        }

    @Test
    fun onAppsSaved_storesNewList() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = null,
                        includedApps = null,
                    )
                )

            underTest.onAppsSaved(listOf(APP_CHAT_CAT, APP_POST_CAT)) { onExitInvoked = true }

            assertThat(underTest.rule.includedApps)
                .isEqualTo(
                    RuleValue.Specified(IncludedAppsModel(listOf(APP_CHAT_CAT, APP_POST_CAT)))
                )
            assertThat(onExitInvoked).isTrue()
        }

    @Test
    fun onAppsSaved_emptyList_resetsToNull() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = null,
                        includedApps =
                            RuleValue.Specified(
                                IncludedAppsModel(listOf(APP_CHAT_CAT, APP_POST_CAT))
                            ),
                    )
                )

            underTest.onAppsSaved(newApps = emptyList()) { onExitInvoked = true }

            assertThat(underTest.rule.includedApps).isNull()
            assertThat(onExitInvoked).isTrue()
        }

    @Test
    fun onContactsSaved_storesNewList() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = null,
                        includedApps = null,
                    )
                )

            underTest.onContactsSaved(listOf(CONTACT_CAT)) { onExitInvoked = true }

            assertThat(underTest.rule.contacts)
                .isEqualTo(RuleValue.Specified(ContactsModel(listOf(CONTACT_CAT))))
            assertThat(onExitInvoked).isTrue()
        }

    @Test
    fun onContactsSaved_emptyList_resetsToNull() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel(
                        action = ActionModel.Highlight,
                        contacts = RuleValue.Specified(ContactsModel(listOf(CONTACT_CAT))),
                        includedApps = null,
                    )
                )

            underTest.onContactsSaved(newContacts = emptyList()) { onExitInvoked = true }

            assertThat(underTest.rule.contacts).isNull()
            assertThat(onExitInvoked).isTrue()
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
