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

package com.android.settingslib.graph

import android.content.Context

import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.types.AnyInt
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.metadata.preferencesapi.category.Category
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class PreferenceGraphBuilderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private open class TestPreference(
        override val sensitivityLevel: Int,
        private val writePermissions: Permissions? = null
    ) : PersistentPreference<Int> {
        override val bindingKey: String = "test_key"
        override val valueType: Class<Int> = Int::class.javaObjectType
        override val key: String = "test_key"
        override val purpose: Int = 2737

        override fun isPersistent(context: Context): Boolean = true

        override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int): Int? =
            ReadWritePermit.ALLOW

        override fun getWritePermissions(context: Context): Permissions? = writePermissions
    }

    @After
    fun tearDown() {
        ShadowBuild.reset()
    }

    @Test
    fun evalWritePermit_debuggableAndUnknownSensitivity_settingEnabled_returnsWritePermit_onUserdebug() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 1)
        val preference = TestPreference(SensitivityLevel.DO_NOT_EXPOSE)

        val result = preference.evalWritePermit(context, 0, 0)

        // Allowed because it is debuggable AND the flag is set
        assertThat(result).isEqualTo(ReadWritePermit.ALLOW)
    }

    @Test
    fun evalWritePermit_debuggableAndUnknownSensitivity_settingDisabled_returnsDisallow_onUserdebug() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 0)
        val preference = TestPreference(SensitivityLevel.DO_NOT_EXPOSE)

        val result = preference.evalWritePermit(context, 0, 0)

        // Disallowed because the flag is off, even on userdebug
        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    @Test
    fun evalWritePermit_notDebuggableAndUnknownSensitivity_settingEnabled_returnsDisallow() {
        ShadowBuild.setType("user")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 1)
        val preference = TestPreference(SensitivityLevel.DO_NOT_EXPOSE)

        val result = preference.evalWritePermit(context, 0, 0)

        // Disallowed because it is not a debuggable build
        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    @Test
    fun evalWritePermit_highSensitivity_settingEnabled_returnsDisallow() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 1)
        val preference = TestPreference(SensitivityLevel.DEEP_LINK_ONLY)

        val result = preference.evalWritePermit(context, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    private val screenMetadata = object : PreferenceScreenMetadata {
        override val bindingKey: String = "screen_key"
        override val key: String = "screen_key"
        override val purpose: Int = 0
        override fun fragmentClass(): Class<out Fragment>? = null

        override fun getPreferenceHierarchy(
            context: Context,
            coroutineScope: CoroutineScope
        ): PreferenceHierarchy = preferenceHierarchy(context) {}
    }

    @Test
    fun toProto_isApiPreference_includesScreenPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preconditions("screen_precondition_desc") { Allowed }
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get { execute { 42 } }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)
        assertThat(proto.getPreconditionsList).containsExactly("screen_precondition_desc")
    }

    @Test
    fun toProto_isApiPreference_includesPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    preconditions("precondition_desc") { Allowed }
                    get { execute { 42 } }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.getPreconditionsList).containsExactly("precondition_desc")
    }

    @Test
    fun toProto_isApiPreference_includesGetPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        preconditions("get_precondition_desc") { Allowed }
                        execute { 42 }
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.getPreconditionsList).containsExactly("get_precondition_desc")
    }

    @Test
    fun toProto_isApiPreference_includesSetPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        execute { 1 }
                    }
                    set {
                        preconditions("set_precondition_desc") { Allowed }
                        execute {}
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.setPreconditionsList).containsExactly("set_precondition_desc")
    }

    @Test
    fun toProto_isApiPreference_includesSetValuePreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        execute { 1 }
                    }
                    set {
                        valuePreconditions("set_value_precondition_desc") { _ -> Allowed }
                        execute {}
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.setPreconditionsList).containsExactly("set_value_precondition_desc")
    }

    @Test
    fun toProto_isPreferencesApiScreen_includesGetPreconditions() {
        val preference = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preconditions("get_precondition_desc") { Allowed }
            }
        }
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.getPreconditionsList).containsExactly("get_precondition_desc")
    }

    @Test
    fun toProto_apiPreferenceWithSetter_isWritable() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get { execute { 42 } }
                    set { execute { } }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.writable).isTrue()
    }

    @Test
    fun toProto_apiPreferenceWithoutSetter_isNotWritable() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get { execute { 42 } }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.writable).isFalse()
    }

    @Test
    fun toProto_preferencesApiScreen_isNotWritable() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {}
        val proto = screen.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.writable).isFalse()
    }

    @Test
    fun toProto_legacyPreference_isNotWritable() {
        val preference = TestPreference(SensitivityLevel.NO_SENSITIVITY)
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.writable).isFalse()
    }

    companion object {
        private const val SETTING_KEY = "com.android.settings.UNKNOWN_SENSITIVITY_IS_AVAILABLE"
    }
}
