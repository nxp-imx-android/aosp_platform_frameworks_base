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

package com.android.settingslib.metadata.apifirst

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.apifirst.category.Category
import com.android.settingslib.metadata.apifirst.types.AnyBoolean
import com.android.settingslib.metadata.apifirst.types.AnyInt
import com.android.settingslib.preference.PreferenceFragment
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiFirstPreferenceScreenTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun createApiFirstPreferenceScreenWithoutGetter_throwsError() {
        assertThrows(IllegalStateException::class.java) {
            val preferenceKey1 = "ApiFirstPreference"

            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey1, purpose = 0, type = AnyBoolean
                    ) {}
                }
            }
        }
    }

    @Test
    fun createApiFirstPreferenceScreen_orderIsCorrect() {
        val preferenceKey1 = "ApiFirstPreference1"
        val preferenceKey2 = "ApiFirstPreference2"

        val preferenceScreen = object : ApiFirstPreferenceScreen(
            key = SCREEN_KEY,
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = PreferenceFragment::class,
            purpose = 0
        ) {
            init {
                preference(
                    key = preferenceKey1, purpose = 0, type = AnyBoolean
                ) {
                    get {
                        execute {
                            false
                        }
                    }
                }

                preference(
                    key = preferenceKey2, purpose = 0, type = AnyInt
                ) {
                    get {
                        execute {
                            0
                        }
                    }
                }
            }
        }

        // Check we only have 2 preferences in the list
        assertThat(preferenceScreen.preferencesList.size).isEqualTo(2)

        // Check preference order is correct
        val firstPreference = preferenceScreen.preferencesList[0] as ApiFirstPreference<Boolean>
        assertThat(firstPreference.key).isEqualTo(preferenceKey1)

        val secondPreference = preferenceScreen.preferencesList[1] as ApiFirstPreference<Int>
        assertThat(secondPreference.key).isEqualTo(preferenceKey2)
    }

    @Test
    fun createApiFirstPreferenceScreenWithGetters_succeeds() {
        val preferenceValue1 = false
        val preferenceKey1 = "ApiFirstPreference1"
        val preferenceValue2 = 0
        val preferenceKey2 = "ApiFirstPreference2"

        val preferenceScreen = object : ApiFirstPreferenceScreen(
            key = SCREEN_KEY,
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = PreferenceFragment::class,
            purpose = 0
        ) {
            init {
                preference(
                    key = preferenceKey1, purpose = 0, type = AnyBoolean
                ) {
                    get {
                        execute {
                            preferenceValue1
                        }
                    }
                }

                preference(
                    key = preferenceKey2, purpose = 0, type = AnyInt
                ) {
                    get {
                        execute {
                            preferenceValue2
                        }
                    }
                }
            }
        }


        // Check we only have 2 preferences in the list
        assertThat(preferenceScreen.preferencesList.size).isEqualTo(2)

        // Check that getters return the correct value
        val firstPreference = preferenceScreen.preferencesList[0] as ApiFirstPreference<Boolean>
        assertThat(firstPreference.get.execute(context)).isEqualTo(preferenceValue1)

        val secondPreference = preferenceScreen.preferencesList[1] as ApiFirstPreference<Int>
        assertThat(secondPreference.key).isEqualTo("ApiFirstPreference2")
        assertThat(secondPreference.get.execute(context)).isEqualTo(preferenceValue2)
    }

    @Test
    fun createApiFirstPreferenceScreenWithGettersAndSetters_succeeds() {
        val preferenceValue1 = false
        val preferenceKey1 = "ApiFirstPreference1"
        var preferenceValue2 = 0
        val preferenceKey2 = "ApiFirstPreference2"
        val newPreferenceValue2 = 22

        val preferenceScreen = object : ApiFirstPreferenceScreen(
            key = SCREEN_KEY,
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = PreferenceFragment::class,
            purpose = 0
        ) {
            init {
                preference(
                    key = preferenceKey1, purpose = 0, type = AnyBoolean
                ) {
                    get {
                        execute {
                            preferenceValue1
                        }
                    }
                }

                preference(
                    key = preferenceKey2, purpose = 0, type = AnyInt
                ) {
                    get {
                        execute {
                            preferenceValue2
                        }
                    }

                    set {
                        execute { context, value ->
                            preferenceValue2 = value
                        }
                    }
                }
            }
        }

        // Check we only have 2 preferences in the list
        assertThat(preferenceScreen.preferencesList.size).isEqualTo(2)

        // First preference doesn't have a setter, so the getter should return the same value
        val firstPreference = preferenceScreen.preferencesList[0] as ApiFirstPreference<Boolean>
        assertThat(firstPreference.key).isEqualTo(preferenceKey1)
        assertThat(firstPreference.set?.execute(context, true)).isEqualTo(null)
        assertThat(firstPreference.get.execute(context)).isEqualTo(preferenceValue1)

        // Value of the second preference should be changed by setter
        val secondPreference = preferenceScreen.preferencesList[1] as ApiFirstPreference<Int>
        assertThat(secondPreference.key).isEqualTo(preferenceKey2)
        assertThat(
            secondPreference.set?.execute(
                context, newPreferenceValue2
            )
        ).isNotEqualTo(null)
        assertThat(secondPreference.get.execute(context)).isEqualTo(newPreferenceValue2)
    }

    companion object {
        const val SCREEN_KEY = "ApiFirstScreen"
    }
}