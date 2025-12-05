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
package com.android.systemui.statusbar.policy

import android.content.pm.PackageManager
import android.media.MediaRouter
import android.media.projection.MediaProjectionInfo
import android.media.projection.MediaProjectionManager
import android.os.fakeHandler
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_CAST_CONTROLLER_MEDIA_ROUTER_IN_BG
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.DumpManager
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.testKosmosNew
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@EnableFlags(FLAG_CAST_CONTROLLER_MEDIA_ROUTER_IN_BG)
class CastControllerImplTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val mediaRouter: MediaRouter = mock()

    private val mediaProjectionManager: MediaProjectionManager = mock()

    private val projection: MediaProjectionInfo = mock()

    private lateinit var underTest: CastControllerImpl

    @Before
    fun setUp() {
        whenever(mediaProjectionManager.activeProjectionInfo).thenReturn(projection)
        whenever(projection.packageName).thenReturn("fake.package")
        underTest =
            CastControllerImpl(
                mContext,
                mock<PackageManager>(),
                { mediaRouter },
                { mediaProjectionManager },
                CastControllerLogger(logcatLogBuffer("CastControllerImplTest")),
                kosmos.testScope,
                kosmos.testDispatcher,
                kosmos.fakeHandler,
                kosmos.testDispatcher,
                DumpManager(),
            )
        underTest.start()
    }

    @Test
    fun addCallback_onCastDevicesChangedCalled() =
        kosmos.runTest {
            val mockCallback = mock<CastController.Callback>()
            underTest.addCallback(mockCallback)
            verify(mockCallback, times(1)).onCastDevicesChanged()
        }

    @Test
    fun removeCallback_onCastDevicesChangedCalled() =
        kosmos.runTest {
            val mockCallback = mock<CastController.Callback>()
            underTest.addCallback(mockCallback)
            verify(mockCallback, times(1)).onCastDevicesChanged()
            underTest.removeCallback(mockCallback)
            verify(mockCallback, times(1)).onCastDevicesChanged()
        }

    @Test
    fun removeCallback_noop_onCastDeviceChangedCalled() =
        kosmos.runTest {
            val mockCallback = mock<CastController.Callback>()
            underTest.removeCallback(mockCallback)
            verify(mockCallback, never()).onCastDevicesChanged()
        }

    @Test
    @Throws(InterruptedException::class)
    fun addCallbackRemoveCallback_concurrently_noConcurrentModificationException() =
        kosmos.runTest {
            val callbackCount = 20
            val numThreads = 2 * callbackCount
            val startThreadsLatch = CountDownLatch(1)
            val threadsDone = CountDownLatch(numThreads)
            val callbackList: Array<CastController.Callback?> = arrayOfNulls(callbackCount)
            underTest.setDiscovering(true)
            val error = AtomicBoolean(false)
            for (cbIndex in 0..<callbackCount) {
                callbackList[cbIndex] = mock<CastController.Callback>()
            }
            for (i in 0..<numThreads) {
                val mCallback = callbackList[i / 2]
                val shouldAdd = (i % 2 == 0)
                object : Thread() {
                        override fun run() {
                            runBlocking {
                                try {
                                    startThreadsLatch.await(10, TimeUnit.SECONDS)
                                } catch (e: InterruptedException) {
                                    throw RuntimeException(e)
                                }
                                try {
                                    if (shouldAdd) {
                                        underTest.addCallback(mCallback!!)
                                    } else {
                                        underTest.removeCallback(mCallback!!)
                                    }
                                    underTest.fireOnCastDevicesChanged()
                                } catch (exc: ConcurrentModificationException) {
                                    error.compareAndSet(false, true)
                                } finally {
                                    threadsDone.countDown()
                                }
                            }
                        }
                    }
                    .start()
            }
            testScope.runCurrent()
            startThreadsLatch.countDown()
            threadsDone.await(10, TimeUnit.SECONDS)
            if (error.get()) {
                Assert.fail("Concurrent modification exception")
            }
        }

    /** Regression test for b/317700495 */
    @Test
    fun removeCallbackWhileIterating_doesntCrash() =
        kosmos.runTest {
            val remove = AtomicBoolean(false)
            val callback: CastController.Callback =
                object : CastController.Callback {
                    override fun onCastDevicesChanged() {
                        if (remove.get()) {
                            underTest.removeCallback(this)
                        }
                    }
                }
            underTest.addCallback(callback)
            // Add another callback so the iteration continues
            underTest.addCallback {}
            remove.set(true)
            underTest.fireOnCastDevicesChanged()
        }

    @Test
    fun hasConnectedCastDevice_connected() =
        kosmos.runTest {
            val castDevice =
                CastDevice(
                    "id",
                    /* name= */ null,
                    /* description= */ null,
                    /* state= */ CastDevice.CastState.Connected,
                    /* origin= */ CastDevice.CastOrigin.MediaProjection,
                    /* tag= */ null,
                )
            underTest.startCasting(castDevice)
            Assert.assertTrue(underTest.hasConnectedCastDevice())
        }

    @Test
    fun hasConnectedCastDevice_notConnected() =
        kosmos.runTest {
            val castDevice =
                CastDevice(
                    "id",
                    /* name= */ null,
                    /* description= */ null,
                    /* state= */ CastDevice.CastState.Connecting,
                    /* origin= */ CastDevice.CastOrigin.MediaProjection,
                    /* tag= */ null,
                )
            underTest.startCasting(castDevice)
            Assert.assertTrue(underTest.hasConnectedCastDevice())
        }
}
