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

import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE
import android.app.appfunctions.AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE
import android.app.appfunctions.IObserveAppFunctionChangesCallback
import android.app.appfunctions.flags.Flags
import android.app.appsearch.observer.DocumentChangeInfo
import android.app.appsearch.observer.SchemaChangeInfo
import android.os.Binder
import android.os.IBinder
import android.os.ParcelableException
import android.platform.test.annotations.RequiresFlagsEnabled
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class InternalObserverCallbackRouterTest {
    private val testExecutorService = MoreExecutors.newDirectExecutorService()

    @Test
    fun onSchemaChanged_packageMatch_routesToMatchingCallbacks() {
        val callback1 = TestInternalCallback()
        val searchSpec1 = createMockSearchSpec(setOf("testPackage1"), null)
        val callback2 = TestInternalCallback()
        val searchSpec2 =
            createMockSearchSpec(
                setOf("testPackage1"),
                setOf(AppFunctionName("testPackage1", "id1")),
            )
        val callback3 = TestInternalCallback()
        val searchSpec3 = createMockSearchSpec(setOf("testPackage2"), null)

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)
        callbacksRouter.addCallback(callback2, searchSpec2)
        callbacksRouter.addCallback(callback3, searchSpec3)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )
        )

        assertThat(callback1.changedPackageNames).isEqualTo(listOf("testPackage1"))
        assertThat(callback2.changedPackageNames).isEqualTo(listOf("testPackage1"))
        assertThat(callback3.changedPackageNames).isNull()
    }

    @Test
    fun onSchemaChanged_nullPackageFilter_acceptsAllPackages() {
        val callback1 = TestInternalCallback()
        val searchSpec1 = createMockSearchSpec(null, null)

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1", "$STATIC_SCHEMA_TYPE-testPackage2"),
            )
        )

        assertThat(callback1.changedPackageNames).isEqualTo(listOf("testPackage1", "testPackage2"))
    }

    @Test
    fun onDocumentChanged_packageMatch_routesToMatchingCallbacks() {
        val callback1 = TestInternalCallback()
        val searchSpec1 = createMockSearchSpec(setOf("testPackage1"), null)
        val callback2 = TestInternalCallback()
        val searchSpec2 = createMockSearchSpec(setOf("testPackage2"), null)
        val callback3 = TestInternalCallback()
        val searchSpec3 = createMockSearchSpec(setOf("testPackage3"), null)

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)
        callbacksRouter.addCallback(callback2, searchSpec2)
        callbacksRouter.addCallback(callback3, searchSpec3)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1", "testPackage2/id2"),
            )
        )

        assertThat(callback1.changedFunctionNames)
            .isEqualTo(listOf(AppFunctionName("testPackage1", "id1")))
        assertThat(callback2.changedFunctionNames)
            .isEqualTo(listOf(AppFunctionName("testPackage2", "id2")))
        assertThat(callback3.changedFunctionNames).isNull()
    }

    @Test
    fun onDocumentChanged_functionMatch_routesToMatchingCallbacks() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            createMockSearchSpec(
                null,
                setOf(
                    AppFunctionName("testPackage1", "id1"),
                    AppFunctionName("testPackage2", "id2"),
                ),
            )
        val callback2 = TestInternalCallback()
        val searchSpec2 = createMockSearchSpec(null, setOf(AppFunctionName("testPackage2", "id2")))
        val callback3 = TestInternalCallback()
        val searchSpec3 = createMockSearchSpec(null, setOf(AppFunctionName("testPackage3", "id3")))

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)
        callbacksRouter.addCallback(callback2, searchSpec2)
        callbacksRouter.addCallback(callback3, searchSpec3)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1", "testPackage2/id2"),
            )
        )

        assertThat(callback1.changedFunctionNames)
            .isEqualTo(
                listOf(
                    AppFunctionName("testPackage1", "id1"),
                    AppFunctionName("testPackage2", "id2"),
                )
            )
        assertThat(callback2.changedFunctionNames)
            .isEqualTo(listOf(AppFunctionName("testPackage2", "id2")))
        assertThat(callback3.changedFunctionNames).isNull()
    }

    @Test
    fun onDocumentChanged_bothFiltersSet_routesToMatchingCallbacks() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            createMockSearchSpec(
                setOf("testPackage1", "testPackage2"),
                setOf(
                    AppFunctionName("testPackage1", "id1"),
                    AppFunctionName("testPackage2", "id2"),
                ),
            )
        val callback2 = TestInternalCallback()
        val searchSpec2 =
            createMockSearchSpec(
                setOf("testPackage3"),
                setOf(AppFunctionName("testPackage3", "id2")),
            )

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)
        callbacksRouter.addCallback(callback2, searchSpec2)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1", "testPackage2/id2"),
            )
        )

        assertThat(callback1.changedFunctionNames)
            .isEqualTo(
                listOf(
                    AppFunctionName("testPackage1", "id1"),
                    AppFunctionName("testPackage2", "id2"),
                )
            )
        assertThat(callback2.changedFunctionNames).isNull()
    }

    @Test
    fun onDocumentChanged_nonAppFunctionDocument_routesOnPackageChanged() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            createMockSearchSpec(
                setOf("testPackage1", "testPackage2"),
                setOf(
                    AppFunctionName("testPackage1", "id1"),
                    AppFunctionName("testPackage2", "id2"),
                ),
            )
        val callback2 = TestInternalCallback()
        val searchSpec2 =
            createMockSearchSpec(
                setOf("testPackage2"),
                setOf(AppFunctionName("testPackage2", "id2")),
            )

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)
        callbacksRouter.addCallback(callback2, searchSpec2)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "AnotherSchema-testPackage1",
                setOf("testPackage1/id1", "testPackage1/id2"),
            )
        )

        assertThat(callback1.changedFunctionNames).isNull()
        assertThat(callback1.changedPackageNames).isEqualTo(listOf("testPackage1"))
        assertThat(callback2.changedFunctionNames).isNull()
        assertThat(callback2.changedPackageNames).isNull()
    }

    @Test
    fun onDocumentChanged_malformedId_skipsResult() {
        val callback1 = TestInternalCallback()
        val searchSpec1 = createMockSearchSpec(setOf("testPackage1", "testPackage2"), null)

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/", "testPackage1", ""),
            )
        )

        assertThat(callback1.changedFunctionNames).isNull()
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun addCallback_packageAndFunctionFiltersSet_onSchemaChanged_routesToIntersectingPackageNames() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .build()
        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1", "$STATIC_SCHEMA_TYPE-testPackage2"),
            )
        )

        assertThat(callback1.changedPackageNames).isEqualTo(listOf("testPackage1"))
    }

    @Test
    fun addCallback_packageAndFunctionFiltersSet_onDocumentChanged_routesToIntersectingAppFunctions() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .build()

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1", "testPackage2/id2"),
            )
        )

        assertThat(callback1.changedFunctionNames)
            .containsExactly(AppFunctionName("testPackage1", "id1"))
    }

    @Test
    fun addCallback_packageAndFunctionFiltersSet_noIntersection_onSchemaChanged_doesNotRoute() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage3"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .build()
        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1", "$STATIC_SCHEMA_TYPE-testPackage2"),
            )
        )

        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun addCallback_packageAndFunctionFiltersSet_noIntersection_onDocumentChanged_doesNotRoute() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage3"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .build()

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1", "testPackage2/id2"),
            )
        )

        assertThat(callback1.changedFunctionNames).isNull()
    }

    @Test
    fun addCallback_onlyFunctionFilterSet_onSchemaChanged_routesToAllFunctionNamePackages() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .build()
        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf(
                    "$STATIC_SCHEMA_TYPE-testPackage1",
                    "$STATIC_SCHEMA_TYPE-testPackage2",
                    "$STATIC_SCHEMA_TYPE-testPackage3",
                ),
            )
        )

        assertThat(callback1.changedPackageNames).containsExactly("testPackage1", "testPackage2")
    }

    @Test
    fun addCallback_onlyFunctionFilterSet_onDocumentChanged_routesToAllAppFunctions() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .build()

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1", "testPackage2/id2", "testPackage3/id3"),
            )
        )

        assertThat(callback1.changedFunctionNames)
            .containsExactly(
                AppFunctionName("testPackage1", "id1"),
                AppFunctionName("testPackage2", "id2"),
            )
    }

    @Test
    fun addCallback_onlyPackageFilterSet_onSchemaChanged_routesToAllPackageNames() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .build()
        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf(
                    "$STATIC_SCHEMA_TYPE-testPackage1",
                    "$STATIC_SCHEMA_TYPE-testPackage2",
                    "$STATIC_SCHEMA_TYPE-testPackage3",
                ),
            )
        )

        assertThat(callback1.changedPackageNames).containsExactly("testPackage1", "testPackage2")
    }

    @Test
    fun addCallback_onlyPackageFilterSet_onDocumentChanged_routesToAllAppFunctionsInPackage() {
        val callback1 = TestInternalCallback()
        val searchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .build()

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1", "testPackage2/id2", "testPackage3/id3"),
            )
        )

        assertThat(callback1.changedFunctionNames)
            .containsExactly(
                AppFunctionName("testPackage1", "id1"),
                AppFunctionName("testPackage2", "id2"),
            )
    }

    @Test
    fun removeCallback_doesNotRoute() {
        val callback1 = TestInternalCallback()
        val searchSpec1 = AppFunctionSearchSpec.Builder().build()
        val callback2 = TestInternalCallback()
        val searchSpec2 = AppFunctionSearchSpec.Builder().build()

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)
        callbacksRouter.addCallback(callback2, searchSpec2)
        callbacksRouter.removeCallback(callback1)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )
        )
        assertThat(callback1.changedPackageNames).isNull()
        assertThat(callback2.changedPackageNames).containsExactly("testPackage1")

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1"),
            )
        )
        assertThat(callback1.changedFunctionNames).isNull()
        assertThat(callback2.changedFunctionNames)
            .containsExactly(AppFunctionName("testPackage1", "id1"))
    }

    @Test
    fun shutdown_attemptInvokingCallback_failsGracefully() {
        val callback1 = TestInternalCallback()
        val searchSpec1 = AppFunctionSearchSpec.Builder().build()

        val callbacksRouter = InternalObserverCallbackRouter(testExecutorService)
        callbacksRouter.addCallback(callback1, searchSpec1)

        callbacksRouter.shutDown()

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )
        )
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/id1"),
            )
        )
        assertThat(callback1.changedPackageNames).isNull()
        assertThat(callback1.changedFunctionNames).isNull()
    }

    private fun createMockSearchSpec(
        observedPackageNames: Set<String>?,
        observedAppFunctions: Set<AppFunctionName>?,
    ): AppFunctionSearchSpec {
        val searchSpec = mock<AppFunctionSearchSpec>()
        whenever(searchSpec.observedPackageNames).thenReturn(observedPackageNames)
        whenever(searchSpec.observedAppFunctions).thenReturn(observedAppFunctions)
        return searchSpec
    }

    class TestInternalCallback() : IObserveAppFunctionChangesCallback {
        var changedPackageNames: List<String>? = null
        var changedFunctionNames: List<AppFunctionName>? = null
        var callbackException: Throwable? = null

        override fun onAppFunctionsChanged(appFunctions: List<AppFunctionName>) {
            changedFunctionNames = appFunctions
        }

        override fun onPackagesChanged(packageNames: List<String>) {
            changedPackageNames = packageNames
        }

        override fun onRegistrationError(exception: ParcelableException?) {
            if (exception != null) {
                callbackException = exception.cause
            }
        }

        override fun asBinder(): IBinder? {
            return Binder()
        }
    }
}
