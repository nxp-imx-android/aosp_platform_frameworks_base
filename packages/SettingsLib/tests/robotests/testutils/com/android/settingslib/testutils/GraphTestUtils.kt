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

package com.android.settingslib.testutils

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.NoOpKeyedObservable
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.FixedArrayMap
import com.android.settingslib.metadata.IntRangeValuePreference
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceRestrictionProvider
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.PreferenceScreenRegistry
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.preferenceHierarchy
import kotlinx.coroutines.CoroutineScope
import org.mockito.kotlin.whenever
import org.mockito.kotlin.whenever

/**
 * Utility class for testing preference graphs.
 */
object GraphTestUtils {

    /**
     * Configuration for persistent preferences.
     *
     * @property preferenceConfig Base configuration for the preference.
     * @property valueType The type of the values this preference holds.
     * @property defaultValue The default value to use.
     * @property storage The storage backend ([KeyValueStore]) for the preference.
     * @property sensitivityLevel The sensitivity level of the data.
     * @property readPermission Permission required for reading.
     * @property readPermit The permit level for reading.
     * @property writePermission Permission required for writing.
     * @property writePermit The permit level for writing.
     */
    data class PersistentPreferenceConfig(
        val preferenceConfig: PreferenceConfig,
        val valueType : Class<*> = Boolean::class.java,
        val defaultValue: Any? = false,
        val storage: KeyValueStore = createStorage(defaultValue, preferenceConfig.key),
        val sensitivityLevel: @SensitivityLevel Int = SensitivityLevel.LOW_SENSITIVITY,
        val readPermission: String? = Manifest.permission.INTERACT_ACROSS_USERS,
        val readPermit: @ReadWritePermit Int = ReadWritePermit.ALLOW,
        val writePermission: String? = Manifest.permission.INTERACT_ACROSS_PROFILES,
        val writePermit: @ReadWritePermit Int? = ReadWritePermit.ALLOW,
    )

    /**
     * Configuration for preferences.
     *
     * @property key The preference key.
     * @property isAvailable Whether the preference is available.
     * @property isRestricted Whether the preference is restricted.
     * @property isEnabled Whether the preference is enabled.
     * @property throwsError Whether an error should be thrown during certain operations (like write
     *   permit check).
     */
    data class PreferenceConfig(
        val key : String,
        val isAvailable : Boolean = true,
        val isRestricted: Boolean = false,
        val isEnabled : Boolean = true,
        val throwsError : Boolean = false,
    )

    /**
     * Sets the preference screen metadata factories in [PreferenceScreenRegistry].
     *
     * @param preferenceScreens The preference screens to register.
     */
    fun setRegistryFactories(vararg preferenceScreens : PreferenceScreenMetadata) {
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(preferenceScreens.size) { screens ->
                for (preferenceScreen in preferenceScreens) {
                    screens.put(preferenceScreen.key) { _ -> preferenceScreen }
                }
            }
    }

    /**
     * Creates a mock [PreferenceScreenMetadata].
     *
     * @param screenKey The key for the preference screen.
     * @param preferences The preferences contained in the screen.
     * @return A mock [PreferenceScreenMetadata] implementation.
     */
    fun createScreen(
        screenKey: String,
        vararg preferences: PreferenceMetadata
    ) = object : PreferenceScreenMetadata {

        override fun fragmentClass(): Class<out Fragment>? = null
        override fun getPreferenceHierarchy(
            context: Context,
            coroutineScope: CoroutineScope
        ): PreferenceHierarchy = preferenceHierarchy(context) {
            for (preference in preferences) {
                +preference
            }
        }
        override val key: String
            get() = screenKey
        override val purpose: Int
            get() = 12
    }

    /**
     * Creates a simple [PreferenceMetadata] with the given key.
     *
     * @param preferenceKey The key for the preference.
     * @return A simple [PreferenceMetadata] implementation.
     */
    fun createSimplePreference(
        preferenceKey : String,
    ) = object : PreferenceMetadata {
        override val key: String
            get() = preferenceKey
        override val purpose: Int
            get() = 12
    }

    /**
     * Creates a [PersistentPreference] with the given configurations.
     *
     * @param T The type of the preference value.
     * @param persistentPreferenceConfig The configuration for the persistent preference.
     * @return A [PreferenceMetadata] implementation that also implements [PersistentPreference].
     */
    fun <T: Any> createPersistentPreference(
        persistentPreferenceConfig: PersistentPreferenceConfig
    ) : PreferenceMetadata = object: PersistentPreference<T>,
        PreferenceAvailabilityProvider,
        PreferenceRestrictionProvider {

        @Suppress("UNCHECKED_CAST")
        override val valueType: Class<T>
            get() = persistentPreferenceConfig.valueType as Class<T>

        override val key: String
            get() = persistentPreferenceConfig.preferenceConfig.key

        override fun storage(context: Context) = persistentPreferenceConfig.storage

        override val sensitivityLevel = persistentPreferenceConfig.sensitivityLevel

        override fun getReadPermit(
            context: Context,
            callingPid: Int,
            callingUid: Int
        ): @ReadWritePermit Int =
            persistentPreferenceConfig.readPermit

        override fun getReadPermissions(context: Context): Permissions? =
            persistentPreferenceConfig.readPermission?.let {
                Permissions.allOf(it)
            }

        override fun getWritePermit(
            context: Context,
            callingPid: Int,
            callingUid: Int
        ): @ReadWritePermit Int? =
            if (!persistentPreferenceConfig.preferenceConfig.throwsError)
                persistentPreferenceConfig.writePermit
            else error("Write permit failed")

        override fun getWritePermissions(context: Context) =
            persistentPreferenceConfig.writePermission?.let {
                Permissions.allOf(it)
            }
        override val purpose: Int
            get() = 12
        override fun isAvailable(context: Context): Boolean =
            persistentPreferenceConfig.preferenceConfig.isAvailable
        override fun isRestricted(context: Context): Boolean =
            persistentPreferenceConfig.preferenceConfig.isRestricted
        override fun isEnabled(context: Context): Boolean =
            persistentPreferenceConfig.preferenceConfig.isEnabled
    }

    /**
     * Creates an [IntRangeValuePreference].
     *
     * @param key The key for the preference.
     * @param minValue The minimum value allowed.
     * @param maxValue The maximum value allowed.
     * @param defaultValue The default value.
     * @param storage The storage backend for the preference.
     * @return An [IntRangeValuePreference] implementation.
     */
    fun createIntRangePreference(
        key: String,
        minValue: Int,
        maxValue: Int,
        defaultValue: Int,
        storage : KeyValueStore = createStorage(defaultValue, key)
    ) = object: IntRangeValuePreference {
        override fun getMinValue(context: Context): Int = minValue
        override fun getMaxValue(context: Context): Int = maxValue
        override val sensitivityLevel = SensitivityLevel.LOW_SENSITIVITY
        override fun getWritePermit(
            context: Context,
            callingPid: Int,
            callingUid: Int
        ): @ReadWritePermit Int = ReadWritePermit.ALLOW
        override fun storage(context: Context): KeyValueStore = storage
        override val key: String
            get() = key
        override val purpose: Int
            get() = 12
    }

    /**
     * Creates a simple in-memory [KeyValueStore].
     *
     * @param defaultValue The initial value to store.
     * @param defaultKey The key to use for getting and setting the value in this store.
     * @return A simple [KeyValueStore] implementation.
     */
    fun createStorage(defaultValue: Any? = null, defaultKey: String) : KeyValueStore {
        return object : NoOpKeyedObservable<String>(), KeyValueStore {
            val hashMap = HashMap<String, Any>().also {
                if(defaultValue != null)
                    it[defaultKey] = defaultValue
            }
            override fun contains(key: String): Boolean {
                return hashMap[key] != null
            }
            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getValue(
                key: String,
                valueType: Class<T>
            ): T? = hashMap[defaultKey] as T?

            override fun <T : Any> setValue(
                key: String,
                valueType: Class<T>,
                value: T?
            ) {
                hashMap[defaultKey] = value as Any
            }
        }
    }

    /**
     * Mocks a permission check result.
     *
     * @param application The mock application.
     * @param permission The permission to check.
     * @param value Whether the permission should be granted.
     */
    fun makePermissionPass(application: Application, permission: String, value: Boolean) {
        whenever(application.checkPermission(permission, 0, 0))
            .thenReturn(if (value) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED)
    }
}