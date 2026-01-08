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

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.IAppFunctionExecutor
import android.app.appfunctions.IExecuteAppFunctionCallback
import android.app.appfunctions.SafeOneTimeExecuteAppFunctionCallback
import android.os.IBinder
import android.os.ICancellationSignal
import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.eq
import org.mockito.Mockito.anyInt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.times


@RunWith(AndroidJUnit4::class)
class DynamicAppFunctionRegistryTest {
    private lateinit var registry: DynamicAppFunctionRegistry

    @Before
    fun setUp() {
        registry = DynamicAppFunctionRegistry()
    }

    @Test
    fun register_success() {
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, createExecutorMock())

        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun register_alreadyRegistered_throwsException() {
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, createExecutorMock())

        assertThrows(IllegalStateException::class.java) {
            registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, createExecutorMock())
        }
    }

    @Test
    fun register_afterUnregistration_succeeds() {
        val executor = createExecutorMock()
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
        registry.unregisterAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun register_sameFunctionIdAndDifferentPackages_registersBoth() {
        val executor1 = createExecutorMock()
        val executor2 = createExecutorMock()

        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor1)
        registry.registerAppFunction(TEST_PACKAGE2, TEST_FUNCTION, executor2)

        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE2, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun register_deathListenerAttached() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
        verify(binder).linkToDeath(any(), anyInt())
    }

    @Test
    fun unregister_success() {
        val executor = createExecutorMock()
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()

        registry.unregisterAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
    }

    @Test
    fun unregister_notRegistered_noOp() {
        registry.unregisterAppFunction(TEST_PACKAGE, TEST_FUNCTION, createExecutorMock())

        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
    }

    @Test
    fun unregister_wrongExecutor_noOp() {
        val executorA = createExecutorMock()
        val executorB = createExecutorMock()
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executorA)
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()

        registry.unregisterAppFunction(TEST_PACKAGE, TEST_FUNCTION, executorB)

        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun unregister_doesNotAffectAnotherWithSameExecutor() {
        val executor = createExecutorMock()
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION2, executor)
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION2)).isTrue()

        registry.unregisterAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION2)).isTrue()
    }

    @Test
    fun unregister_nonExistentFunction_isHarmless() {
        val executor = createExecutorMock()
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        registry.unregisterAppFunction(TEST_PACKAGE, TEST_FUNCTION2, executor)

        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
    }

    @Test
    fun registerUnregister_multipleThreadsRegisterAndUnregister() {
        val numThreads = 10
        val numOperations = 100
        val executorService = Executors.newFixedThreadPool(numThreads)
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(numThreads)

        for (i in 0 until numThreads) {
            executorService.submit {
                startLatch.await() // Wait for all threads to be ready
                val executor = createExecutorMock()
                for (j in 0 until numOperations) {
                    val functionName = "function_" + i + "_" + j
                    try {
                        registry.registerAppFunction(TEST_PACKAGE, functionName, executor)
                        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, functionName)).isTrue()
                        registry.unregisterAppFunction(TEST_PACKAGE, functionName, executor)
                        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, functionName)).isFalse()
                    } catch (e: Exception) {
                        // Fail the test if any exception occurs
                        throw e
                    }
                }
                endLatch.countDown()
            }
        }

        startLatch.countDown() // Start all threads
        endLatch.await() // Wait for all threads to finish
        executorService.shutdown()
    }

    @Test
    fun onCallbackDied_unregistersRegistration() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isTrue()
        deathRecipientCaptor.lastValue.binderDied(binder)
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
    }

    @Test
    fun onCallbackDied_unregistersAllRelatedRegistrations() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION2, executor)

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        deathRecipientCaptor.lastValue.binderDied(binder)
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION2)).isFalse()
    }

    @Test
    fun onCallbackDied_forUnregisteredExecutor_noOp() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
        registry.unregisterAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        deathRecipientCaptor.lastValue.binderDied(binder)
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
    }

    @Test
    fun concurrency_onCallbackDiedAndUnregister_isSafe() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION2, executor)

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder).linkToDeath(deathRecipientCaptor.capture(), anyInt())
        val deathRecipient = deathRecipientCaptor.lastValue

        val executorService = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)

        executorService.submit {
            try {
                deathRecipient.binderDied(binder)
            } finally {
                latch.countDown()
            }
        }

        executorService.submit {
            try {
                registry.unregisterAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)
            } finally {
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executorService.shutdown()

        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION2)).isFalse()
    }

    @Test
    fun isAppFunctionRegistered_notRegistered_returnsFalse() {
        assertThat(registry.isAppFunctionRegistered(TEST_PACKAGE, TEST_FUNCTION)).isFalse()
    }

    @Test
    fun executeAppFunction_success_invokesExecutor() {
        val executor = createExecutorMock()
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val executionCallback = mock<IExecuteAppFunctionCallback>()
        whenever<IExecuteAppFunctionCallback>(safeCallback.wrapToExecutionCallback()).thenReturn(executionCallback)
        val cancellationSignal = mock<ICancellationSignal>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        verify(executor).execute(eq(request), any(), eq(executionCallback))
    }

    @Test
    fun executeAppFunction_functionNotFound_callsOnError() {
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, createExecutorMock())
        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION2)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val cancellationSignal = mock<ICancellationSignal>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        val exceptionCaptor = argumentCaptor<AppFunctionException>()
        verify(safeCallback).onError(exceptionCaptor.capture())
        assertThat(exceptionCaptor.firstValue.errorCode)
            .isEqualTo(AppFunctionException.ERROR_DISABLED)
    }

    @Test
    fun executeAppFunction_executorThrowsRemoteException_callsOnError() {
        val executor = createExecutorMock()
        whenever(executor.execute(any(), any(), any())).doThrow(RemoteException("Test"))
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val executeCallback = mock<IExecuteAppFunctionCallback>()
        whenever(safeCallback.wrapToExecutionCallback()).thenReturn(executeCallback)
        val cancellationSignal = mock<ICancellationSignal>()
        val exceptionCaptor = argumentCaptor<AppFunctionException>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        verify(safeCallback).onError(exceptionCaptor.capture())
        assertThat(exceptionCaptor.firstValue.errorCode)
            .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
    }

    @Test
    fun executeAppFunction_attachesDeathListener() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        val executionCallback = mock<IExecuteAppFunctionCallback>()
        whenever(safeCallback.wrapToExecutionCallback()).thenReturn(executionCallback)
        val cancellationSignal = mock<ICancellationSignal>()

        registry.executeAppFunction(request, safeCallback, cancellationSignal)

        verify(safeCallback).attachOnDeathListener(binder)
    }

    @Test
    fun executeAppFunction_binderAlreadyDied_reportsError() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = mock<SafeOneTimeExecuteAppFunctionCallback>()
        whenever(safeCallback.wrapToExecutionCallback()).thenReturn(mock<IExecuteAppFunctionCallback>())
        whenever(safeCallback.attachOnDeathListener(binder)).doThrow(RemoteException("Binder died"))
        registry.executeAppFunction(request, safeCallback, mock<ICancellationSignal>())

        val exceptionCaptor = argumentCaptor<AppFunctionException>()
        verify(safeCallback).onError(exceptionCaptor.capture())
        assertThat(exceptionCaptor.firstValue.errorCode)
            .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
    }

    @Test
    fun executeAppFunction_onSuccess_unlinksExecutionDeathListener() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = SafeOneTimeExecuteAppFunctionCallback(
            mock<IExecuteAppFunctionCallback>()
        )

        registry.executeAppFunction(request, safeCallback, mock<ICancellationSignal>())

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder, times(2)).linkToDeath(deathRecipientCaptor.capture(), eq(0))

        val executorCallbackCaptor = argumentCaptor<IExecuteAppFunctionCallback>()
        verify(executor).execute(eq(request), any(), executorCallbackCaptor.capture())

        val response = mock<ExecuteAppFunctionResponse>()
        executorCallbackCaptor.firstValue.onSuccess(response)

        // The first is registration listener which shouldn't be unlinked
        verify(binder, never()).unlinkToDeath(deathRecipientCaptor.firstValue, 0)

        // Second is execution listener should be unlinked
        verify(binder).unlinkToDeath(deathRecipientCaptor.secondValue, 0)
    }

    @Test
    fun executeAppFunction_onError_unlinksExecutionDeathListener() {
        val binder = mock<IBinder>()
        val executor = createExecutorMock(binder)
        registry.registerAppFunction(TEST_PACKAGE, TEST_FUNCTION, executor)

        val request = createMockExecutionRequest(TEST_PACKAGE, TEST_FUNCTION)
        val safeCallback = SafeOneTimeExecuteAppFunctionCallback(
            mock<IExecuteAppFunctionCallback>()
        )

        registry.executeAppFunction(request, safeCallback, mock<ICancellationSignal>())

        val deathRecipientCaptor = argumentCaptor<IBinder.DeathRecipient>()
        verify(binder, times(2)).linkToDeath(deathRecipientCaptor.capture(), eq(0))

        val executorCallbackCaptor = argumentCaptor<IExecuteAppFunctionCallback>()
        verify(executor).execute(eq(request), any(), executorCallbackCaptor.capture())

        val error = AppFunctionException(AppFunctionException.ERROR_APP_UNKNOWN_ERROR, "Test error")
        executorCallbackCaptor.firstValue.onError(error)

        // The first is registration listener which shouldn't be unlinked
        verify(binder, never()).unlinkToDeath(deathRecipientCaptor.firstValue, 0)

        // Second is execution listener should be unlinked
        verify(binder).unlinkToDeath(deathRecipientCaptor.secondValue, 0)
    }

    private fun createExecutorMock(binder: IBinder = mock()): IAppFunctionExecutor {
        val executor = mock<IAppFunctionExecutor>()
        whenever(executor.asBinder()).thenReturn(binder)
        return executor
    }

    private fun createMockExecutionRequest(
        packageName: String,
        functionId: String
    ): ExecuteAppFunctionRequest {
        val request = mock<ExecuteAppFunctionRequest>()
        whenever(request.functionIdentifier).doReturn(functionId)
        whenever(request.targetPackageName).doReturn(packageName)
        return request
    }

    private companion object {
        const val TEST_PACKAGE = "com.example.app"
        const val TEST_PACKAGE2 = "com.example.other"

        const val TEST_FUNCTION = "myFunction"
        const val TEST_FUNCTION2 = "myFunctionOther"
    }
}