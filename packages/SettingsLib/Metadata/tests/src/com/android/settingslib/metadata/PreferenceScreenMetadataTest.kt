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

package com.android.settingslib.metadata

import android.content.Context
import android.os.Bundle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.catalyst.flags.Flags
import com.android.settingslib.preference.PreferenceFragment
import com.android.settingslib.preference.PreferenceScreenCreator
import com.android.settingslib.preference.launchFragmentScenario
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceScreenMetadataTest {
    @get:Rule
    val mSetFlagsRule = SetFlagsRule()

    @Test
    fun isContainer_isEntryPoint() {
        val innerScreen = Screen("Screen2")
        val screen =
            object : Screen("Screen1") {
                override fun getPreferenceHierarchy(
                    context: Context,
                    coroutineScope: CoroutineScope,
                ) = preferenceHierarchy(context) { +innerScreen.key }
            }
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(2) {
                it.put(screen.key) { screen }
                it.put(innerScreen.key) { innerScreen }
            }
        screen
            .launchFragmentScenario()
            .onFragment {
                val context = screen.preferenceLifecycleContext
                assertThat(screen.isContainer(context)).isTrue()
                assertThat(screen.isEntryPoint(context)).isFalse()
                assertThat(innerScreen.isContainer(context)).isFalse()
                assertThat(innerScreen.isEntryPoint(context)).isTrue()
            }
            .close()
    }

    @Test
    @DisableFlags(Flags.FLAG_CATALYST_USE_KEY_PARAMETERS)
    fun isContainer_isEntryPoint_parameterizedScreen_flagDisabled() {
        val innerScreen =
            object : Screen("Screen2", 0.toArgument()) {
                override val bindingKey
                    get() = "screen2:0"
            }
        val screen =
            object : Screen("Screen1", 0.toArgument()) {
                override val bindingKey
                    get() = "screen1:0"

                override fun getPreferenceHierarchy(
                    context: Context,
                    coroutineScope: CoroutineScope,
                ) = preferenceHierarchy(context) { +(innerScreen.key args 0.toArgument()) }
            }
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(2) {
                it.put(
                    screen.key,
                    object : TestParameterizedFactory {
                        override fun create(context: Context, args: Bundle) = screen
                    },
                )
                it.put(
                    innerScreen.key,
                    object : TestParameterizedFactory {
                        override fun create(context: Context, args: Bundle) = innerScreen
                    },
                )
            }
        screen
            .launchFragmentScenario()
            .onFragment {
                val context = screen.preferenceLifecycleContext
                assertThat(screen.isContainer(context)).isTrue()
                assertThat(screen.isEntryPoint(context)).isFalse()
                assertThat(innerScreen.isContainer(context)).isFalse()
                assertThat(innerScreen.isEntryPoint(context)).isTrue()
            }
            .close()
    }

    @Test
    @EnableFlags(Flags.FLAG_CATALYST_USE_KEY_PARAMETERS)
    fun isContainer_isEntryPoint_parameterizedScreen_flagEnabled() {
        val schema = KeyParametersSchema { parameter("id", "test id") }
        val keyParams = schema.prepare("id" to "0")
        val innerScreen =
            object : ScreenWithKeyParams("Screen2", keyParams) {
                override val bindingKey
                    get() = "screen2:0"
            }
        val screen =
            object : ScreenWithKeyParams("Screen1", keyParams) {
                override val bindingKey
                    get() = "screen1:0"

                override fun getPreferenceHierarchy(
                    context: Context,
                    coroutineScope: CoroutineScope,
                ) = preferenceHierarchy(context) { +(innerScreen.key withParameters keyParams) }
            }
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(2) {
                it.put(
                    screen.key,
                    object : TestParameterizedFactory {
                        override fun createWithKeyParameters(context: Context, keyParameters: KeyParameters) = screen
                        override val parametersSchema = schema
                    },
                )
                it.put(
                    innerScreen.key,
                    object : TestParameterizedFactory {
                        override fun createWithKeyParameters(context: Context, keyParameters: KeyParameters) = innerScreen
                        override val parametersSchema = schema
                    },
                )
            }
        screen
            .launchFragmentScenario()
            .onFragment {
                val context = screen.preferenceLifecycleContext
                assertThat(screen.isContainer(context)).isTrue()
                assertThat(screen.isEntryPoint(context)).isFalse()
                assertThat(innerScreen.isContainer(context)).isFalse()
                assertThat(innerScreen.isEntryPoint(context)).isTrue()
            }
            .close()
    }

    open class Screen(override val key: String, override val arguments: Bundle? = null) :
        PreferenceScreenCreator, PreferenceLifecycleProvider {

        lateinit var preferenceLifecycleContext: PreferenceLifecycleContext

        override fun fragmentClass() = PreferenceFragment::class.java

        override fun onCreate(context: PreferenceLifecycleContext) {
            preferenceLifecycleContext = context
        }

        override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
            preferenceHierarchy(context) {}
    }

    open class ScreenWithKeyParams(override val key: String, override val keyParameters: KeyParameters? = null) :
        PreferenceScreenCreator, PreferenceLifecycleProvider {

        lateinit var preferenceLifecycleContext: PreferenceLifecycleContext

        override fun fragmentClass() = PreferenceFragment::class.java

        override fun onCreate(context: PreferenceLifecycleContext) {
            preferenceLifecycleContext = context
        }

        override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
            preferenceHierarchy(context) {}
    }

    interface TestParameterizedFactory : PreferenceScreenMetadataParameterizedFactory {
        override fun create(context: Context, args: Bundle): PreferenceScreenMetadata = TODO("Not yet implemented")
        override fun parameters(context: Context): Flow<Bundle> = flowOf(0.toArgument())
        override fun createWithKeyParameters(context: Context, keyParameters: KeyParameters): PreferenceScreenMetadata = TODO("Not yet implemented")
        override fun keyParameters(context: Context): Flow<KeyParameters> = parameters(context).map { parametersSchema.prepare(it) }
        override val parametersSchema: KeyParametersSchema get() = KeyParametersSchema {}
    }
}

fun Int.toArgument() = Bundle().also { it.putInt(null, this) }