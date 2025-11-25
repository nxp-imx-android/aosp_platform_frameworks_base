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
import com.android.settingslib.preference.PreferenceFragment
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

            setupApiFirstPreferenceScreen(
                listOf(
                    createPreference<Boolean> {
                        key = preferenceKey1
                    },
                )
            )
        }
    }

    @Test
    fun createApiFirstPreferenceScreen_orderIsCorrect() {
        val preferenceKey1 = "ApiFirstPreference1"
        val preferenceKey2 = "ApiFirstPreference2"


        val preferencesList = getPreferencesList(
            setupApiFirstPreferenceScreen(
                listOf(
                    createPreference {
                        key = preferenceKey1

                        getter {
                            execute {
                            }
                        }
                    },

                    createPreference {
                        key = preferenceKey2

                        getter {
                            execute {
                            }
                        }
                    }
                )))

        // Check we only have 2 preferences in the list
        assertThat(preferencesList.size).isEqualTo(2)

        // Check preference order is correct
        val firstPreference = preferencesList[0] as ApiFirstPreference<Boolean>
        assertThat(firstPreference.key).isEqualTo(preferenceKey1)

        val secondPreference = preferencesList[1] as ApiFirstPreference<Int>
        assertThat(secondPreference.key).isEqualTo(preferenceKey2)
    }

    @Test
    fun createApiFirstPreferenceScreenWithGetters_succeeds() {
        val preferenceValue1 = false
        val preferenceKey1 = "ApiFirstPreference1"
        val preferenceValue2 = 0
        val preferenceKey2 = "ApiFirstPreference2"

        val preferencesList = getPreferencesList(
            setupApiFirstPreferenceScreen(
                listOf(
                    createPreference {
                        key = preferenceKey1

                        getter {
                            execute {
                                preferenceValue1
                            }
                        }
                    },

                    createPreference {
                        key = preferenceKey2

                        getter {
                            execute {
                                preferenceValue2
                            }
                        }
                    }
                )))

        // Check we only have 2 preferences in the list
        assertThat(preferencesList.size).isEqualTo(2)

        // Check that getters return the correct value
        val firstPreference = preferencesList[0] as ApiFirstPreference<Boolean>
        assertThat(firstPreference.getter.execute(context)).isEqualTo(preferenceValue1)

        val secondPreference = preferencesList[1] as ApiFirstPreference<Int>
        assertThat(secondPreference.key).isEqualTo("ApiFirstPreference2")
        assertThat(secondPreference.getter.execute(context)).isEqualTo(preferenceValue2)
    }

    @Test
    fun createApiFirstPreferenceScreenWithGettersAndSetters_succeeds() {
        val preferenceValue1 = false
        val preferenceKey1 = "ApiFirstPreference1"
        var preferenceValue2 = 0
        val preferenceKey2 = "ApiFirstPreference2"
        val newPreferenceValue2 = 22

        val preferencesList = getPreferencesList(
            setupApiFirstPreferenceScreen(
                listOf(
                    createPreference {
                        key = preferenceKey1

                        getter {
                            execute {
                                preferenceValue1
                            }
                        }
                    },

                    createPreference {
                        key = preferenceKey2

                        getter {
                            execute {
                                preferenceValue2
                            }
                        }

                        setter {
                            execute { context, value ->
                                preferenceValue2 = value
                            }
                        }
                    }
                )))

        // Check we only have 2 preferences in the list
        assertThat(preferencesList.size).isEqualTo(2)

        // First preference doesn't have a setter, so the getter should return the same value
        val firstPreference = preferencesList[0] as ApiFirstPreference<Boolean>
        assertThat(firstPreference.key).isEqualTo(preferenceKey1)
        assertThat(firstPreference.setter?.execute(context, true)).isEqualTo(null)
        assertThat(firstPreference.getter.execute(context)).isEqualTo(preferenceValue1)

        // Value of the second preference should be changed by setter
        val secondPreference = preferencesList[1] as ApiFirstPreference<Int>
        assertThat(secondPreference.key).isEqualTo(preferenceKey2)
        assertThat(
            secondPreference.setter?.execute(
                context,
                newPreferenceValue2
            )
        ).isNotEqualTo(null)
        assertThat(secondPreference.getter.execute(context)).isEqualTo(newPreferenceValue2)
    }

    private fun setupApiFirstPreferenceScreen(preferencesList: List<ApiFirstPreference<*>>) =
        object : ApiFirstPreferenceScreen() {
            override fun fragmentClass() = PreferenceFragment::class.java
            override val key: String = SCREEN_KEY
            override val purpose: Int = 0
            override fun preferences(context: Context) = preferencesList
        }

    private fun getPreferencesList(apiFirstPreferenceScreen: ApiFirstPreferenceScreen): List<ApiFirstPreference<*>> {
        val preferencesList = mutableListOf<ApiFirstPreference<*>>()
        apiFirstPreferenceScreen.getPreferenceHierarchy(
            context,
            CoroutineScope(Dispatchers.Unconfined)
        ).forEachRecursively { child ->
            if (child.metadata is ApiFirstPreference<*>) {
                preferencesList.add(child.metadata)
            }
        }

        return preferencesList
    }

    companion object {
        const val SCREEN_KEY = "ApiFirstScreen"
    }
}