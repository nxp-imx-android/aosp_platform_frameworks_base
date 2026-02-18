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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationRulesScreenViewModelTest : SysuiTestCase() {
    val kosmos = testKosmosNew()

    val Kosmos.underTest by Kosmos.Fixture { notificationRulesScreenViewModel }

    @Test
    fun launchEditRuleScreen_updatesViewState() =
        kosmos.runTest {
            assertThat(underTest.viewState).isEqualTo(RulesScreenViewState.CurrentRules)

            val draftRule =
                DraftRuleModel(action = ActionModel.Highlight, contacts = null, includedApps = null)

            kosmos.underTest.launchEditRuleScreen(draftRule)

            assertThat(underTest.viewState).isInstanceOf(RulesScreenViewState.RuleEdit::class.java)
            assertThat((underTest.viewState as RulesScreenViewState.RuleEdit).editViewModel.rule)
                .isEqualTo(draftRule)
        }
}
