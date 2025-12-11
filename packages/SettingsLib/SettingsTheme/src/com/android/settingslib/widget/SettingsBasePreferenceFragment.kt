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

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.widget.theme.R

/** Base class for Settings to use PreferenceFragmentCompat */
abstract class SettingsBasePreferenceFragment : PreferenceFragmentCompat() {

    val footerDataMap = mutableMapOf<String, FooterData>()
    protected open val isPreferenceSpacingEnabled = true

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (SettingsThemeHelper.isExpressiveTheme(requireContext())) {
            // This null check is to fix b/412578060 on our side;
            // If this will be resolved in the future in PreferenceFragmentCompat this can be removed
            if (listView != null) {
                // Don't allow any divider in between the preferences in expressive design.
                setDivider(null)
            }
            if (isPreferenceSpacingEnabled) {
                listView?.addItemDecoration(MarginItemDecoration())
            }
        }

        preferenceScreen?.let { screen ->
            recursiveInitializePreferences(screen)
        }
    }

    override fun setPreferenceScreen(preferenceScreen: PreferenceScreen?) {
        super.setPreferenceScreen(preferenceScreen)
        if (preferenceScreen != null && view != null) {
            recursiveInitializePreferences(preferenceScreen)
        }
    }

    /**
     * Recursive helper to find preferences even inside Categories or nested Groups.
     */
    private fun recursiveInitializePreferences(group: PreferenceGroup) {
        val count = group.preferenceCount
        for (i in 0 until count) {
            val preference = group.getPreference(i)

            // 1. Initialize our custom preference
            if (preference is FragmentAttachablePreference) {
                preference.onFragmentViewCreated(viewLifecycleOwner)
            }

            // 2. If it's a group (like PreferenceCategory), dive deeper
            if (preference is PreferenceGroup) {
                recursiveInitializePreferences(preference)
            }
        }
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        if (SettingsThemeHelper.isExpressiveTheme(requireContext())) {
            return SettingsPreferenceGroupAdapter(preferenceScreen, footerDataMap)
        }
        return super.onCreateAdapter(preferenceScreen)
    }

    /**
     * Attaches a clickable footer to a preference and handles the UI refresh.
     *
     * @param preferenceKey The key of the [Preference] to attach the footer to.
     * @param text The text to display in the footer.
     * @param listener The [View.OnClickListener] to be invoked when the footer is clicked.
     */
     fun attachFooter(
        preferenceKey: String,
        text: CharSequence,
        listener: View.OnClickListener,
    ) {
        footerDataMap[preferenceKey] = FooterData(text, listener)

        val adapter = listView?.adapter as? SettingsPreferenceGroupAdapter

        if (adapter != null) {
            val preference = findPreference<Preference>(preferenceKey)

            if (preference != null) {
                adapter.notifyPreferenceChanged(preference)
            }
        }
    }

    /**
     * Attaches a clickable footer to a preference.
     *
     * @param preference The object [Preference] to attach the footer to.
     * @param text The text to display in the footer.
     * @param listener The [View.OnClickListener] to be invoked when the footer is clicked.
     */
    fun attachFooter(preference: Preference, text: CharSequence, listener: View.OnClickListener) {
        if (findPreference<Preference>(preference.key) == null) {
            return
        }
        attachFooter(preference.key, text, listener)
    }

    /** Handles the display of custom dialogs for preferences. */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        // Check if the preference implements the generic interface
        if (preference is FragmentAttachablePreference) {
            // Now ListDialogSwitcherPreference will execute its dialog-showing logic
            preference.displayCustomDialog(this, preference.key)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    internal class MarginItemDecoration() : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val viewHolder = parent.getChildViewHolder(view)
            val position = viewHolder.bindingAdapterPosition

            val dimensionResId =
                (parent.adapter as? SettingsPreferenceGroupAdapter)?.let { adapter ->
                    if (adapter.getItem(position) is ChainedMixin) {
                        R.dimen.settingslib_expressive_space_none
                    } else {
                        R.dimen.settingslib_expressive_space_extrasmall1
                    }
                }
                    ?: R.dimen.settingslib_expressive_space_extrasmall1 // Default if adapter is null or wrong type

            with(outRect) {
                bottom = view.resources.getDimensionPixelSize(dimensionResId)
            }
        }
    }
}
