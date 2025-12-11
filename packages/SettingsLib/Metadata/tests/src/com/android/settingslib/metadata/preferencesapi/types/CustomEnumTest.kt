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

package com.android.settingslib.metadata.preferencesapi.types

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomEnumTest {

    /** A mock Integer-based Enum to test generic type <Int>. */
    private enum class TestIntEnum(override val asApiValue: Int, override val purpose: Int = 0) :
        EnumApi<Int> {
        FIRST(1),
        SECOND(2),
        NEGATIVE(-100),
    }

    /** A mock String-based Enum to test generic type <String>. */
    private enum class TestStringEnum(
        override val asApiValue: String,
        override val purpose: Int = 0,
    ) : EnumApi<String> {
        ACTIVE("active_state"),
        INACTIVE("inactive_state"),
    }

    /** An empty Enum */
    private enum class EmptyEnum(override val asApiValue: Int, override val purpose: Int = 0) :
        EnumApi<Int>

    @Test
    fun fromApiValue_validInteger_returnCorrectName() {
        val customEnum = CustomEnum(TestIntEnum::class)

        assertThat(customEnum.fromApiValue(1)).isEqualTo(TestIntEnum.FIRST)
        assertThat(customEnum.fromApiValue(2)).isEqualTo(TestIntEnum.SECOND)
        assertThat(customEnum.fromApiValue(-100)).isEqualTo(TestIntEnum.NEGATIVE)
    }

    @Test
    fun fromApiValue_invalidInteger_returnNull() {
        val customEnum = CustomEnum(TestIntEnum::class)

        assertThat(customEnum.fromApiValue(999)).isNull()
    }

    @Test
    fun fromApiValue_validString_returnCorrectName() {
        val customEnum = CustomEnum(TestStringEnum::class)

        assertThat(customEnum.fromApiValue("active_state")).isEqualTo(TestStringEnum.ACTIVE)
        assertThat(customEnum.fromApiValue("inactive_state")).isEqualTo(TestStringEnum.INACTIVE)
    }

    @Test
    fun fromApiValue_invalidString_returnNull() {
        val customEnum = CustomEnum(TestStringEnum::class)

        assertThat(customEnum.fromApiValue("weird_state")).isNull()
    }

    @Test
    fun fromApiValue_emptyEnum_returnNull() {
        val customEnum = CustomEnum(EmptyEnum::class)

        assertThat(customEnum.fromApiValue(1)).isNull()
    }

    @Test
    fun getEntries_validEnum_returnAllConstants() {
        val customEnum = CustomEnum(TestIntEnum::class)

        assertThat(customEnum.getEntries()).apply {
            hasSize(3)
            containsExactlyElementsIn(
                listOf(TestIntEnum.FIRST, TestIntEnum.SECOND, TestIntEnum.NEGATIVE)
            )
        }
    }

    @Test
    fun getEntries_emptyEnum_returnEmptyList() {
        val customEnum = CustomEnum(EmptyEnum::class)

        assertThat(customEnum.getEntries()).isEmpty()
    }
}
