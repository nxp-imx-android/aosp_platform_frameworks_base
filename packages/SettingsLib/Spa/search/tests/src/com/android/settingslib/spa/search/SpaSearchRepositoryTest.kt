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

package com.android.settingslib.spa.search

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingKey
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingSpaPage
import com.android.settingslib.spa.search.SpaSearchRepository.Companion.createSearchIndexableData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class SpaSearchRepositoryTest {

    @Test
    fun createSearchIndexableData() {
        val pageProvider =
            object : SettingsPageProvider {
                override val name = PAGE_NAME
            }
        val searchItem = SearchablePage.SearchItem(ITEM_TITLE)

        val searchIndexableData =
            pageProvider.createSearchIndexableData(
                intentAction = INTENT_ACTION,
                intentTargetClass = INTENT_TARGET_CLASS,
                getPageTitleForSearch = { PAGE_TITLE },
                searchItemsProvider = { listOf(searchItem) },
            )
        val dynamicRawDataToIndex =
            searchIndexableData.searchIndexProvider.getDynamicRawDataToIndex(mock<Context>(), true)

        assertThat(searchIndexableData.targetClass).isEqualTo(pageProvider::class.java)
        assertThat(dynamicRawDataToIndex).hasSize(1)
        val rawData = dynamicRawDataToIndex[0]
        assertThat(decodeToSpaSearchLandingKey(rawData.key))
            .isEqualTo(
                SpaSearchLandingKey.newBuilder()
                    .setSpaPage(SpaSearchLandingSpaPage.newBuilder().setDestination(PAGE_NAME))
                    .build()
            )
        assertThat(rawData.title).isEqualTo(ITEM_TITLE)
        assertThat(rawData.intentAction).isEqualTo(INTENT_ACTION)
        assertThat(rawData.intentTargetClass).isEqualTo(INTENT_TARGET_CLASS)
        assertThat(rawData.className).isEqualTo(pageProvider::class.java.name)
        assertThat(rawData.screenTitle).isEqualTo(PAGE_TITLE)
    }

    private companion object {
        const val INTENT_ACTION = "intent.Action"
        const val INTENT_TARGET_CLASS = "target.Class"
        const val PAGE_NAME = "PageName"
        const val PAGE_TITLE = "Page Title"
        const val ITEM_TITLE = "Item Title"
    }
}
