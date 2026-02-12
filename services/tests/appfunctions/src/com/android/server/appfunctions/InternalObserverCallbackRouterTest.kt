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
import android.platform.test.annotations.RequiresFlagsEnabled
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class InternalObserverCallbackRouterTest {
    private val testExecutorService = MoreExecutors.newDirectExecutorService()
    private val mockServiceConfig: ServiceConfig = mock()

    private fun createRouter(
        debounceExecutor: ScheduledExecutorService,
        debounceMs: Int = 0,
    ): InternalObserverCallbackRouter {
        whenever(mockServiceConfig.getAppFunctionMetadataChangeDebounceMilliseconds())
            .thenReturn(debounceMs)
        return InternalObserverCallbackRouter(
            mockServiceConfig,
            debounceExecutor,
        )
    }

    @Test
    fun onSchemaChanged_routesToAllCallbacks() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)
        callbacksRouter.addCallback(callback2)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1", "$STATIC_SCHEMA_TYPE-testPackage2"),
            )
        )

        fakeDebounceExecutor.fastForwardTime(101L)
        val expectedPackages = listOf("testPackage1", "testPackage2")
        assertThat(callback1.changedPackageNames).containsExactlyElementsIn(expectedPackages)
        assertThat(callback2.changedPackageNames).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onSchemaChanged_consequentUpdates_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change for the same package
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).containsExactly("testPackage1")

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onSchemaChanged_consequentUpdatesForMultiplePackages_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change for another package
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage2"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames)
            .containsExactlyElementsIn(listOf("testPackage1", "testPackage2"))

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onDocumentChanged_routesToAllCallbacks() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()
        val callback3 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)
        callbacksRouter.addCallback(callback2)
        callbacksRouter.addCallback(callback3)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id1"),
            )
        )
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage2",
                setOf("testPackage2/id2"),
            )
        )

        fakeDebounceExecutor.fastForwardTime(101L)
        val expectedPackages = listOf("testPackage1", "testPackage2")
        assertThat(callback1.changedPackageNames).containsExactlyElementsIn(expectedPackages)
        assertThat(callback2.changedPackageNames).containsExactlyElementsIn(expectedPackages)
        assertThat(callback3.changedPackageNames).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onDocumentChanged_malformedId_skipsResult() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("testPackage1/", "testPackage1", ""),
            )
        )

        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onDocumentChanged_consequentUpdates_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change for the same package
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id2"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).containsExactly("testPackage1")

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onDocumentChanged_consequentUpdatesForMultiplePackages_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change for another package
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage2",
                setOf("testPackage2/id2"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames)
            .containsExactlyElementsIn(listOf("testPackage1", "testPackage2"))

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onMixedChanges_consequentUpdatesForMultiplePackages_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change (schema)
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change (document) for another package
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage2",
                setOf("testPackage2/id2"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames)
            .containsExactlyElementsIn(listOf("testPackage1", "testPackage2"))

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun removeCallback_doesNotRoute() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, debounceMs = 100)
        callbacksRouter.addCallback(callback1)
        callbacksRouter.addCallback(callback2)
        callbacksRouter.removeCallback(callback1)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )
        )
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
        assertThat(callback2.changedPackageNames).containsExactly("testPackage1")

        // Reset for next check
        callback2.changedPackageNames = null

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id1"),
            )
        )
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
        assertThat(callback2.changedPackageNames).containsExactly("testPackage1")
    }

    @Test
    fun shutdown_attemptInvokingCallback_failsGracefully() {
        val callback1 = TestInternalCallback()

        val callbacksRouter =
            createRouter(Executors.newSingleThreadScheduledExecutor(), debounceMs = 0)
        callbacksRouter.addCallback(callback1)

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
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id1"),
            )
        )
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onEnabledStatesChanged_routesToAllCallbacks() {
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()

        val callbacksRouter = createRouter(FakeScheduledExecutorService())
        callbacksRouter.addCallback(callback1)
        callbacksRouter.addCallback(callback2)

        val changedFunctions =
            setOf(
                AppFunctionName("testPackage1", "id1"),
                AppFunctionName("testPackage2", "id2"),
            )
        callbacksRouter.onEnabledStatesChanged(changedFunctions)

        assertThat(callback1.stateChangedFunctionNames)
            .containsExactlyElementsIn(changedFunctions)
        assertThat(callback2.stateChangedFunctionNames)
            .containsExactlyElementsIn(changedFunctions)
    }

    private fun InternalObserverCallbackRouter.addCallback(
        callback: IObserveAppFunctionChangesCallback
    ) {
        addCallback(callback, mock<AppFunctionSearchSpec>())
    }

    class TestInternalCallback : IObserveAppFunctionChangesCallback {
        var changedPackageNames: List<String>? = null
        var stateChangedFunctionNames: List<AppFunctionName>? = null
        val binder = Binder((binderId++).toString())

        override fun onPackagesChanged(changedPackageNames: List<String>) {
            this.changedPackageNames = changedPackageNames
        }

        override fun onAppFunctionStatesChanged(appFunctions: List<AppFunctionName>) {
            stateChangedFunctionNames = appFunctions
        }

        override fun asBinder(): IBinder {
            return binder
        }

        companion object {
            var binderId = 1
        }
    }
}
