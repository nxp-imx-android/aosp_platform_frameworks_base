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

import android.Manifest.permission.INTERACT_ACROSS_PROFILES
import android.app.Application
import android.content.Context
import android.platform.test.annotations.EnableFlags
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.catalyst.flags.Flags
import com.android.settingslib.ipc.ApiPermissionChecker
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceScreenRegistry
import com.android.settingslib.testutils.GraphTestUtils.PersistentPreferenceConfig
import com.android.settingslib.testutils.GraphTestUtils.PreferenceConfig
import com.android.settingslib.testutils.GraphTestUtils.setRegistryFactories
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.testutils.GraphTestUtils.PreferenceScreenConfig
import com.android.settingslib.testutils.GraphTestUtils.createIntRangePreference
import com.android.settingslib.testutils.GraphTestUtils.createPersistentPreference
import com.android.settingslib.testutils.GraphTestUtils.createScreen
import com.android.settingslib.testutils.GraphTestUtils.createSimplePreference
import com.android.settingslib.testutils.GraphTestUtils.makePermissionPass
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild
import com.android.settingslib.robotests.R

@RunWith(RobolectricTestRunner::class)
@EnableFlags(Flags.FLAG_CATALYST_USE_KEY_PARAMETERS)
class PreferenceSetterApiHandlerTest {

    private val application = spy(ApplicationProvider.getApplicationContext<Application>()!!)
    private val context = application as Context
    private val dummyId = 0

    private fun <T : Any> getPreferenceValue(preference: PreferenceMetadata): T? {
        val persistentPreference = (preference as PersistentPreference<*>)
        @Suppress("UNCHECKED_CAST")
        return persistentPreference.storage(context).getValue(
            persistentPreference.key,
            persistentPreference.valueType as Class<T>
        )
    }

    private val preferenceSetterApiHandler = PreferenceSetterApiHandler(
        dummyId,
        ApiPermissionChecker.alwaysAllow()
    )

    private fun invokeWithRequest(screenKey: String, preferenceKey: String, value: Any): Int =
        runBlocking {
            val valueProto = when (value) {
                is Boolean -> preferenceValueProto {
                    booleanValue = value
                }

                is Int -> preferenceValueProto {
                    intValue = value
                }

                is String -> preferenceValueProto {
                    stringValue = value
                }

                is Float -> preferenceValueProto {
                    floatValue = value
                }

                else -> error("Not supported by setter")
            }
            return@runBlocking preferenceSetterApiHandler.invoke(
                application,
                dummyId,
                dummyId,
                PreferenceSetterRequest(
                    screenKey = screenKey,
                    keyParameters = null,
                    key = preferenceKey,
                    value = valueProto
                )
            )
        }

    @Before
    fun setUp() {
        setRegistryFactories()
        PreferenceScreenRegistry.defaultWritePermit = ReadWritePermit.DISALLOW
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
    }

    @After
    fun tearDown() {
        ShadowBuild.reset()
    }

    @Test
    fun invoke_onInexistentScreen_returnUnsupported() {
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(
                        createSimplePreference(
                            PreferenceConfig(
                                key = "preference_key",
                                purpose = R.string.preference_purpose
                            )
                        )
                    )
                )
            )
        )
        assertThat(
            invokeWithRequest("inexistent_screen", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.UNSUPPORTED)
    }

    @Test
    fun invoke_onInexistentPreference_returnsUnsupported() {
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(
                        createSimplePreference(
                            PreferenceConfig(
                                key = "preference_key",
                                purpose = R.string.preference_purpose
                            )
                        )
                    )
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "inexistent_preference", true)
        ).isEqualTo(PreferenceSetterResult.UNSUPPORTED)
    }

    @Test
    fun invoke_onNonPersistentPreference_returnsUnsupported() {
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(
                        createSimplePreference(
                            PreferenceConfig(
                                key = "preference_key",
                                purpose = R.string.preference_purpose
                            )
                        )
                    )
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.UNSUPPORTED)
    }

    @Test
    fun invoke_onDisabledPreference_returnsDisabled() {
        val disabledPreference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                    isEnabled = false
                )
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(
                        disabledPreference
                    )
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.DISABLED)
        assertThat(getPreferenceValue<Boolean>(disabledPreference)).isEqualTo(false)
    }

    @Test
    fun invoke_onRestrictedPreference_returnsRestricted() {
        val restrictedPreference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                    isRestricted = true
                )
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(restrictedPreference)
                )

            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.RESTRICTED)
        assertThat(getPreferenceValue<Boolean>(restrictedPreference)).isEqualTo(false)
    }

    @Test
    fun invoke_onUnavailablePreference_returnsUnavailable() {
        val unavailablePreference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                    isAvailable = false
                )
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(unavailablePreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.UNAVAILABLE)
        assertThat(getPreferenceValue<Boolean>(unavailablePreference)).isEqualTo(false)
    }

    @Test
    fun invoke_onIntPreferenceWithBooleanValueType_returnsInvalid() {
        val intPreference = createPersistentPreference<Int>(
            persistentPreferenceConfig = PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Int::class.javaObjectType,
                defaultValue = 3
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(intPreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.INVALID_REQUEST)
        assertThat(getPreferenceValue<Int>(intPreference)).isEqualTo(3)
    }

    @Test
    fun invoke_onHighSensitivityPreference_returnsDisallow() {
        val highSensitivityPreference = createPersistentPreference<Boolean>(
            persistentPreferenceConfig = PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                defaultValue = false,
                sensitivityLevel = SensitivityLevel.DEEP_LINK_ONLY,
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(highSensitivityPreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.DISALLOW)
        assertThat(getPreferenceValue<Boolean>(highSensitivityPreference)).isEqualTo(false)
    }

    @Test
    fun invoke_onUnknownSensitivityPreferenceAndNotDebuggable_returnsDisallow() {
        // makes build non-debuggable
        ShadowBuild.setType("user")
        val unknownSensitivityPreference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                defaultValue = false,
                sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(unknownSensitivityPreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.DISALLOW)
        assertThat(getPreferenceValue<Boolean>(unknownSensitivityPreference)).isEqualTo(false)
    }

    @Test
    fun invoke_onUnknownSensitivityPreferenceAndDebuggable_succeeds() {
        // makes build debuggable
        ShadowBuild.setType("userdebug")
        val unknownSensitivityPreference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                defaultValue = false,
                sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(unknownSensitivityPreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<Boolean>(unknownSensitivityPreference)).isEqualTo(true)
    }

    @Test
    fun invoke_onFailingPermission_returnsRequireAppPermission() {
        val preference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                writePermission = INTERACT_ACROSS_PROFILES,
                defaultValue = false
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(preference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, false)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.REQUIRE_APP_PERMISSION)
        assertThat(getPreferenceValue<Boolean>(preference)).isEqualTo(false)
    }

    @Test
    fun invoke_onPermissionPassing_returnsWritePermit() {
        val preference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                defaultValue = false,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.REQUIRE_USER_AGREEMENT
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(preference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.REQUIRE_USER_AGREEMENT)
        assertThat(getPreferenceValue<Boolean>(preference)).isEqualTo(false)
    }

    @Test
    fun invoke_onWritePermissionAbsent_returnsWritePermit() {
        val preference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                defaultValue = false,
                writePermission = null,
                writePermit = ReadWritePermit.REQUIRE_USER_AGREEMENT
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(preference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.REQUIRE_USER_AGREEMENT)
        assertThat(getPreferenceValue<Boolean>(preference)).isEqualTo(false)
    }

    @Test
    fun invoke_onWritePermitAbsent_returnsDefaultPermit() {
        PreferenceScreenRegistry.defaultWritePermit = ReadWritePermit.REQUIRE_USER_AGREEMENT
        val preference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                defaultValue = false,
                writePermission = null,
                writePermit = null,
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(preference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.REQUIRE_USER_AGREEMENT)
        assertThat(getPreferenceValue<Boolean>(preference)).isEqualTo(false)
    }

    @Test
    fun invoke_onSetToTrue_succeeds() {
        val preference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = false
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(preference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", true)
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<Boolean>(preference)).isEqualTo(true)
    }

    @Test
    fun invoke_onSetToFalse_succeeds() {
        val preference = createPersistentPreference<Boolean>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Boolean::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = true
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(preference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", false)
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<Boolean>(preference)).isEqualTo(false)
    }

    @Test
    fun invoke_onIntPreference_succeeds() {
        val intPreference = createPersistentPreference<Int>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Int::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.REQUIRES_CONFIRMATION,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = 4
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(intPreference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", 8)
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<Int>(intPreference)).isEqualTo(8)
    }

    @Test
    fun invoke_onIntPreferenceWithStringValueType_returnsInvalid() {
        val intPreference = createPersistentPreference<Int>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Int::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.REQUIRES_CONFIRMATION,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = 4
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(intPreference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", "hey")
        ).isEqualTo(PreferenceSetterResult.INVALID_REQUEST)
        assertThat(getPreferenceValue<Int>(intPreference)).isEqualTo(4)
    }

    // TODO (b/479126443) Enforce float values in request can only be performed on float preferences
    @Test
    fun invoke_onStringPreferenceWithFloatValueType_succeeds() {
        val stringPreference = createPersistentPreference<String>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = String::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.REQUIRES_CONFIRMATION,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = "hello"
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(stringPreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", 2.3f)
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<Float>(stringPreference)).isWithin(0.001f).of(2.3f)
    }

    @Test
    fun invoke_onIntRangeWithHigherValue_returnsInvalid() {
        val intRangePreference = createIntRangePreference(
            "preference_key",
            purpose = R.string.preference_purpose,
            minValue = 3,
            maxValue = 6,
            defaultValue = 4
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(intRangePreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", 10)
        ).isEqualTo(PreferenceSetterResult.INVALID_REQUEST)
        assertThat(getPreferenceValue<Int>(intRangePreference)).isEqualTo(4)
    }

    @Test
    fun invoke_onIntRangeWithLowerValue_returnsInvalid() {
        val intRangePreference = createIntRangePreference(
            "preference_key",
            purpose = R.string.preference_purpose,
            minValue = 3,
            maxValue = 6,
            defaultValue = 4
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(intRangePreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", 2)
        ).isEqualTo(PreferenceSetterResult.INVALID_REQUEST)
        assertThat(getPreferenceValue<Int>(intRangePreference)).isEqualTo(4)
    }

    @Test
    fun invoke_onIntRangeWithInRageValue_succeeds() {
        val intRangePreference = createIntRangePreference(
            "preference_key",
            purpose = R.string.preference_purpose,
            minValue = 3,
            maxValue = 6,
            defaultValue = 4
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(intRangePreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", 5)
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<Int>(intRangePreference)).isEqualTo(5)
    }

    @Test
    fun invoke_onFloatValueType_succeeds() {
        val floatPreference = createPersistentPreference<Float>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = Float::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.REQUIRES_CONFIRMATION,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = 4.5f
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(floatPreference)
                )
            )
        )
        assertThat(
            invokeWithRequest("screen_key", "preference_key", 2.3f)
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<Float>(floatPreference)).isWithin(0.001f).of(2.3f)
    }

    @Test
    fun invoke_onStringPreference_succeeds() {
        val stringPreference = createPersistentPreference<String>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = String::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = "hello"
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(stringPreference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", "there")
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<String>(stringPreference)).isEqualTo("there")
    }

    @Test
    fun invoke_onStringPreferenceWithFailingWritePermit_returnsWritePermit() {
        val stringPreference = createPersistentPreference<String>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = String::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.REQUIRE_USER_AGREEMENT,
                defaultValue = "hello"
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(stringPreference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", "there")
        ).isEqualTo(PreferenceSetterResult.REQUIRE_USER_AGREEMENT)

        assertThat(getPreferenceValue<String>(stringPreference)).isEqualTo("hello")
    }

    // TODO (b/479126443) Enforce int values in request can only be performed on int preferences
    @Test
    fun invoke_onStringPreferenceWithIntValueType_succeeds() {
        val stringPreference = createPersistentPreference<String>(
            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = String::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = "hello"
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(stringPreference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", 67)
        ).isEqualTo(PreferenceSetterResult.OK)
        assertThat(getPreferenceValue<Int>(stringPreference)).isEqualTo(67)
    }

    @Test
    fun invoke_onStringPreferenceWithError_returnsInternalError() {
        val stringPreference = createPersistentPreference<String>(

            PersistentPreferenceConfig(
                preferenceConfig = PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                ),
                valueType = String::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.REQUIRE_USER_AGREEMENT,
                defaultValue = "hello",
                throwsError = true,
            )
        )
        setRegistryFactories(
            createScreen(
                PreferenceScreenConfig(
                    screenKey = "screen_key",
                    purpose = R.string.preference_screen_purpose,
                    preferences = listOf(stringPreference)
                )
            )
        )
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)
        assertThat(
            invokeWithRequest("screen_key", "preference_key", "there")
        ).isEqualTo(PreferenceSetterResult.INTERNAL_ERROR)
        assertThat(getPreferenceValue<String>(stringPreference)).isEqualTo("hello")
    }
}