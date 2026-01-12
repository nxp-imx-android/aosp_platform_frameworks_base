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

package com.android.settingslib.graph

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceRestrictionProvider
import com.android.settingslib.metadata.PreferenceScreenRegistry
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.testutils.GraphTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GraphTestUtilsTest {

    private val application = spy(ApplicationProvider.getApplicationContext<Application>()!!)
    private val context = application as Context

    @Test
    fun setRegistryFactories_setsFactoriesCorrectly() {
        val screen = GraphTestUtils.createScreen("test_screen")
        GraphTestUtils.setRegistryFactories(screen)

        val factory = PreferenceScreenRegistry.preferenceScreenMetadataFactories?.get("test_screen")
        assertThat(factory).isNotNull()
        assertThat(factory?.create(context)).isEqualTo(screen)
    }

    @Test
    fun createScreen_createsScreenWithCorrectKey() {
        val screen = GraphTestUtils.createScreen("test_screen")
        assertThat(screen.key).isEqualTo("test_screen")
    }

    @Test
    fun createSimplePreference_createsPreferenceWithCorrectKey() {
        val preference = GraphTestUtils.createSimplePreference("test_preference")
        assertThat(preference.key).isEqualTo("test_preference")
    }

    @Test
    fun createPersistentPreference_respectsPreferenceConfigFlags() {
        val config = GraphTestUtils.PreferenceConfig(
            key = "test_key",
            isAvailable = false,
            isRestricted = true,
            isEnabled = false
        )
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(
            GraphTestUtils.PersistentPreferenceConfig(config)
        )

        assertThat(preference.key).isEqualTo("test_key")
        assertThat((preference as PreferenceAvailabilityProvider).isAvailable(context)).isFalse()
        assertThat((preference as PreferenceRestrictionProvider).isRestricted(context)).isTrue()
        assertThat((preference).isEnabled(context)).isFalse()
    }

    @Test
    fun createPersistentPreference_respectsPersistentPreferenceConfigPermits() {
        val persistentConfig = GraphTestUtils.PersistentPreferenceConfig(
            preferenceConfig = GraphTestUtils.PreferenceConfig("test_key"),
            valueType = Boolean::class.javaObjectType,
            readPermit = ReadWritePermit.DISALLOW,
            writePermit = ReadWritePermit.REQUIRE_USER_AGREEMENT,
            readPermission = "read_perm",
            writePermission = "write_perm"
        )
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(persistentConfig)

        @Suppress("UNCHECKED_CAST")
        val persistentPreference = preference as PersistentPreference<Boolean>
        assertThat(persistentPreference.valueType).isEqualTo(Boolean::class.javaObjectType)
        assertThat(persistentPreference.getReadPermit(context, 0, 0)).isEqualTo(ReadWritePermit.DISALLOW)
        assertThat(persistentPreference.getWritePermit(context, 0, 0)).isEqualTo(ReadWritePermit.REQUIRE_USER_AGREEMENT)
        assertThat(persistentPreference.getReadPermissions(context)).isEqualTo(Permissions.allOf("read_perm"))
        assertThat(persistentPreference.getWritePermissions(context)).isEqualTo(Permissions.allOf("write_perm"))
    }

    @Test
    fun createPersistentPreference_throwsErrorWhenConfigured() {
        val config = GraphTestUtils.PreferenceConfig(key = "test_key", throwsError = true)
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(
            GraphTestUtils.PersistentPreferenceConfig(config)
        )

        @Suppress("UNCHECKED_CAST")
        val persistentPreference = preference as PersistentPreference<Boolean>
        assertThrows(IllegalStateException::class.java) {
            persistentPreference.getWritePermit(context, 0, 0)
        }
    }

    @Test
    fun createIntRangePreference_createsPreferenceWithCorrectValues() {
        val preference = GraphTestUtils.createIntRangePreference("test_key", 1, 10, 5)
        assertThat(preference.key).isEqualTo("test_key")
        assertThat(preference.getMinValue(context)).isEqualTo(1)
        assertThat(preference.getMaxValue(context)).isEqualTo(10)
        assertThat(preference.storage(context).getInt("test_key")).isEqualTo(5)
    }

    @Test
    fun createStorage_storesAndRetrievesValue() {
        val storage = GraphTestUtils.createStorage("initial_value", "preference_key")
        assertThat(storage.getValue("preference_key", String::class.javaObjectType))
            .isEqualTo("initial_value")

        storage.setValue("preference_key", String::class.javaObjectType, "new_value")
        assertThat(storage.getValue("preference_key", String::class.javaObjectType))
            .isEqualTo("new_value")
    }

    @Test
    fun makePermissionPass_mocksPermissionCorrectly() {
        GraphTestUtils.makePermissionPass(application, "test_permission", true)
        assertThat(application.checkPermission("test_permission", 0, 0))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)

        GraphTestUtils.makePermissionPass(application, "test_permission", false)
        assertThat(application.checkPermission("test_permission", 0, 0))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
    }
}