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
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class PreferenceGraphBuilderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class TestContext(base: Context) : ContextWrapper(base) {
        var permissionGranted = true

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
            return if (permissionGranted) PackageManager.PERMISSION_GRANTED
            else PackageManager.PERMISSION_DENIED
        }
    }

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
    fun evalWritePermit_debuggableAndUnknownSensitivity_returnsWritePermit_onUserdebug() {
        // Set Build.TYPE to "userdebug" to simulate a debuggable build
        ShadowBuild.setType("userdebug")
        val testContext = TestContext(context)
        val preference = TestPreference(SensitivityLevel.UNKNOWN_SENSITIVITY)

        val result = preference.evalWritePermit(testContext, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.ALLOW)
    }

    @Test
    fun evalWritePermit_debuggableAndUnknownSensitivity_returnsWritePermit_onEng() {
        // Set Build.TYPE to "eng" to simulate a debuggable build
        ShadowBuild.setType("eng")
        val testContext = TestContext(context)
        val preference = TestPreference(SensitivityLevel.UNKNOWN_SENSITIVITY)

        val result = preference.evalWritePermit(testContext, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.ALLOW)
    }

    @Test
    fun evalWritePermit_debuggableAndUnknownSensitivity_permissionCheckFails_returnsRequireAppPermission_onUserdebug() {
        ShadowBuild.setType("userdebug")
        val testContext = TestContext(context).apply { permissionGranted = false }
        val permissions = Permissions.allOf("test.permission")
        val preference = TestPreference(SensitivityLevel.UNKNOWN_SENSITIVITY, permissions)

        val result = preference.evalWritePermit(testContext, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.REQUIRE_APP_PERMISSION)
    }

    @Test
    fun evalWritePermit_notDebuggableAndUnknownSensitivity_returnsDisallow() {
        // Set Build.TYPE to "user" to simulate a non-debuggable build
        ShadowBuild.setType("user")
        val testContext = TestContext(context)
        val preference = TestPreference(SensitivityLevel.UNKNOWN_SENSITIVITY)

        val result = preference.evalWritePermit(testContext, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    @Test
    fun evalWritePermit_highSensitivity_returnsDisallow() {
        // Even on userdebug, HIGH_SENSITIVITY should return DISALLOW
        ShadowBuild.setType("userdebug")
        val testContext = TestContext(context)
        val preference = TestPreference(SensitivityLevel.HIGH_SENSITIVITY)

        val result = preference.evalWritePermit(testContext, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    @Test
    fun evalWritePermit_permissionCheckFails_returnsRequireAppPermission() {
        ShadowBuild.setType("user")
        val testContext = TestContext(context).apply { permissionGranted = false }
        val permissions = Permissions.allOf("test.permission")
        val preference = TestPreference(SensitivityLevel.NO_SENSITIVITY, permissions)

        val result = preference.evalWritePermit(testContext, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.REQUIRE_APP_PERMISSION)
    }

    @Test
    fun evalWritePermit_permissionCheckPasses_returnsWritePermit() {
        ShadowBuild.setType("user")
        val testContext = TestContext(context).apply { permissionGranted = true }
        val permissions = Permissions.allOf("test.permission")
        val preference = TestPreference(SensitivityLevel.NO_SENSITIVITY, permissions)

        val result = preference.evalWritePermit(testContext, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.ALLOW)
    }
}
