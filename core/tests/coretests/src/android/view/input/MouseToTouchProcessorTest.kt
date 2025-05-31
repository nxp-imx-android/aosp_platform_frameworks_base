/*
* Copyright 2025 The Android Open Source Project
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

package android.view.input

import android.compat.testing.PlatformCompatChangeRule
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.hardware.input.Flags
import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests for [MouseToTouchProcessor].
 *
 * Build/Install/Run:
 * atest FrameworksCoreTests:MouseToTouchProcessorTest
 */
@SmallTest
@Presubmit
class MouseToTouchProcessorTest {
    private lateinit var processor: MouseToTouchProcessor
    private lateinit var context: Context

    @get:Rule
    val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @get:Rule
    val compatChangeRule = PlatformCompatChangeRule()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        processor = MouseToTouchProcessor(context, null)
    }

    @Test
    @DisableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    fun compatibilityNotNeededIfFlagIsDisabled() {
        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(context), equalTo(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    @DisableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun compatibilityNotNeededIfCompatChangesDisabled() {
        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(context), equalTo(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    @EnableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun compatibilityNotNeededIfCompatChangesEnabled() {
        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(context), equalTo(true))
    }

    @Test
    @EnableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    @EnableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun compatibilityNotNeededIfFeaturePCPresent() {
        val mockPackageInfo = PackageInfo().apply {
            reqFeatures = arrayOf(FeatureInfo().apply { name = PackageManager.FEATURE_PC })
        }
        val packageManager = mock<PackageManager> {
            on { getPackageInfo(anyOrNull<String>(), any<Int>()) } doReturn mockPackageInfo
        }
        val mockContext = mock<Context> {
            on { getPackageManager() } doReturn packageManager
        }

        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(mockContext), equalTo(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)
    @EnableCompatChanges(ActivityInfo.OVERRIDE_MOUSE_TO_TOUCH)
    fun compatibilityNeededIfFeaturePCNotPresent() {
        val mockPackageInfo = PackageInfo().apply {
            reqFeatures = arrayOf(FeatureInfo().apply { name = PackageManager.FEATURE_TOUCHSCREEN })
        }
        val packageManager = mock<PackageManager> {
            on { getPackageInfo(anyOrNull<String>(), any<Int>()) } doReturn mockPackageInfo
        }
        val mockContext = mock<Context> {
            on { getPackageManager() } doReturn packageManager
        }

        assertThat(MouseToTouchProcessor.isCompatibilityNeeded(mockContext), equalTo(true))
    }
}
