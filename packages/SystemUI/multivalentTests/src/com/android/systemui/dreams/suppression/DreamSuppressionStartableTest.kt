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

package com.android.systemui.dreams.suppression

import android.os.PowerManager
import android.os.powerManager
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_DREAM_SUPPRESSION
import com.android.systemui.SysuiTestCase
import com.android.systemui.dreams.suppression.data.repository.fakeActivityRecognitionRepository
import com.android.systemui.dreams.suppression.shared.model.DreamSuppression
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.testKosmos
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_DREAM_SUPPRESSION)
class DreamSuppressionStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Fixture {
        DreamSuppressionStartable(
            bgScope = kosmos.applicationCoroutineScope,
            activityRecognitionRepository = kosmos.fakeActivityRecognitionRepository,
            powerManager = kosmos.powerManager,
            logBuffer = logcatLogBuffer("DreamSuppressionStartableTest"),
        )
    }

    @Test
    fun suppressDreamWhenInVehicle() =
        kosmos.runTest {
            underTest.start()
            verify(powerManager, never()).suppressAmbientDisplay(any<String>(), any<Int>())

            fakeActivityRecognitionRepository.setInVehicle(true)
            verify(powerManager)
                .suppressAmbientDisplay(
                    DreamSuppression.InVehicle.token,
                    PowerManager.FLAG_AMBIENT_SUPPRESSION_DREAM,
                )

            fakeActivityRecognitionRepository.setInVehicle(false)
            verify(powerManager)
                .suppressAmbientDisplay(DreamSuppression.None.token, /* suppress= */ false)
        }
}
