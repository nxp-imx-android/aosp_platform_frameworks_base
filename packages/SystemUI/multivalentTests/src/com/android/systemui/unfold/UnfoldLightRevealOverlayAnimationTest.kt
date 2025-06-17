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

package com.android.systemui.unfold

import android.content.ContentResolver
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.statusbar.LightRevealScrim
import com.android.systemui.testKosmos
import com.android.systemui.unfold.FullscreenLightRevealAnimationController.Factory
import com.android.systemui.unfold.util.fakeDeviceStateManager
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.concurrency.ThreadFactory
import com.android.systemui.util.time.FakeSystemClock
import com.android.systemui.utils.os.FakeHandler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidJUnit4::class)
class UnfoldLightRevealOverlayAnimationTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val featureFlags = kosmos.featureFlagsClassic
    private val progressProvider = kosmos.fakeUnfoldTransitionProgressProvider
    private val deviceStateManager = kosmos.fakeDeviceStateManager

    private val contentResolver: ContentResolver = mock()
    private val mockFullScreenController = kosmos.fullscreenLightRevealAnimationController
    private val mockScrimView = mock<LightRevealScrim>()
    private val threadFactory: ThreadFactory = mock()
    private val fullscreenLightRevealAnimationControllerFactory: Factory = mock()

    private val fakeSystemClock = FakeSystemClock()
    private val executor = FakeExecutor(fakeSystemClock)

    private lateinit var fakeHandler: FakeHandler
    private lateinit var animation: UnfoldLightRevealOverlayAnimation

    @Before
    fun setup() {
        whenever(mockFullScreenController.scrimView).thenReturn(mockScrimView)
        whenever(fullscreenLightRevealAnimationControllerFactory.create(any(), any(), any()))
            .thenReturn(mockFullScreenController)
        whenever(threadFactory.buildDelayableExecutorOnHandler(any())).thenReturn(executor)

        featureFlags.fake.set(Flags.ENABLE_DARK_VIGNETTE_WHEN_FOLDING, true)
        fakeHandler = FakeHandler(TestableLooper.get(this).looper)

        animation =
            UnfoldLightRevealOverlayAnimation(
                mContext,
                featureFlags,
                contentResolver,
                fakeHandler,
                { progressProvider },
                { progressProvider },
                deviceStateManager.deviceStateManager,
                threadFactory,
                fullscreenLightRevealAnimationControllerFactory,
            )
        animation.init()
    }

    @Test
    fun onScreenTurnedOff_overlayIsRemoved() {
        animation.onScreenTurnedOff()

        verifyOverlayRemoved()
    }

    @Test
    fun transitionFinishedAt0Progress_overlayIsNotRemoved() {
        progressProvider.onTransitionStarted()
        progressProvider.onTransitionProgress(1.0f)

        progressProvider.onTransitionProgress(0.0f)
        progressProvider.onTransitionFinished()

        verifyOverlayIsNotRemoved()
    }

    @Test
    fun deviceFolds_overlayIsNotRemoved() {
        deviceStateManager.fold()

        verifyOverlayIsNotRemoved()
    }

    @Test
    fun transitionFinishedAtNonZeroProgress_overlayIsRemoved() {
        progressProvider.onTransitionStarted()
        progressProvider.onTransitionProgress(0.0f)

        progressProvider.onTransitionProgress(0.123f)
        progressProvider.onTransitionFinished()

        verifyOverlayRemoved()
    }

    private fun verifyOverlayIsNotRemoved() =
        verify(mockFullScreenController, never()).ensureOverlayRemoved()

    private fun verifyOverlayRemoved() = verify(mockFullScreenController).ensureOverlayRemoved()
}
