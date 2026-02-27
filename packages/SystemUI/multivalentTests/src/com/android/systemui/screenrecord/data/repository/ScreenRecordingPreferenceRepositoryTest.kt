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

package com.android.systemui.screenrecord.data.repository

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenRecordingPreferenceRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val underTest by lazy { kosmos.screenRecordingPreferenceRepository }

    @Test
    fun setShouldShowTaps_true_updatesSettings() {
        underTest.setShouldShowTaps(true)

        val value = Settings.System.getInt(context.contentResolver, Settings.System.SHOW_TOUCHES)
        assertThat(value).isEqualTo(1)
    }

    @Test
    fun setShouldShowTaps_false_updatesSettings() {
        underTest.setShouldShowTaps(false)

        val value = Settings.System.getInt(context.contentResolver, Settings.System.SHOW_TOUCHES)
        assertThat(value).isEqualTo(0)
    }

    @Test
    fun setShouldShowSeconds_true_updatesSettings() {
        underTest.setShouldShowSeconds(true)

        val value = Settings.Secure.getInt(context.contentResolver, Clock.CLOCK_SECONDS)
        assertThat(value).isEqualTo(1)
    }

    @Test
    fun setShouldShowSeconds_false_updatesSettings() {
        underTest.setShouldShowSeconds(false)

        val value = Settings.Secure.getInt(context.contentResolver, Clock.CLOCK_SECONDS)
        assertThat(value).isEqualTo(0)
    }

    @Test
    fun maybeRestoreSetting_restoresOriginalTapsOnlyIfChanged() {
        Settings.System.putInt(context.contentResolver, Settings.System.SHOW_TOUCHES, 1)

        underTest.setShouldShowTaps(false)
        assertThat(Settings.System.getInt(context.contentResolver, Settings.System.SHOW_TOUCHES))
            .isEqualTo(0)

        underTest.maybeRestoreSetting()
        assertThat(Settings.System.getInt(context.contentResolver, Settings.System.SHOW_TOUCHES))
            .isEqualTo(1)
    }

    @Test
    fun maybeRestoreSetting_restoresOriginalSecondsOnlyIfChanged() {
        Settings.Secure.putInt(context.contentResolver, Clock.CLOCK_SECONDS, 1)

        underTest.setShouldShowSeconds(false)
        assertThat(Settings.Secure.getInt(context.contentResolver, Clock.CLOCK_SECONDS))
            .isEqualTo(0)

        underTest.maybeRestoreSetting()
        assertThat(Settings.Secure.getInt(context.contentResolver, Clock.CLOCK_SECONDS))
            .isEqualTo(1)
    }

    @Test
    fun setShouldShowTaps_doesNotSaveOriginalIfAlreadySaving() {
        Settings.System.putInt(context.contentResolver, Settings.System.SHOW_TOUCHES, 1)

        underTest.setShouldShowTaps(false)

        underTest.setShouldShowTaps(true)

        underTest.maybeRestoreSetting()
        assertThat(Settings.System.getInt(context.contentResolver, Settings.System.SHOW_TOUCHES))
            .isEqualTo(1)
    }
}
