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

package com.android.settingslib.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.theme.R

class SettingsTitlelessPreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource =
            if (SettingsThemeHelper.isExpressiveTheme(context)) {
                R.layout.settingslib_expressive_preference_category_no_title
            } else {
                R.layout.settingslib_preference_category_no_title
            }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.setDividerAllowedAbove(false)
        holder.setDividerAllowedBelow(false)
    }
}
