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

package com.android.systemui.statusbar.notification.row

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.data.repository.FakeStatusBarModeRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeadsUpStyleProviderImplTest : SysuiTestCase() {

    private val primaryDisplayId = 0
    private val secondaryDisplayId = 1

    private val statusBarModeRepository = FakeStatusBarModeRepository()
    private val defaultDisplayRepository = statusBarModeRepository.forDisplay(primaryDisplayId)
    private val secondaryDisplayRepository = statusBarModeRepository.forDisplay(secondaryDisplayId)

    private val headsUpStyleProvider = HeadsUpStyleProviderImpl(statusBarModeRepository)

    @Test
    @DisableFlags(Flags.FLAG_HEADS_UP_STYLE_PROVIDER_DISPLAY_AWARE)
    fun shouldApplyCompactStyle_flagDisabled_defaultDisplayInImmersiveMode_returnsTrue() {
        defaultDisplayRepository.isInFullscreenMode.value = true

        val result = headsUpStyleProvider.shouldApplyCompactStyle(secondaryDisplayId)

        assertThat(result).isTrue()
    }

    @Test
    @DisableFlags(Flags.FLAG_HEADS_UP_STYLE_PROVIDER_DISPLAY_AWARE)
    fun shouldApplyCompactStyle_flagDisabled_defaultDisplayNotInImmersiveMode_returnsFalse() {
        defaultDisplayRepository.isInFullscreenMode.value = false

        val result = headsUpStyleProvider.shouldApplyCompactStyle(secondaryDisplayId)

        assertThat(result).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_HEADS_UP_STYLE_PROVIDER_DISPLAY_AWARE)
    fun shouldApplyCompactStyle_flagDisabled_secondaryDisplayImmersive_usesDefaultAndReturnsFalse() {
        secondaryDisplayRepository.isInFullscreenMode.value = true
        defaultDisplayRepository.isInFullscreenMode.value = false

        val result = headsUpStyleProvider.shouldApplyCompactStyle(secondaryDisplayId)

        assertThat(result).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_HEADS_UP_STYLE_PROVIDER_DISPLAY_AWARE)
    fun shouldApplyCompactStyle_flagEnabled_primaryDisplayInImmersiveMode_returnsTrue() {
        defaultDisplayRepository.isInFullscreenMode.value = true

        val result = headsUpStyleProvider.shouldApplyCompactStyle(primaryDisplayId)

        assertThat(result).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_HEADS_UP_STYLE_PROVIDER_DISPLAY_AWARE)
    fun shouldApplyCompactStyle_flagEnabled_primaryDisplayNotInImmersiveMode_returnsFalse() {
        defaultDisplayRepository.isInFullscreenMode.value = false

        val result = headsUpStyleProvider.shouldApplyCompactStyle(primaryDisplayId)

        assertThat(result).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_HEADS_UP_STYLE_PROVIDER_DISPLAY_AWARE)
    fun shouldApplyCompactStyle_flagEnabled_secondaryDisplayInImmersiveMode_returnsTrue() {
        secondaryDisplayRepository.isInFullscreenMode.value = true

        val result = headsUpStyleProvider.shouldApplyCompactStyle(secondaryDisplayId)

        assertThat(result).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_HEADS_UP_STYLE_PROVIDER_DISPLAY_AWARE)
    fun shouldApplyCompactStyle_flagEnabled_secondaryDisplayNotInImmersiveMode_returnsFalse() {
        secondaryDisplayRepository.isInFullscreenMode.value = false

        val result = headsUpStyleProvider.shouldApplyCompactStyle(secondaryDisplayId)

        assertThat(result).isFalse()
    }
}
