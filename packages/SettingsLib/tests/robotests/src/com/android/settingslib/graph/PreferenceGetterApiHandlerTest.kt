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
import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.app.Application
import android.content.Context
import android.platform.test.annotations.EnableFlags
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.catalyst.flags.Flags
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.ipc.ApiPermissionChecker
import com.android.settingslib.metadata.PreferenceCoordinate
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.testutils.GraphTestUtils
import com.android.settingslib.testutils.GraphTestUtils.createPersistentPreference
import com.android.settingslib.testutils.GraphTestUtils.createScreen
import com.android.settingslib.testutils.GraphTestUtils.makePermissionPass
import com.android.settingslib.testutils.GraphTestUtils.setRegistryFactories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.robolectric.RobolectricTestRunner
import com.android.settingslib.robotests.R

@RunWith(RobolectricTestRunner::class)
@EnableFlags(Flags.FLAG_CATALYST_USE_KEY_PARAMETERS)
class PreferenceGetterApiHandlerTest {

    val application = spy(ApplicationProvider.getApplicationContext<Application>()!!)
    val context = application as Context

    private val preferenceGetterApiHandler = PreferenceGetterApiHandler(
        0,
        ApiPermissionChecker.alwaysAllow()
    )

    private fun invokeWithRequest(
        screenKey: String,
        key: String,
        flags: Int = PreferenceGetterFlags.ALL
    ): PreferenceGetterResponse =
        runBlocking {
            preferenceGetterApiHandler.invoke(
                application,
                0,
                0,
                PreferenceGetterRequest(
                    preferences = arrayOf(
                        PreferenceCoordinate(
                            screenKey = screenKey,
                            key = key,
                        )
                    ),
                    flags,
                )
            )
        }

    @Test
    fun invoke_onStringPreference_succeeds() {
        val persistentPreference = createPersistentPreference<String>(
            persistentPreferenceConfig = GraphTestUtils.PersistentPreferenceConfig(
                preferenceConfig = GraphTestUtils.PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                    isEnabled = true,
                    isAvailable = true,
                    isRestricted = false,
                ),
                valueType = String::class.javaObjectType,
                sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO,
                readPermission = INTERACT_ACROSS_USERS,
                readPermit = ReadWritePermit.ALLOW,
                writePermission = INTERACT_ACROSS_PROFILES,
                writePermit = ReadWritePermit.ALLOW,
                defaultValue = "hello"
            )
        )
        val screen = createScreen(
            screenConfig = GraphTestUtils.PreferenceScreenConfig(
                screenKey = "screen_key",
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(persistentPreference)
            )

        )
        setRegistryFactories(screen)
        makePermissionPass(application, INTERACT_ACROSS_USERS, true)
        makePermissionPass(application, INTERACT_ACROSS_PROFILES, true)

        val expectedProto = preferenceProto {
            key = "preference_key"
            enabled = true
            available = true
            indexable = true
            restricted = false
            purpose = R.string.preference_purpose
            persistent = true
            sensitivityLevel = SensitivityLevel.MUST_PROVIDE_UNDO
            readPermissions = Permissions.allOf(
                INTERACT_ACROSS_USERS
            ).toProto()
            writePermissions = Permissions.allOf(
                INTERACT_ACROSS_PROFILES
            ).toProto()
            readWritePermit = ReadWritePermit.make(
                readPermit = ReadWritePermit.ALLOW,
                writePermit = ReadWritePermit.ALLOW
            )
            value = preferenceValueProto {
                stringValue = "hello"
            }
            valueDescriptor = preferenceValueDescriptorProto {
                stringType = true
            }
            launchIntent = screen.getLaunchIntent(context, persistentPreference)?.toProto()
        }

        val actualResponse = invokeWithRequest("screen_key", "preference_key")
        assertThat(actualResponse.errors).isEmpty()
        val actualProto = actualResponse.preferences[
            PreferenceCoordinate("screen_key", "preference_key")
        ]
        assertThat(actualProto).isEqualTo(expectedProto)
    }
}