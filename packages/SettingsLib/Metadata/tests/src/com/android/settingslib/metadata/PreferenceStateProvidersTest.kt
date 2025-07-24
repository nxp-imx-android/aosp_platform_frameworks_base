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
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class PreferenceStateProvidersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val screen = mock<PreferenceScreenMetadata>()
    private val subScreen = mock<PreferenceScreenMetadata>()
    private val preference = mock<PreferenceMetadata> { on { key } doReturn "key" }

    @Test
    fun PreferenceTitleProvider_getTitleAsync_delegatesToGetTitle() = runBlocking {
        val provider =
            object : PreferenceTitleProvider {
                override fun getTitle(context: Context) = "title"
            }

        assertThat(provider.getTitleAsync(context)).isEqualTo("title")
    }

    @Test
    fun PreferenceIndexableTitleProvider_getIndexableTitleAsync_delegatesToGetIndexableTitle() = runBlocking {
        val provider =
            object : PreferenceIndexableTitleProvider {
                override fun getIndexableTitle(context: Context) = "indexable title"
            }

        assertThat(provider.getIndexableTitleAsync(context)).isEqualTo("indexable title")
    }

    @Test
    fun PreferenceSummaryProvider_getSummaryAsync_delegatesToGetSummary() = runBlocking {
        val provider =
            object : PreferenceSummaryProvider {
                override fun getSummary(context: Context) = "summary"
            }

        assertThat(provider.getSummaryAsync(context)).isEqualTo("summary")
    }

    @Test
    fun PreferenceIconProvider_getIconAsync_delegatesToGetIcon() = runBlocking {
        val provider =
            object : PreferenceIconProvider {
                override fun getIcon(context: Context) = 1
            }

        assertThat(provider.getIconAsync(context)).isEqualTo(1)
    }

    @Test
    fun PreferenceAvailabilityProvider_isAvailableAsync_delegatesToIsAvailable() = runBlocking {
        val provider =
            object : PreferenceAvailabilityProvider {
                override fun isAvailable(context: Context) = true
            }

        assertThat(provider.isAvailableAsync(context)).isTrue()
    }

    @Test
    fun PreferenceRestrictionProvider_isRestrictedAsync_delegatesToIsRestricted() = runBlocking {
        val provider =
            object : PreferenceRestrictionProvider {
                override fun isRestricted(context: Context) = true
            }

        assertThat(provider.isRestrictedAsync(context)).isTrue()
    }
}
