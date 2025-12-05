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
package com.android.server.appfunctions

import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionMetadata
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_CATEGORY
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_NAME
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_VERSION
import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_ENABLED
import android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_PACKAGE_NAME
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appfunctions.flags.Flags
import android.app.appsearch.GenericDocument
import android.app.appsearch.SearchResult
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.internal.infra.AndroidFuture
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class AppFunctionMetadataReaderTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    val appFunctionMetadataReader = AppFunctionMetadataReader(mock())

    @Test
    fun searchAppFunctions_emptyList_succeeds() {
        val testFutureSearchResults = object : FutureSearchResults {
            override fun getNextPage(): AndroidFuture<List<SearchResult?>?> =
                AndroidFuture.completedFuture(listOf<SearchResult>())

            override fun close() {}
        }
        val futureGlobalSearchSession = mock<FutureGlobalSearchSession>()

        whenever(futureGlobalSearchSession.search(any(), any()))
            .thenReturn(AndroidFuture.completedFuture(testFutureSearchResults))

        val result = appFunctionMetadataReader.searchAppFunctions(
            futureGlobalSearchSession,
            EMPTY_SEARCH_SPEC
        )

        assertThat(result).isEqualTo(listOf<AppFunctionMetadata>())
    }

    @Test
    fun searchAppFunctions_multiplePages_succeeds() {
        val testFutureSearchResults = object : FutureSearchResults {
            var pageNumber = 0
            override fun getNextPage(): AndroidFuture<List<SearchResult?>?> {
                if (pageNumber == 0) {
                    pageNumber++
                    return AndroidFuture.completedFuture(
                        listOf(
                            TEST_SEARCH_RESULT_VALID
                        )
                    )
                } else if (pageNumber == 1) {
                    pageNumber++
                    return AndroidFuture.completedFuture(
                        listOf(
                            TEST_SEARCH_RESULT_VALID_2
                        )
                    )
                } else {
                    return AndroidFuture.completedFuture(listOf())
                }
            }

            override fun close() {}
        }
        val futureGlobalSearchSession = mock<FutureGlobalSearchSession>()

        whenever(futureGlobalSearchSession.search(any(), any()))
            .thenReturn(AndroidFuture.completedFuture(testFutureSearchResults))

        val result = appFunctionMetadataReader.searchAppFunctions(
            futureGlobalSearchSession,
            EMPTY_SEARCH_SPEC
        )

        assertThat(result).isEqualTo(
            listOf<AppFunctionMetadata>(
                AppFunctionMetadata.create(
                    STATIC_METADATA_DOCUMENT,
                    RUNTIME_METADATA_DOCUMENT,
                    AppFunctionPackageMetadata.create("testPackage", listOf())
                ),
                AppFunctionMetadata.create(
                    STATIC_METADATA_DOCUMENT_2,
                    RUNTIME_METADATA_DOCUMENT,
                    AppFunctionPackageMetadata.create("testPackage", listOf())
                )
            )
        )
    }

    @Test
    fun searchAppFunctions_multipleResults_succeedsAndSkipsInvalidResult() {
        val testFutureSearchResults = object : FutureSearchResults {
            var isFirstPage = true
            override fun getNextPage(): AndroidFuture<List<SearchResult?>?> {
                if (isFirstPage) {
                    isFirstPage = false
                    return AndroidFuture.completedFuture(
                        listOf(
                            TEST_SEARCH_RESULT_VALID,
                            TEST_SEARCH_RESULT_MISSING_RUNTIME_METADATA
                        )
                    )
                } else {
                    return AndroidFuture.completedFuture(listOf())
                }
            }

            override fun close() {}
        }
        val futureGlobalSearchSession = mock<FutureGlobalSearchSession>()

        whenever(futureGlobalSearchSession.search(any(), any()))
            .thenReturn(AndroidFuture.completedFuture(testFutureSearchResults))

        val result = appFunctionMetadataReader.searchAppFunctions(
            futureGlobalSearchSession,
            EMPTY_SEARCH_SPEC
        )

        assertThat(result).isEqualTo(
            listOf<AppFunctionMetadata>(
                AppFunctionMetadata.create(
                    STATIC_METADATA_DOCUMENT,
                    RUNTIME_METADATA_DOCUMENT,
                    AppFunctionPackageMetadata.create("testPackage", listOf())
                )
            )
        )
    }

    @Test
    fun convertSearchResultToAppFunctionMetadata_validInput_returnsMetadata() {
        val result =
            AppFunctionMetadataReader.convertSearchResultToAppFunctionMetadata(TEST_SEARCH_RESULT_VALID)

        assertThat(result).isEqualTo(
            AppFunctionMetadata.create(
                STATIC_METADATA_DOCUMENT,
                RUNTIME_METADATA_DOCUMENT,
                AppFunctionPackageMetadata.create("testPackage", listOf())
            )
        )
    }

    @Test
    fun convertSearchResultToAppFunctionMetadata_noRuntimeMetadata_returnsNull() {
        val result =
            AppFunctionMetadataReader.convertSearchResultToAppFunctionMetadata(TEST_SEARCH_RESULT_MISSING_RUNTIME_METADATA)

        assertThat(result).isNull()
    }

    @Test
    fun convertSearchResultToAppFunctionMetadata_multipleRuntimeMetadata_returnsNull() {
        val searchResult = SearchResult.Builder("", "")
            .setGenericDocument(STATIC_METADATA_DOCUMENT)
            .addJoinedResult(JOINED_RESULT)
            .addJoinedResult(JOINED_RESULT)
            .build()

        val result =
            AppFunctionMetadataReader.convertSearchResultToAppFunctionMetadata(searchResult)

        assertThat(result).isNull()
    }

    @Test
    fun convertSearchResultToAppFunctionMetadata_missingPackageName_returnsNull() {
        val runtimeMetadataDocumentNoPackageName =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                "",
                "",
                ""
            )
                .setPropertyLong(
                    PROPERTY_ENABLED,
                    AppFunctionManager.APP_FUNCTION_STATE_DEFAULT.toLong()
                )
                .build()
        val joinedResultNoPackageName = SearchResult.Builder("", "")
            .setGenericDocument(runtimeMetadataDocumentNoPackageName)
            .build()
        val searchResult = SearchResult.Builder("", "")
            .setGenericDocument(STATIC_METADATA_DOCUMENT)
            .addJoinedResult(joinedResultNoPackageName)
            .build()

        val result =
            AppFunctionMetadataReader.convertSearchResultToAppFunctionMetadata(searchResult)

        assertThat(result).isNull()
    }

    companion object {
        val STATIC_METADATA_DOCUMENT =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                "",
                "testPackage/testFunctionId",
                ""
            )
                .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
                .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
                .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    true
                )
                .build()
        val STATIC_METADATA_DOCUMENT_2 =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                "",
                "testPackage/testFunctionId2",
                ""
            )
                .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
                .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
                .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    true
                )
                .build()
        val RUNTIME_METADATA_DOCUMENT =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                "",
                "",
                ""
            )
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyLong(
                    PROPERTY_ENABLED,
                    AppFunctionManager.APP_FUNCTION_STATE_DEFAULT.toLong()
                )
                .build()

        val JOINED_RESULT = SearchResult.Builder("", "")
            .setGenericDocument(RUNTIME_METADATA_DOCUMENT)
            .build()


        val TEST_SEARCH_RESULT_VALID =
            SearchResult.Builder("", "")
                .setGenericDocument(STATIC_METADATA_DOCUMENT)
                .addJoinedResult(JOINED_RESULT)
                .build()
        val TEST_SEARCH_RESULT_VALID_2 =
            SearchResult.Builder("", "")
                .setGenericDocument(STATIC_METADATA_DOCUMENT_2)
                .addJoinedResult(JOINED_RESULT)
                .build()
        val TEST_SEARCH_RESULT_MISSING_RUNTIME_METADATA =
            SearchResult.Builder("", "")
                .setGenericDocument(STATIC_METADATA_DOCUMENT)
                .build()

        val EMPTY_SEARCH_SPEC = AppFunctionSearchSpec.Builder().build()
    }
}
