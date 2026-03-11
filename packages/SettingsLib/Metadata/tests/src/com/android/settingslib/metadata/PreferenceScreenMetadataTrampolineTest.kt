/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.settingslib.metadata

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.preferencesapi.types.AnyString
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PreferenceScreenMetadataTrampolineTest {

    private lateinit var context: Context
    private val screenKey = "test_screen_key"
    private val customAction = "com.android.settings.CUSTOM_ACTION"

    private val mockCatalystFlagProvider = mock<CatalystFlagProvider>()

    private lateinit var originalProvider: CatalystFlagProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalProvider = CatalystFlagProviderFactory.getInstance()
        CatalystFlagProviderFactory.setProvider(mockCatalystFlagProvider)
        whenever(mockCatalystFlagProvider.catalystUseKeyParameters()).thenReturn(true)
    }

    @After
    fun tearDown() {
        CatalystFlagProviderFactory.setProvider(originalProvider)
    }

    @Test
    fun getTrampolinedLaunchIntent_nullIntent_returnsNull() {
        val screenMetadata = FakeScreenMetadata(screenKey, null)

        val result = screenMetadata.getTrampolinedLaunchIntent(context, null)

        assertThat(result).isNull()
    }

    @Test
    fun getTrampolinedLaunchIntent_standardTrampolineIntent_returnsAsIs() {
        val standardIntent = Intent(PreferenceScreenMetadata.LAUNCH_SETTINGS_PAGES_ACTION)
        val screenMetadata = FakeScreenMetadata(screenKey, standardIntent)

        val result = screenMetadata.getTrampolinedLaunchIntent(context, null)

        assertThat(result).isSameInstanceAs(standardIntent)
    }

    @Test
    fun getTrampolinedLaunchIntent_customIntent_returnsWrappedTrampoline() {
        val customIntent = Intent(customAction)
        val screenMetadata = FakeScreenMetadata(screenKey, customIntent)

        val result = screenMetadata.getTrampolinedLaunchIntent(context, null)

        assertThat(result!!).isNotNull()
        assertThat(result.action).isEqualTo(PreferenceScreenMetadata.LAUNCH_SETTINGS_PAGES_ACTION)
        assertThat(result.`package`).isEqualTo("com.android.settings")
        assertThat(result.getStringExtra(PreferenceScreenMetadata.EXTRA_SCREEN_KEY)).isEqualTo(
            screenKey
        )
    }

    @Test
    fun getTrampolinedLaunchIntent_customIntent_preservesBundleArguments() {
        whenever(mockCatalystFlagProvider.catalystUseKeyParameters()).thenReturn(false)
        val arguments = Bundle().apply { putString("test_arg", "2737") }
        val customIntent = Intent(customAction)
        val screenMetadata = FakeScreenMetadata(screenKey, customIntent, arguments = arguments)

        val result = screenMetadata.getTrampolinedLaunchIntent(context, null)

        val resultArgs = result?.getBundleExtra(PreferenceScreenMetadata.EXTRA_SCREEN_ARGS)
        assertThat(resultArgs?.getString("test_arg")).isEqualTo("2737")
    }

    @Test
    fun getTrampolinedLaunchIntent_customIntent_preservesParameters() {
        val testSchema = KeyParametersSchema {
            parameter(KEY_PACKAGE_NAME, "Package", required = true, type = AnyString)
        }
        val params = testSchema.prepare(mapOf(KEY_PACKAGE_NAME to "com.android.settings"))
        val customIntent = Intent(customAction)
        val screenMetadata = FakeScreenMetadata(screenKey, customIntent, keyParameters = params)

        val result = screenMetadata.getTrampolinedLaunchIntent(context, null)

        val resultArgs = result?.getBundleExtra(PreferenceScreenMetadata.EXTRA_SCREEN_ARGS)
        val resultParams = testSchema.prepare(resultArgs!!)
        assertEquals(params, resultParams)
    }

    /** Helper class to provide metadata for testing. */
    private class FakeScreenMetadata(
        override val key: String,
        private val launchIntent: Intent?,
        @Deprecated("This property will be removed once the catalyst framework stops passing the arguments as a bundle. Use the keyParameters instead.")
        override val arguments: Bundle? = null,
        override val keyParameters: ValidatedKeyParameters? = null
    ) : PreferenceScreenMetadata {
        override val purpose = 0
        override fun fragmentClass() = null
        override fun getPreferenceHierarchy(
            context: Context,
            coroutineScope: CoroutineScope
        ): PreferenceHierarchy = mock<PreferenceHierarchy>()

        override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?) = launchIntent
    }
}