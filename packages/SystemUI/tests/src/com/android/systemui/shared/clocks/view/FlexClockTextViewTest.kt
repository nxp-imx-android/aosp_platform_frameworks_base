/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.shared.clocks.view

import android.graphics.Typeface
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.customization.clocks.ClockContextImpl
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.FontTextStyleImpl
import com.android.systemui.customization.clocks.TestTimeKeeper
import com.android.systemui.customization.clocks.TypefaceCache
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class FlexClockTextViewTest : SysuiTestCase() {
    private val messageBuffer = ClockLogger.DEBUG_MESSAGE_BUFFER
    private lateinit var underTest: FlexClockTextView
    private val defaultLargeClockTextSize = 500F
    private val smallerTextSize = 300F
    private val largerTextSize = 800F
    private val firstMeasureTextSize = 100F

    @Before
    fun setup() {
        underTest =
            FlexClockTextView(
                ClockContextImpl(
                    context,
                    context.resources,
                    ClockSettings(),
                    TypefaceCache(messageBuffer, 20) {
                        // TODO(b/364680873): Move constant to config_clockFontFamily when shipping
                        return@TypefaceCache Typeface.create(
                            "google-sans-flex-clock",
                            Typeface.NORMAL,
                        )
                    },
                    messageBuffer,
                    vibrator = null,
                    timeKeeper = TestTimeKeeper(),
                    isAnimationEnabled = false,
                ),
                isLargeClock = false,
            )
        underTest.applyStyles(FontTextStyleImpl(), FontTextStyleImpl())
        underTest.text = "0"
        underTest.applyTextSize(defaultLargeClockTextSize, constrainedByHeight = false)
    }

    @Test
    fun applySmallerConstrainedTextSize_applyConstrainedTextSize() {
        underTest.applyTextSize(smallerTextSize, constrainedByHeight = true)
        assertEquals(smallerTextSize, underTest.textSize * underTest.fontSizeAdjustFactor)
    }

    @Test
    fun applyLargerConstrainedTextSize_applyUnconstrainedTextSize() {
        underTest.applyTextSize(largerTextSize, constrainedByHeight = true)
        assertEquals(defaultLargeClockTextSize, underTest.textSize)
    }

    @Test
    fun applyFirstMeasureConstrainedTextSize_getConstrainedTextSize() {
        underTest.applyTextSize(firstMeasureTextSize, constrainedByHeight = true)
        underTest.applyTextSize(smallerTextSize, constrainedByHeight = true)
        assertEquals(smallerTextSize, underTest.textSize * underTest.fontSizeAdjustFactor)
    }

    @Test
    fun applySmallFirstMeasureConstrainedSizeAndLargerConstrainedTextSize_applyDefaultSize() {
        underTest.applyTextSize(firstMeasureTextSize, constrainedByHeight = true)
        underTest.applyTextSize(largerTextSize, constrainedByHeight = true)
        assertEquals(defaultLargeClockTextSize, underTest.textSize)
    }

    @Test
    fun applyFirstMeasureConstrainedTextSize_applyUnconstrainedTextSize() {
        underTest.applyTextSize(firstMeasureTextSize, constrainedByHeight = true)
        underTest.applyTextSize(defaultLargeClockTextSize, constrainedByHeight = false)
        assertEquals(defaultLargeClockTextSize, underTest.textSize)
    }
}
