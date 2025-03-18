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

package com.android.settingslib.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.segmentedbutton.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class SegmentedButtonPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {
    private var buttonGroup: MaterialButtonToggleGroup? = null
    private var buttonLabels: MutableList<TextView> = mutableListOf()
    private var buttonCheckedListener: MaterialButtonToggleGroup.OnButtonCheckedListener? = null

    // Data to be applied during onBindViewHolder
    private val buttonSetupData = mutableListOf<Triple<Int, String, Int>>() // (index, text, icon)
    private val buttonVisibilityData = mutableListOf<Pair<Int, Boolean>>() // (index, visibility)
    private val buttonEnableData = mutableListOf<Pair<Int, Boolean>>() // (index, enable)

    init {
        layoutResource = R.layout.settingslib_expressive_preference_segmentedbutton
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedBelow = false
        holder.isDividerAllowedAbove = false

        buttonGroup = holder.findViewById(R.id.button_group) as MaterialButtonToggleGroup?
        buttonLabels.add(holder.findViewById(R.id.button_1_text) as TextView)
        buttonLabels.add(holder.findViewById(R.id.button_2_text) as TextView)
        buttonLabels.add(holder.findViewById(R.id.button_3_text) as TextView)
        buttonLabels.add(holder.findViewById(R.id.button_4_text) as TextView)

        // Apply stored data
        applyButtonSetupData()
        applyButtonVisibilityData()
        applyButtonEnableData()
        buttonGroup?.apply {
            clearOnButtonCheckedListeners()
            buttonCheckedListener?.let { listener ->
                addOnButtonCheckedListener(listener)
            }
        }
    }

    fun setUpButton(index: Int, text: String, @DrawableRes icon: Int) {
        if (buttonGroup == null) {
            // Store data for later application
            buttonSetupData.add(Triple(index, text, icon))
        } else {
            // Apply data
            applyButtonSetupData(index, text, icon)
        }
    }

    fun setButtonVisibility(index: Int, visible: Boolean) {
        if (buttonGroup == null) {
            // Store data for later application
            buttonVisibilityData.add(Pair(index, visible))
        } else {
            // Apply data
            applyButtonVisibilityData(index, visible)
        }
    }

    fun setButtonEnabled(index: Int, enabled: Boolean) {
        if (buttonGroup == null) {
            // Store data for later application
            buttonEnableData.add(Pair(index, enabled))
        } else {
            // Apply data
            applyButtonEnableData(index, enabled)
        }
    }

    fun setOnButtonClickListener(listener: MaterialButtonToggleGroup.OnButtonCheckedListener) {
        buttonCheckedListener = listener
        notifyChanged()
    }

    fun removeOnButtonClickListener() {
        buttonCheckedListener = null
        notifyChanged()
    }

    private fun applyButtonSetupData() {
        for (config in buttonSetupData) {
            applyButtonSetupData(config.first, config.second, config.third)
        }
        buttonSetupData.clear() // Clear data after applying
    }

    private fun applyButtonVisibilityData() {
        for (config in buttonVisibilityData) {
            applyButtonVisibilityData(config.first, config.second)
        }
        buttonVisibilityData.clear() // Clear data after applying
    }

    private fun applyButtonEnableData() {
        for (config in buttonEnableData) {
            applyButtonEnableData(config.first, config.second)
        }
        buttonEnableData.clear() // Clear data after applying
    }

    private fun applyButtonSetupData(index: Int, text: String, @DrawableRes icon: Int) {
        if (index in 0 until buttonLabels.size) {
            (buttonGroup?.getChildAt(index) as? MaterialButton)?.setIconResource(icon)
            buttonLabels[index].text = text
        }
    }

    private fun applyButtonVisibilityData(index: Int, visible: Boolean) {
        if (index in 0 until buttonLabels.size) {
            (buttonGroup?.getChildAt(index) as? MaterialButton)?.visibility =
                if (visible) VISIBLE else GONE

            buttonLabels[index].visibility = if (visible) VISIBLE else GONE
        }
    }

    private fun applyButtonEnableData(index: Int, enabled: Boolean) {
        if (index in 0 until buttonLabels.size) {
            (buttonGroup?.getChildAt(index) as? MaterialButton)?.isEnabled = enabled
        }
    }
}