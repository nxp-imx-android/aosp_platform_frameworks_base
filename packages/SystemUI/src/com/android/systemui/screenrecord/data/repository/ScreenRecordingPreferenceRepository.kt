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

import android.annotation.SuppressLint
import android.app.Service.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import com.android.systemui.statusbar.policy.Clock

// This repository might be used in a separate process where we don't have access to the System UI
// dagger graph.
@SuppressLint("StaticSettingsProvider")
class ScreenRecordingPreferenceRepository(private val context: Context) {

    fun setShouldShowTaps(showTaps: Boolean) {
        val originalShowTapsSetting = getShowTaps()
        setShowTaps(showTaps)
        val hasTapsToRestore = sharedPreference().getBoolean(UPDATE_SHOW_TAPS, false)
        if (!hasTapsToRestore && showTaps != originalShowTapsSetting) {
            sharedPreference().edit {
                putBoolean(STORED_SHOW_TAPS_VALUE, originalShowTapsSetting)
                putBoolean(UPDATE_SHOW_TAPS, true)
            }
        }
    }

    fun setShouldShowSeconds(showSeconds: Boolean) {
        val hasSecondsToRestore = sharedPreference().getBoolean(UPDATE_SHOW_SECONDS, false)
        val originalShowSecondsSetting = getShowSeconds()
        setShowSeconds(showSeconds)
        if (!hasSecondsToRestore && showSeconds != originalShowSecondsSetting) {
            sharedPreference().edit {
                putBoolean(STORED_SHOW_SECONDS_VALUE, originalShowSecondsSetting)
                putBoolean(UPDATE_SHOW_SECONDS, true)
            }
        }
    }

    fun maybeRestoreSetting(): ScreenRecordingPreferenceRepository = apply {
        if (sharedPreference().getBoolean(UPDATE_SHOW_TAPS, false)) {
            restoreShowTapsSetting()
        }
        if (sharedPreference().getBoolean(UPDATE_SHOW_SECONDS, false)) {
            restoreShowSecondsSetting()
        }
    }

    private fun restoreShowTapsSetting() {
        setShowTaps(sharedPreference().getBoolean(STORED_SHOW_TAPS_VALUE, false))
        sharedPreference().edit { putBoolean(UPDATE_SHOW_TAPS, false) }
    }

    private fun setShowTaps(isOn: Boolean) {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SHOW_TOUCHES,
            if (isOn) 1 else 0,
        )
    }

    private fun getShowTaps(): Boolean {
        return Settings.System.getInt(context.contentResolver, Settings.System.SHOW_TOUCHES, 0) != 0
    }

    private fun restoreShowSecondsSetting() {
        setShowSeconds(sharedPreference().getBoolean(STORED_SHOW_SECONDS_VALUE, false))
        sharedPreference().edit { putBoolean(UPDATE_SHOW_SECONDS, false) }
    }

    private fun setShowSeconds(isOn: Boolean) {
        Settings.Secure.putInt(context.contentResolver, Clock.CLOCK_SECONDS, if (isOn) 1 else 0)
    }

    private fun getShowSeconds(): Boolean {
        return Settings.Secure.getInt(context.contentResolver, Clock.CLOCK_SECONDS, 0) != 0
    }

    private fun sharedPreference(): SharedPreferences {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE)
    }

    companion object {
        const val SHARED_PREFERENCES_NAME = "com.android.systemui.screenrecord"
        const val STORED_SHOW_TAPS_VALUE = "stored_show_taps_value"
        const val UPDATE_SHOW_TAPS = "update_show_taps"
        const val STORED_SHOW_SECONDS_VALUE = "stored_show_seconds_value"
        const val UPDATE_SHOW_SECONDS = "update_show_seconds"
    }
}
