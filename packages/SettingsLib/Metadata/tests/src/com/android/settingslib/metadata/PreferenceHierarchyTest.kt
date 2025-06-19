/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settingslib.metadata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@RunWith(AndroidJUnit4::class)
class PreferenceHierarchyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val screen = mock<PreferenceScreenMetadata>()
    private val subScreen = mock<PreferenceScreenMetadata>()
    private val preference = mock<PreferenceMetadata> { on { key } doReturn "key" }

    @Test
    fun addScreen_flagDisabled() {
        subScreen.stub { on { isFlagEnabled(context) } doReturn false }
        val hierarchy =
            screen.preferenceHierarchy(context) {
                +subScreen
                +preference
                add(subScreen, 1)
                addBefore(preference.key, subScreen)
                addAfter(preference.key, subScreen)
                addGroup(subScreen)
                addGroupBefore(preference.key, subScreen)
                addGroupAfter(preference.key, subScreen)
            }
        assertThat(hierarchy.children).hasSize(1)
        (hierarchy.children[0] as PreferenceHierarchyNode).apply {
            assertThat(metadata).isSameInstanceAs(preference)
        }
    }

    @Test
    fun addScreen_flagEnabled() {
        subScreen.stub { on { isFlagEnabled(context) } doReturn true }
        val hierarchy = screen.preferenceHierarchy(context) { +subScreen }
        assertThat(hierarchy.children).hasSize(1)
        (hierarchy.children[0] as PreferenceHierarchyNode).apply {
            assertThat(metadata).isSameInstanceAs(subScreen)
        }
    }

    @Test
    fun addScreen_flagEnabled_withOrder() {
        subScreen.stub { on { isFlagEnabled(context) } doReturn true }
        val hierarchy = screen.preferenceHierarchy(context) { +subScreen order 1 }
        assertThat(hierarchy.children).hasSize(1)
        (hierarchy.children[0] as PreferenceHierarchyNode).apply {
            assertThat(metadata).isSameInstanceAs(subScreen)
            assertThat(order).isEqualTo(1)
        }
    }
}
