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

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.apifirst.ExceptionMessagesFormatter.getExceptionMessageMultipleDefines
import com.android.settingslib.metadata.apifirst.ExceptionMessagesFormatter.getExceptionMessageWrongOrder
import com.android.settingslib.metadata.apifirst.category.Category
import com.android.settingslib.metadata.apifirst.preconditions.Allowed
import com.android.settingslib.metadata.apifirst.preconditions.Custom
import com.android.settingslib.metadata.apifirst.preconditions.EnterpriseRestriction
import com.android.settingslib.metadata.apifirst.preconditions.HardwareUnsupported
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
        val exception = assertThrows(IllegalStateException::class.java) {
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

        assertThat(exception.message).isEqualTo("'get' block is required")
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
        assertThat(preferenceScreen.preferences.size).isEqualTo(2)

        // Check preference order is correct
        val firstPreference = preferenceScreen.preferences[0] as ApiFirstPreference<Boolean>
        assertThat(firstPreference.key).isEqualTo(preferenceKey1)

        val secondPreference = preferenceScreen.preferences[1] as ApiFirstPreference<Int>
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
        assertThat(preferenceScreen.preferences.size).isEqualTo(2)

        // Check that getters return the correct value
        val firstPreference = preferenceScreen.preferences[0] as ApiFirstPreference<Boolean>
        assertThat(
            firstPreference.storage(context).getValue(preferenceKey1, Boolean::class.java)
        ).isEqualTo(preferenceValue1)

        val secondPreference = preferenceScreen.preferences[1] as ApiFirstPreference<Int>
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
        assertThat(preferenceScreen.preferences.size).isEqualTo(2)

        // First preference doesn't have a setter, so the getter should return the same value
        val firstPreference = preferenceScreen.preferences[0] as ApiFirstPreference<Boolean>
        assertThat(firstPreference.key).isEqualTo(preferenceKey1)
        firstPreference.storage(context).setValue(preferenceKey1, Boolean::class.java, true)
        assertThat(
            firstPreference.storage(context).getValue(preferenceKey1, Boolean::class.java)
        ).isEqualTo(preferenceValue1)

        // Value of the second preference should be changed by setter
        val secondPreference = preferenceScreen.preferences[1] as ApiFirstPreference<Int>
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
        assertThat(preferenceScreen.preferences.size).isEqualTo(1)

        // Trying to set a wrong value throws exception and value stays the same
        val preference = preferenceScreen.preferences[0] as ApiFirstPreference<Int>
        assertThat(preference.key).isEqualTo(preferenceKey)
        val exception = assertThrows(IllegalStateException::class.java) {
            preference.storage(context)
                .setValue(preferenceKey, Int::class.java, newPreferenceWrongValue)
        }
        assertThat(exception.message).isEqualTo("Wrong value")
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

    @Test
    fun createApiFirstPreferenceScreenWithParameters_wrongOrderPreferenceBeforeParameters_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }

                    parameters {
                        parameter("package", "description")
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("parameters"))
    }

    @Test
    fun createApiFirstPreferenceScreenWithParametersPermissionsPreconditions_wrongOrderPreconditionsBeforePermissions_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    parameters {
                        parameter("package", "description")
                    }

                    preconditions("preconditions description") {
                        HardwareUnsupported("plug in the device")
                    }

                    permissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))

                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("permissions"))
    }

    @Test
    fun createApiFirstPreferenceScreenMultipleParametersBlocks_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    parameters {
                        parameter("package", "description")
                    }

                    parameters {
                        parameter("package", "description")
                    }

                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("parameters"))
    }

    @Test
    fun createApiFirstPreferenceScreenMultiplePermissionsBlocks_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    permissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    permissions(listOf(Manifest.permission.WRITE_SETTINGS))

                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("permissions"))
    }

    @Test
    fun createApiFirstPreferenceScreenMultiplePreconditionsBlocks_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preconditions("preconditions description 1") {
                        HardwareUnsupported("plug in the device")
                    }
                    preconditions("preconditions description 2") {
                        EnterpriseRestriction("blocked by admin")
                    }

                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("preconditions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithMultiplePermissionsBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        permissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        permissions(listOf(Manifest.permission.WRITE_SETTINGS))

                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("permissions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithMultiplePreconditionsBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        preconditions("preconditions description 1") {
                            HardwareUnsupported("plug in the device")
                        }
                        preconditions("preconditions description 2") {
                            EnterpriseRestriction("blocked by admin")
                        }

                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("preconditions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithMultipleGetBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }
                        }

                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("get"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithMultipleSetBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }
                        }

                        set {
                            execute { _, value ->
                                preferenceValue = value
                            }
                        }

                        set {
                            execute { _, value ->
                                preferenceValue = value
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("set"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithGetterAndSetter_wrongOrderSetBeforeGet_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        set {
                            execute { _, value ->
                                preferenceValue = value
                            }
                        }

                        get {
                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("get"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithPermissionsPreconditionsGetterSetter_wrongOrderGetBeforePermissions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }
                        }

                        permissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        preconditions("preconditions description") {
                            HardwareUnsupported("plug in the device")
                        }

                        set {
                            execute { _, value ->
                                preferenceValue = value
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("permissions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithGetter_wrongOrderExecuteBeforePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            permissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))

                            execute {
                                preferenceValue
                            }

                            preconditions("preconditions description") {
                                HardwareUnsupported("plug in the device")
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("preconditions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithGetter_withMultiplePermissions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            permissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
                            permissions(listOf(Manifest.permission.WRITE_SETTINGS))

                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("permissions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithGetter_withMultiplePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            preconditions("preconditions description 1") {
                                HardwareUnsupported("plug in the device")
                            }
                            preconditions("preconditions description 2") {
                                EnterpriseRestriction("blocked by admin")
                            }

                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("preconditions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithGetter_withMultipleExecuteBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        get {
                            execute {
                                preferenceValue
                            }

                            execute {
                                preferenceValue
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("execute"))
    }
    // -- ///

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithSetter_wrongOrderExecuteBeforeValuePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        set {
                            permissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))

                            preconditions("preconditions description") {
                                HardwareUnsupported("plug in the device")
                            }

                            execute { _, value ->
                                preferenceValue = value
                            }

                            valuePreconditions("Value preconditions description") { _, value ->
                                Allowed
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("valuePreconditions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithSetter_withMultiplePermissions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        set {
                            permissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
                            permissions(listOf(Manifest.permission.WRITE_SETTINGS))

                            execute { _, value ->
                                preferenceValue = value
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("permissions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithSetter_withMultiplePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        set {
                            preconditions("preconditions description 1") {
                                HardwareUnsupported("plug in the device")
                            }
                            preconditions("preconditions description 2") {
                                EnterpriseRestriction("blocked by admin")
                            }

                            execute { _, value ->
                                preferenceValue = value
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("preconditions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithSetter_withMultipleValuePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        set {
                            valuePreconditions("Value preconditions description 1") { _, value ->
                                Allowed
                            }
                            valuePreconditions("Value preconditions description 2") { _, value ->
                                Allowed
                            }

                            execute { _, value ->
                                preferenceValue = value
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("valuePreconditions"))
    }

    @Test
    fun createApiFirstPreferenceScreenPreferenceWithSetter_withMultipleExecuteBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiFirstPreference"

        val exception = assertThrows(IllegalStateException::class.java) {
            object : ApiFirstPreferenceScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                fragment = PreferenceFragment::class,
                purpose = 0
            ) {
                init {
                    preference(
                        key = preferenceKey, purpose = 0, type = AnyBoolean
                    ) {
                        set {
                            execute { _, value ->
                                preferenceValue = value
                            }

                            execute { _, value ->
                                preferenceValue = value
                            }
                        }
                    }
                }
            }
        }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("execute"))
    }

    companion object {
        const val SCREEN_KEY = "ApiFirstScreen"
    }
}