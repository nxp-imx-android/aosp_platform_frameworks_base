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

package com.android.server.contentrestriction

import android.os.UserHandle
import android.util.ArraySet
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [ContentRestrictionSettings].
 *
 * Run with `atest ContentRestrictionSettingsTest`.
 */
@RunWith(AndroidJUnit4::class)
class ContentRestrictionSettingsTest {

    private lateinit var mContentRestrictionSettings: ContentRestrictionSettings
    private lateinit var tempContentRestrictionDir: File

    @Before
    fun setUp() {
        // Creating a temporary folder to enable access.
        tempContentRestrictionDir = Files.createTempDirectory("tempContentRestrictionFolder").toFile()
        mContentRestrictionSettings = ContentRestrictionSettings(tempContentRestrictionDir)
    }

    @Test
    fun saveAndLoadContentRestrictionUserData_oneUser_retrievesUserDataCorrectly() {
        // Get and set user data
        mContentRestrictionSettings.updateUserData(1, true)

        // Save, change and load user data
        mContentRestrictionSettings.saveUserData()
        mContentRestrictionSettings.updateUserData(1, false)
        val newSettings = ContentRestrictionSettings(tempContentRestrictionDir)

        // Check if user data was loaded correctly
        newSettings.verifyUserData(1, true)
    }

    @Test
    fun saveAndLoadContentRestrictionUserData_manyUsers_retrievesUserDataCorrectly() {
        // Get and set user data
        mContentRestrictionSettings.updateUserData(1, true)
        mContentRestrictionSettings.updateUserData(2, true)
        mContentRestrictionSettings.updateUserData(3, false)
        mContentRestrictionSettings.updateUserData(4, false)

        // Save, change and load user data
        mContentRestrictionSettings.saveUserData()
        mContentRestrictionSettings.updateUserData(1, false)
        mContentRestrictionSettings.updateUserData(2, true)
        mContentRestrictionSettings.updateUserData(3, true)
        mContentRestrictionSettings.updateUserData(4, false)
        val newSettings = ContentRestrictionSettings(tempContentRestrictionDir)

        // Check if user data was loaded correctly
        newSettings.verifyUserData(1, true)
        newSettings.verifyUserData(2, true)
        newSettings.verifyUserData(3, false)
        newSettings.verifyUserData(4, false)
    }

    @Test
    fun removeAndGetUserData_returnsNewInstanceOfUserData() {
        // Get and set user data
        mContentRestrictionSettings.updateUserData(1, true)

        // Remove user data and get a new instance
        mContentRestrictionSettings.removeUserData(1)
        val newUserData = mContentRestrictionSettings.getUserData(1)

        // Check that user data is not the old instance and has default values
        assertThat(newUserData).isNotSameInstanceAs(mContentRestrictionSettings.getUserData(1))
        assertThat(newUserData.contentRestrictionEnabled).isFalse()
    }

    @Test
    fun saveAndLoadContentRestrictionUserData_hasRoleHolders_retrievesUserDataCorrectly() {
        // Get and set user data
        val roleHolders = ArraySet<String>(setOf("package1", "package2", "package3"))
        mContentRestrictionSettings.updateUserData(1, true, roleHolders)

        // Save, change and load user data
        mContentRestrictionSettings.saveUserData()
        mContentRestrictionSettings.updateUserData(1, false)
        val newSettings = ContentRestrictionSettings(tempContentRestrictionDir)

        // Check if user data was loaded correctly
        newSettings.verifyUserData(1, true, roleHolders)
    }

    @Test
    fun loadUserData_withUnknownTag_skipsTagAndLoadsCorrectly() {
        // TODO(b/465618368): Transition to a XML resource file.
        val malformedFile = File(tempContentRestrictionDir, "contentrestriction_settings.xml")
        malformedFile.writeText(
            """
            <contentrestriction_data>
                <contentrestriction_user_data
                    user_id="1"
                    contentrestriction_enabled="true"
                    contentrestriction_app_package="package1"
                    <unknown_tag>some value</unknown_tag>
                </contentrestriction_user_data>
            </contentrestriction_data>
            """.trimIndent()
        )

        mContentRestrictionSettings.loadUserData()

        mContentRestrictionSettings.verifyUserData(1, true)
    }
}

private fun ContentRestrictionSettings.updateUserData(
    userId: Int,
    enabled: Boolean,
    roleHolders: ArraySet<String> = ArraySet<String>(),
) {
    getUserData(userId).let {
        it.contentRestrictionEnabled = enabled
        it.contentRestrictionRoleHolders = roleHolders
    }
}

private fun ContentRestrictionSettings.verifyUserData(
    userId: Int,
    enabled: Boolean,
    roleHolders: ArraySet<String> = ArraySet<String>(),
) {
    getUserData(userId).let {
        assertThat(it.contentRestrictionEnabled).isEqualTo(enabled)
        assertThat(it.contentRestrictionRoleHolders).containsExactlyElementsIn(roleHolders)
    }
}
