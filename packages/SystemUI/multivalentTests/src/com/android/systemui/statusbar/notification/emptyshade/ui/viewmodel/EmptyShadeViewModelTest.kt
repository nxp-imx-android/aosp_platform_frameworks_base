/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.emptyshade.ui.viewmodel

import android.app.NotificationManager.Policy
import android.content.res.Configuration
import android.os.LocaleList
import android.platform.test.flag.junit.FlagsParameterization
import android.provider.Settings
import android.service.notification.ZenPolicy.VISUAL_EFFECT_NOTIFICATION_LIST
import androidx.test.filters.SmallTest
import com.android.settingslib.notification.data.repository.updateNotificationPolicy
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shared.settings.data.repository.fakeSecureSettingsRepository
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.policy.data.repository.zenModeRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@SmallTest
class EmptyShadeViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos().apply { useUnconfinedTestDispatcher() }
    private val fakeConfigurationRepository = kosmos.fakeConfigurationRepository

    /** Backup of the current locales, to be restored at the end of the test if they are changed. */
    private lateinit var originalLocales: LocaleList

    private val underTest by lazy { kosmos.emptyShadeViewModel }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        originalLocales = context.resources.configuration.locales
        updateLocales(LocaleList(Locale.US))
    }

    @After
    fun tearDown() {
        // Make sure we restore the original locale even if a test fails after changing it
        updateLocales(originalLocales)
    }

    @Test
    fun areNotificationsHiddenInShade_true() =
        kosmos.runTest {
            val hidden by collectLastValue(underTest.areNotificationsHiddenInShade)

            zenModeRepository.updateNotificationPolicy(
                suppressedVisualEffects = Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST
            )
            zenModeRepository.updateZenMode(Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS)

            assertThat(hidden).isTrue()
        }

    @Test
    fun areNotificationsHiddenInShade_false() =
        kosmos.runTest {
            val hidden by collectLastValue(underTest.areNotificationsHiddenInShade)

            zenModeRepository.updateNotificationPolicy(
                suppressedVisualEffects = Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST
            )
            zenModeRepository.updateZenMode(Settings.Global.ZEN_MODE_OFF)

            assertThat(hidden).isFalse()
        }

    @Test
    fun hasFilteredOutSeenNotifications_true() =
        kosmos.runTest {
            val hasFilteredNotifs by collectLastValue(underTest.hasFilteredOutSeenNotifications)
            activeNotificationListRepository.hasFilteredOutSeenNotifications.value = true
            assertThat(hasFilteredNotifs).isTrue()
        }

    @Test
    fun hasFilteredOutSeenNotifications_false() =
        kosmos.runTest {
            val hasFilteredNotifs by collectLastValue(underTest.hasFilteredOutSeenNotifications)
            activeNotificationListRepository.hasFilteredOutSeenNotifications.value = false
            assertThat(hasFilteredNotifs).isFalse()
        }

    @Test
    fun text_changesWhenLocaleChanges() =
        kosmos.runTest {
            val text by collectLastValue(underTest.text)

            zenModeRepository.updateNotificationPolicy(
                suppressedVisualEffects = Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST
            )
            zenModeRepository.updateZenMode(Settings.Global.ZEN_MODE_OFF)
            assertThat(text).isEqualTo("No notifications")

            updateLocales(LocaleList(Locale.GERMAN))
            assertThat(text).isEqualTo("Keine Benachrichtigungen")

            // Make sure we restore the original locales
            updateLocales(originalLocales)
        }

    @Test
    fun text_reflectsModesHidingNotifications() =
        kosmos.runTest {
            val text by collectLastValue(underTest.text)

            assertThat(text).isEqualTo("No notifications")

            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("Do not disturb")
                    .setName("Do not disturb")
                    .setActive(true)
                    .setVisualEffect(VISUAL_EFFECT_NOTIFICATION_LIST, /* allowed= */ false)
                    .build()
            )
            assertThat(text).isEqualTo("Notifications paused by Do not disturb")

            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("Work")
                    .setName("Work")
                    .setActive(true)
                    .setVisualEffect(VISUAL_EFFECT_NOTIFICATION_LIST, /* allowed= */ false)
                    .build()
            )
            assertThat(text).isEqualTo("Notifications paused by Do not disturb and one other mode")

            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("Gym")
                    .setName("Gym")
                    .setActive(true)
                    .setVisualEffect(VISUAL_EFFECT_NOTIFICATION_LIST, /* allowed= */ false)
                    .build()
            )
            assertThat(text).isEqualTo("Notifications paused by Do not disturb and 2 other modes")

            zenModeRepository.deactivateMode("Do not disturb")
            zenModeRepository.deactivateMode("Work")
            assertThat(text).isEqualTo("Notifications paused by Gym")
        }

    @Test
    fun footer_isVisibleWhenSeenNotifsAreFilteredOut() =
        kosmos.runTest {
            val footerVisible by collectLastValue(underTest.footer.isVisible)

            activeNotificationListRepository.hasFilteredOutSeenNotifications.value = false
            assertThat(footerVisible).isFalse()

            activeNotificationListRepository.hasFilteredOutSeenNotifications.value = true
            assertThat(footerVisible).isTrue()
        }

    @Test
    fun onClick_whenHistoryDisabled_leadsToSettingsPage() =
        kosmos.runTest {
            val onClick by collectLastValue(underTest.onClick)

            fakeSecureSettingsRepository.setInt(Settings.Secure.NOTIFICATION_HISTORY_ENABLED, 0)

            assertThat(onClick?.targetIntent?.action)
                .isEqualTo(Settings.ACTION_NOTIFICATION_SETTINGS)
            assertThat(onClick?.backStack).isEmpty()
        }

    @Test
    fun onClick_whenHistoryEnabled_leadsToHistoryPage() =
        kosmos.runTest {
            val onClick by collectLastValue(underTest.onClick)

            fakeSecureSettingsRepository.setInt(Settings.Secure.NOTIFICATION_HISTORY_ENABLED, 1)

            assertThat(onClick?.targetIntent?.action)
                .isEqualTo(Settings.ACTION_NOTIFICATION_HISTORY)
            assertThat(onClick?.backStack?.map { it.action })
                .containsExactly(Settings.ACTION_NOTIFICATION_SETTINGS)
        }

    @Test
    fun onClick_whenOneModeHidingNotifications_leadsToModeSettings() =
        kosmos.runTest {
            val onClick by collectLastValue(underTest.onClick)
            runCurrent()

            zenModeRepository.addMode(
                TestModeBuilder()
                    .setId("ID")
                    .setActive(true)
                    .setVisualEffect(VISUAL_EFFECT_NOTIFICATION_LIST, /* allowed= */ false)
                    .build()
            )

            assertThat(onClick?.targetIntent?.action)
                .isEqualTo(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS)
            assertThat(
                    onClick?.targetIntent?.extras?.getString(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID)
                )
                .isEqualTo("ID")
            assertThat(onClick?.backStack?.map { it.action })
                .containsExactly(Settings.ACTION_ZEN_MODE_SETTINGS)
        }

    @Test
    fun onClick_whenMultipleModesHidingNotifications_leadsToGeneralModesSettings() =
        kosmos.runTest {
            val onClick by collectLastValue(underTest.onClick)

            zenModeRepository.addMode(
                TestModeBuilder()
                    .setActive(true)
                    .setVisualEffect(VISUAL_EFFECT_NOTIFICATION_LIST, /* allowed= */ false)
                    .build()
            )
            zenModeRepository.addMode(
                TestModeBuilder()
                    .setActive(true)
                    .setVisualEffect(VISUAL_EFFECT_NOTIFICATION_LIST, /* allowed= */ false)
                    .build()
            )

            assertThat(onClick?.targetIntent?.action).isEqualTo(Settings.ACTION_ZEN_MODE_SETTINGS)
            assertThat(onClick?.backStack).isEmpty()
        }

    private fun updateLocales(locales: LocaleList) {
        val configuration = Configuration()
        configuration.setLocales(locales)
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        fakeConfigurationRepository.onConfigurationChange(configuration)
    }
}
