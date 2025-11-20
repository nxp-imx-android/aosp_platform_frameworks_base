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

package com.android.settingslib.metadata.apifirst

import android.content.Context
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.preferenceHierarchy
import kotlinx.coroutines.CoroutineScope

/**
 * Container for all information and preferences on a Settings screen which is intended to be
 * exposed via API using 2026 "Lightweight" way.
 */
abstract class ApiFirstPreferenceScreen() : PreferenceScreenMetadata {
    override fun isFlagEnabled(context: Context): Boolean = true

    override fun getPreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
    ): PreferenceHierarchy =
        preferenceHierarchy(context) {
            for (preference in preferences(context)) {
                +preference
            }
        }

    /** List of the preferences contained on the Settings screen. */
    abstract fun preferences(context: Context): List<ApiFirstPreference<*>>
}
