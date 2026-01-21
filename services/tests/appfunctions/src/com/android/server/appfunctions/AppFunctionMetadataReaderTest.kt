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
import android.app.appfunctions.IAppFunctionSearchResultCallback
import android.app.appfunctions.IAppFunctionSearchResults
import android.app.appfunctions.flags.Flags
import android.app.appsearch.GenericDocument
import android.app.appsearch.SearchResult
import android.os.ParcelableException
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.internal.infra.AndroidFuture
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Before
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

    private lateinit var dynamicRegistry: MultiUserDynamicAppFunctionRegistry

    private lateinit var metadataCache: AppFunctionsMetadataCache

    private lateinit var appFunctionMetadataReader: AppFunctionMetadataReader

    @Before
    fun setup() {
        dynamicRegistry = mock()
        metadataCache = mock()
        appFunctionMetadataReader =
            AppFunctionMetadataReader(
                dynamicRegistry,
                metadataCache,
                object : ServiceConfig {
                    override fun getExecuteAppFunctionCancellationTimeoutMillis(): Long = 0

                    override fun getSearchAppFunctionInternalPageSize(): Int = 100
                },
            )
    }

    @Test
    fun searchAppFunctions_emptyList_succeeds() = doBlocking {
        val testFutureSearchResults =
            object : FutureSearchResults {
                override fun getNextPage(): AndroidFuture<List<SearchResult?>?> =
                    AndroidFuture.completedFuture(listOf<SearchResult>())

                override fun close() {}
            }
        val futureGlobalSearchSession = mock<FutureGlobalSearchSession>()

        whenever(futureGlobalSearchSession.search(any(), any()))
            .thenReturn(AndroidFuture.completedFuture(testFutureSearchResults))

        val result =
            appFunctionMetadataReader.searchAppFunctions(
                futureGlobalSearchSession,
                EMPTY_SEARCH_SPEC,
                MoreExecutors.directExecutor(),
                0,
            )

        assertThat(result.getNextPage()).isEmpty()
    }

    @Test
    fun searchAppFunctions_multiplePages_succeeds() = doBlocking {
        val testFutureStaticSearchResults =
            object : FutureSearchResults {
                var pageNumber = 0

                override fun getNextPage(): AndroidFuture<List<SearchResult?>?> {
                    if (pageNumber == 0) {
                        pageNumber++
                        return AndroidFuture.completedFuture(listOf(TEST_SEARCH_RESULT_VALID))
                    } else if (pageNumber == 1) {
                        pageNumber++
                        return AndroidFuture.completedFuture(listOf(TEST_SEARCH_RESULT_VALID_2))
                    } else {
                        return AndroidFuture.completedFuture(listOf())
                    }
                }

                override fun close() {}
            }
        val testFutureTopLevelSearchResults =
            object : FutureSearchResults {
                override fun getNextPage(): AndroidFuture<List<SearchResult?>?> {
                    return AndroidFuture.completedFuture(listOf(TEST_TOP_LEVEL_SEARCH_RESULT))
                }

                override fun close() {}
            }
        val futureGlobalSearchSession = mock<FutureGlobalSearchSession>()
        whenever(futureGlobalSearchSession.search(any(), any()))
            .thenReturn(AndroidFuture.completedFuture(testFutureStaticSearchResults))
            .thenReturn(AndroidFuture.completedFuture(testFutureTopLevelSearchResults))

        val result =
            appFunctionMetadataReader.searchAppFunctions(
                futureGlobalSearchSession,
                EMPTY_SEARCH_SPEC,
                MoreExecutors.directExecutor(),
                0,
            )

        assertThat(result.getNextPage())
            .containsExactly(
                AppFunctionMetadata.Builder(
                        STATIC_METADATA_DOCUMENT,
                        AppFunctionPackageMetadata.create(
                            "testPackage",
                            listOf(TEST_TOP_LEVEL_DOCUMENT),
                        ),
                    )
                    .setEnabled(true)
                    .build()
            )
        assertThat(result.getNextPage())
            .containsExactly(
                AppFunctionMetadata.Builder(
                        STATIC_METADATA_DOCUMENT_2,
                        AppFunctionPackageMetadata.create(
                            "testPackage",
                            listOf(TEST_TOP_LEVEL_DOCUMENT),
                        ),
                    )
                    .setEnabled(false)
                    .build()
            )
    }

    @Test
    fun searchAppFunctions_multipleResults_succeedsAndSkipsInvalidResult() = doBlocking {
        val testFutureStaticSearchResults =
            object : FutureSearchResults {
                var pageNumber = -1

                override fun getNextPage(): AndroidFuture<List<SearchResult?>?> {
                    pageNumber++
                    when (pageNumber) {
                        0 -> {
                            return AndroidFuture.completedFuture(
                                listOf(TEST_SEARCH_RESULT_MISSING_RUNTIME_METADATA)
                            )
                        }
                        1 -> {
                            return AndroidFuture.completedFuture(listOf(TEST_SEARCH_RESULT_VALID))
                        }
                        else -> {
                            return AndroidFuture.completedFuture(listOf())
                        }
                    }
                }

                override fun close() {}
            }
        val testFutureTopLevelSearchResults =
            object : FutureSearchResults {
                override fun getNextPage(): AndroidFuture<List<SearchResult?>?> {
                    return AndroidFuture.completedFuture(listOf(TEST_TOP_LEVEL_SEARCH_RESULT))
                }

                override fun close() {}
            }
        val futureGlobalSearchSession = mock<FutureGlobalSearchSession>()
        whenever(futureGlobalSearchSession.search(any(), any()))
            .thenReturn(AndroidFuture.completedFuture(testFutureStaticSearchResults))
            .thenReturn(AndroidFuture.completedFuture(testFutureTopLevelSearchResults))

        val result =
            appFunctionMetadataReader.searchAppFunctions(
                futureGlobalSearchSession,
                EMPTY_SEARCH_SPEC,
                MoreExecutors.directExecutor(),
                0,
            )

        assertThat(result.getNextPage())
            .containsExactly(
                AppFunctionMetadata.Builder(
                        STATIC_METADATA_DOCUMENT,
                        AppFunctionPackageMetadata.create(
                            "testPackage",
                            listOf(TEST_TOP_LEVEL_DOCUMENT),
                        ),
                    )
                    .setEnabled(true)
                    .build()
            )
        assertThat(result.getNextPage()).isEmpty()
    }

    @Test
    fun buildAppFunctionMetadata_dynamicAppFunctionNotRegisteredAndNotEnabled() {
        whenever(metadataCache.isDynamicFunction(any(), any(), any())).thenReturn(true)
        whenever(dynamicRegistry.isAppFunctionRegistered(any(), any(), any())).thenReturn(false)

        val packageMetadata = AppFunctionPackageMetadata.create("testPackage", listOf())
        val result =
            appFunctionMetadataReader.buildAppFunctionMetadata(
                TEST_SEARCH_RESULT_VALID_DISABLED,
                packageMetadata,
                0,
            )

        assertThat(result)
            .isEqualTo(
                AppFunctionMetadata.Builder(STATIC_METADATA_DOCUMENT, packageMetadata)
                    .setEnabled(false)
                    .build()
            )
    }

    @Test
    fun buildAppFunctionMetadata_dynamicAppFunctionNotRegisteredButEnabled() {
        whenever(metadataCache.isDynamicFunction(any(), any(), any())).thenReturn(true)
        whenever(dynamicRegistry.isAppFunctionRegistered(any(), any(), any())).thenReturn(false)

        val packageMetadata = AppFunctionPackageMetadata.create("testPackage", listOf())
        val result =
            appFunctionMetadataReader.buildAppFunctionMetadata(
                TEST_SEARCH_RESULT_VALID,
                packageMetadata,
                0,
            )

        assertThat(result)
            .isEqualTo(
                AppFunctionMetadata.Builder(STATIC_METADATA_DOCUMENT, packageMetadata)
                    .setEnabled(false)
                    .build()
            )
    }

    @Test
    fun buildAppFunctionMetadata_dynamicAppFunctionRegisteredButNotEnabled() {
        whenever(metadataCache.isDynamicFunction(any(), any(), any())).thenReturn(true)
        whenever(dynamicRegistry.isAppFunctionRegistered(any(), any(), any())).thenReturn(true)

        val packageMetadata = AppFunctionPackageMetadata.create("testPackage", listOf())
        val result =
            appFunctionMetadataReader.buildAppFunctionMetadata(
                TEST_SEARCH_RESULT_VALID_DISABLED,
                packageMetadata,
                0,
            )

        assertThat(result)
            .isEqualTo(
                AppFunctionMetadata.Builder(STATIC_METADATA_DOCUMENT, packageMetadata)
                    .setEnabled(false)
                    .build()
            )
    }

    @Test
    fun buildAppFunctionMetadata_dynamicAppFunctionRegisteredAndEnabled() {
        whenever(metadataCache.isDynamicFunction(any(), any(), any())).thenReturn(true)
        whenever(dynamicRegistry.isAppFunctionRegistered(any(), any(), any())).thenReturn(true)

        val packageMetadata = AppFunctionPackageMetadata.create("testPackage", listOf())
        val result =
            appFunctionMetadataReader.buildAppFunctionMetadata(
                TEST_SEARCH_RESULT_VALID,
                packageMetadata,
                0,
            )

        assertThat(result)
            .isEqualTo(
                AppFunctionMetadata.Builder(STATIC_METADATA_DOCUMENT, packageMetadata)
                    .setEnabled(true)
                    .build()
            )
    }

    @Test
    fun buildAppFunctionMetadata_staticAppFunctionEnabled() {
        whenever(metadataCache.isDynamicFunction(any(), any(), any())).thenReturn(false)

        val packageMetadata = AppFunctionPackageMetadata.create("testPackage", listOf())
        val result =
            appFunctionMetadataReader.buildAppFunctionMetadata(
                TEST_SEARCH_RESULT_VALID,
                packageMetadata,
                0,
            )

        assertThat(result)
            .isEqualTo(
                AppFunctionMetadata.Builder(STATIC_METADATA_DOCUMENT, packageMetadata)
                    .setEnabled(false)
                    .build()
            )
    }

    @Test
    fun buildAppFunctionMetadata_staticAppFunctionDisabled() {
        whenever(metadataCache.isDynamicFunction(any(), any(), any())).thenReturn(false)

        val packageMetadata = AppFunctionPackageMetadata.create("testPackage", listOf())
        val result =
            appFunctionMetadataReader.buildAppFunctionMetadata(
                TEST_SEARCH_RESULT_VALID_2,
                packageMetadata,
                0,
            )

        assertThat(result)
            .isEqualTo(
                AppFunctionMetadata.Builder(STATIC_METADATA_DOCUMENT_2, packageMetadata)
                    .setEnabled(true)
                    .build()
            )
    }

    @Test
    fun buildAppFunctionMetadata_noRuntimeMetadata_returnsNull() {
        val packageMetadata = AppFunctionPackageMetadata.create("testPackage", listOf())

        val result =
            appFunctionMetadataReader.buildAppFunctionMetadata(
                TEST_SEARCH_RESULT_MISSING_RUNTIME_METADATA,
                packageMetadata,
                0,
            )

        assertThat(result).isNull()
    }

    @Test
    fun buildAppFunctionMetadata_multipleRuntimeMetadata_returnsNull() {
        val searchResult =
            SearchResult.Builder("", "")
                .setGenericDocument(STATIC_METADATA_DOCUMENT)
                .addJoinedResult(JOINED_RESULT)
                .addJoinedResult(JOINED_RESULT)
                .build()

        val packageMetadata = AppFunctionPackageMetadata.create("testPackage", listOf())
        val result =
            appFunctionMetadataReader.buildAppFunctionMetadata(searchResult, packageMetadata, 0)

        assertThat(result).isNull()
    }

    private suspend fun IAppFunctionSearchResults.getNextPage(): List<AppFunctionMetadata> {
        return suspendCancellableCoroutine { cont ->
            getNextPage(
                object : IAppFunctionSearchResultCallback.Stub() {
                    override fun onResult(result: List<AppFunctionMetadata>) {
                        cont.resume(result)
                    }

                    override fun onError(exception: ParcelableException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    companion object {
        val STATIC_METADATA_DOCUMENT =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "",
                    "testPackage/testFunctionId",
                    "",
                )
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
                .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
                .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    true,
                )
                .build()
        val STATIC_METADATA_DOCUMENT_2 =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "",
                    "testPackage/testFunctionId2",
                    "",
                )
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
                .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
                .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    false,
                )
                .build()
        val RUNTIME_METADATA_DOCUMENT =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyLong(
                    PROPERTY_ENABLED,
                    AppFunctionManager.APP_FUNCTION_STATE_DEFAULT.toLong(),
                )
                .build()
        val RUNTIME_METADATA_DOCUMENT_DISABLED =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyLong(
                    PROPERTY_ENABLED,
                    AppFunctionManager.APP_FUNCTION_STATE_DISABLED.toLong(),
                )
                .build()

        val JOINED_RESULT =
            SearchResult.Builder("", "").setGenericDocument(RUNTIME_METADATA_DOCUMENT).build()
        val JOINED_RESULT_DISABLED =
            SearchResult.Builder("", "")
                .setGenericDocument(RUNTIME_METADATA_DOCUMENT_DISABLED)
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

        val TEST_TOP_LEVEL_DOCUMENT =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "",
                    "testPackage/testTopLevelDocument",
                    "",
                )
                .setPropertyString("customTopLevelProperty", "value1")
                .build()
        val TEST_TOP_LEVEL_SEARCH_RESULT =
            SearchResult.Builder("", "").setGenericDocument(TEST_TOP_LEVEL_DOCUMENT).build()
        val TEST_SEARCH_RESULT_VALID_DISABLED =
            SearchResult.Builder("", "")
                .setGenericDocument(STATIC_METADATA_DOCUMENT)
                .addJoinedResult(JOINED_RESULT_DISABLED)
                .build()
        val TEST_SEARCH_RESULT_MISSING_RUNTIME_METADATA =
            SearchResult.Builder("", "").setGenericDocument(STATIC_METADATA_DOCUMENT).build()

        val EMPTY_SEARCH_SPEC = AppFunctionSearchSpec.Builder().build()
    }
}
