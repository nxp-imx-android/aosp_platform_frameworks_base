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
import com.android.settingslib.metadata.apifirst.preconditions.Allowed
import com.android.settingslib.metadata.apifirst.preconditions.Custom
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
        assertThat(
            firstPreference.storage(context).getValue(preferenceKey1, Boolean::class.java)
        ).isEqualTo(preferenceValue1)

        val secondPreference = preferenceScreen.preferencesList[1] as ApiFirstPreference<Int>
        assertThat(secondPreference.key).isEqualTo("ApiFirstPreference2")
        assertThat(
            secondPreference.storage(context).getValue(preferenceKey2, Int::class.java)
        ).isEqualTo(preferenceValue2)
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
        firstPreference.storage(context).setValue(preferenceKey1, Boolean::class.java, true)
        assertThat(
            firstPreference.storage(context).getValue(preferenceKey1, Boolean::class.java)
        ).isEqualTo(preferenceValue1)

        // Value of the second preference should be changed by setter
        val secondPreference = preferenceScreen.preferencesList[1] as ApiFirstPreference<Int>
        assertThat(secondPreference.key).isEqualTo(preferenceKey2)
        secondPreference.storage(context)
            .setValue(preferenceKey2, Int::class.java, newPreferenceValue2)
        assertThat(
            secondPreference.storage(context).getValue(preferenceKey2, Int::class.java)
        ).isEqualTo(newPreferenceValue2)
    }

    @Test
    fun createApiFirstPreferenceScreenWithGetterAndSetter_withValuePreconditions() {
        val initialPreferenceValue = 0
        var preferenceValue = initialPreferenceValue
        val preferenceKey = "ApiFirstPreference"
        val newPreferenceWrongValue = 23
        val newPreferenceValue = 112

        val preferenceScreen = object : ApiFirstPreferenceScreen(
            key = SCREEN_KEY,
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = PreferenceFragment::class,
            purpose = 0
        ) {
            init {
                preference(
                    key = preferenceKey, purpose = 0, type = AnyInt
                ) {
                    get {
                        execute {
                            preferenceValue
                        }
                    }

                    set {
                        valuePreconditions("Value's digits must add up to even number") { _, value ->
                            var sum = 0
                            for (digit in value.toString()) {
                                sum += digit.digitToInt()
                            }
                            if (sum % 2 == 0) {
                                Allowed
                            } else {
                                Custom("Wrong value")
                            }
                        }

                        execute { _, value ->
                            preferenceValue = value
                        }
                    }
                }
            }
        }

        // Check we only have 1 preference in the list
        assertThat(preferenceScreen.preferencesList.size).isEqualTo(1)

        // Trying to set a wrong value throws exception and value stays the same
        val preference = preferenceScreen.preferencesList[0] as ApiFirstPreference<Int>
        assertThat(preference.key).isEqualTo(preferenceKey)
        assertThrows(IllegalStateException::class.java) {
            preference.storage(context)
                .setValue(preferenceKey, Int::class.java, newPreferenceWrongValue)
        }
        assertThat(
            preference.storage(context).getValue(preferenceKey, Int::class.java)
        ).isEqualTo(initialPreferenceValue)

        // Setting correct value succeeds
        assertThat(preference.key).isEqualTo(preferenceKey)
        preference.storage(context).setValue(preferenceKey, Int::class.java, newPreferenceValue)
        assertThat(
            preference.storage(context).getValue(preferenceKey, Int::class.java)
        ).isEqualTo(newPreferenceValue)
    }

    companion object {
        const val SCREEN_KEY = "ApiFirstScreen"
    }
}