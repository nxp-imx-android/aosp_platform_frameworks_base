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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.banner.R
import kotlin.math.max

/**
 * Custom PreferenceGroup that allows expanding and collapsing child [BannerMessagePreference]s.
 *
 * This group will always display the first BannerMessagePreference added to it. If more than one
 * banners are added, the remaining preferences will be hidden and a collapse button will show,
 * displaying the number of collapsed preferences. When expanded, a configurable collapse button
 * will be shown.
 *
 * This group can also have a simple subsection added, which is a titled preference category that
 * also contains banner preferences. This subsection will be shown and collapsed along with the
 * remaining banner preferences added directly to the group.
 */
class BannerMessagePreferenceGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    private var isExpanded = false
    private var expandPreference: NumberButtonPreference? = null
    private var collapsePreference: SectionButtonPreference? = null
    private var subsectionCategory: PreferenceCategory? = null
    private val childPreferences = mutableListOf<BannerMessagePreference>()
    private var expandKey: String? = null
    private var expandTitle: CharSequence? = null
    private var collapseKey: String? = null
    private var collapseTitle: CharSequence? = null
    private var collapseIcon: Drawable? = null
    private val collapsiblePreferenceCount
        get() = max(childPreferences.size - 1, 0) + subsectionPreferenceCount
    private val subsectionPreferenceCount
        get() = subsectionCategory?.preferenceCount ?: 0

    var expandContentDescription: Int = 0
        set(value) {
            field = value
            expandPreference?.btnContentDescription = expandContentDescription
        }

    init {
        isPersistent = false // This group doesn't store data
        layoutResource = R.layout.settingslib_banner_message_preference_group

        initAttributes(context, attrs, defStyleAttr)
    }

    override fun addPreference(preference: Preference): Boolean {
        if (preference !is BannerMessagePreference) {
            return false
        }

        if (childPreferences.contains(preference)) return true

        val wasAdded = super.addPreference(preference)
        if (wasAdded) {
            childPreferences.add(preference)
            maybeCreateExpandCollapsePreference()
            updateCollapsedItemCount()
            updateVisibilities()
        }
        return wasAdded
    }

    override fun removePreference(preference: Preference): Boolean {
        if (preference !is BannerMessagePreference) {
            return false
        }

        val wasRemoved = super.removePreference(preference)
        if (wasRemoved) {
            childPreferences.remove(preference)
            updateCollapsedItemCount()
            updateVisibilities()
            return true
        }

        if (subsectionCategory?.removePreference(preference) == true) {
            updateCollapsedItemCount()
            updateVisibilities()
            return true
        }

        return false
    }

    override fun removeAll() {
        subsectionCategory?.removeAll()
        childPreferences.forEach { child ->
            super.removePreference(child)
        }
        childPreferences.clear()
        updateCollapsedItemCount()
        updateVisibilities()
    }

    /** Sets the title of the expand button shown when this group is collapsed. */
    override fun setTitle(title: CharSequence?) {
        expandTitle = title
    }

    /** Sets the title of the collapse button shown when this group is expanded. */
    fun setCollapseTitle(title: CharSequence?) {
        collapseTitle = title
    }

    /**
     * Adds a titled subsection to this group.
     *
     * The subsection displays beneath any directly added BannerMessagePreferences (via
     * [addPreference]. It may only contain [BannerMessagePreference]s. When the group is expanded,
     * the subsection and its contents are shown. They're hidden when the group is collapsed.
     * Only one subsection can be added. Subsequent calls will be no-ops.
     */
    fun addSubsection(subsectionTitle: CharSequence) {
        if (subsectionCategory == null) {
            subsectionCategory = PreferenceCategory(context).apply {
                key = SUBSECTION_KEY
                title = subsectionTitle
                order = SUBSECTION_ORDER
                super.addPreference(this)
            }
        }
    }

    /** Sets the title of the subsection, if one has been added. */
    fun setSubsectionTitle(subsectionTitle: CharSequence?) {
        subsectionCategory?.title = subsectionTitle
    }

    /** Removes the subsection, if one has been added. */
    fun removeSubsection() {
      subsectionCategory?.let {
        super.removePreference(it)
        subsectionCategory = null
      }
      updateCollapsedItemCount()
      updateVisibilities()
    }

    /**
     * Adds a preference to this group's subsection. No-ops if the subsection hasn't been created
     * previously.
     *
     * The preference can be removed by calling [removePreference] or [removePreferenceRecursively].
     */
    fun addSubsectionPreference(preference: BannerMessagePreference) {
        subsectionCategory?.addPreference(preference)
        updateCollapsedItemCount()
        updateVisibilities()
    }

    override fun removePreferenceRecursively(key: CharSequence): Boolean {
        val preference = findPreference<Preference>(key) ?: return false

        if (preference !is BannerMessagePreference) {
            return false
        }

        val wasRemoved = super.removePreferenceRecursively(key)
        if (wasRemoved) {
            childPreferences.remove(preference)
            updateCollapsedItemCount()
            updateVisibilities()
        }
        return wasRemoved
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        maybeCreateExpandCollapsePreference()
        updateVisibilities()
    }

    private fun maybeCreateExpandCollapsePreference() {
        if (collapsiblePreferenceCount > 0) {
            if (expandPreference == null) {
                expandPreference = NumberButtonPreference(context).apply {
                    key = expandKey
                    title = expandTitle
                    count = collapsiblePreferenceCount
                    btnContentDescription = expandContentDescription
                    order = EXPAND_ORDER
                    clickListener = View.OnClickListener {
                        toggleExpansion()
                    }
                    isVisible = true
                    super.addPreference(this)
                }
            }
            if (collapsePreference == null) {
                collapsePreference = SectionButtonPreference(context).apply {
                    key = collapseKey
                    title = collapseTitle
                    icon = collapseIcon
                    order = COLLAPSE_ORDER
                    setOnClickListener { toggleExpansion() }
                    super.addPreference(this)
                }
            }
        }
    }

    private fun updateVisibilities() {
        childPreferences.forEachIndexed { i, childBanner ->
            // The first BannerMessagePref is always visible.
            childBanner.isVisible = i == 0 || isExpanded
        }

        expandPreference?.isVisible = !isExpanded && collapsiblePreferenceCount > 0
        collapsePreference?.isVisible = isExpanded && collapsiblePreferenceCount > 0
        subsectionCategory?.isVisible = isExpanded && subsectionPreferenceCount > 0
    }

    private fun updateCollapsedItemCount() {
        expandPreference?.let {
            it.count = collapsiblePreferenceCount
        }
    }

    private fun toggleExpansion() {
        isExpanded = !isExpanded
        updateVisibilities()
    }

    private fun initAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.BannerMessagePreferenceGroup, defStyleAttr, 0
        ).apply {
            expandKey = getString(R.styleable.BannerMessagePreferenceGroup_expandKey)
            expandTitle = getString(R.styleable.BannerMessagePreferenceGroup_expandTitle)
            collapseKey = getString(R.styleable.BannerMessagePreferenceGroup_collapseKey)
            collapseTitle = getString(R.styleable.BannerMessagePreferenceGroup_collapseTitle)
            collapseIcon = getDrawable(R.styleable.BannerMessagePreferenceGroup_collapseIcon)
            recycle()
        }
    }

    companion object {
        private const val SUBSECTION_KEY = "banner_message_preference_group_subsection"

        // Arbitrary large order numbers for the three preferences
        // needed to make sure any Banners are added above them
        private const val SUBSECTION_ORDER = 98
        private const val EXPAND_ORDER = 99
        private const val COLLAPSE_ORDER = 100
    }
}