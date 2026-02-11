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
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.catalyst.flags.Flags
import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.android.settingslib.ipc.ApiPermissionChecker
import com.android.settingslib.metadata.PreferenceCategory
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceScreenCoordinate
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.UI_ONLY_PREFERENCE
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.robotests.R
import com.android.settingslib.testutils.GraphTestUtils
import com.android.settingslib.testutils.GraphTestUtils.createSimplePreference
import com.android.settingslib.testutils.GraphTestUtils.setRegistryFactories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@EnableFlags(Flags.FLAG_CATALYST_USE_KEY_PARAMETERS)
class GetPreferenceGraphApiHandlerTest {

    val application = spy(ApplicationProvider.getApplicationContext<Application>()!!)
    val context = application as Context

    private val dummyId = 0

    private val getPreferenceGraphApiHandler = GetPreferenceGraphApiHandler(
        dummyId,
        ApiPermissionChecker.alwaysAllow()
    )

    private fun invokeWithFlags(
        screenKey: String?,
        flags: Int,
    ) = runBlocking {
            getPreferenceGraphApiHandler.invoke(
                application,
                dummyId,
                dummyId,
                GetPreferenceGraphRequest(
                    screens = if (screenKey == null) setOf() else
                        setOf(PreferenceScreenCoordinate(screenKey)),
                    flags = flags
                ),
            )
        }

    @After
    fun tearDown() {
        ShadowBuild.reset()
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithNonUiPreferences_returnsFullHierarchyProto() {
        setRegistryFactories(
            simpleScreen
        )

        val responseProto = invokeWithFlags(
            "simple_screen_key",
            PreferenceGetterFlags.METADATA
        )
        assertThat(responseProto.screensMap).hasSize(1)

        val screen = responseProto.screensMap["simple_screen_key"]!!.root
        assertThat(screen.preferenceOrNull?.key).isEqualTo("simple_screen_key")
        assertThat(screen.preferencesList).hasSize(2)
        assertThat(screen.preferencesList[0].preferenceOrNull?.key).isEqualTo("preference_1")

        val category1Proto = screen.preferencesList[1].group
        assertThat(category1Proto.preferenceOrNull?.key).isEqualTo("preference_category_1")
        assertThat(category1Proto.preferencesList).hasSize(2)
        assertThat(category1Proto.preferencesList[0].preferenceOrNull?.key).isEqualTo("preference_2")

        val category2Proto = category1Proto.preferencesList[1].group
        assertThat(category2Proto.preferenceOrNull?.key).isEqualTo("preference_category_2")
        assertThat(category2Proto.preferencesList).hasSize(1)
        assertThat(category2Proto.preferencesList[0].preferenceOrNull?.key).isEqualTo("preference_3")
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithUiPreferences_NonDebuggableBuild_excludesUiOnlyPreferences() {
        // makes build non-debuggable
        ShadowBuild.setType("user")
        setRegistryFactories(
            screenWithUiOnlyPreferences
        )

        val responseProto = invokeWithFlags(
            "screen_key_with_ui_only_preferences",
            PreferenceGetterFlags.METADATA
        )

        screenWithUiPreferencesProtoHasOnlyNonUiOnly(responseProto)
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithUiPreferences_DebuggableBuild_NoExcludeSetting_includesAllPreferences() {
        // makes build debuggable
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
            0
        )
        setRegistryFactories(
            screenWithUiOnlyPreferences
        )

        val responseProto = invokeWithFlags(
            "screen_key_with_ui_only_preferences",
            PreferenceGetterFlags.METADATA
        )

        screenWithUiPreferencesProtoHasAllPreferences(responseProto)
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithUiPreferences_DebuggableBuild_ExcludeSetting_includesAllPreferences() {
        // makes build debuggable
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
            1
        )
        setRegistryFactories(
            screenWithUiOnlyPreferences
        )
        val responseProto = invokeWithFlags(
            "screen_key_with_ui_only_preferences",
            PreferenceGetterFlags.METADATA
        )
        screenWithUiPreferencesProtoHasOnlyNonUiOnly(responseProto)
    }

    @Test
    fun invoke_withRequestMetadata_onScreenWithUiPreferences_DebuggableBuild_MissingExcludeSettings_excludesUiOnlyPreferences() {
        // makes build debuggable
        ShadowBuild.setType("userdebug")
        Settings.Global.putString(
            context.contentResolver,
            "com.android.settings.EXCLUDE_UI_ONLY_PREFERENCES",
            null
        )
        setRegistryFactories(
            screenWithUiOnlyPreferences
        )
        val responseProto = invokeWithFlags(
            "screen_key_with_ui_only_preferences",
            PreferenceGetterFlags.METADATA
        )
        screenWithUiPreferencesProtoHasOnlyNonUiOnly(responseProto)
    }

    private fun screenWithUiPreferencesProtoHasOnlyNonUiOnly(responseProto: PreferenceGraphProto) {
        assertThat(responseProto.screensMap).hasSize(1)

        val screen = responseProto.screensMap["screen_key_with_ui_only_preferences"]!!.root
        assertThat(screen.preferenceOrNull?.key).isEqualTo("screen_key_with_ui_only_preferences")
        assertThat(screen.preferencesList).hasSize(2)
        // Ui only preference does not exist in proto
        assertThat(screen.preferencesList[0].preferenceOrNull).isNull()
        assertThat(screen.preferencesList[0].groupOrNull).isNull()

        val uiOnlyCategory = screen.preferencesList[1].group
        // Ui only category is excluded
        assertThat(uiOnlyCategory.preferenceOrNull).isNull()
        assertThat(uiOnlyCategory.preferencesList).hasSize(2)
        assertThat(uiOnlyCategory.preferencesList[0].preference.key).isEqualTo("preference_2")

        val uiOnlyCategory2 = uiOnlyCategory.preferencesList[1].group
        assertThat(uiOnlyCategory2.preferenceOrNull).isNull()
        assertThat(uiOnlyCategory2.preferencesList).hasSize(2)
        // Non-UI only preference appears
        assertThat(uiOnlyCategory2.preferencesList[0].preference.key).isEqualTo("preference_3")
        // Ui only preference 4
        assertThat(uiOnlyCategory2.preferencesList[1].preferenceOrNull).isNull()
        assertThat(uiOnlyCategory2.preferencesList[1].groupOrNull).isNull()
    }

    private fun screenWithUiPreferencesProtoHasAllPreferences(responseProto: PreferenceGraphProto) {
        assertThat(responseProto.screensMap).hasSize(1)

        val screen = responseProto.screensMap["screen_key_with_ui_only_preferences"]!!.root
        assertThat(screen.preferenceOrNull?.key).isEqualTo("screen_key_with_ui_only_preferences")
        assertThat(screen.preferencesList).hasSize(2)
        // Ui only preference is included
        assertThat(screen.preferencesList[0].preferenceOrNull?.key).isEqualTo(uiOnlyPreference1.key)
        assertThat(screen.preferencesList[0].groupOrNull).isNull()

        val uiOnlyCategoryProto = screen.preferencesList[1].group
        // Ui only category is included
        assertThat(uiOnlyCategoryProto.preferenceOrNull?.key).isEqualTo(uiOnlyCategory.key)
        assertThat(uiOnlyCategoryProto.preferencesList).hasSize(2)
        assertThat(uiOnlyCategoryProto.preferencesList[0].preference.key).isEqualTo(preference2.key)

        val uiOnlyCategory2Proto = uiOnlyCategoryProto.preferencesList[1].group
        assertThat(uiOnlyCategory2Proto.preferenceOrNull?.key).isEqualTo(uiOnlyCategory2.key)
        assertThat(uiOnlyCategory2Proto.preferencesList).hasSize(2)
        // Non-UI only preference is included
        assertThat(uiOnlyCategory2Proto.preferencesList[0].preference.key).isEqualTo(preference3.key)
        // Ui only preference is included
        assertThat(uiOnlyCategory2Proto.preferencesList[1].preferenceOrNull?.key).isEqualTo(uiOnlyPreference4.key)
        assertThat(uiOnlyCategory2Proto.preferencesList[1].groupOrNull).isNull()
    }


    val preference1 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "preference_1",
            purpose = R.string.preference_purpose,
        )
    )

    val uiOnlyPreference1 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "ui_only_preference_1",
            purpose = R.string.preference_purpose,
            isUiOnly = true
        )
    )
    val preference2 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "preference_2",
            purpose = R.string.preference_purpose
        )
    )

    val preference3 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "preference_3",
            purpose = R.string.preference_purpose
        )
    )

    val uiOnlyPreference4 = createSimplePreference(
        preferenceConfig = GraphTestUtils.PreferenceConfig(
            key = "ui_only_preference_4",
            purpose = R.string.preference_purpose,
            isUiOnly = true
        )
    )

    val uiOnlyCategory = object: PreferenceCategory(
        key = "ui_only_preference_category",
        purpose = R.string.preference_purpose,
        title = 0
    ) {
        override fun tags(context: Context) =
            arrayOf(UI_ONLY_PREFERENCE)
    }

    val uiOnlyCategory2 = object: PreferenceCategory(
        key = "ui_only_preference_category_2",
        purpose = R.string.preference_purpose,
        title = 0
    ) {
        override fun tags(context: Context) =
            arrayOf(UI_ONLY_PREFERENCE)
    }

    val category = object: PreferenceCategory(
        key = "preference_category_1",
        purpose = R.string.preference_purpose,
        title = 0
    ){}

    val category2 = object: PreferenceCategory(
        key = "preference_category_2",
        purpose = R.string.preference_purpose,
        title = 0
    ){}

     val simpleScreen = object : PreferenceScreenMetadata {
        override fun fragmentClass(): Class<out Fragment>? = null

        override fun getPreferenceHierarchy(
            context: Context,
            coroutineScope: CoroutineScope
        ): PreferenceHierarchy = preferenceHierarchy(context) {
            +preference1
            +category += {
                +preference2
                +category2 += {
                    +preference3
                }
            }
        }

        override val key: String
            get() = "simple_screen_key"
        override val purpose: Int
            get() = R.string.preference_screen_purpose
    }

    val screenWithUiOnlyPreferences = object : PreferenceScreenMetadata {

        override fun fragmentClass(): Class<out Fragment>? = null
        override fun getPreferenceHierarchy(
            context: Context,
            coroutineScope: CoroutineScope
        ): PreferenceHierarchy = preferenceHierarchy(context) {
            +uiOnlyPreference1
            +uiOnlyCategory += {
                +preference2
                +uiOnlyCategory2 += {
                    +preference3
                    +uiOnlyPreference4
                }
            }
        }

        override val key: String
            get() = "screen_key_with_ui_only_preferences"
        override val purpose: Int
            get() = R.string.preference_screen_purpose
    }
}