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
import android.provider.SearchIndexableResource
import android.util.Log
import com.android.settingslib.search.Indexable
import com.android.settingslib.search.SearchIndexableData
import com.android.settingslib.search.SearchIndexableRaw
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.SpaEnvironment
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.search.SearchablePage.SearchItem
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingKey
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingSpaPage

class SpaSearchRepository(
    private val spaEnvironment: SpaEnvironment = SpaEnvironmentFactory.instance
) {
    /**
     * Gets the search indexable data list
     *
     * @param intentAction The Intent action for the search landing activity.
     * @param intentTargetClass The Intent target class for the search landing activity.
     */
    fun getSearchIndexableDataList(
        intentAction: String,
        intentTargetClass: String,
    ): List<SearchIndexableData> {
        Log.d(TAG, "getSearchIndexableDataList")
        return spaEnvironment.pageProviderRepository.value.getAllProviders().mapNotNull { page ->
            if (page is SearchablePage) {
                page.createSearchIndexableData(
                    intentAction = intentAction,
                    intentTargetClass = intentTargetClass,
                    getPageTitleForSearch = page::getPageTitleForSearch,
                    searchItemsProvider = page::getSearchItems,
                )
            } else null
        }
    }

    companion object {
        private const val TAG = "SpaSearchRepository"

        fun SettingsPageProvider.createSearchIndexableData(
            intentAction: String,
            intentTargetClass: String,
            getPageTitleForSearch: (context: Context) -> String,
            searchItemsProvider: (context: Context) -> List<SearchItem>,
        ): SearchIndexableData {
            val key =
                SpaSearchLandingKey.newBuilder()
                    .setSpaPage(SpaSearchLandingSpaPage.newBuilder().setDestination(name))
                    .build()
            val indexableClass = this::class.java
            val searchIndexProvider = searchIndexProviderOf { context ->
                val pageTitle = getPageTitleForSearch(context)
                searchItemsProvider(context).map { searchItem ->
                    createSearchIndexableRaw(
                        context = context,
                        spaSearchLandingKey = key,
                        itemTitle = searchItem.itemTitle,
                        indexableClass = indexableClass,
                        pageTitle = pageTitle,
                        intentAction = intentAction,
                        intentTargetClass = intentTargetClass,
                        keywords = searchItem.keywords,
                    )
                }
            }
            return SearchIndexableData(indexableClass, searchIndexProvider)
        }

        fun searchIndexProviderOf(
            getDynamicRawDataToIndex: (context: Context) -> List<SearchIndexableRaw>
        ) =
            object : Indexable.SearchIndexProvider {
                override fun getXmlResourcesToIndex(
                    context: Context,
                    enabled: Boolean,
                ): List<SearchIndexableResource> = emptyList()

                override fun getRawDataToIndex(
                    context: Context,
                    enabled: Boolean,
                ): List<SearchIndexableRaw> = emptyList()

                override fun getDynamicRawDataToIndex(
                    context: Context,
                    enabled: Boolean,
                ): List<SearchIndexableRaw> = getDynamicRawDataToIndex(context)

                override fun getNonIndexableKeys(context: Context): List<String> = emptyList()
            }

        fun createSearchIndexableRaw(
            context: Context,
            spaSearchLandingKey: SpaSearchLandingKey,
            itemTitle: String,
            indexableClass: Class<*>,
            pageTitle: String,
            intentAction: String,
            intentTargetClass: String,
            keywords: String? = null,
        ) =
            SearchIndexableRaw(context).apply {
                key = spaSearchLandingKey.encodeToString()
                title = itemTitle
                this.keywords = keywords
                this.intentAction = intentAction
                this.intentTargetClass = intentTargetClass
                packageName = context.packageName
                className = indexableClass.name
                screenTitle = pageTitle
            }
    }
}
