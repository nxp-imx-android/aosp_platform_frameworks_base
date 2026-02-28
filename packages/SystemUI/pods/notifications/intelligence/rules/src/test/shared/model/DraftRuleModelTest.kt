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

package com.android.systemui.notifications.intelligence.rules.shared.model

import android.graphics.drawable.ShapeDrawable
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toDraft
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DraftRuleModelTest : SysuiTestCase() {
    @Test
    fun toDraft_allNull() {
        val rule =
            RuleModel(
                action = ActionModel.Bundle,
                filter = FilterModel(contacts = null, includedApps = null),
            )

        val draftRule = rule.toDraft()

        assertThat(draftRule.action).isEqualTo(ActionModel.Bundle)
        assertThat(draftRule.contacts).isNull()
        assertThat(draftRule.includedApps).isNull()
    }

    @Test
    fun toDraft_allFilledIn() {
        val contacts =
            ContactsModel(
                listOf(
                    ContactModel(lookupUri = "key".toUri(), name = "name", photoUri = "uri".toUri())
                )
            )
        val includedApps =
            IncludedAppsModel(
                listOf(
                    AppModel(
                        uid = 13,
                        label = "app label",
                        icon = ShapeDrawable(),
                        packageName = "app.name",
                    )
                )
            )
        val rule =
            RuleModel(
                action = ActionModel.Silence,
                filter = FilterModel(contacts = contacts, includedApps = includedApps),
            )

        val draftRule = rule.toDraft()

        assertThat(draftRule.action).isEqualTo(ActionModel.Silence)
        assertThat(draftRule.contacts).isEqualTo(RuleValue.Specified(contacts))
        assertThat(draftRule.includedApps).isEqualTo(RuleValue.Specified(includedApps))
    }
}
