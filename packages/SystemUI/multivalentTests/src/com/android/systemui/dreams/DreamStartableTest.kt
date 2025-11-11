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

package com.android.systemui.dreams

import android.service.dream.dreamManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.keyguardRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private var Kosmos.underTest by Fixture {
        DreamStartable(
            applicationScope = backgroundScope,
            sceneInteractor = sceneInteractor,
            dreamManager = dreamManager,
            keyguardInteractor = keyguardInteractor,
            logBuffer = logcatLogBuffer("DreamStartableTest"),
        )
    }

    @EnableSceneContainer
    @Test
    fun testStopDreamWhenGoingToGone() =
        kosmos.runTest {
            underTest.start()

            // Start dreaming
            keyguardRepository.setDreaming(true)
            advanceTimeBy(DREAMING_DELAY_MS)
            sceneInteractor.changeScene(Scenes.Dream, loggingReason = "dream started")

            verify(dreamManager, never()).stopDream()

            // Show bouncer
            sceneInteractor.showOverlay(Overlays.Bouncer, loggingReason = "show bouncer")

            verify(dreamManager, never()).stopDream()

            // Authenticate and unlock
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            sceneInteractor.changeScene(Scenes.Gone, loggingReason = "device unlocked")

            // Verify dream is stopped upon leaving the dream scene
            verify(dreamManager).stopDream()
        }

    private companion object {
        // Move past initial delay with [KeyguardInteractor#isAbleToDream]
        const val DREAMING_DELAY_MS = KeyguardInteractor.IS_ABLE_TO_DREAM_DELAY_MS + 100L
    }
}
